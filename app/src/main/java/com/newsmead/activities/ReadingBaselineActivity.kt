package com.newsmead.activities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.ViewTreeObserver
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.button.MaterialButton
import com.newsmead.data.StudyConfig
import com.newsmead.gaze.ParticipantReadingBaseline
import com.newsmead.gaze.ReadingFeaturePipeline
import com.newsmead.gaze.TextTarget
import com.newsmead.gaze.mahalanobis.MahalanobisBaselineCollector
import com.newsmead.gaze.mgazenet.MgazeNetCalibrationStore
import com.newsmead.gaze.mgazenet.MgazeNetGazeProvider
import com.newsmead.gaze.reading.ArticleReadingSession
import com.newsmead.research.MahalanobisJson
import com.newsmead.research.MahalanobisSessionLog
import com.newsmead.research.ResearchSession
import org.json.JSONObject
import java.security.MessageDigest

/** Explicit unassisted baseline. The frozen profile is process/session scoped, never restored. */
class ReadingBaselineActivity : AppCompatActivity() {
    private val config = StudyConfig.MAHALANOBIS_CONFIG
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var root: LinearLayout
    private lateinit var body: TextView
    private lateinit var instructions: TextView
    private lateinit var participant: EditText
    private lateinit var status: TextView
    private lateinit var startButton: MaterialButton
    private lateinit var finishButton: MaterialButton
    private lateinit var exportButton: MaterialButton
    private var pipeline = ReadingFeaturePipeline(config)
    private var collector = MahalanobisBaselineCollector(config)
    private var adapter: ArticleReadingSession? = null
    private var provider: MgazeNetGazeProvider? = null
    private var log: MahalanobisSessionLog? = null
    private var running = false
    private var stopping = false
    private var resumed = false
    private var destroyed = false
    private var frozen = false
    private var pendingPermission = false
    private var participantCode = ""
    private var fingerprint = ""
    private var sessionId = ""
    private var startedAt = 0L
    private var lineCount = 0
    private var vectors = 0
    private var validVectors = 0
    private var latestWindowSummary = "Waiting for gaze samples"
    private var intervalInputCount = 0
    private var intervalEligibleCount = 0
    private var intervalWatchdogStaleCount = 0
    private val intervalExclusions = linkedMapOf<String, Int>()
    private var intervalAgeCount = 0
    private var intervalAgeSumMs = 0.0
    private var intervalAgeMaxMs = 0.0
    private val passageHash = MessageDigest.getInstance("SHA-256")
        .digest(BASELINE_PASSAGE.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val requested = pendingPermission
        pendingPermission = false
        if (requested && granted && resumed && !destroyed) beginAttempt()
        else if (requested) status.text = "Camera permission is required. Press Start when ready."
    }
    private val preDraw = ViewTreeObserver.OnPreDrawListener { adapter?.checkGeometry(); true }
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            collectTick()
            status.text = "Valid $validVectors/$vectors (need ${config.minimumRetainedWindows}); ${(now() - startedAt) / 1000}s\n" +
                "Covered ${collector.coveredDurationMs / 1000}s/${config.minimumBaselineDurationMs / 1000}s\n" +
                latestWindowSummary
            handler.postDelayed(this, config.updateIntervalMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        root.addView(MaterialButton(this).apply { text = "Back"; setOnClickListener { finish() } })
        root.addView(TextView(this).apply {
            text = "Unassisted reading baseline"
            textSize = 22f
        })
        instructions = TextView(this).apply {
            text = "Read naturally and scroll by hand. A ${config.windowMs / 1000}-second window must fill first; then collect at least ${config.minimumRetainedWindows} valid windows and ${config.minimumBaselineDurationMs / 1000} seconds of covered valid reading. This can take longer than one minute. A new baseline is required after app restart or recalibration."
        }
        root.addView(instructions)
        participant = EditText(this).apply { hint = "Participant code"; isSingleLine = true }
        root.addView(participant)
        status = TextView(this).apply { minLines = 3; maxLines = 3 }
        root.addView(status)
        startButton = MaterialButton(this).apply { text = "Start"; setOnClickListener { requestStart() } }
        finishButton = MaterialButton(this).apply { text = "Finish and freeze"; setOnClickListener { finishAttempt() } }
        exportButton = MaterialButton(this).apply {
            text = "Export baseline log"
            setOnClickListener {
                val journal = log ?: return@setOnClickListener
                runCatching { startActivity(journal.share(this@ReadingBaselineActivity)) }
                    .onFailure { status.text = "Export failed: ${it.message}" }
            }
        }
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        buttons.addView(startButton); buttons.addView(finishButton); buttons.addView(exportButton)
        root.addView(buttons)
        body = TextView(this).apply {
            text = BASELINE_PASSAGE
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, StudyConfig.ARTICLE_FONT_SIZE_DP)
            setPadding(dp(8), dp(8), dp(8), dp(24))
        }
        val scroll = NestedScrollView(this).apply { addView(body) }
        scroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, y, _, oldY ->
            if (running && y != oldY) adapter?.checkGeometry()
        })
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        body.viewTreeObserver.addOnPreDrawListener(preDraw)
        root.post { refreshReadyState() }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        root.post { refreshReadyState() }
    }

    private fun refreshReadyState() {
        if (running || stopping || destroyed) return
        val store = MgazeNetCalibrationStore(this)
        val existing = ParticipantReadingBaseline.current(ResearchSession.sessionId, store.fingerprint())
        if (existing != null) {
            frozen = true
            participant.setText(existing.participantCode)
            status.text = "Baseline frozen for this session. Return to article reading, or explicitly start a new session."
        } else if (!frozen) {
            val issue = store.compatibilityIssue(root)
            status.text = issue ?: "Ready. Enter a participant code and press Start to use the camera."
        }
        updateButtons()
    }

    private fun updateButtons() {
        if (destroyed) return
        instructions.visibility = if (running) View.GONE else View.VISIBLE
        participant.visibility = if (running) View.GONE else View.VISIBLE
        startButton.visibility = if (running) View.GONE else View.VISIBLE
        finishButton.visibility = if (running) View.VISIBLE else View.GONE
        exportButton.visibility = if (!running && log != null) View.VISIBLE else View.GONE
        participant.isEnabled = !running && !stopping && !frozen
        startButton.text = if (frozen) "Start New session" else "Start / retry baseline"
        startButton.isEnabled = !running && !stopping &&
            MgazeNetCalibrationStore(this).compatibilityIssue(root) == null
        finishButton.isEnabled = running && !stopping
        exportButton.isEnabled = log != null && !running && !stopping
    }

    private fun requestStart() {
        if (!resumed || running || stopping) return
        if (frozen) {
            // This explicit action is the only replacement path for a successful baseline.
            frozen = false
            ParticipantReadingBaseline.beginNewBaseline()
            participant.isEnabled = true
            status.text = "Enter the participant code and press Start for the new session."
            updateButtons()
            return
        }
        if (participant.text.toString().trim().isEmpty()) {
            participant.error = "Enter a participant code"
            return
        }
        val issue = MgazeNetCalibrationStore(this).compatibilityIssue(root)
        if (issue != null) { status.text = issue; updateButtons(); return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingPermission = true
            cameraPermission.launch(Manifest.permission.CAMERA)
        } else beginAttempt()
    }

    private fun beginAttempt() {
        if (!resumed || destroyed || running || stopping || frozen) return
        val store = MgazeNetCalibrationStore(this)
        val issue = store.compatibilityIssue(root)
        val calibration = store.fingerprint()
        participantCode = participant.text.toString().trim()
        if (issue != null || calibration == null || participantCode.isEmpty()) {
            status.text = issue ?: "Participant code and compatible calibration are required."
            return
        }
        fingerprint = calibration
        participant.clearFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(participant.windowToken, 0)
        log?.close()
        ResearchSession.start(this, participantCode, "mahalanobis")
        ParticipantReadingBaseline.beginNewBaseline()
        sessionId = ResearchSession.sessionId!!
        log = MahalanobisSessionLog(this, "baseline")
        log?.write(provenance().put("type", "baseline_setup").put("config", MahalanobisJson.config(config))
            .put("stimulusStatus", "fixed_provisional_baseline_stimulus_v1")
            .put("aids", false).put("gazeDot", false).put("autoScroll", false)
            .put("smoothForReading", true).put("rejectBlinks", true)
            .put("maxOutputAgeMs", StudyConfig.READING_MAX_OUTPUT_AGE_MS)
            .put("baselinePersistence", "process_session_only_new_baseline_after_restart"))
        pipeline = ReadingFeaturePipeline(config)
        collector = MahalanobisBaselineCollector(config)
        vectors = 0; validVectors = 0
        latestWindowSummary = "Waiting for gaze samples"
        intervalInputCount = 0; intervalEligibleCount = 0; intervalWatchdogStaleCount = 0
        intervalExclusions.clear()
        intervalAgeCount = 0; intervalAgeSumMs = 0.0; intervalAgeMaxMs = 0.0
        startedAt = now()
        running = true
        adapter = ArticleReadingSession(body,
            onGeometry = { _, geometry -> lineCount = geometry?.lines?.size ?: 0 },
            onInput = { _, sample ->
                intervalInputCount++
                if (sample.eligible) intervalEligibleCount++
                else {
                    val reason = sample.exclusion ?: "unknown"
                    intervalExclusions[reason] = (intervalExclusions[reason] ?: 0) + 1
                }
                val ageMs = sample.captureMs?.let { capture ->
                    sample.deliveryMs?.minus(capture)?.takeIf { it.isFinite() && it >= 0.0 }
                }
                if (ageMs != null) {
                    intervalAgeCount++
                    intervalAgeSumMs += ageMs
                    intervalAgeMaxMs = maxOf(intervalAgeMaxMs, ageMs)
                }
                val capture = sample.captureMs
                if (running && capture != null && capture.isFinite() && capture >= startedAt) {
                    val target = sample.line?.let { line -> TextTarget(line, lineCount,
                        sample.wordStartUtf16 ?: TextTarget.NO_OFFSET, sample.wordEndUtf16 ?: TextTarget.NO_OFFSET) }
                        ?: TextTarget.INVALID
                    pipeline.sample(target, sample.eligible, capture.toLong())
                }
            },
            onBoundary = { reason ->
                when (reason) {
                    "invalid_sample", "non_monotonic_capture" -> Unit
                    "reading_scroll" -> pipeline.breakContinuity()
                    else -> pipeline.boundary()
                }
                log?.write(JSONObject().put("type", "reading_boundary").put("reason", reason).put("atMs", now()))
            },
            // The baseline has no controlled auto-scroll. Its stationary text geometry
            // remains valid across camera delivery latency; ordinary scroll changes
            // still create a boundary and require the geometry settle interval.
            motionCompensatedAids = false)
        adapter?.checkGeometry()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val camera = MgazeNetGazeProvider(this, body, smoothForReading = true,
            rejectBlinks = true, maxOutputAgeMs = StudyConfig.READING_MAX_OUTPUT_AGE_MS)
        provider = camera
        camera.onObservation = { observation ->
            if (running && provider === camera) {
                if (observation.timeoutWatchdog) intervalWatchdogStaleCount++
                adapter?.record(observation)
            }
        }
        camera.onFailure = { reason ->
            if (provider === camera && !destroyed) {
                stopCollection("provider_failure") { status.text = "Baseline stopped: $reason. Press Start to retry." }
            }
        }
        updateButtons()
        handler.post(tick)
        runCatching { camera.start(this) }.onFailure { error ->
            stopCollection("provider_start_failure") { status.text = "Camera could not start: ${error.message}. Press Start to retry." }
        }
    }

    private fun collectTick() {
        pipeline.tick(now())?.let { vector ->
            collector.add(vector)
            vectors++
            if (vector.isUsable(config)) validVectors++
            val dominantExclusion = when (intervalExclusions.maxByOrNull { it.value }?.key) {
                "outside_visible_text" -> "off text"
                "geometry_unsettled" -> "scroll settling"
                "stale" -> "late gaze"
                "blink" -> "blink"
                else -> null
            }
            latestWindowSummary = "${vector.quality}: ${(vector.validFraction * 100).toInt()}% valid gaze" +
                (dominantExclusion?.let { "; $it" } ?: "")
            val reasons = JSONObject()
            intervalExclusions.forEach { (reason, count) -> reasons.put(reason, count) }
            log?.write(MahalanobisJson.vector(vector, "baseline_vector", config)
                .put("inputCountSincePreviousUpdate", intervalInputCount)
                .put("eligibleInputCountSincePreviousUpdate", intervalEligibleCount)
                .put("watchdogStaleCountSincePreviousUpdate", intervalWatchdogStaleCount)
                .put("inputExclusionsSincePreviousUpdate", reasons)
                .put("inputAgeMeanMsSincePreviousUpdate",
                    if (intervalAgeCount > 0) intervalAgeSumMs / intervalAgeCount else JSONObject.NULL)
                .put("inputAgeMaxMsSincePreviousUpdate",
                    if (intervalAgeCount > 0) intervalAgeMaxMs else JSONObject.NULL))
            intervalInputCount = 0; intervalEligibleCount = 0; intervalWatchdogStaleCount = 0
            intervalExclusions.clear()
            intervalAgeCount = 0; intervalAgeSumMs = 0.0; intervalAgeMaxMs = 0.0
        }
    }

    private fun finishAttempt() {
        if (!running || stopping) return
        collectTick()
        stopCollection("baseline_finish") {
            val store = MgazeNetCalibrationStore(this)
            if (ResearchSession.sessionId != sessionId || store.fingerprint() != fingerprint || store.compatibilityIssue(root) != null) {
                status.text = "Baseline rejected: session or calibration changed. Press Start to retry."
                log?.write(provenance().put("type", "baseline_rejected").put("reason", "identity_changed"))
            } else {
                val fit = collector.freeze()
                val profile = fit.profile
                if (profile == null) {
                    log?.write(provenance().put("type", "baseline_rejected").put("status", fit.status.name))
                    status.text = "Baseline invalid: ${fit.status.name}. No profile saved. Press Start to retry."
                } else {
                    log?.write(MahalanobisJson.profile(profile).put("participantCode", participantCode)
                        .put("researchSessionId", sessionId).put("calibrationFingerprint", fingerprint)
                        .put("passageId", PASSAGE_ID).put("passageSha256", passageHash))
                    ParticipantReadingBaseline.freeze(ParticipantReadingBaseline.Entry(participantCode,
                        sessionId, fingerprint, profile, log!!.file.name))
                    frozen = true
                    status.text = "Baseline frozen. Return to article reading in this session."
                }
            }
            log?.close()
            updateButtons()
        }
    }

    /** Keep provider ownership until its asynchronous close completes; Start stays disabled. */
    private fun stopCollection(reason: String, after: () -> Unit = {}) {
        running = false
        handler.removeCallbacks(tick)
        adapter?.close(reason); adapter = null
        pipeline.boundary()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val closing = provider
        stopping = closing != null
        updateButtons()
        val complete = {
            if (provider === closing) provider = null
            stopping = false
            if (!destroyed) { after(); updateButtons() }
        }
        if (closing == null) complete() else closing.stopAndThen { runOnUiThread { complete() } }
    }

    override fun onPause() {
        resumed = false
        if (running) {
            log?.write(provenance().put("type", "baseline_interrupted").put("reason", "activity_paused").put("atMs", now()))
            stopCollection("activity_paused") { status.text = "Baseline interrupted. Press Start to retry; paused time is excluded." }
        }
        super.onPause()
    }

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        if (body.viewTreeObserver.isAlive) body.viewTreeObserver.removeOnPreDrawListener(preDraw)
        stopCollection("activity_destroyed")
        log?.close()
        super.onDestroy()
    }

    private fun provenance() = JSONObject().put("participantCode", participantCode)
        .put("researchSessionId", sessionId).put("calibrationFingerprint", fingerprint)
        .put("passageId", PASSAGE_ID).put("passageSha256", passageHash)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun now() = SystemClock.elapsedRealtime()

    companion object {
        private const val PASSAGE_ID = "fixed_provisional_baseline_stimulus_v1"
        // Deliberate copy: changing this stimulus must not change frozen accuracy validation.
        private val BASELINE_PASSAGE = """
            On quiet mornings, Lina walked to the community library before the streets became busy. She liked the wide windows, the wooden tables, and the patient rhythm of pages turning nearby. The familiar room made it easier to settle into a story without rushing.

            One week, the librarian displayed a report about gardens built above crowded buildings. These rooftop spaces reduced heat, absorbed rainwater, and gave residents a place to grow herbs. Volunteers recorded which plants survived strong sunlight and which needed shade during the afternoon.

            Interpreting the results required care because the buildings differed in height, wind exposure, and access to water. A plant that struggled on one roof sometimes flourished only two streets away. The researchers therefore compared repeated observations instead of trusting a single measurement.

            Lina reread that explanation and noticed how easily one dramatic result could hide the larger pattern. She wrote a short note in the margin, then continued to a diagram showing the temperature changes across several months. The evidence became clearer when she connected the sentences to the chart.

            By noon, the room had grown brighter and more readers had arrived. Lina closed the report, returned it to the desk, and carried the main lesson home: careful conclusions depend on consistent observations gathered under different conditions.
        """.trimIndent()
    }
}
