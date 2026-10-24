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
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Development-only BlazeFace + two-eye iris shadow.
 *
 * This class owns submitted bitmap copies and never emits gaze. It exists only to compare an
 * independently acquired lightweight feature with the current 478-point reference feature.
 */
internal class HybridEyeShadowBackend(
    context: Context,
    private val onResult: (HybridEyeShadowSample) -> Unit,
) {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "hybrid-eye-shadow").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val busy = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    private var faceDetector: FaceDetector? = null
    private var irisInterpreter: Interpreter? = null
    private var irisEyeOutputIndex = -1
    private var irisLandmarkOutputIndex = -1
    private var eyeBitmap: Bitmap? = null
    private var eyeCanvas: Canvas? = null
    private var cachedCrops: HybridEyeGeometry.CropPair? = null
    private var framesUntilFaceRefresh = 0
    private val eyePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pixels = IntArray(MODEL_EDGE * MODEL_EDGE)
    private val input = ByteBuffer.allocateDirect(MODEL_EDGE * MODEL_EDGE * RGB_CHANNELS * FLOAT_BYTES)
        .order(ByteOrder.nativeOrder())
    private val eyeOutput = Array(1) { FloatArray(HybridEyeGeometry.EYE_LANDMARK_FLOATS) }
    private val irisOutput = Array(1) { FloatArray(HybridEyeGeometry.IRIS_LANDMARK_FLOATS) }
    private val outputMap = mutableMapOf<Int, Any>()

    private val totalSubmitted = AtomicLong(0L)
    private var totalCompleted = 0L
    private val totalBusyDropped = AtomicLong(0L)
    private var totalNoFace = 0L
    private var totalInvalid = 0L
    private var lastCompletionNs = 0L
    private var completionFps = 0f
    private val referenceHorizontal = mutableListOf<Double>()
    private val referenceVertical = mutableListOf<Double>()
    private val candidateHorizontal = mutableListOf<Double>()
    private val candidateVertical = mutableListOf<Double>()
    private val totalLatenciesMs = mutableListOf<Double>()
    private val detectorLatenciesMs = mutableListOf<Double>()
    private val eyeLatenciesMs = mutableListOf<Double>()
    private var detectorRunsInWindow = 0

    fun canAcceptFrame(): Boolean = !closed.get() && !busy.get()

    fun noteBusyFrameDropped() {
        if (!closed.get()) totalBusyDropped.incrementAndGet()
    }

    fun submit(
        bitmap: Bitmap,
        referenceHorizontal: Float,
        referenceVertical: Float,
        captureTimestampNs: Long,
    ) {
        if (closed.get() || !busy.compareAndSet(false, true)) {
            if (!closed.get()) totalBusyDropped.incrementAndGet()
            bitmap.recycle()
            return
        }
        totalSubmitted.incrementAndGet()
        try {
            executor.execute {
                try {
                    process(bitmap, referenceHorizontal, referenceVertical, captureTimestampNs)
                } catch (error: Throwable) {
                    totalInvalid += 1
                    Log.e(TAG, "Hybrid eye shadow frame failed; active gaze unchanged", error)
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
                faceDetector?.close()
                faceDetector = null
                irisInterpreter?.close()
                irisInterpreter = null
                eyeBitmap?.recycle()
                eyeBitmap = null
                eyeCanvas = null
                cachedCrops = null
            }
        } catch (_: RejectedExecutionException) {
            // The executor is already terminating.
        } finally {
            executor.shutdown()
        }
    }

    private fun process(
        bitmap: Bitmap,
        referenceH: Float,
        referenceV: Float,
        captureTimestampNs: Long,
    ) {
        if (closed.get() || !ensureModels()) return
        val startedNs = SystemClock.elapsedRealtimeNanos()
        val detectorRan = cachedCrops == null || framesUntilFaceRefresh <= 0
        val crops = if (detectorRan) {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val detection = faceDetector!!.detect(mpImage).detections().firstOrNull()
            if (detection == null) {
                cachedCrops = null
                framesUntilFaceRefresh = 0
                totalNoFace += 1
                logLossIfDue("no_face")
                return
            }
            val keypoints = detection.keypoints().orElse(emptyList())
            if (keypoints.size < REQUIRED_FACE_KEYPOINTS) {
                cachedCrops = null
                framesUntilFaceRefresh = 0
                totalInvalid += 1
                logLossIfDue("missing_eye_keypoints")
                return
            }

            // Official BlazeFace order: 0 anatomical right eye, 1 anatomical left eye.
            val rightEye = HybridEyeGeometry.Point(
                keypoints[RIGHT_EYE_KEYPOINT].x() * bitmap.width,
                keypoints[RIGHT_EYE_KEYPOINT].y() * bitmap.height,
            )
            val leftEye = HybridEyeGeometry.Point(
                keypoints[LEFT_EYE_KEYPOINT].x() * bitmap.width,
                keypoints[LEFT_EYE_KEYPOINT].y() * bitmap.height,
            )
            val refreshed = HybridEyeGeometry.cropPair(
                rightEye,
                leftEye,
                detection.boundingBox().width(),
            )
            if (refreshed == null) {
                cachedCrops = null
                framesUntilFaceRefresh = 0
                totalInvalid += 1
                logLossIfDue("invalid_eye_geometry")
                return
            }
            cachedCrops = refreshed
            framesUntilFaceRefresh = FACE_DETECTION_INTERVAL - 1
            refreshed
        } else {
            framesUntilFaceRefresh -= 1
            cachedCrops!!
        }
        val detectorDoneNs = SystemClock.elapsedRealtimeNanos()

        val initialRight = inferEye(bitmap, crops.rightEye)
        val initialLeft = inferEye(bitmap, crops.leftEye)
        if (initialRight == null || initialLeft == null) {
            cachedCrops = null
            framesUntilFaceRefresh = 0
            totalInvalid += 1
            logLossIfDue("invalid_iris_output")
            return
        }
        var right = initialRight
        var left = initialLeft

        var reextractApplied = false
        var rightCenterShiftPx = Float.NaN
        var leftCenterShiftPx = Float.NaN
        var rightSideRatio = Float.NaN
        var leftSideRatio = Float.NaN
        var reextractMs = 0.0
        if (detectorRan) {
            val refinedRight = HybridEyeGeometry.detectorAnchoredRefinement(crops.rightEye, right)
            val refinedLeft = HybridEyeGeometry.detectorAnchoredRefinement(crops.leftEye, left)
            if (refinedRight != null && refinedLeft != null) {
                rightCenterShiftPx = refinedRight.centerShiftPx
                leftCenterShiftPx = refinedLeft.centerShiftPx
                rightSideRatio = refinedRight.sideRatio
                leftSideRatio = refinedLeft.sideRatio
                val reextractStartedNs = SystemClock.elapsedRealtimeNanos()
                val reextractedRight = inferEye(bitmap, refinedRight.crop)
                val reextractedLeft = inferEye(bitmap, refinedLeft.crop)
                reextractMs = nsToMs(SystemClock.elapsedRealtimeNanos() - reextractStartedNs)
                if (reextractedRight != null && reextractedLeft != null) {
                    cachedCrops = HybridEyeGeometry.CropPair(refinedRight.crop, refinedLeft.crop)
                    right = reextractedRight
                    left = reextractedLeft
                    reextractApplied = true
                }
            }
        }
        val finishedNs = SystemClock.elapsedRealtimeNanos()

        val candidateH = (right.feature.horizontal + left.feature.horizontal) / 2f
        val candidateV = (right.feature.vertical + left.feature.vertical) / 2f
        if (!candidateH.isFinite() || !candidateV.isFinite()) {
            totalInvalid += 1
            logLossIfDue("non_finite_feature")
            return
        }

        totalCompleted += 1
        updateCompletionFps(finishedNs)
        val totalMs = nsToMs(finishedNs - startedNs)
        val detectorMs = if (detectorRan) nsToMs(detectorDoneNs - startedNs) else 0.0
        val eyeMs = nsToMs(finishedNs - detectorDoneNs)
        record(referenceH, referenceV, candidateH, candidateV, totalMs, detectorMs, eyeMs, detectorRan)
        onResult(
            HybridEyeShadowSample(
                captureTimestampNs = captureTimestampNs,
                resultElapsedNs = finishedNs,
                referenceHorizontal = referenceH,
                referenceVertical = referenceV,
                candidateHorizontal = candidateH,
                candidateVertical = candidateV,
                rightEyeOpenness = right.openness,
                leftEyeOpenness = left.openness,
                faceDetectorRan = detectorRan,
                detectorReextractApplied = reextractApplied,
                rightCropCenterShiftPx = rightCenterShiftPx,
                leftCropCenterShiftPx = leftCenterShiftPx,
                rightCropSideRatio = rightSideRatio,
                leftCropSideRatio = leftSideRatio,
                totalMs = totalMs.toFloat(),
                detectorMs = detectorMs.toFloat(),
                eyeMs = eyeMs.toFloat(),
                reextractMs = reextractMs.toFloat(),
            ),
        )

        if (totalCompleted % RAW_LOG_INTERVAL == 0L) {
            Log.i(
                TAG,
                String.format(
                    Locale.US,
                    "sample=%d ref=(%.4f,%.4f) candidate=(%.4f,%.4f) " +
                    "open=(%.3f,%.3f) detectorRan=%b reextract=%b shift=(%.2f,%.2f) " +
                        "sideRatio=(%.3f,%.3f) totalMs=%.3f detectorMs=%.3f eyeMs=%.3f " +
                        "reextractMs=%.3f fps=%.1f " +
                        "captureAgeMs=%.1f drop=%d noFace=%d invalid=%d",
                    totalCompleted,
                    referenceH,
                    referenceV,
                    candidateH,
                    candidateV,
                    right.openness,
                    left.openness,
                    detectorRan,
                    reextractApplied,
                    rightCenterShiftPx,
                    leftCenterShiftPx,
                    rightSideRatio,
                    leftSideRatio,
                    totalMs,
                    detectorMs,
                    eyeMs,
                    reextractMs,
                    completionFps,
                    nsToMs(finishedNs - captureTimestampNs),
                    totalBusyDropped.get(),
                    totalNoFace,
                    totalInvalid,
                ),
            )
        }
        if (totalLatenciesMs.size >= SUMMARY_WINDOW) {
            logSummaryAndReset()
        }
    }

    private fun ensureModels(): Boolean {
        if (faceDetector != null && irisInterpreter != null) return true
        return try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(FACE_MODEL_ASSET)
                .setDelegate(Delegate.CPU)
                .build()
            val detectorOptions = FaceDetector.FaceDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setMinDetectionConfidence(MIN_FACE_CONFIDENCE)
                .build()
            faceDetector = FaceDetector.createFromOptions(appContext, detectorOptions)

            val modelBytes = appContext.assets.open(IRIS_MODEL_ASSET).use { it.readBytes() }
            val modelBuffer = ByteBuffer.allocateDirect(modelBytes.size)
                .order(ByteOrder.nativeOrder())
                .put(modelBytes)
            modelBuffer.rewind()
            irisInterpreter = Interpreter(modelBuffer, Interpreter.Options().setNumThreads(IRIS_THREADS))
            val interpreter = irisInterpreter!!
            irisEyeOutputIndex = (0 until interpreter.outputTensorCount).firstOrNull {
                interpreter.getOutputTensor(it).numElements() == HybridEyeGeometry.EYE_LANDMARK_FLOATS
            } ?: error("Iris model is missing the 71-point eye output")
            irisLandmarkOutputIndex = (0 until interpreter.outputTensorCount).firstOrNull {
                interpreter.getOutputTensor(it).numElements() == HybridEyeGeometry.IRIS_LANDMARK_FLOATS
            } ?: error("Iris model is missing the five-point iris output")
            outputMap.clear()
            outputMap[irisEyeOutputIndex] = eyeOutput
            outputMap[irisLandmarkOutputIndex] = irisOutput
            Log.i(
                TAG,
                "Hybrid eye shadow ready; BlazeFace+64x64 iris CPU; active gaze unchanged",
            )
            true
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to initialize hybrid eye shadow; active gaze unchanged", error)
            close()
            false
        }
    }

    private fun inferEye(
        source: Bitmap,
        crop: HybridEyeGeometry.CropSpec,
    ): HybridEyeGeometry.EyeSample? {
        val bitmap = eyeBitmap ?: Bitmap.createBitmap(MODEL_EDGE, MODEL_EDGE, Bitmap.Config.ARGB_8888)
            .also {
                eyeBitmap = it
                eyeCanvas = Canvas(it)
            }
        val canvas = eyeCanvas ?: Canvas(bitmap).also { eyeCanvas = it }
        canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)
        canvas.save()
        canvas.translate(MODEL_EDGE / 2f, MODEL_EDGE / 2f)
        if (crop.flipHorizontally) canvas.scale(-1f, 1f)
        canvas.rotate(-crop.rotationDegrees)
        val scale = MODEL_EDGE / crop.sidePx
        canvas.scale(scale, scale)
        canvas.translate(-crop.center.x, -crop.center.y)
        canvas.drawBitmap(source, 0f, 0f, eyePaint)
        canvas.restore()

        bitmap.getPixels(pixels, 0, MODEL_EDGE, 0, 0, MODEL_EDGE, MODEL_EDGE)
        input.rewind()
        for (color in pixels) {
            input.putFloat(Color.red(color) / 255f)
            input.putFloat(Color.green(color) / 255f)
            input.putFloat(Color.blue(color) / 255f)
        }
        input.rewind()
        irisInterpreter!!.runForMultipleInputsOutputs(arrayOf(input), outputMap)
        return HybridEyeGeometry.decodeEye(
            eyeLandmarks = eyeOutput[0],
            irisLandmarks = irisOutput[0],
            wasFlipped = crop.flipHorizontally,
        )
    }

    private fun record(
        referenceH: Float,
        referenceV: Float,
        candidateH: Float,
        candidateV: Float,
        totalMs: Double,
        detectorMs: Double,
        eyeMs: Double,
        detectorRan: Boolean,
    ) {
        referenceHorizontal += referenceH.toDouble()
        referenceVertical += referenceV.toDouble()
        candidateHorizontal += candidateH.toDouble()
        candidateVertical += candidateV.toDouble()
        totalLatenciesMs += totalMs
        detectorLatenciesMs += detectorMs
        eyeLatenciesMs += eyeMs
        if (detectorRan) detectorRunsInWindow += 1
    }

    private fun logSummaryAndReset() {
        val absH = referenceHorizontal.zip(candidateHorizontal) { reference, candidate ->
            kotlin.math.abs(reference - candidate)
        }
        val absV = referenceVertical.zip(candidateVertical) { reference, candidate ->
            kotlin.math.abs(reference - candidate)
        }
        Log.i(
            TAG,
            String.format(
                Locale.US,
                "HYBRID_EYE_RESULT n=%d totalMeanMs=%.3f totalP95Ms=%.3f " +
                    "detectorMeanMs=%.3f detectorRunFraction=%.3f eyeMeanMs=%.3f fps=%.1f " +
                    "absHMedian=%.4f absHP95=%.4f corrH=%.4f " +
                    "absVMedian=%.4f absVP95=%.4f corrV=%.4f " +
                    "submitted=%d completed=%d drop=%d noFace=%d invalid=%d",
                totalLatenciesMs.size,
                mean(totalLatenciesMs),
                percentile(totalLatenciesMs, 0.95),
                mean(detectorLatenciesMs),
                detectorRunsInWindow.toDouble() / totalLatenciesMs.size,
                mean(eyeLatenciesMs),
                completionFps,
                percentile(absH, 0.50),
                percentile(absH, 0.95),
                correlation(referenceHorizontal, candidateHorizontal),
                percentile(absV, 0.50),
                percentile(absV, 0.95),
                correlation(referenceVertical, candidateVertical),
                totalSubmitted.get(),
                totalCompleted,
                totalBusyDropped.get(),
                totalNoFace,
                totalInvalid,
            ),
        )
        referenceHorizontal.clear()
        referenceVertical.clear()
        candidateHorizontal.clear()
        candidateVertical.clear()
        totalLatenciesMs.clear()
        detectorLatenciesMs.clear()
        eyeLatenciesMs.clear()
        detectorRunsInWindow = 0
    }

    private fun logLossIfDue(reason: String) {
        val losses = totalNoFace + totalInvalid
        if (losses % LOSS_LOG_INTERVAL == 0L) {
            Log.i(
                TAG,
                "loss=$reason submitted=${totalSubmitted.get()} completed=$totalCompleted " +
                    "drop=${totalBusyDropped.get()} noFace=$totalNoFace invalid=$totalInvalid",
            )
        }
    }

    private fun updateCompletionFps(nowNs: Long) {
        if (lastCompletionNs != 0L) {
            val dt = nowNs - lastCompletionNs
            if (dt > 0L) {
                val instantaneous = 1_000_000_000f / dt
                completionFps = if (completionFps == 0f) {
                    instantaneous
                } else {
                    completionFps * 0.9f + instantaneous * 0.1f
                }
            }
        }
        lastCompletionNs = nowNs
    }

    private fun mean(values: List<Double>): Double = if (values.isEmpty()) {
        Double.NaN
    } else {
        values.sum() / values.size
    }

    private fun percentile(values: List<Double>, fraction: Double): Double {
        if (values.isEmpty()) return Double.NaN
        val sorted = values.sorted()
        val index = (ceil(fraction * sorted.size).toInt() - 1).coerceIn(sorted.indices)
        return sorted[index]
    }

    private fun correlation(a: List<Double>, b: List<Double>): Double {
        if (a.size != b.size || a.size < 2) return Double.NaN
        val meanA = mean(a)
        val meanB = mean(b)
        var numerator = 0.0
        var sumA = 0.0
        var sumB = 0.0
        for (index in a.indices) {
            val da = a[index] - meanA
            val db = b[index] - meanB
            numerator += da * db
            sumA += da * da
            sumB += db * db
        }
        val denominator = sqrt(sumA * sumB)
        return if (denominator <= 1e-12) Double.NaN else numerator / denominator
    }

    private fun nsToMs(ns: Long): Double = ns / 1_000_000.0

    companion object {
        private const val TAG = "HybridEyeShadow"
        private const val FACE_MODEL_ASSET = "blaze_face_short_range.tflite"
        private const val IRIS_MODEL_ASSET = "iris_landmark.tflite"
        private const val MODEL_EDGE = 64
        private const val RGB_CHANNELS = 3
        private const val FLOAT_BYTES = 4
        private const val IRIS_THREADS = 2
        private const val MIN_FACE_CONFIDENCE = 0.5f
        private const val RIGHT_EYE_KEYPOINT = 0
        private const val LEFT_EYE_KEYPOINT = 1
        private const val REQUIRED_FACE_KEYPOINTS = 2
        private const val FACE_DETECTION_INTERVAL = 4
        private const val RAW_LOG_INTERVAL = 15L
        private const val LOSS_LOG_INTERVAL = 30L
        private const val SUMMARY_WINDOW = 120
    }
}
