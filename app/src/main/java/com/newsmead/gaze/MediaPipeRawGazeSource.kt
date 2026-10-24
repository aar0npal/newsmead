package com.newsmead.gaze

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import kotlin.math.atan2
import kotlin.math.hypot
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
    private val featureMode: RawGazeFeatureMode = RawGazeFeatureMode.EYE_LOCAL_WIDTH_AVERAGE_V1,
) : LocalRawGazeSource {

    private val appContext = context.applicationContext
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private var listener: LocalRawGazeSource.OnRawGaze? = null
    private var fpsListener: LocalRawGazeSource.OnFps? = null
    private var blinkStatsListener: LocalRawGazeSource.OnBlinkStats? = null
    @Volatile private var diagnosticsListener: LocalRawGazeSource.OnDiagnostics? = null
    @Volatile private var hybridEyeDiagnosticsListener: LocalRawGazeSource.OnHybridEyeDiagnostics? = null
    private var faceLandmarker: FaceLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var stopped = true
    private var lastTimestampMs = 0L
    private var frameSizeLogged = false
    private var resultCount = 0
    private var blinkState = false
    private var blinkLogCounter = 0
    private var fps = 0f
    private var totalResults = 0L
    private var emittedSamples = 0L
    private var blinkDroppedFrames = 0L
    private var noFaceFrames = 0L
    private var busyDroppedFrames = 0L
    private var diagnosticSequence = 0L
    private var lastOpenness = Float.NaN
    private var lastResultTimeMs = 0L
    private var lastFpsCallbackElapsedNs = 0L
    private var inFlightCaptureTimestampNs = 0L
    private var inFlightSubmittedElapsedNs = 0L
    private var inFlightRotationDegrees = 0
    @Volatile private var inFlightHybridBitmap: Bitmap? = null
    private val lifecycleLock = Any()
    private val frameTransformer = ReusableArgbFrameTransformer()
    private var hybridEyeShadow: HybridEyeShadowBackend? = null

    override fun setOnRawGaze(listener: LocalRawGazeSource.OnRawGaze) {
        this.listener = listener
    }

    override fun setOnFps(listener: LocalRawGazeSource.OnFps) {
        this.fpsListener = listener
    }

    override fun setOnBlinkStats(listener: LocalRawGazeSource.OnBlinkStats) {
        this.blinkStatsListener = listener
    }

    override fun setOnDiagnostics(listener: LocalRawGazeSource.OnDiagnostics) {
        diagnosticsListener = listener
    }

    override fun clearOnDiagnostics() {
        diagnosticsListener = null
    }

    override fun setOnHybridEyeDiagnostics(listener: LocalRawGazeSource.OnHybridEyeDiagnostics?) {
        hybridEyeDiagnosticsListener = listener
    }

    override fun start(owner: LifecycleOwner) {
        if (!hasCameraPermission()) {
            requestCameraPermissionIfPossible()
            Log.w(TAG, "Camera permission missing; grant CAMERA permission and start gaze again")
            return
        }

        stopped = false
        if (StudyConfig.GAZE_HYBRID_SHADOW_ENABLED && hybridEyeShadow == null) {
            hybridEyeShadow = HybridEyeShadowBackend(appContext) { sample ->
                hybridEyeDiagnosticsListener?.onDiagnostics(sample)
            }
        }
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
            inFlightHybridBitmap = null
            hybridEyeShadow?.close()
            hybridEyeShadow = null
            hybridEyeDiagnosticsListener = null
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
                inFlightHybridBitmap = null
                Log.e(TAG, "FaceLandmarker error", error)
            }
            .build()

        return FaceLandmarker.createFromOptions(appContext, options)
    }

    @OptIn(markerClass = [ExperimentalCamera2Interop::class])
    private fun bindCamera(provider: ProcessCameraProvider, owner: LifecycleOwner) {
        // Cap the analysis resolution and pin the camera to 30 fps. MediaPipe on a
        // full-resolution stream drops well below real time, which starves the
        // median/One Euro smoothing and wrecks vertical accuracy. The former
        // 480x360 request was not supported by the A56 and its lower-first fallback
        // silently negotiated 320x240. Request the standard 640x480 profile so the
        // iris/eyelid geometry has four times as many source pixels while retaining
        // the same 4:3 aspect ratio and 30 fps target.
        val analysisBuilder = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
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
        Log.i(
            TAG,
            String.format(
                Locale.US,
                "Local MediaPipe raw gaze source started; feature=%s blinkClose=%.3f blinkOpen=%.3f",
                featureMode.logLabel,
                StudyConfig.GAZE_BLINK_CLOSE_THRESHOLD,
                StudyConfig.GAZE_BLINK_OPEN_THRESHOLD,
            ),
        )
    }

    private fun analyze(imageProxy: ImageProxy) {
        if (!frameSizeLogged) {
            frameSizeLogged = true
            Log.i(
                TAG,
                "CameraX analysis frame=${imageProxy.width}x${imageProxy.height} " +
                    "rotation=${imageProxy.imageInfo.rotationDegrees}",
            )
        }
        val landmarker = synchronized(lifecycleLock) {
            if (stopped) null else faceLandmarker
        }
        if (landmarker == null) {
            imageProxy.close()
            return
        }
        if (!busy.compareAndSet(false, true)) {
            busyDroppedFrames += 1
            emitDiagnostics(
                outcome = LocalRawGazeSource.DiagnosticOutcome.BUSY_DROPPED,
                captureTimestampNs = imageProxy.imageInfo.timestamp,
                submittedElapsedNs = 0L,
                resultElapsedNs = SystemClock.elapsedRealtimeNanos(),
                frameWidth = imageProxy.width,
                frameHeight = imageProxy.height,
                rotationDegrees = imageProxy.imageInfo.rotationDegrees,
            )
            imageProxy.close()
            return
        }

        try {
            if (stopped) return
            inFlightCaptureTimestampNs = imageProxy.imageInfo.timestamp
            inFlightSubmittedElapsedNs = SystemClock.elapsedRealtimeNanos()
            inFlightRotationDegrees = imageProxy.imageInfo.rotationDegrees
            val bitmap = frameTransformer.copyAndTransform(imageProxy, inFlightRotationDegrees)
            inFlightHybridBitmap = if (hybridEyeShadow != null) bitmap else null
            val mpImage = BitmapImageBuilder(bitmap).build()
            landmarker.detectAsync(mpImage, nextTimestampMs())
        } catch (e: Exception) {
            busy.set(false)
            inFlightHybridBitmap = null
            Log.e(TAG, "Failed to analyze camera frame", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun onFaceLandmarkerResult(result: FaceLandmarkerResult, input: MPImage) {
        val face = result.faceLandmarks().firstOrNull()
        // Copy before releasing the source busy guard; the frame transformer reuses its bitmap.
        val hybridBitmap = if (face != null && hybridEyeShadow?.canAcceptFrame() == true) {
            inFlightHybridBitmap?.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            null
        }
        val hybridCaptureTimestampNs = inFlightCaptureTimestampNs
        inFlightHybridBitmap = null
        busy.set(false)
        val resultElapsedNs = SystemClock.elapsedRealtimeNanos()
        totalResults += 1
        updateFps()
        emitFpsIfDue(resultElapsedNs)
        if (face == null) {
            noFaceFrames += 1
            emitDiagnostics(
                outcome = LocalRawGazeSource.DiagnosticOutcome.NO_FACE,
                resultElapsedNs = resultElapsedNs,
                frameWidth = input.width,
                frameHeight = input.height,
            )
            emitBlinkStats()
            Log.d(TAG, "No face landmarks")
            return
        }
        if (face.size <= MIN_REQUIRED_LANDMARK_INDEX) {
            hybridBitmap?.recycle()
            noFaceFrames += 1
            emitDiagnostics(
                outcome = LocalRawGazeSource.DiagnosticOutcome.INSUFFICIENT_LANDMARKS,
                resultElapsedNs = resultElapsedNs,
                frameWidth = input.width,
                frameHeight = input.height,
            )
            emitBlinkStats()
            return
        }

        val leftIris = center(face, LEFT_IRIS)
        val rightIris = center(face, RIGHT_IRIS)
        val gaze = computeGazeDetails(face, leftIris, rightIris, input.width, input.height)
        val posture = computePosture(face, input.width, input.height)

        // Drop blink frames: during a blink the iris/eyelid landmarks are
        // unreliable, so the gaze feature would be garbage. Matches the prototype,
        // which dropped blink frames before storing/mapping calibration samples.
        val eyeOpenness = computeEyeOpenness(face, input.width, input.height)
        val openness = (eyeOpenness[0] + eyeOpenness[1]) / 2f
        lastOpenness = openness
        if (updateBlink(openness)) {
            hybridBitmap?.recycle()
            blinkDroppedFrames += 1
            emitDiagnostics(
                outcome = LocalRawGazeSource.DiagnosticOutcome.BLINK_DROPPED,
                resultElapsedNs = resultElapsedNs,
                frameWidth = input.width,
                frameHeight = input.height,
                gaze = gaze,
                eyeOpenness = eyeOpenness,
                posture = posture,
            )
            emitBlinkStats()
            return
        }

        if (hybridBitmap != null) {
            hybridEyeShadow?.submit(
                bitmap = hybridBitmap,
                referenceHorizontal = gaze[4],
                referenceVertical = gaze[5],
                captureTimestampNs = hybridCaptureTimestampNs,
            ) ?: hybridBitmap.recycle()
        }

        if (++resultCount % RAW_LOG_INTERVAL == 0) {
            Log.d(TAG, String.format(Locale.US, "raw gaze=(%.4f, %.4f) fps=%.1f", gaze[4], gaze[5], fps))
        }
        emittedSamples += 1
        emitDiagnostics(
            outcome = LocalRawGazeSource.DiagnosticOutcome.EMITTED,
            resultElapsedNs = resultElapsedNs,
            frameWidth = input.width,
            frameHeight = input.height,
            gaze = gaze,
            eyeOpenness = eyeOpenness,
            posture = posture,
        )
        emitBlinkStats()
        listener?.onRawGaze(
            LocalRawGazeSource.Sample(
                gazeX = gaze[4],
                gazeY = gaze[5],
                timestampMs = System.currentTimeMillis(),
                eye1X = gaze[0],
                eye1Y = gaze[1],
                eye2X = gaze[2],
                eye2Y = gaze[3],
                faceCenterX = posture.faceCenterX,
                faceCenterY = posture.faceCenterY,
                faceScale = posture.faceScale,
                headRollDeg = posture.headRollDeg,
            ),
        )
    }

    /** Passive pose geometry; never used to calculate or modify gaze. */
    private fun computePosture(
        lm: List<NormalizedLandmark>,
        frameWidth: Int,
        frameHeight: Int,
    ): PostureFeatures {
        val eye1X = (lm[EYE1_CORNER_A].x() + lm[EYE1_CORNER_B].x()) / 2f
        val eye1Y = (lm[EYE1_CORNER_A].y() + lm[EYE1_CORNER_B].y()) / 2f
        val eye2X = (lm[EYE2_CORNER_A].x() + lm[EYE2_CORNER_B].x()) / 2f
        val eye2Y = (lm[EYE2_CORNER_A].y() + lm[EYE2_CORNER_B].y()) / 2f
        // Order by image x so an eye-index convention cannot turn a near-zero
        // roll into an angle near 180 degrees.
        val (leftX, leftY, rightX, rightY) = if (eye1X <= eye2X) {
            floatArrayOf(eye1X, eye1Y, eye2X, eye2Y)
        } else {
            floatArrayOf(eye2X, eye2Y, eye1X, eye1Y)
        }
        val dxPx = (rightX - leftX) * frameWidth
        val dyPx = (rightY - leftY) * frameHeight
        return PostureFeatures(
            faceCenterX = (leftX + rightX) / 2f,
            faceCenterY = (leftY + rightY) / 2f,
            faceScale = hypot(dxPx, dyPx) / frameWidth,
            headRollDeg = Math.toDegrees(atan2(dyPx, dxPx).toDouble()).toFloat(),
        )
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
    private fun computeGazeDetails(
        lm: List<NormalizedLandmark>,
        irisA: FloatArray,
        irisB: FloatArray,
        frameWidth: Int,
        frameHeight: Int,
    ): FloatArray {
        val eye1cx = (lm[EYE1_CORNER_A].x() + lm[EYE1_CORNER_B].x()) / 2f
        val (iris1, iris2) =
            if (abs(irisA[0] - eye1cx) <= abs(irisB[0] - eye1cx)) irisA to irisB else irisB to irisA

        val (h1, v1, h2, v2) = when (featureMode) {
            RawGazeFeatureMode.EYELID_FRACTION_AVERAGE -> floatArrayOf(
                frac(iris1[0], lm[EYE1_CORNER_A].x(), lm[EYE1_CORNER_B].x()),
                frac(iris1[1], lm[EYE1_LID_TOP].y(), lm[EYE1_LID_BOTTOM].y()),
                frac(iris2[0], lm[EYE2_CORNER_A].x(), lm[EYE2_CORNER_B].x()),
                frac(iris2[1], lm[EYE2_LID_TOP].y(), lm[EYE2_LID_BOTTOM].y()),
            )
            RawGazeFeatureMode.EYE_LOCAL_WIDTH_AVERAGE_V1 -> {
                val eye1 = eyeLocalFeature(
                    lm,
                    iris1,
                    EYE1_CORNER_A,
                    EYE1_CORNER_B,
                    frameWidth,
                    frameHeight,
                )
                val eye2 = eyeLocalFeature(
                    lm,
                    iris2,
                    EYE2_CORNER_A,
                    EYE2_CORNER_B,
                    frameWidth,
                    frameHeight,
                )
                floatArrayOf(eye1.horizontal, eye1.vertical, eye2.horizontal, eye2.vertical)
            }
        }
        return floatArrayOf(h1, v1, h2, v2, (h1 + h2) / 2f, (v1 + v2) / 2f)
    }

    private fun eyeLocalFeature(
        lm: List<NormalizedLandmark>,
        iris: FloatArray,
        cornerA: Int,
        cornerB: Int,
        frameWidth: Int,
        frameHeight: Int,
    ): EyeLocalGazeGeometry.Feature = EyeLocalGazeGeometry.feature(
        iris = EyeLocalGazeGeometry.Point(iris[0] * frameWidth, iris[1] * frameHeight),
        cornerA = EyeLocalGazeGeometry.Point(
            lm[cornerA].x() * frameWidth,
            lm[cornerA].y() * frameHeight,
        ),
        cornerB = EyeLocalGazeGeometry.Point(
            lm[cornerB].x() * frameWidth,
            lm[cornerB].y() * frameHeight,
        ),
    )

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
    private fun computeEyeOpenness(lm: List<NormalizedLandmark>, w: Int, h: Int): FloatArray {
        val e1 = eyeAspect(lm, EYE1_LID_TOP, EYE1_LID_BOTTOM, EYE1_CORNER_A, EYE1_CORNER_B, w, h)
        val e2 = eyeAspect(lm, EYE2_LID_TOP, EYE2_LID_BOTTOM, EYE2_CORNER_A, EYE2_CORNER_B, w, h)
        return floatArrayOf(e1, e2)
    }

    private fun emitDiagnostics(
        outcome: LocalRawGazeSource.DiagnosticOutcome,
        captureTimestampNs: Long = inFlightCaptureTimestampNs,
        submittedElapsedNs: Long = inFlightSubmittedElapsedNs,
        resultElapsedNs: Long,
        frameWidth: Int,
        frameHeight: Int,
        rotationDegrees: Int = inFlightRotationDegrees,
        gaze: FloatArray? = null,
        eyeOpenness: FloatArray? = null,
        posture: PostureFeatures? = null,
    ) {
        val sink = diagnosticsListener ?: return
        sink.onDiagnostics(
            LocalRawGazeSource.Diagnostics(
                sequence = ++diagnosticSequence,
                outcome = outcome,
                captureTimestampNs = captureTimestampNs,
                submittedElapsedNs = submittedElapsedNs,
                resultElapsedNs = resultElapsedNs,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                rotationDegrees = rotationDegrees,
                eye1X = gaze?.getOrNull(0) ?: Float.NaN,
                eye1Y = gaze?.getOrNull(1) ?: Float.NaN,
                eye2X = gaze?.getOrNull(2) ?: Float.NaN,
                eye2Y = gaze?.getOrNull(3) ?: Float.NaN,
                gazeX = gaze?.getOrNull(4) ?: Float.NaN,
                gazeY = gaze?.getOrNull(5) ?: Float.NaN,
                eye1Openness = eyeOpenness?.getOrNull(0) ?: Float.NaN,
                eye2Openness = eyeOpenness?.getOrNull(1) ?: Float.NaN,
                faceCenterX = posture?.faceCenterX ?: Float.NaN,
                faceCenterY = posture?.faceCenterY ?: Float.NaN,
                faceScale = posture?.faceScale ?: Float.NaN,
                headRollDeg = posture?.headRollDeg ?: Float.NaN,
                busyDroppedFrames = busyDroppedFrames,
            ),
        )
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

    /** UI and lightweight logs need a readable summary, not one callback per result. */
    private fun emitFpsIfDue(resultElapsedNs: Long) {
        val callback = fpsListener ?: return
        if (
            lastFpsCallbackElapsedNs == 0L ||
            resultElapsedNs - lastFpsCallbackElapsedNs >= FPS_CALLBACK_INTERVAL_NS
        ) {
            lastFpsCallbackElapsedNs = resultElapsedNs
            callback.onFps(fps)
        }
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
        private const val FPS_CALLBACK_INTERVAL_NS = 250_000_000L

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
