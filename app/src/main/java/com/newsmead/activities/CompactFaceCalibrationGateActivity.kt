package com.newsmead.activities

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.util.Range
import android.util.Size
import android.util.TypedValue
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
import com.newsmead.data.StudyConfig
import com.newsmead.gaze.CalibrationPointCollector
import com.newsmead.gaze.CalibrationQuality
import com.newsmead.gaze.CalibrationSample
import com.newsmead.gaze.CalibrationView
import com.newsmead.gaze.CompactFaceIrisBackend
import com.newsmead.gaze.CompactFaceIrisSample
import com.newsmead.gaze.FixationWindowFilter
import com.newsmead.gaze.GazeMapper
import com.newsmead.gaze.ReadingSpatialMetrics
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

/** Standalone compact-candidate fit/held-out gate. It never writes the app calibration. */
class CompactFaceCalibrationGateActivity : AppCompatActivity() {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val frameTransformer = ReusableArgbFrameTransformer()
    private val handler = Handler(Looper.getMainLooper())
    private var cameraProvider: ProcessCameraProvider? = null
    private var backend: CompactFaceIrisBackend? = null

    private lateinit var targetView: CalibrationView
    private lateinit var progressText: TextView
    private lateinit var countdownText: TextView
    private lateinit var instructionPanel: LinearLayout
    private lateinit var instructionText: TextView
    private lateinit var startButton: Button
    private lateinit var collector: CalibrationPointCollector

