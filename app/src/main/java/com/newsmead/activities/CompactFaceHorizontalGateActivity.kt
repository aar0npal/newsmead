package com.newsmead.activities

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Range
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
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
import com.newsmead.gaze.CalibrationPointCollector
import com.newsmead.gaze.CalibrationView
import com.newsmead.gaze.CompactFaceIrisBackend
import com.newsmead.gaze.CompactFaceIrisSample
import com.newsmead.gaze.FixationWindowFilter
import com.newsmead.gaze.ReusableArgbFrameTransformer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Researcher-only, target-linked horizontal geometry gate for the compact face + iris candidate.
 * It has no calibrated provider, mapper, gaze dot, correction, or calibration-store dependency.
 */
class CompactFaceHorizontalGateActivity : AppCompatActivity() {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val frameTransformer = ReusableArgbFrameTransformer()
    private val handler = Handler(Looper.getMainLooper())
    private var cameraProvider: ProcessCameraProvider? = null
    private var backend: CompactFaceIrisBackend? = null

    private lateinit var root: FrameLayout
    private lateinit var targetView: CalibrationView
    private lateinit var progressText: TextView
    private lateinit var countdownText: TextView
    private lateinit var instructionPanel: LinearLayout
    private lateinit var instructionText: TextView
    private lateinit var startButton: Button
    private lateinit var collector: CalibrationPointCollector

