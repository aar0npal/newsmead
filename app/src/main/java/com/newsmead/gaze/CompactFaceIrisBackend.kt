package com.newsmead.gaze

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.exp
import kotlin.math.ceil

internal data class CompactFaceIrisSample(
    val captureTimestampNs: Long,
    val resultElapsedNs: Long,
    val candidateHorizontal: Float,
    val candidateVertical: Float,
    val rightHorizontal: Float,
    val rightVertical: Float,
    val leftHorizontal: Float,
    val leftVertical: Float,
    val facePresence: Float,
    val detectorRan: Boolean,
    val totalMs: Float,
    val detectorMs: Float,
    val faceLandmarkMs: Float,
    val irisMs: Float,
    val completionFps: Float,
    val busyDroppedFrames: Long,
    val sourceWidth: Int,
    val sourceHeight: Int,
)

/**
 * Research-only compact 468-point face-landmark + two-eye iris acquisition backend.
 * It produces raw candidate geometry only; it cannot emit mapped gaze or access calibration.
 */
internal class CompactFaceIrisBackend(
    context: Context,
    private val onResult: (CompactFaceIrisSample) -> Unit,
) {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "compact-face-iris").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val busy = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val busyDropped = AtomicLong(0L)

    private var detector: FaceDetector? = null
    private var faceInterpreter: Interpreter? = null
    private var irisInterpreter: Interpreter? = null
    private var faceLandmarkOutputIndex = -1
    private var facePresenceOutputIndex = -1
    private var irisEyeOutputIndex = -1
    private var irisLandmarkOutputIndex = -1
    private var cachedFaceCrop: CompactFaceIrisGeometry.FaceCrop? = null
    private var framesUntilDetector = 0

    private var faceBitmap: Bitmap? = null
    private var faceCanvas: Canvas? = null
    private var eyeBitmap: Bitmap? = null
    private var eyeCanvas: Canvas? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val facePixels = IntArray(FACE_EDGE * FACE_EDGE)
    private val eyePixels = IntArray(EYE_EDGE * EYE_EDGE)
    private val faceFloats = FloatArray(FACE_EDGE * FACE_EDGE * RGB_CHANNELS)
    private val eyeFloats = FloatArray(EYE_EDGE * EYE_EDGE * RGB_CHANNELS)
    private val faceInput = floatInput(FACE_EDGE)
    private val irisInput = floatInput(EYE_EDGE)
    private val faceFloatInput = faceInput.asFloatBuffer()
    private val irisFloatInput = irisInput.asFloatBuffer()
    private val normalizedChannel = FloatArray(256) { it / 255f }
    private val faceLandmarks = FloatArray(CompactFaceIrisGeometry.FACE_LANDMARK_FLOATS)
    private var faceLandmarkOutput: ByteBuffer? = null
    private var facePresenceOutput: ByteBuffer? = null
    private val faceOutputs = mutableMapOf<Int, Any>()
    private val eyeOutput = Array(1) { FloatArray(HybridEyeGeometry.EYE_LANDMARK_FLOATS) }
    private val irisOutput = Array(1) { FloatArray(HybridEyeGeometry.IRIS_LANDMARK_FLOATS) }
    private val irisOutputs = mutableMapOf<Int, Any>()

    private var lastCompletionNs = 0L
    private var completionFps = 0f
    private var completedFrames = 0L
    private val totalWindowMs = ArrayList<Double>(PERFORMANCE_WINDOW)
    private val detectorWindowMs = ArrayList<Double>(PERFORMANCE_WINDOW)
    private val faceWindowMs = ArrayList<Double>(PERFORMANCE_WINDOW)
    private val irisWindowMs = ArrayList<Double>(PERFORMANCE_WINDOW)

    fun canAcceptFrame(): Boolean = !closed.get() && !busy.get()

    fun noteBusyFrameDropped() {
        if (!closed.get()) busyDropped.incrementAndGet()
    }

    fun submit(bitmap: Bitmap, captureTimestampNs: Long) {
        if (closed.get() || !busy.compareAndSet(false, true)) {
            if (!closed.get()) busyDropped.incrementAndGet()
            bitmap.recycle()
            return
        }
        try {
            executor.execute {
                try {
                    process(bitmap, captureTimestampNs)
                } catch (error: Throwable) {
                    Log.e(TAG, "Compact candidate frame failed", error)
                    cachedFaceCrop = null
                    framesUntilDetector = 0
                } finally {
                    bitmap.recycle()
                    busy.set(false)
                }
            }
        } catch (_: RejectedExecutionException) {
            bitmap.recycle()
            busy.set(false)
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            executor.execute {
                detector?.close()
                detector = null
                faceInterpreter?.close()
                faceInterpreter = null
                irisInterpreter?.close()
                irisInterpreter = null
                faceBitmap?.recycle()
                faceBitmap = null
                eyeBitmap?.recycle()
                eyeBitmap = null
                cachedFaceCrop = null
            }
        } catch (_: RejectedExecutionException) {
            // Executor is already terminating.
        } finally {
            executor.shutdown()
        }
    }

    private fun process(source: Bitmap, captureTimestampNs: Long) {
        if (closed.get() || !ensureModels()) return
        val startNs = SystemClock.elapsedRealtimeNanos()
        val detectorRan = cachedFaceCrop == null || framesUntilDetector <= 0
        val faceCrop = if (detectorRan) {
            detectFaceCrop(source) ?: run {
                cachedFaceCrop = null
                framesUntilDetector = 0
                return
            }
        } else {
            framesUntilDetector -= 1
            cachedFaceCrop!!
        }
        val detectorDoneNs = SystemClock.elapsedRealtimeNanos()

        val facePresence = inferFaceLandmarks(source, faceCrop)
        if (!facePresence.isFinite() || facePresence < FACE_PRESENCE_THRESHOLD) {
            cachedFaceCrop = null
            framesUntilDetector = 0
            return
        }
        val faceDoneNs = SystemClock.elapsedRealtimeNanos()
        val crops = CompactFaceIrisGeometry.eyeCrops(faceLandmarks, faceCrop) ?: run {
            cachedFaceCrop = null
            framesUntilDetector = 0
            return
        }
        val right = inferEye(source, crops.rightEye) ?: return
        val left = inferEye(source, crops.leftEye) ?: return
        val finishedNs = SystemClock.elapsedRealtimeNanos()

        val horizontal = (right.feature.horizontal + left.feature.horizontal) / 2f
        val vertical = (right.feature.vertical + left.feature.vertical) / 2f
        if (!horizontal.isFinite() || !vertical.isFinite()) return
        updateFps(finishedNs)
        val totalMs = nsToMs(finishedNs - startNs)
        val detectorMs = if (detectorRan) nsToMs(detectorDoneNs - startNs) else 0f
        val faceMs = nsToMs(faceDoneNs - detectorDoneNs)
        val irisMs = nsToMs(finishedNs - faceDoneNs)
        recordPerformance(totalMs, detectorMs, faceMs, irisMs)
        onResult(
            CompactFaceIrisSample(
                captureTimestampNs = captureTimestampNs,
                resultElapsedNs = finishedNs,
                candidateHorizontal = horizontal,
                candidateVertical = vertical,
                rightHorizontal = right.feature.horizontal,
                rightVertical = right.feature.vertical,
                leftHorizontal = left.feature.horizontal,
                leftVertical = left.feature.vertical,
                facePresence = facePresence,
                detectorRan = detectorRan,
                totalMs = totalMs,
                detectorMs = detectorMs,
                faceLandmarkMs = faceMs,
                irisMs = irisMs,
                completionFps = completionFps,
                busyDroppedFrames = busyDropped.get(),
                sourceWidth = source.width,
                sourceHeight = source.height,
            ),
        )
    }

    private fun detectFaceCrop(source: Bitmap): CompactFaceIrisGeometry.FaceCrop? {
        val detection = detector!!.detect(BitmapImageBuilder(source).build()).detections().firstOrNull()
            ?: return null
        val keypoints = detection.keypoints().orElse(emptyList())
        if (keypoints.size < REQUIRED_FACE_KEYPOINTS) return null
        val rightEye = HybridEyeGeometry.Point(
            keypoints[RIGHT_EYE_KEYPOINT].x() * source.width,
            keypoints[RIGHT_EYE_KEYPOINT].y() * source.height,
        )
        val leftEye = HybridEyeGeometry.Point(
            keypoints[LEFT_EYE_KEYPOINT].x() * source.width,
            keypoints[LEFT_EYE_KEYPOINT].y() * source.height,
        )
        val box = detection.boundingBox()
        return CompactFaceIrisGeometry.faceCrop(
            rightEye = rightEye,
            leftEye = leftEye,
            boxLeft = box.left,
            boxTop = box.top,
            boxRight = box.right,
            boxBottom = box.bottom,
        )?.also {
            cachedFaceCrop = it
            framesUntilDetector = FACE_DETECTION_INTERVAL - 1
        }
    }

    private fun inferFaceLandmarks(
        source: Bitmap,
        crop: CompactFaceIrisGeometry.FaceCrop,
    ): Float {
        val bitmap = faceBitmap ?: Bitmap.createBitmap(FACE_EDGE, FACE_EDGE, Bitmap.Config.ARGB_8888)
            .also {
                faceBitmap = it
                faceCanvas = Canvas(it)
            }
        val canvas = faceCanvas ?: Canvas(bitmap).also { faceCanvas = it }
        drawCrop(source, canvas, FACE_EDGE.toFloat(), crop.center, crop.sidePx, crop.rotationDegrees, false)
        fillInput(bitmap, facePixels, faceFloats, faceFloatInput, faceInput)
        val landmarkBuffer = faceLandmarkOutput ?: return Float.NaN
        val presenceBuffer = facePresenceOutput ?: return Float.NaN
        landmarkBuffer.rewind()
        presenceBuffer.rewind()
        faceInterpreter!!.runForMultipleInputsOutputs(arrayOf(faceInput), faceOutputs)
        landmarkBuffer.rewind()
        landmarkBuffer.asFloatBuffer().get(faceLandmarks)
        val rawPresence = presenceBuffer.getFloat(0)
        return (1.0 / (1.0 + exp(-rawPresence.toDouble()))).toFloat()
    }

    private fun inferEye(
        source: Bitmap,
        crop: HybridEyeGeometry.CropSpec,
    ): HybridEyeGeometry.EyeSample? {
        val bitmap = eyeBitmap ?: Bitmap.createBitmap(EYE_EDGE, EYE_EDGE, Bitmap.Config.ARGB_8888)
            .also {
                eyeBitmap = it
                eyeCanvas = Canvas(it)
            }
        val canvas = eyeCanvas ?: Canvas(bitmap).also { eyeCanvas = it }
        drawCrop(
            source,
            canvas,
            EYE_EDGE.toFloat(),
            crop.center,
            crop.sidePx,
            crop.rotationDegrees,
            crop.flipHorizontally,
        )
        fillInput(bitmap, eyePixels, eyeFloats, irisFloatInput, irisInput)
        irisInterpreter!!.runForMultipleInputsOutputs(arrayOf(irisInput), irisOutputs)
        return HybridEyeGeometry.decodeEye(
            eyeLandmarks = eyeOutput[0],
            irisLandmarks = irisOutput[0],
            wasFlipped = crop.flipHorizontally,
        )
    }

    private fun drawCrop(
        source: Bitmap,
        canvas: Canvas,
        outputEdge: Float,
        center: HybridEyeGeometry.Point,
        sidePx: Float,
        rotationDegrees: Float,
        flipHorizontally: Boolean,
    ) {
        canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)
        canvas.save()
        canvas.translate(outputEdge / 2f, outputEdge / 2f)
        if (flipHorizontally) canvas.scale(-1f, 1f)
        canvas.rotate(-rotationDegrees)
        val scale = outputEdge / sidePx
        canvas.scale(scale, scale)
        canvas.translate(-center.x, -center.y)
        canvas.drawBitmap(source, 0f, 0f, paint)
        canvas.restore()
    }

    private fun fillInput(
        bitmap: Bitmap,
        pixels: IntArray,
        floats: FloatArray,
        floatInput: FloatBuffer,
        byteInput: ByteBuffer,
    ) {
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var outputIndex = 0
        for (color in pixels) {
            floats[outputIndex++] = normalizedChannel[(color ushr 16) and 0xff]
            floats[outputIndex++] = normalizedChannel[(color ushr 8) and 0xff]
            floats[outputIndex++] = normalizedChannel[color and 0xff]
        }
        floatInput.clear()
        floatInput.put(floats)
        byteInput.rewind()
    }

    private fun recordPerformance(totalMs: Float, detectorMs: Float, faceMs: Float, irisMs: Float) {
        completedFrames += 1
        totalWindowMs += totalMs.toDouble()
        detectorWindowMs += detectorMs.toDouble()
        faceWindowMs += faceMs.toDouble()
        irisWindowMs += irisMs.toDouble()
        if (totalWindowMs.size < PERFORMANCE_WINDOW) return
        Log.i(
            TAG,
            String.format(
                Locale.US,
                "COMPACT_FACE_IRIS_PERF_V2 n=%d total_median_ms=%.3f total_p95_ms=%.3f " +
                    "detector_mean_ms=%.3f face_median_ms=%.3f face_p95_ms=%.3f " +
                    "iris_median_ms=%.3f iris_p95_ms=%.3f fps=%.2f completed=%d busy_drop=%d",
                totalWindowMs.size,
                percentile(totalWindowMs, 0.50),
                percentile(totalWindowMs, 0.95),
                detectorWindowMs.average(),
                percentile(faceWindowMs, 0.50),
                percentile(faceWindowMs, 0.95),
                percentile(irisWindowMs, 0.50),
                percentile(irisWindowMs, 0.95),
                completionFps,
                completedFrames,
                busyDropped.get(),
            ),
        )
        totalWindowMs.clear()
        detectorWindowMs.clear()
        faceWindowMs.clear()
        irisWindowMs.clear()
    }

    private fun percentile(values: List<Double>, fraction: Double): Double {
        if (values.isEmpty()) return Double.NaN
        val sorted = values.sorted()
        val index = (ceil(values.size * fraction).toInt() - 1).coerceIn(sorted.indices)
        return sorted[index]
    }

    private fun ensureModels(): Boolean {
        if (detector != null && faceInterpreter != null && irisInterpreter != null) return true
        return try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(FACE_DETECTOR_ASSET)
                .setDelegate(Delegate.CPU)
                .build()
            detector = FaceDetector.createFromOptions(
                appContext,
                FaceDetector.FaceDetectorOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.IMAGE)
                    .setMinDetectionConfidence(MIN_FACE_DETECTION_CONFIDENCE)
                    .build(),
            )
            faceInterpreter = openInterpreter(FACE_LANDMARK_ASSET)
            faceLandmarkOutputIndex = outputIndex(faceInterpreter!!, CompactFaceIrisGeometry.FACE_LANDMARK_FLOATS)
            facePresenceOutputIndex = outputIndex(faceInterpreter!!, 1)
            faceLandmarkOutput = outputBuffer(faceInterpreter!!, faceLandmarkOutputIndex)
            facePresenceOutput = outputBuffer(faceInterpreter!!, facePresenceOutputIndex)
            faceOutputs.clear()
            faceOutputs[faceLandmarkOutputIndex] = faceLandmarkOutput!!
            faceOutputs[facePresenceOutputIndex] = facePresenceOutput!!

            irisInterpreter = openInterpreter(IRIS_LANDMARK_ASSET)
            irisEyeOutputIndex = outputIndex(irisInterpreter!!, HybridEyeGeometry.EYE_LANDMARK_FLOATS)
            irisLandmarkOutputIndex = outputIndex(irisInterpreter!!, HybridEyeGeometry.IRIS_LANDMARK_FLOATS)
            irisOutputs.clear()
            irisOutputs[irisEyeOutputIndex] = eyeOutput
            irisOutputs[irisLandmarkOutputIndex] = irisOutput
            Log.i(TAG, "Compact 468-face + 64x64 iris candidate ready; no gaze/calibration access")
            true
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to initialize compact face + iris candidate", error)
            close()
            false
        }
    }

    private fun openInterpreter(assetName: String): Interpreter {
        val bytes = appContext.assets.open(assetName).use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())
            .put(bytes)
        buffer.rewind()
        return Interpreter(buffer, Interpreter.Options().setNumThreads(INFERENCE_THREADS))
    }

    private fun outputIndex(interpreter: Interpreter, elements: Int): Int =
        (0 until interpreter.outputTensorCount).firstOrNull {
            interpreter.getOutputTensor(it).numElements() == elements
        } ?: error("Model output with $elements elements not found")

    private fun outputBuffer(interpreter: Interpreter, index: Int): ByteBuffer =
        ByteBuffer.allocateDirect(interpreter.getOutputTensor(index).numBytes())
            .order(ByteOrder.nativeOrder())

    private fun updateFps(nowNs: Long) {
        if (lastCompletionNs != 0L) {
            val delta = nowNs - lastCompletionNs
            if (delta > 0L) {
                val instant = 1_000_000_000f / delta
                completionFps = if (completionFps == 0f) instant else completionFps * 0.9f + instant * 0.1f
            }
        }
        lastCompletionNs = nowNs
    }

    private fun nsToMs(ns: Long): Float = ns / 1_000_000f

    companion object {
        private const val TAG = "CompactFaceIris"
        private const val FACE_DETECTOR_ASSET = "blaze_face_short_range.tflite"
        private const val FACE_LANDMARK_ASSET = "face_landmark.tflite"
        private const val IRIS_LANDMARK_ASSET = "iris_landmark.tflite"
        private const val FACE_EDGE = 192
        private const val EYE_EDGE = 64
        private const val RGB_CHANNELS = 3
        private const val FLOAT_BYTES = 4
        private const val INFERENCE_THREADS = 2
        private const val FACE_DETECTION_INTERVAL = 4
        private const val MIN_FACE_DETECTION_CONFIDENCE = 0.5f
        private const val FACE_PRESENCE_THRESHOLD = 0.5f
        private const val RIGHT_EYE_KEYPOINT = 0
        private const val LEFT_EYE_KEYPOINT = 1
        private const val REQUIRED_FACE_KEYPOINTS = 2
        private const val PERFORMANCE_WINDOW = 120

        private fun floatInput(edge: Int): ByteBuffer =
            ByteBuffer.allocateDirect(edge * edge * RGB_CHANNELS * FLOAT_BYTES)
                .order(ByteOrder.nativeOrder())
    }
}
