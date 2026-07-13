package com.newsmead.gaze

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CaptureRequest
import android.os.SystemClock
import android.util.Log
import android.util.Range
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.newsmead.data.StudyConfig
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Local raw gaze source backed by CameraX and MediaPipe FaceLandmarker.
 *
 * It emits normalized iris-in-eye ratios. The values are deliberately raw
 * features, not screen coordinates; 16-point calibration maps them to screen px.
 */
class MediaPipeRawGazeSource(
    private val context: Context,
    private val modelAssetPath: String = MODEL_ASSET_PATH,
) : LocalRawGazeSource {

    private val appContext = context.applicationContext
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private var listener: LocalRawGazeSource.OnRawGaze? = null
    private var fpsListener: LocalRawGazeSource.OnFps? = null
    private var blinkStatsListener: LocalRawGazeSource.OnBlinkStats? = null
    private var faceLandmarker: FaceLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var stopped = true
    private var lastTimestampMs = 0L
    private var resultCount = 0
    private var blinkState = false
    private var blinkLogCounter = 0
    private var fps = 0f
    private var totalResults = 0L
    private var emittedSamples = 0L
    private var blinkDroppedFrames = 0L
    private var noFaceFrames = 0L
    private var lastOpenness = Float.NaN
    private var lastResultTimeMs = 0L
    private val lifecycleLock = Any()

    override fun setOnRawGaze(listener: LocalRawGazeSource.OnRawGaze) {
        this.listener = listener
    }

    override fun setOnFps(listener: LocalRawGazeSource.OnFps) {
        this.fpsListener = listener
    }

    override fun setOnBlinkStats(listener: LocalRawGazeSource.OnBlinkStats) {
        this.blinkStatsListener = listener
    }

    override fun start(owner: LifecycleOwner) {
        if (!hasCameraPermission()) {
            requestCameraPermissionIfPossible()
            Log.w(TAG, "Camera permission missing; grant CAMERA permission and start gaze again")
            return
        }

        stopped = false
        try {
            faceLandmarker = createFaceLandmarker()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create FaceLandmarker. Ensure $modelAssetPath exists in app/src/main/assets", e)
            return
        }

        val providerFuture = ProcessCameraProvider.getInstance(appContext)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                bindCamera(provider, owner)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start CameraX gaze source", e)
                stop()
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    override fun stop() {
        synchronized(lifecycleLock) {
            stopped = true
            busy.set(false)
            cameraProvider?.unbindAll()
            cameraProvider = null
            faceLandmarker?.close()
            faceLandmarker = null
        }
    }

    fun shutdown() {
        stop()
        cameraExecutor.shutdown()
    }

    /** Prefer the GPU delegate for frame rate; fall back to CPU if it won't init. */
    private fun createFaceLandmarker(): FaceLandmarker =
        try {
            buildFaceLandmarker(Delegate.GPU).also { Log.i(TAG, "FaceLandmarker using GPU delegate") }
        } catch (gpuError: Exception) {
            Log.w(TAG, "GPU delegate unavailable, falling back to CPU", gpuError)
            buildFaceLandmarker(Delegate.CPU).also { Log.i(TAG, "FaceLandmarker using CPU delegate") }
        }

    private fun buildFaceLandmarker(delegate: Delegate): FaceLandmarker {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(modelAssetPath)
            .setDelegate(delegate)
            .build()

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { result, input -> onFaceLandmarkerResult(result, input) }
            .setErrorListener { error ->
                busy.set(false)
                Log.e(TAG, "FaceLandmarker error", error)
            }
            .build()

        return FaceLandmarker.createFromOptions(appContext, options)
    }

    @OptIn(markerClass = [ExperimentalCamera2Interop::class])
    private fun bindCamera(provider: ProcessCameraProvider, owner: LifecycleOwner) {
        // Cap the analysis resolution and pin the camera to 30 fps. MediaPipe on a
        // full-resolution stream drops well below real time, which starves the
        // median/One Euro smoothing and wrecks vertical accuracy. 480x360 @ 30 fps
        // is the Stage 1 profile the prototype validated on the A56.
        val analysisBuilder = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(480, 360),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                        ),
                    )
                    .build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)

        Camera2Interop.Extender(analysisBuilder)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(30, 30))

        val analysis = analysisBuilder.build().also { imageAnalysis ->
            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy -> analyze(imageProxy) }
        }

        provider.unbindAll()
        provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
        Log.i(TAG, String.format(Locale.US, "Local MediaPipe raw gaze source started; blinkClose=%.3f blinkOpen=%.3f", StudyConfig.GAZE_BLINK_CLOSE_THRESHOLD, StudyConfig.GAZE_BLINK_OPEN_THRESHOLD))
    }

    private fun analyze(imageProxy: ImageProxy) {
        val landmarker = synchronized(lifecycleLock) {
            if (stopped) null else faceLandmarker
        }
        if (landmarker == null) {
            imageProxy.close()
            return
        }
        if (!busy.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        try {
            if (stopped) return
            val bitmap = imageProxy.toBitmapArgb8888().uprightMirrored(imageProxy.imageInfo.rotationDegrees)
            val mpImage = BitmapImageBuilder(bitmap).build()
            landmarker.detectAsync(mpImage, nextTimestampMs())
        } catch (e: Exception) {
            busy.set(false)
            Log.e(TAG, "Failed to analyze camera frame", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun onFaceLandmarkerResult(result: FaceLandmarkerResult, input: MPImage) {
        busy.set(false)
        totalResults += 1
        updateFps()
        fpsListener?.onFps(fps)
        val face = result.faceLandmarks().firstOrNull()
        if (face == null) {
            noFaceFrames += 1
            emitBlinkStats()
            Log.d(TAG, "No face landmarks")
            return
        }
        if (face.size <= MIN_REQUIRED_LANDMARK_INDEX) {
            noFaceFrames += 1
            emitBlinkStats()
            return
        }

        // Drop blink frames: during a blink the iris/eyelid landmarks are
        // unreliable, so the gaze feature would be garbage. Matches the prototype,
        // which dropped blink frames before storing/mapping calibration samples.
        val openness = computeOpenness(face, input.width, input.height)
        lastOpenness = openness
        if (updateBlink(openness)) {
            blinkDroppedFrames += 1
            emitBlinkStats()
            return
        }

        val leftIris = center(face, LEFT_IRIS)
        val rightIris = center(face, RIGHT_IRIS)
        val gaze = computeGaze(face, leftIris, rightIris)
        if (++resultCount % RAW_LOG_INTERVAL == 0) {
            Log.d(TAG, String.format(Locale.US, "raw gaze=(%.4f, %.4f) fps=%.1f", gaze[0], gaze[1], fps))
        }
        emittedSamples += 1
        emitBlinkStats()
        listener?.onRawGaze(gaze[0], gaze[1], System.currentTimeMillis())
    }
    /** Average (normalized) position over a contiguous landmark range. */
    private fun center(lm: List<NormalizedLandmark>, range: IntRange): FloatArray {
        var x = 0f
        var y = 0f
        for (i in range) {
            x += lm[i].x()
            y += lm[i].y()
        }
        val n = range.count()
        return floatArrayOf(x / n, y / n)
    }

    /**
     * Iris position relative to each eye's corners (horizontal) and eyelids
     * (vertical), averaged across both eyes. Each iris ring is paired with its
     * nearer eye so the corner math holds regardless of MediaPipe's left/right
     * ordering or the frame mirroring.
     */
    private fun computeGaze(lm: List<NormalizedLandmark>, irisA: FloatArray, irisB: FloatArray): FloatArray {
        val eye1cx = (lm[EYE1_CORNER_A].x() + lm[EYE1_CORNER_B].x()) / 2f
        val (iris1, iris2) =
            if (abs(irisA[0] - eye1cx) <= abs(irisB[0] - eye1cx)) irisA to irisB else irisB to irisA

        val h1 = frac(iris1[0], lm[EYE1_CORNER_A].x(), lm[EYE1_CORNER_B].x())
        val v1 = frac(iris1[1], lm[EYE1_LID_TOP].y(), lm[EYE1_LID_BOTTOM].y())
        val h2 = frac(iris2[0], lm[EYE2_CORNER_A].x(), lm[EYE2_CORNER_B].x())
        val v2 = frac(iris2[1], lm[EYE2_LID_TOP].y(), lm[EYE2_LID_BOTTOM].y())
        return floatArrayOf((h1 + h2) / 2f, (v1 + v2) / 2f)
    }

    /** Fraction of [v] between bounds [a] and [b] (order-independent). */
    private fun frac(v: Float, a: Float, b: Float): Float {
        val lo = min(a, b)
        val hi = max(a, b)
        val d = hi - lo
        return if (d <= 1e-6f) 0.5f else (v - lo) / d
    }

    /**
     * Eye aspect ratio (vertical eyelid gap / horizontal eye width) averaged over
     * both eyes, computed in pixels so the ratio is aspect-correct. Near 0 when the
     * eyes are closed; compared against the blink thresholds.
     */
    private fun computeOpenness(lm: List<NormalizedLandmark>, w: Int, h: Int): Float {
        val e1 = eyeAspect(lm, EYE1_LID_TOP, EYE1_LID_BOTTOM, EYE1_CORNER_A, EYE1_CORNER_B, w, h)
        val e2 = eyeAspect(lm, EYE2_LID_TOP, EYE2_LID_BOTTOM, EYE2_CORNER_A, EYE2_CORNER_B, w, h)
        return (e1 + e2) / 2f
    }

    private fun eyeAspect(
        lm: List<NormalizedLandmark>,
        top: Int, bottom: Int, cornerA: Int, cornerB: Int,
        w: Int, h: Int,
    ): Float {
        val vGap = abs(lm[top].y() - lm[bottom].y()) * h
        val hWidth = abs(lm[cornerA].x() - lm[cornerB].x()) * w
        return if (hWidth <= 1e-3f) 0f else vGap / hWidth
    }

    /**
     * Blink state with hysteresis: enter the blink state when openness falls below
     * the configured close threshold, and leave it only when openness rises back above the open threshold.
     * The dead-band between the two stops the state chattering when openness hovers
     * near a single threshold. Returns true while a blink is in progress.
     */
    private fun updateBlink(openness: Float): Boolean {
        val prev = blinkState
        blinkState = when {
            openness < StudyConfig.GAZE_BLINK_CLOSE_THRESHOLD -> true
            openness > StudyConfig.GAZE_BLINK_OPEN_THRESHOLD -> false
            else -> blinkState
        }
        if (blinkState != prev || ++blinkLogCounter % RAW_LOG_INTERVAL == 0) {
            Log.d(
                TAG,
                String.format(
                    Locale.US,
                    "open=%.3f blink=%b close=%.3f openTh=%.3f drop=%d emit=%d noFace=%d",
                    openness,
                    blinkState,
                    StudyConfig.GAZE_BLINK_CLOSE_THRESHOLD,
                    StudyConfig.GAZE_BLINK_OPEN_THRESHOLD,
                    blinkDroppedFrames,
                    emittedSamples,
                    noFaceFrames,
                ),
            )
        }
        return blinkState
    }
    private fun ImageProxy.toBitmapArgb8888(): Bitmap {
        val plane = planes[0]
        val buffer = plane.buffer
        buffer.rewind()

        val pixelStride = plane.pixelStride.coerceAtLeast(1)
        val rowStride = plane.rowStride
        val rowWidth = rowStride / pixelStride
        val rowBitmap = Bitmap.createBitmap(rowWidth, height, Bitmap.Config.ARGB_8888)
        rowBitmap.copyPixelsFromBuffer(buffer)
        return if (rowWidth == width) {
            rowBitmap
        } else {
            Bitmap.createBitmap(rowBitmap, 0, 0, width, height)
        }
    }
    private fun emitBlinkStats() {
        blinkStatsListener?.onBlinkStats(
            LocalRawGazeSource.BlinkStats(
                totalResults = totalResults,
                emittedSamples = emittedSamples,
                blinkDroppedFrames = blinkDroppedFrames,
                noFaceFrames = noFaceFrames,
                lastOpenness = lastOpenness,
                blink = blinkState,
                closeThreshold = StudyConfig.GAZE_BLINK_CLOSE_THRESHOLD,
                openThreshold = StudyConfig.GAZE_BLINK_OPEN_THRESHOLD,
            )
        )
    }
    private fun nextTimestampMs(): Long {
        val now = System.currentTimeMillis()
        val next = if (now <= lastTimestampMs) lastTimestampMs + 1 else now
        lastTimestampMs = next
        return next
    }

    /** Exponential moving average of the inference result rate (frames/sec). */
    private fun updateFps() {
        val now = SystemClock.uptimeMillis()
        if (lastResultTimeMs != 0L) {
            val dt = now - lastResultTimeMs
            if (dt > 0) {
                val instantaneous = 1000f / dt
                fps = if (fps == 0f) instantaneous else fps * 0.9f + instantaneous * 0.1f
            }
        }
        lastResultTimeMs = now
    }

    /** Rotate upright and mirror horizontally for the front camera (selfie view). */
    private fun Bitmap.uprightMirrored(rotationDegrees: Int): Bitmap {
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
            postScale(-1f, 1f) // front camera: mirror horizontally
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermissionIfPossible() {
        val activity = context as? Activity ?: return
        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
    }

    companion object {
        private const val TAG = "MediaPipeGaze"
        private const val MODEL_ASSET_PATH = "face_landmarker.task"
        private const val CAMERA_PERMISSION_REQUEST = 4104
        private const val RAW_LOG_INTERVAL = 15

        // 478-landmark model: iris points are contiguous ranges (inclusive).
        private val LEFT_IRIS = 468..472
        private val RIGHT_IRIS = 473..477

        // Eye contour landmarks for the eye-corner-relative gaze feature.
        private const val EYE1_CORNER_A = 33
        private const val EYE1_CORNER_B = 133
        private const val EYE1_LID_TOP = 159
        private const val EYE1_LID_BOTTOM = 145
        private const val EYE2_CORNER_A = 263
        private const val EYE2_CORNER_B = 362
        private const val EYE2_LID_TOP = 386
        private const val EYE2_LID_BOTTOM = 374
        private const val MIN_REQUIRED_LANDMARK_INDEX = 477
    }
}
