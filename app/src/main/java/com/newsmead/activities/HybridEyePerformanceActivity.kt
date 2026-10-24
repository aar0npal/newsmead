package com.newsmead.activities

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
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
import com.newsmead.gaze.HybridEyeShadowBackend
import com.newsmead.gaze.ReusableArgbFrameTransformer
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.ceil

/**
 * Researcher-only candidate throughput screen. It never emits gaze and never reads or writes a
 * calibration. Launch explicitly through adb; it is not reachable from the participant UI.
 */
class HybridEyePerformanceActivity : AppCompatActivity() {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val frameTransformer = ReusableArgbFrameTransformer()
    private var cameraProvider: ProcessCameraProvider? = null
    private var backend: HybridEyeShadowBackend? = null
    private lateinit var statusText: TextView
    private var resultCount = 0L
    private var lastResultNs = 0L
    private var fps = 0f
    private var lastUiNs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        statusText = TextView(this).apply {
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            text = "Candidate-only performance preview\n\nWaiting for camera…"
        }
        setContentView(
            FrameLayout(this).apply {
                addView(
                    statusText,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            },
        )
        if (intent.getBooleanExtra(EXTRA_FACE_LANDMARK_COMPUTE_ONLY, false)) {
            startFaceLandmarkComputeBenchmark()
        } else if (hasCameraPermission()) {
            startCandidatePreview()
        } else {
            requestCameraPermission()
        }
    }

    /**
     * Times only the official compact face-landmark TFLite graph on a fixed in-memory tensor.
     * This path never opens CameraX, creates a gaze backend, or touches calibration storage.
     */
    private fun startFaceLandmarkComputeBenchmark() {
        statusText.text = "Official face-landmark compute gate\n\nWarming up…\n\nNo camera · no gaze · no calibration access"
        cameraExecutor.execute {
            try {
                val modelBytes = assets.open(FACE_LANDMARK_MODEL_ASSET).use { it.readBytes() }
                val modelBuffer = ByteBuffer.allocateDirect(modelBytes.size)
                    .order(ByteOrder.nativeOrder())
                    .put(modelBytes)
                    .also { it.rewind() }

                val initStartNs = SystemClock.elapsedRealtimeNanos()
                Interpreter(
                    modelBuffer,
                    Interpreter.Options().setNumThreads(FACE_LANDMARK_THREADS),
                ).use { interpreter ->
                    val initMs = nsToMs(SystemClock.elapsedRealtimeNanos() - initStartNs)
                    require(interpreter.inputTensorCount == 1) {
                        "Expected one input tensor, found ${interpreter.inputTensorCount}"
                    }
                    val inputTensor = interpreter.getInputTensor(0)
                    require(inputTensor.dataType() == DataType.FLOAT32) {
                        "Expected FLOAT32 input, found ${inputTensor.dataType()}"
                    }
                    val input = ByteBuffer.allocateDirect(inputTensor.numBytes())
                        .order(ByteOrder.nativeOrder())
                    while (input.remaining() >= FLOAT_BYTES) input.putFloat(SYNTHETIC_PIXEL_VALUE)
                    input.rewind()

                    val outputs = mutableMapOf<Int, Any>()
                    repeat(interpreter.outputTensorCount) { index ->
                        outputs[index] = ByteBuffer
                            .allocateDirect(interpreter.getOutputTensor(index).numBytes())
                            .order(ByteOrder.nativeOrder())
                    }
                    val outputShapes = (0 until interpreter.outputTensorCount).joinToString("; ") { index ->
                        "${interpreter.getOutputTensor(index).shape().contentToString()}:" +
                            interpreter.getOutputTensor(index).dataType()
                    }

                    repeat(FACE_LANDMARK_WARMUP_RUNS) {
                        runFaceLandmarkInference(interpreter, input, outputs)
                    }

                    val latenciesMs = DoubleArray(FACE_LANDMARK_MEASURED_RUNS)
                    repeat(FACE_LANDMARK_MEASURED_RUNS) { index ->
                        val startNs = SystemClock.elapsedRealtimeNanos()
                        runFaceLandmarkInference(interpreter, input, outputs)
                        latenciesMs[index] = nsToMs(SystemClock.elapsedRealtimeNanos() - startNs)
                    }
                    val sorted = latenciesMs.sortedArray()
                    val medianMs = percentile(sorted, 0.50)
                    val p95Ms = percentile(sorted, 0.95)
                    val meanMs = latenciesMs.average()
                    val medianBudgetMs = FRAME_BUDGET_MS - medianMs
                    val p95BudgetMs = FRAME_BUDGET_MS - p95Ms
                    val inputShape = inputTensor.shape().contentToString()
                    val result = String.format(
                        Locale.US,
                        "Official face-landmark compute gate\n\n" +
                            "Model: %s (%d bytes)\nCPU threads: %d\nInput: %s FLOAT32\nOutputs: %s\n\n" +
                            "Initialization: %.2f ms\nInference median: %.2f ms\nInference P95: %.2f ms\nInference mean: %.2f ms\n" +
                            "50 ms budget left: %.2f ms median / %.2f ms P95\n\n" +
                            "%d warm-up + %d measured runs\nNo camera · no gaze · no calibration access",
                        FACE_LANDMARK_MODEL_ASSET,
                        modelBytes.size,
                        FACE_LANDMARK_THREADS,
                        inputShape,
                        outputShapes,
                        initMs,
                        medianMs,
                        p95Ms,
                        meanMs,
                        medianBudgetMs,
                        p95BudgetMs,
                        FACE_LANDMARK_WARMUP_RUNS,
                        FACE_LANDMARK_MEASURED_RUNS,
                    )
                    Log.i(
                        TAG,
                        String.format(
                            Locale.US,
                            "FACE_LANDMARK_COMPUTE_V1 model_bytes=%d threads=%d input=%s outputs=%s " +
                                "init_ms=%.3f median_ms=%.3f p95_ms=%.3f mean_ms=%.3f " +
                                "budget_left_median_ms=%.3f budget_left_p95_ms=%.3f warmup=%d measured=%d",
                            modelBytes.size,
                            FACE_LANDMARK_THREADS,
                            inputShape.replace(" ", ""),
                            outputShapes.replace(" ", ""),
                            initMs,
                            medianMs,
                            p95Ms,
                            meanMs,
                            medianBudgetMs,
                            p95BudgetMs,
                            FACE_LANDMARK_WARMUP_RUNS,
                            FACE_LANDMARK_MEASURED_RUNS,
                        ),
                    )
                    runOnUiThread { statusText.text = result }
                }
            } catch (error: Throwable) {
                Log.e(TAG, "FACE_LANDMARK_COMPUTE_V1 failed", error)
                runOnUiThread {
                    statusText.text = "Official face-landmark compute gate\n\nFAILED\n${error.message}\n\nNo camera · no gaze · no calibration access"
                }
            }
        }
    }