    @Volatile private var sampleWindowActive = false
    private var ready = false
    private var running = false
    private var presentationIndex = 0
    private var attempts = 0
    private var mapper: GazeMapper? = null
    private var lineHeightPx = 1f
    private val presentations = ArrayList<Presentation>()
    private val fitSamples = LinkedHashMap<Int, CalibrationSample>()
    private val pointSamples = ArrayList<CompactFaceIrisSample>()
    private val records = ArrayList<PointRecord>()
    private val validation = ArrayList<ReadingSpatialMetrics.Observation>()
    private val runLabel by lazy {
        sanitizeLabel(intent.getStringExtra(EXTRA_RUN_LABEL) ?: DEFAULT_RUN_LABEL)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUi()
        lineHeightPx = computeLineHeightPx()
        collector = CalibrationPointCollector(targetView, tone = null)
        collector.setOnSampleWindowChanged { active ->
            sampleWindowActive = active
            if (active) pointSamples.clear()
        }
        startButton.setOnClickListener { startRun() }
        if (hasCameraPermission()) startCamera() else requestCameraPermission()
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(250, 250, 250)) }
        targetView = CalibrationView(this)
        root.addView(targetView, matchParent())
        progressText = TextView(this).apply {
            setTextColor(Color.rgb(50, 50, 50))
            textSize = 17f
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        root.addView(
            progressText,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP)
                .apply { topMargin = dp(24) },
        )
        countdownText = TextView(this).apply {
            setTextColor(Color.rgb(33, 33, 33))
            textSize = 72f
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        root.addView(
            countdownText,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER),
        )
        instructionText = TextView(this).apply {
            setTextColor(Color.rgb(35, 35, 35))
            textSize = 18f
            gravity = Gravity.CENTER
            text = "Compact candidate calibration check\n\n" +
                "This is a separate research measurement. It will show one practice target, 16 fit targets, and five held-out targets.\n\n" +
                "Look directly at each red target until it moves. No gaze dot is shown. Your real NewsMead calibration will not be used or changed.\n\nPreparing camera…"
        }
        startButton = Button(this).apply { text = "Start"; isEnabled = false }
        instructionPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            addView(instructionText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(
                startButton,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(24) },
            )
        }
        root.addView(instructionPanel, matchParent())
        setContentView(root)
    }

    private fun matchParent() = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )

    private fun startCamera() {
        backend = CompactFaceIrisBackend(applicationContext) { sample ->
            runOnUiThread { onSample(sample) }
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
                ResolutionSelector.Builder().setResolutionStrategy(
                    ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                ).build(),
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
            current.submit(transformed.copy(Bitmap.Config.ARGB_8888, false), frame.imageInfo.timestamp)
        } finally {
            frame.close()
        }
    }

    private fun onSample(sample: CompactFaceIrisSample) {
        if (!ready) {
            ready = true
            startButton.isEnabled = true
            instructionText.text = instructionText.text.toString().replace("Preparing camera…", "Ready.")
        }
        if (!running) return
        if (sampleWindowActive) pointSamples.add(sample)
        collector.onRawSample(sample.candidateHorizontal, sample.candidateVertical, sample.resultElapsedNs / 1_000_000L)
    }

    private fun startRun() {
        if (!ready || running) return
        running = true
        instructionPanel.visibility = View.GONE
        buildFitPresentations()
        countdown(3) { runPresentation(0) }
    }

    private fun buildFitPresentations() {
        presentations.clear()
        presentations += Presentation(Kind.PRACTICE, -1, 0.5f, 0.5f)
        val fractions = floatArrayOf(0.1f, 0.3667f, 0.6333f, 0.9f)
        var index = 0
        for (y in fractions) for (x in fractions) presentations += Presentation(Kind.FIT, index++, x, y)
    }

    private fun countdown(value: Int, done: () -> Unit) {
        if (value == 0) {
            countdownText.visibility = View.GONE
            done()
        } else {
            countdownText.visibility = View.VISIBLE
            countdownText.text = value.toString()
            handler.postDelayed({ countdown(value - 1, done) }, 1_000L)
        }
    }

    private fun runPresentation(index: Int) {
        presentationIndex = index
        attempts = 0
        capture()
    }

    private fun capture() {
        val presentation = presentations[presentationIndex]
        val point = PointF(targetView.width * presentation.xFraction, targetView.height * presentation.yFraction)
        progressText.visibility = View.VISIBLE
        progressText.text = when (presentation.kind) {
            Kind.PRACTICE -> "Practice target"
            Kind.FIT -> "Calibration target ${presentation.pointIndex + 1} of 16"
            Kind.VALIDATION -> "Held-out target ${presentation.pointIndex + 1} of 5"
        }
        updateMiniMap(presentation)
        collector.capture(point.x, point.y) { result ->
            record(presentation, point, result)
            if (result.status == FixationWindowFilter.Status.ACCEPTED) {
                accept(presentation, point, result)
                handler.postDelayed({ advance() }, CONFIRM_MS)
            } else {
                attempts += 1
                if (attempts < MAX_ATTEMPTS) handler.postDelayed({ capture() }, RETRY_PAUSE_MS)
                else handler.postDelayed({ advance() }, RETRY_PAUSE_MS)
            }
        }
    }

    private fun accept(presentation: Presentation, target: PointF, result: FixationWindowFilter.Result) {
        when (presentation.kind) {
            Kind.PRACTICE -> Unit
            Kind.FIT -> fitSamples[presentation.pointIndex] = CalibrationSample(
                target.x, target.y, result.medianX, result.medianY,
            )
            Kind.VALIDATION -> {
                val predicted = mapper?.map(result.medianX, result.medianY) ?: return
                validation += ReadingSpatialMetrics.Observation(
                    presentation.pointIndex,
                    validationLabel(presentation.pointIndex),
                    target.x,
                    target.y,
                    predicted[0],
                    predicted[1],
                )
            }
        }
    }

    private fun record(
        presentation: Presentation,
        target: PointF,
        result: FixationWindowFilter.Result,
    ) {
        records += PointRecord(
            presentation.kind.name.lowercase(Locale.US),
            presentation.pointIndex,
            attempts + 1,
            target.x,
            target.y,
            result,
            pointSamples.toList(),
        )
    }

    private fun advance() {
        val next = presentationIndex + 1
        if (next < presentations.size) {
            runPresentation(next)
        } else if (mapper == null) {
            fitAndStartValidation()
        } else {
            finishRun()
        }
    }

    private fun fitAndStartValidation() {
        if (fitSamples.size < MIN_FIT_POINTS) {
            finishFailure("Only ${fitSamples.size}/16 fit targets were accepted.")
            return
        }
        mapper = try {
            GazeMapper(fitSamples.entries.sortedBy { it.key }.map { it.value })
        } catch (error: Exception) {
            finishFailure("Candidate fit failed: ${error.message}")
            return
        }
        val start = presentations.size
        VALIDATION_FRACTIONS.forEachIndexed { index, pair ->
            presentations += Presentation(Kind.VALIDATION, index, pair.first, pair.second)
        }
        runPresentation(start)
    }

    private fun finishRun() {
        running = false
        collector.cancel()
        targetView.hideTarget()
        targetView.setMiniMap(emptyList())
        progressText.visibility = View.GONE
        cameraProvider?.unbindAll()
        backend?.close()
        backend = null

        val orderedFit = fitSamples.entries.sortedBy { it.key }
        val loo = CalibrationQuality.leaveOneOut(orderedFit.map { it.value })
        val heldOut = ReadingSpatialMetrics.summarize(validation, lineHeightPx)
        val looSummary = loo?.let { report ->
            ReadingSpatialMetrics.summarize(
                orderedFit.mapIndexed { index, entry ->
                    ReadingSpatialMetrics.Observation(
                        entry.key,
                        "row ${entry.key / 4 + 1}, col ${entry.key % 4 + 1}",
                        entry.value.screenX,
                        entry.value.screenY,
                        entry.value.screenX + report.dxPx[index],
                        entry.value.screenY + report.dyPx[index],
                    )
                },
                lineHeightPx,
            )
        }
        val artifact = writeArtifact(orderedFit, loo, looSummary, heldOut)
        instructionText.text = if (heldOut == null || looSummary == null) {
            "Candidate calibration complete, but a complete summary could not be calculated.\n\nSaved: ${artifact.name}\n\nYour real calibration was not changed."
        } else {
            String.format(
                Locale.US,
                "Candidate calibration complete\n\nFit targets: %d/16\n" +
                    "LOO vertical median/P95/max: %.2f / %.2f / %.2f lines\n" +
                    "Held-out vertical median/P95/max: %.2f / %.2f / %.2f lines\n" +
                    "Held-out 2-D median/P95/max: %.0f / %.0f / %.0f px\n\n" +
                    "Saved separately: %s\n\nYour real NewsMead calibration was not changed.",
                fitSamples.size,
                looSummary.medianVerticalPx / lineHeightPx,
                looSummary.p95VerticalPx / lineHeightPx,
                looSummary.maxVerticalPx / lineHeightPx,
                heldOut.medianVerticalPx / lineHeightPx,
                heldOut.p95VerticalPx / lineHeightPx,
                heldOut.maxVerticalPx / lineHeightPx,
                heldOut.medianErrorPx,
                heldOut.p95ErrorPx,
                heldOut.maxErrorPx,
                artifact.name,
            )
        }
        startButton.visibility = View.GONE
        instructionPanel.visibility = View.VISIBLE
    }

    private fun finishFailure(message: String) {
        running = false
        collector.cancel()
        targetView.hideTarget()
        cameraProvider?.unbindAll()
        backend?.close()
        backend = null
        instructionText.text = "$message\n\nNo candidate was saved as a real calibration, and your existing calibration was not changed."
        startButton.visibility = View.GONE
        instructionPanel.visibility = View.VISIBLE
    }

    private fun writeArtifact(
        fit: List<Map.Entry<Int, CalibrationSample>>,
        loo: CalibrationQuality.Report?,
        looSummary: ReadingSpatialMetrics.Summary?,
        heldOut: ReadingSpatialMetrics.Summary?,
    ): File {
        val directory = File(filesDir, "gaze_diagnostics").apply { mkdirs() }
        val file = File(
            directory,
            "compact_face_calibration_${FILE_TIMESTAMP.format(Instant.now())}_${runLabel}.json",
        )
        val json = JSONObject().apply {
            put("schema_version", 1)
            put("run_label", runLabel)
            put("timestamp_utc", Instant.now().toString())
            put("device_model", Build.MODEL)
            put("candidate", CANDIDATE_NAME)
            put("candidate_affects_gaze_output", false)
            put("calibration_store_read", false)
            put("calibration_store_written", false)
            put("camera_frames_retained", false)
            put("target_order", "practice_center_then_16_fixed_row_major_then_5_held_out")
            put("line_height_px", lineHeightPx)
            put("fit_samples", JSONArray().apply {
                fit.forEach { entry -> put(JSONObject().apply {
                    put("grid_index", entry.key)
                    put("target_x", entry.value.screenX)
                    put("target_y", entry.value.screenY)
                    put("candidate_horizontal", entry.value.gazeX)
                    put("candidate_vertical", entry.value.gazeY)
                }) }
            })
            put("loo", summaryJson(looSummary))
            put("loo_raw", loo?.let { report -> JSONObject().apply {
                put("median_2d_px", report.medianPx)
                put("p95_2d_px", report.p95Px)
            } } ?: JSONObject.NULL)
            put("held_out", summaryJson(heldOut))
            put("points", JSONArray().apply { records.forEach { put(it.toJson()) } })
        }
        file.writeText(json.toString(2))
        return file
    }

    private fun summaryJson(summary: ReadingSpatialMetrics.Summary?): Any = summary?.let {
        JSONObject().apply {
            put("vertical_median_px", it.medianVerticalPx)
            put("vertical_p95_px", it.p95VerticalPx)
            put("vertical_max_px", it.maxVerticalPx)
            put("vertical_median_lines", it.medianVerticalPx / lineHeightPx)
            put("vertical_p95_lines", it.p95VerticalPx / lineHeightPx)
            put("vertical_max_lines", it.maxVerticalPx / lineHeightPx)
            put("median_2d_px", it.medianErrorPx)
            put("p95_2d_px", it.p95ErrorPx)
            put("max_2d_px", it.maxErrorPx)
            put("points", JSONArray().apply { it.points.forEach { point -> put(JSONObject().apply {
                put("id", point.id)
                put("label", point.label)
                put("dx_px", point.dxPx)
                put("dy_px", point.dyPx)
                put("vertical_lines", point.verticalLines)
                put("error_2d_px", point.errorPx)
            }) } })
        }
    } ?: JSONObject.NULL

    private fun PointRecord.toJson(): JSONObject = JSONObject().apply {
        put("kind", kind)
        put("point_index", pointIndex)
        put("attempt", attempt)
        put("target_x", targetX)
        put("target_y", targetY)
        put("status", result.status.name.lowercase(Locale.US))
        put("raw_count", result.rawCount)
        put("retained_count", result.retainedCount)
        put("median_horizontal", finiteOrNull(result.medianX))
        put("median_vertical", finiteOrNull(result.medianY))
        put("dispersion_horizontal", finiteOrNull(result.dispersionX))
        put("dispersion_vertical", finiteOrNull(result.dispersionY))
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

    private fun updateMiniMap(presentation: Presentation) {
        if (presentation.kind != Kind.FIT) return
        targetView.setMiniMap((0 until 16).map { index ->
            when {
                index == presentation.pointIndex -> CalibrationView.MiniDotState.CURRENT
                fitSamples.containsKey(index) -> CalibrationView.MiniDotState.DONE
                else -> CalibrationView.MiniDotState.PENDING
            }
        })
    }

    private fun validationLabel(index: Int): String = when (index) {
        0 -> "center"
        1 -> "top-left"
        2 -> "top-right"
        3 -> "bottom-left"
        4 -> "bottom-right"
        else -> "held-out ${index + 1}"
    }

    private fun computeLineHeightPx(): Float {
        val metrics = resources.displayMetrics
        val paint = TextPaint().apply { textSize = StudyConfig.ARTICLE_FONT_SIZE_DP * metrics.density }
        val font = paint.fontMetrics
        val extra = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, metrics)
        return (font.descent - font.ascent) * ARTICLE_LINE_SPACING_MULT + extra
    }

    private fun finiteOrNull(value: Float): Any = if (value.isFinite()) value else JSONObject.NULL

    private fun sanitizeLabel(value: String): String = value.trim().lowercase(Locale.US)
        .replace(Regex("[^a-z0-9_-]+"), "_").trim('_').take(64).ifEmpty { DEFAULT_RUN_LABEL }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() = ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.CAMERA),
        CAMERA_PERMISSION_REQUEST,
    )

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == CAMERA_PERMISSION_REQUEST && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else if (requestCode == CAMERA_PERMISSION_REQUEST) {
            instructionText.text = "Camera permission is required for this researcher-only gate."
        }
    }

    override fun onDestroy() {
        collector.cancel()
        handler.removeCallbacksAndMessages(null)
        cameraProvider?.unbindAll()
        backend?.close()
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    private enum class Kind { PRACTICE, FIT, VALIDATION }
    private data class Presentation(
        val kind: Kind,
        val pointIndex: Int,
        val xFraction: Float,
        val yFraction: Float,
    )
    private data class PointRecord(
        val kind: String,
        val pointIndex: Int,
        val attempt: Int,
        val targetX: Float,
        val targetY: Float,
        val result: FixationWindowFilter.Result,
        val samples: List<CompactFaceIrisSample>,
    )

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 4107
        private const val EXTRA_RUN_LABEL = "run_label"
        private const val DEFAULT_RUN_LABEL = "compact_face_calibration"
        private const val CANDIDATE_NAME = "blazeface_periodic_plus_face_landmark_468_plus_two_eye_iris_64_bulk_float_v2"
        private const val MIN_FIT_POINTS = 12
        private const val MAX_ATTEMPTS = 2
        private const val CONFIRM_MS = 250L
        private const val RETRY_PAUSE_MS = 500L
        private const val ARTICLE_LINE_SPACING_MULT = 1.6f
        private val VALIDATION_FRACTIONS = listOf(
            0.5f to 0.5f,
            0.25f to 0.25f,
            0.75f to 0.25f,
            0.25f to 0.75f,
            0.75f to 0.75f,
        )
        private val FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US)
            .withZone(ZoneOffset.UTC)
    }
}