    @Volatile private var sampleWindowActive = false
    private var firstValidSampleSeen = false
    private var running = false
    private var pointIndex = 0
    private var attempts = 0
    private val pointSamples = ArrayList<CompactFaceIrisSample>()
    private val pointRecords = ArrayList<PointRecord>()
    private val runLabel by lazy {
        sanitizeLabel(intent.getStringExtra(EXTRA_RUN_LABEL) ?: DEFAULT_RUN_LABEL)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUi()
        collector = CalibrationPointCollector(targetView, tone = null)
        collector.setOnSampleWindowChanged { active ->
            sampleWindowActive = active
            if (active) pointSamples.clear()
        }
        startButton.setOnClickListener { startGate() }
        if (hasCameraPermission()) startCamera() else requestCameraPermission()
    }

    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(250, 250, 250)) }
        targetView = CalibrationView(this)
        root.addView(
            targetView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        progressText = TextView(this).apply {
            setTextColor(Color.rgb(50, 50, 50))
            textSize = 17f
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        root.addView(
            progressText,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            ).apply { topMargin = dp(24) },
        )
        countdownText = TextView(this).apply {
            setTextColor(Color.rgb(33, 33, 33))
            textSize = 72f
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        root.addView(
            countdownText,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        instructionText = TextView(this).apply {
            setTextColor(Color.rgb(35, 35, 35))
            textSize = 18f
            gravity = Gravity.CENTER
            text = "Compact face + iris horizontal check\n\n" +
                "Hold the phone in your normal reading position. After tapping Start, look at each red target until it moves.\n\n" +
                "No gaze dot is shown because this measures raw candidate geometry. Your saved calibration is not used or changed.\n\n" +
                "Preparing camera…"
        }
        startButton = Button(this).apply {
            text = "Start"
            isEnabled = false
        }
        instructionPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            addView(
                instructionText,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                startButton,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(24) },
            )
        }
        root.addView(
            instructionPanel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        setContentView(root)
    }

    private fun startCamera() {
        backend = CompactFaceIrisBackend(applicationContext) { sample ->
            runOnUiThread { onCandidateSample(sample) }
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
            current.submit(
                transformed.copy(Bitmap.Config.ARGB_8888, false),
                frame.imageInfo.timestamp,
            )
        } finally {
            frame.close()
        }
    }

    private fun onCandidateSample(sample: CompactFaceIrisSample) {
        if (!firstValidSampleSeen) {
            firstValidSampleSeen = true
            startButton.isEnabled = true
            instructionText.text = instructionText.text.toString().replace(
                "Preparing camera…",
                "Ready.",
            )
        }
        if (!running) return
        if (sampleWindowActive) pointSamples.add(sample)
        collector.onRawSample(
            sample.candidateHorizontal,
            sample.candidateVertical,
            sample.resultElapsedNs / 1_000_000L,
        )
    }

    private fun startGate() {
        if (running || !firstValidSampleSeen) return
        running = true
        instructionPanel.visibility = View.GONE
        progressText.visibility = View.GONE
        startCountdown(3) { runPoint(0) }
    }

    private fun startCountdown(value: Int, onFinished: () -> Unit) {
        if (value <= 0) {
            countdownText.visibility = View.GONE
            onFinished()
            return
        }
        countdownText.visibility = View.VISIBLE
        countdownText.text = value.toString()
        handler.postDelayed({ startCountdown(value - 1, onFinished) }, 1_000L)
    }

    private fun runPoint(index: Int) {
        pointIndex = index
        attempts = 0
        captureCurrent()
    }

    private fun captureCurrent() {
        val fraction = TARGET_SEQUENCE[pointIndex]
        progressText.visibility = View.VISIBLE
        progressText.text = "Target ${pointIndex + 1} of ${TARGET_SEQUENCE.size}"
        collector.capture(targetView.width * fraction, targetView.height * TARGET_Y_FRACTION) { result ->
            val record = PointRecord(
                presentationIndex = pointIndex,
                attempt = attempts + 1,
                targetXFraction = fraction,
                status = result.status.name.lowercase(Locale.US),
                rawCount = result.rawCount,
                retainedCount = result.retainedCount,
                medianHorizontal = result.medianX,
                medianVertical = result.medianY,
                dispersionHorizontal = result.dispersionX,
                dispersionVertical = result.dispersionY,
                samples = pointSamples.toList(),
            )
            pointRecords.add(record)
            if (result.status == FixationWindowFilter.Status.ACCEPTED) {
                handler.postDelayed({ advance() }, CONFIRM_MS)
            } else {
                attempts += 1
                if (attempts < MAX_ATTEMPTS) {
                    handler.postDelayed({ captureCurrent() }, RETRY_PAUSE_MS)
                } else {
                    handler.postDelayed({ advance() }, RETRY_PAUSE_MS)
                }
            }
        }
    }

    private fun advance() {
        val next = pointIndex + 1
        if (next < TARGET_SEQUENCE.size) runPoint(next) else finishGate()
    }

    private fun finishGate() {
        running = false
        collector.cancel()
        targetView.hideTarget()
        progressText.visibility = View.GONE
        cameraProvider?.unbindAll()
        backend?.close()
        backend = null

        val accepted = pointRecords.filter { it.status == "accepted" }
            .groupBy { it.presentationIndex }
            .values
            .mapNotNull { attempts -> attempts.lastOrNull() }
            .sortedBy { it.presentationIndex }
        val targetValues = accepted.map { it.targetXFraction.toDouble() }
        val horizontalValues = accepted.map { it.medianHorizontal.toDouble() }
        val correlation = correlation(targetValues, horizontalValues)
        val leftMedian = groupMedian(accepted, 0.2f)
        val centerMedian = groupMedian(accepted, 0.5f)
        val rightMedian = groupMedian(accepted, 0.8f)
        val totalSamples = pointRecords.sumOf { it.samples.size }
        val allSamples = pointRecords.flatMap { it.samples }
        val medianTotalMs = percentile(allSamples.map { it.totalMs.toDouble() }, 0.50)
        val p95TotalMs = percentile(allSamples.map { it.totalMs.toDouble() }, 0.95)
        val medianFps = percentile(allSamples.map { it.completionFps.toDouble() }, 0.50)
        val artifact = writeArtifact(
            acceptedCount = accepted.size,
            correlation = correlation,
            leftMedian = leftMedian,
            centerMedian = centerMedian,
            rightMedian = rightMedian,
            medianTotalMs = medianTotalMs,
            p95TotalMs = p95TotalMs,
            medianFps = medianFps,
        )

        instructionText.text = String.format(
            Locale.US,
            "Horizontal gate complete\n\nAccepted: %d/%d presentations\n" +
                "Target/feature correlation: %.3f\n" +
                "Left / centre / right medians:\n%.4f / %.4f / %.4f\n\n" +
                "Candidate median/P95 work: %.1f / %.1f ms\nMedian active FPS: %.1f\nSamples saved: %d\n\n" +
                "No gaze output or calibration change occurred.\n\nSaved: %s",
            accepted.size,
            TARGET_SEQUENCE.size,
            correlation,
            leftMedian,
            centerMedian,
            rightMedian,
            medianTotalMs,
            p95TotalMs,
            medianFps,
            totalSamples,
            artifact.name,
        )
        startButton.visibility = View.GONE
        instructionPanel.visibility = View.VISIBLE
    }

    private fun writeArtifact(
        acceptedCount: Int,
        correlation: Double,
        leftMedian: Double,
        centerMedian: Double,
        rightMedian: Double,
        medianTotalMs: Double,
        p95TotalMs: Double,
        medianFps: Double,
    ): File {
        val directory = File(filesDir, "gaze_diagnostics").apply { mkdirs() }
        val timestamp = FILE_TIMESTAMP.format(Instant.now())
        val file = File(directory, "compact_face_horizontal_${timestamp}_${runLabel}.json")
        val rootJson = JSONObject().apply {
            put("schema_version", 1)
            put("run_label", runLabel)
            put("timestamp_utc", Instant.now().toString())
            put("device_model", Build.MODEL)
            put("candidate", "blazeface_periodic_plus_face_landmark_468_plus_two_eye_iris_64_bulk_float_v2")
            put("candidate_affects_gaze_output", false)
            put("calibration_accessed", false)
            put("camera_frames_retained", false)
            put("target_sequence_x_fractions", JSONArray(TARGET_SEQUENCE.toList()))
            put("target_y_fraction", TARGET_Y_FRACTION)
            put("accepted_presentations", acceptedCount)
            put("target_candidate_horizontal_correlation", finiteOrNull(correlation))
            put("left_median", finiteOrNull(leftMedian))
            put("center_median", finiteOrNull(centerMedian))
            put("right_median", finiteOrNull(rightMedian))
            put("candidate_total_median_ms", finiteOrNull(medianTotalMs))
            put("candidate_total_p95_ms", finiteOrNull(p95TotalMs))
            put("candidate_active_fps_median", finiteOrNull(medianFps))
            put("points", JSONArray().apply { pointRecords.forEach { put(it.toJson()) } })
        }
        file.writeText(rootJson.toString(2))
        return file
    }

    private fun PointRecord.toJson(): JSONObject = JSONObject().apply {
        put("presentation_index", presentationIndex)
        put("attempt", attempt)
        put("target_x_fraction", targetXFraction)
        put("status", status)
        put("raw_count", rawCount)
        put("retained_count", retainedCount)
        put("median_horizontal", finiteOrNull(medianHorizontal.toDouble()))
        put("median_vertical", finiteOrNull(medianVertical.toDouble()))
        put("dispersion_horizontal", finiteOrNull(dispersionHorizontal.toDouble()))
        put("dispersion_vertical", finiteOrNull(dispersionVertical.toDouble()))
        put("samples", JSONArray().apply { samples.forEach { put(it.toJson()) } })
    }

    private fun CompactFaceIrisSample.toJson(): JSONObject = JSONObject().apply {
        put("capture_timestamp_ns", captureTimestampNs)
        put("result_elapsed_ns", resultElapsedNs)
        put("candidate_horizontal", candidateHorizontal)
        put("candidate_vertical", candidateVertical)
        put("right_horizontal", rightHorizontal)
        put("right_vertical", rightVertical)
        put("left_horizontal", leftHorizontal)
        put("left_vertical", leftVertical)
        put("face_presence", facePresence)
        put("detector_ran", detectorRan)
        put("total_ms", totalMs)
        put("detector_ms", detectorMs)
        put("face_landmark_ms", faceLandmarkMs)
        put("iris_ms", irisMs)
        put("completion_fps", completionFps)
        put("busy_dropped_frames_cumulative", busyDroppedFrames)
        put("source_width", sourceWidth)
        put("source_height", sourceHeight)
    }

    private fun groupMedian(records: List<PointRecord>, fraction: Float): Double =
        percentile(
            records.filter { it.targetXFraction == fraction }.map { it.medianHorizontal.toDouble() },
            0.50,
        )

    private fun percentile(values: List<Double>, fraction: Double): Double {
        if (values.isEmpty()) return Double.NaN
        val sorted = values.sorted()
        val index = (ceil(sorted.size * fraction).toInt() - 1).coerceIn(sorted.indices)
        return sorted[index]
    }

    private fun correlation(a: List<Double>, b: List<Double>): Double {
        if (a.size != b.size || a.size < 2) return Double.NaN
        val meanA = a.average()
        val meanB = b.average()
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

    private fun finiteOrNull(value: Double): Any = if (value.isFinite()) value else JSONObject.NULL

    private fun sanitizeLabel(value: String): String = value.trim()
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9_-]+"), "_")
        .trim('_')
        .take(64)
        .ifEmpty { DEFAULT_RUN_LABEL }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else if (requestCode == CAMERA_PERMISSION_REQUEST) {
            instructionText.text = "Camera permission is required for this researcher-only gate."
        }
    }

    override fun onDestroy() {
        collector.cancel()
        handler.removeCallbacksAndMessages(null)
        cameraProvider?.unbindAll()
        cameraProvider = null
        backend?.close()
        backend = null
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    private data class PointRecord(
        val presentationIndex: Int,
        val attempt: Int,
        val targetXFraction: Float,
        val status: String,
        val rawCount: Int,
        val retainedCount: Int,
        val medianHorizontal: Float,
        val medianVertical: Float,
        val dispersionHorizontal: Float,
        val dispersionVertical: Float,
        val samples: List<CompactFaceIrisSample>,
    )

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 4106
        private const val EXTRA_RUN_LABEL = "run_label"
        private const val DEFAULT_RUN_LABEL = "compact_face_horizontal"
        private const val TARGET_Y_FRACTION = 0.5f
        private const val CONFIRM_MS = 250L
        private const val RETRY_PAUSE_MS = 500L
        private const val MAX_ATTEMPTS = 2
        private val TARGET_SEQUENCE = floatArrayOf(0.2f, 0.5f, 0.8f, 0.5f, 0.2f, 0.8f)
        private val FILE_TIMESTAMP = DateTimeFormatter
            .ofPattern("yyyyMMdd_HHmmss", Locale.US)
            .withZone(ZoneOffset.UTC)
    }
}