    private fun runFaceLandmarkInference(
        interpreter: Interpreter,
        input: ByteBuffer,
        outputs: MutableMap<Int, Any>,
    ) {
        input.rewind()
        outputs.values.forEach { (it as ByteBuffer).rewind() }
        interpreter.runForMultipleInputsOutputs(arrayOf(input), outputs)
    }

    private fun percentile(sorted: DoubleArray, fraction: Double): Double {
        if (sorted.isEmpty()) return Double.NaN
        val index = (ceil(sorted.size * fraction).toInt() - 1).coerceIn(sorted.indices)
        return sorted[index]
    }

    private fun nsToMs(ns: Long): Double = ns / 1_000_000.0

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startCandidatePreview()
        }
    }

    private fun startCandidatePreview() {
        backend = HybridEyeShadowBackend(applicationContext) { sample ->
            updateFps(sample.resultElapsedNs)
        }
        val future = ProcessCameraProvider.getInstance(applicationContext)
        future.addListener(
            {
                val provider = future.get()
                cameraProvider = provider
                bindCamera(provider)
            },
            ContextCompat.getMainExecutor(applicationContext),
        )
    }

    @OptIn(markerClass = [ExperimentalCamera2Interop::class])
    private fun bindCamera(provider: ProcessCameraProvider) {
        val builder = ImageAnalysis.Builder()
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
        Camera2Interop.Extender(builder)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(30, 30))
        val analysis = builder.build().also { stream ->
            stream.setAnalyzer(cameraExecutor) { frame -> analyze(frame) }
        }
        provider.unbindAll()
        provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
    }

    private fun analyze(frame: ImageProxy) {
        try {
            val current = backend ?: return
            if (!current.canAcceptFrame()) {
                current.noteBusyFrameDropped()
                return
            }
            val transformed = frameTransformer.copyAndTransform(frame, frame.imageInfo.rotationDegrees)
            val ownedCopy = transformed.copy(Bitmap.Config.ARGB_8888, false)
            current.submit(
                bitmap = ownedCopy,
                referenceHorizontal = Float.NaN,
                referenceVertical = Float.NaN,
                captureTimestampNs = frame.imageInfo.timestamp,
            )
        } finally {
            frame.close()
        }
    }

    private fun updateFps(nowNs: Long) {
        resultCount += 1
        if (lastResultNs != 0L) {
            val delta = nowNs - lastResultNs
            if (delta > 0L) {
                val current = 1_000_000_000f / delta
                fps = if (fps == 0f) current else fps * 0.9f + current * 0.1f
            }
        }
        lastResultNs = nowNs
        if (lastUiNs == 0L || nowNs - lastUiNs >= UI_INTERVAL_NS) {
            lastUiNs = nowNs
            runOnUiThread {
                statusText.text = String.format(
                    Locale.US,
                    "Candidate-only performance preview\n\n%.1f FPS\n%d completed frames\n\nNo gaze output · no calibration writes",
                    fps,
                    resultCount,
                )
            }
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST,
        )
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        backend?.close()
        backend = null
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "HybridEyePerformance"
        private const val CAMERA_PERMISSION_REQUEST = 4105
        private const val UI_INTERVAL_NS = 250_000_000L
        private const val EXTRA_FACE_LANDMARK_COMPUTE_ONLY = "face_landmark_compute_only"
        private const val FACE_LANDMARK_MODEL_ASSET = "face_landmark.tflite"
        private const val FACE_LANDMARK_THREADS = 2
        private const val FACE_LANDMARK_WARMUP_RUNS = 20
        private const val FACE_LANDMARK_MEASURED_RUNS = 100
        private const val FLOAT_BYTES = 4
        private const val SYNTHETIC_PIXEL_VALUE = 0.5f
        private const val FRAME_BUDGET_MS = 50.0
    }
}
