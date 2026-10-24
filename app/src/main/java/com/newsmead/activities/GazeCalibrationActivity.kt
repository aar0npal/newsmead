package com.newsmead.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import java.util.Locale
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.newsmead.databinding.ActivityGazeCalibrationBinding
import com.newsmead.gaze.gazeCoordinateFrame
import com.newsmead.gaze.physicalDisplaySize
import com.newsmead.gaze.ComparisonCalibrationEvidence
import com.newsmead.gaze.ComparisonCalibrationReservation
import com.newsmead.gaze.ComparisonLaunchSpec
import com.newsmead.gaze.ComparisonRuntime
import com.newsmead.gaze.ComparisonSlotStore
import com.newsmead.gaze.mgazenet.*

/** MGazeNet-only calibration. Setup performs no camera/native/store mutation. */
class GazeCalibrationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGazeCalibrationBinding
    private val handler = Handler(Looper.getMainLooper())
    private var source: MgazeNetCameraSource? = null
    private var session: MgazeNetCalibrationSession? = null
    private var identity: CalibrationIdentity? = null
    private var active = false
    private var fitting = false
    private var startedAt = 0.0
    private var verification = -1
    private var checkStart = Double.NaN
    private var checkAttempt = 1
    private var postFitPostureWarningUntil = Double.NEGATIVE_INFINITY
    private val checkPredictions = ArrayList<Pair<Float,Float>>()
    private val checkResults = ArrayList<MgazeNetCalibrationQualityGate.Check>()
    private val summaries = ArrayList<String>()
    private var qualityReport: MgazeNetCalibrationQualityGate.Report? = null
    private var pxPerCmX = Double.NaN
    private var pxPerCmY = Double.NaN
    private var permissionPending = false
    private var preparingComparison = false
    private var comparisonSpec: ComparisonLaunchSpec? = null
    private var comparisonReservation: ComparisonCalibrationReservation? = null
    private var comparisonCalibrationCommitted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGazeCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        WindowInsetsControllerCompat(window,binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        binding.root.keepScreenOn = false
        binding.progressText.text = "MGazeNet 13-point calibration. Start opens the camera; follow each target until it advances."
        binding.startButton.setOnClickListener {
            requestStart()
        }
        binding.btnAccept.setOnClickListener { save() }
        binding.btnRedoAll.setOnClickListener { discardAndPrepareRetry() }
        binding.btnRedoWorst.visibility = View.GONE
        binding.btnRedoAll.visibility = View.GONE

        val comparisonLaunch = ComparisonRuntime.parseLaunch(intent)
        if (comparisonLaunch.requested) {
            val spec = comparisonLaunch.spec
            if (spec == null) {
                binding.progressText.text = comparisonLaunch.error
                binding.startButton.isEnabled = false
            } else {
                comparisonSpec = spec
                val issue = ComparisonSlotStore(this).calibrationStartIssue(spec)
                binding.progressText.text = issue ?:
                    "Matched comparison ${spec.slot.id}. Start verifies and reserves this slot before opening the camera."
                binding.startButton.isEnabled = issue == null
                binding.startButton.text = "Start comparison calibration"
            }
        }
    }

    private fun requestStart() {
        if (active || preparingComparison) return
        if (ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionPending = true
            ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.CAMERA),42)
        } else prepareComparisonThenBegin()
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code,permissions,results)
        if (code == 42 && permissionPending) {
            permissionPending = false
            if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) prepareComparisonThenBegin()
        }
    }

    private fun prepareComparisonThenBegin() {
        val spec = comparisonSpec
        if (spec == null) {
            begin()
            return
        }
        preparingComparison = true
        binding.startButton.isEnabled = false
        binding.progressText.text = "Verifying the installed comparison build…"
        val displaySize = binding.calibrationView.physicalDisplaySize()
        val densityDpi = resources.displayMetrics.densityDpi
        val rotation = binding.calibrationView.display.rotation
        Thread {
            val resolved = runCatching {
                ComparisonRuntime.resolveIdentity(
                    this,
                    spec,
                    displaySize.x,
                    displaySize.y,
                    densityDpi,
                    rotation,
                )
            }
            runOnUiThread {
                preparingComparison = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                resolved.fold(
                    onSuccess = { runtime ->
                        runCatching { ComparisonSlotStore(this).reserveCalibration(runtime) }
                            .onSuccess { reservation ->
                                comparisonReservation = reservation
                                begin()
                            }
                            .onFailure { failure ->
                                binding.progressText.text = failure.message
                                    ?: "The comparison slot could not be reserved."
                            }
                    },
                    onFailure = { failure ->
                        binding.progressText.text = failure.message
                            ?: "The comparison build could not be verified."
                    },
                )
            }
        }.start()
    }
    private fun begin() {
        if (active) return
        if (binding.calibrationView.width == 0) {
            binding.calibrationView.post { begin() }
            return
        }
        val store = MgazeNetCalibrationStore(this)
        val metrics = resources.displayMetrics
        if (!metrics.xdpi.isFinite() || metrics.xdpi <= 0f || !metrics.ydpi.isFinite() || metrics.ydpi <= 0f) {
            stop("Physical display dimensions are unavailable. Calibration was not started.")
            return
        }
        pxPerCmX = metrics.xdpi / 2.54
        pxPerCmY = metrics.ydpi / 2.54
        identity = store.identity(binding.calibrationView)
        active = true; startedAt = MgazeNetCameraSource.now()
        binding.root.keepScreenOn = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.startButton.visibility = View.GONE
        binding.progressText.text = "Preparing MGazeNet…"
        source = MgazeNetCameraSource(this,this,ready = { metadata ->
            identity = identity!!.copy(acquisition = metadata["acquisition"] as String)
            val s = MgazeNetCalibrationSession(identity!!); session = s
            countdown(3) { s.begin(); showTarget() }
        }, result = { frame ->
            if (active) {
                if (!geometryMatches()) stop("Screen geometry changed. Calibration was not saved.")
                else if (verification >= 0) observeCheck(frame)
                else session?.sample(frame.captureMs,frame.outputMs,frame.features,frame.leftArea,frame.rightArea,frame.posture)
            }
        },failed = { stop(it) },store = store).also { it.start() }
        handler.post(tick)
    }
    private fun countdown(n: Int, done: () -> Unit) {
        if (!active) return
        if (n == 0) { binding.countdownText.visibility = View.GONE; done(); return }
        binding.countdownText.visibility = View.VISIBLE; binding.countdownText.text = n.toString()
        handler.postDelayed({ countdown(n-1,done) },1000)
    }
    private fun geometryMatches() = identity?.let {
        val size = binding.calibrationView.physicalDisplaySize()
        size.x == it.screenWidth && size.y == it.screenHeight && binding.calibrationView.display.rotation == it.rotation &&
            binding.calibrationView.gazeCoordinateFrame() == it.viewport
    } == true
    private fun showTarget() {
        val s = session ?: return
        val local = s.identity.viewport.toLocal(s.target.x,s.target.y)
        binding.progressText.text = if (s.index < 0) "Practice" else
            "Target ${s.index+1} of ${CalibrationIdentity.FIT_TARGET_COUNT}"
        binding.calibrationView.onNextTargetDraw {
            if (active) s.drawn(MgazeNetCameraSource.now())
        }
        binding.calibrationView.showTarget(local.x,local.y,CalibrationIdentity.TARGET_CUE_MS)
    }
    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            val now = MgazeNetCameraSource.now()
            if (!geometryMatches()) { stop("Screen geometry changed. Nothing was saved."); return }
            if (now-startedAt > 600000 || (session == null && now-startedAt > 20000)) {
                stop("Calibration timed out. Nothing was saved."); return
            }
            val s = session
            if (s != null && verification < 0) {
                if (s.tick(now)) showTarget()
                if (s.phase == MgazeNetCalibrationSession.Phase.FAILED) { stop("Target capture timed out. Nothing was saved."); return }
                if (s.phase == MgazeNetCalibrationSession.Phase.COLLECT) {
                    binding.statusText.text = when {
                        s.showPostureWarning(now) && s.index < 0 -> "Hold the phone and your head steady · recollecting practice"
                        s.showPostureWarning(now) -> "Return to the practice posture"
                        s.fillingTemporalWindow(now) -> "${s.count} / ${MgazeNetCalibrationSession.SAMPLES_PER_TARGET} stable samples · spreading across 3 seconds"
                        s.index < 0 -> "${s.count} / ${MgazeNetCalibrationSession.SAMPLES_PER_TARGET} posture-reference samples"
                        else -> "${s.count} / ${MgazeNetCalibrationSession.SAMPLES_PER_TARGET} stable samples"
                    }
                }
                if (s.phase == MgazeNetCalibrationSession.Phase.FITTING && !fitting) {
                    fitting = true; binding.calibrationView.hideTarget(); binding.progressText.text = "Fitting calibration…"
                    val (features,labels) = s.takeTraining()
                    source!!.fit(features,labels) { ok ->
                        s.fitted(ok)
                        if (!ok) stop("Calibration fit failed. Nothing was saved.")
                        else { verification = 0; showCheck() }
                    }
                }
            } else if (verification in 0..5) {
                when (MgazeNetPostFitTiming.decision(checkStart, now, checkPredictions.size,
                    checkAttempt, comparisonSpec == null)) {
                    MgazeNetPostFitTiming.Decision.WAIT -> Unit
                    MgazeNetPostFitTiming.Decision.EXTEND ->
                        binding.statusText.text = if (now < postFitPostureWarningUntil)
                            "Return to the calibration posture" else
                            "More usable samples needed: ${checkPredictions.size} / ${MgazeNetCalibrationQualityGate.MIN_SAMPLES_PER_CHECK}. Keep looking at this target."
                    MgazeNetPostFitTiming.Decision.COMPLETE -> {
                        recordCheckAttempt(now, "completed")
                        finishCheck()
                        verification++
                        checkAttempt = 1
                        if (verification == 6) finishChecks() else showCheck()
                    }
                    MgazeNetPostFitTiming.Decision.RETRY -> {
                        recordCheckAttempt(now, "insufficient; repeating this location")
                        checkAttempt++
                        showCheck()
                    }
                    MgazeNetPostFitTiming.Decision.FAILED -> {
                        recordCheckAttempt(now, "insufficient; stopped")
                        stop("${checkSpec(verification).label}: only ${checkPredictions.size} usable samples after two local attempts; ${MgazeNetCalibrationQualityGate.MIN_SAMPLES_PER_CHECK} required. Nothing was saved; any previous calibration remains untouched.")
                        return
                    }
                }
            }
            handler.postDelayed(this,50)
        }
    }
    private fun checkTarget() = identity!!.let { id ->
        if (verification == 0) id.targets[3] else {
            val f = listOf(.5f to .5f,.25f to .25f,.75f to .25f,.25f to .75f,.75f to .75f)[verification-1]
            id.viewport.toScreen(f.first*id.viewport.width,f.second*id.viewport.height)
        }
    }
    private fun showCheck() {
        checkPredictions.clear(); checkStart = Double.NaN
        postFitPostureWarningUntil = Double.NEGATIVE_INFINITY
        val p = checkTarget(); val local = identity!!.viewport.toLocal(p.x,p.y)
        binding.progressText.text = if (checkAttempt > 1) "Retry ${checkSpec(verification).label}" else
            if (verification == 0) "Near-centre repeat" else "Validation $verification of 5"
        binding.statusText.text = "Keep looking at the target"
        binding.calibrationView.onNextTargetDraw { if (active) checkStart = MgazeNetCameraSource.now() }
        binding.calibrationView.showTarget(local.x,local.y,1800)
    }
    private fun observeCheck(frame: MgazeNetCameraSource.Frame) {
        if (verification !in 0..5 || !MgazeNetPostFitTiming.accepts(checkStart, frame.captureMs, frame.outputMs,
            comparisonSpec == null) ||
            !frame.eligible) return
        if (session?.postureAccepted(frame.posture) != true) {
            postFitPostureWarningUntil = frame.outputMs + 1_000.0
            binding.statusText.text = "Return to the calibration posture"
            return
        }
        postFitPostureWarningUntil = Double.NEGATIVE_INFINITY
        val p = frame.prediction ?: return
        val x = p[0]*identity!!.screenWidth
        val y = p[1]*identity!!.screenHeight
        if (x.isFinite() && y.isFinite()) {
            checkPredictions.add(x to y)
            binding.statusText.text = "Usable samples: ${checkPredictions.size} / ${MgazeNetCalibrationQualityGate.MIN_SAMPLES_PER_CHECK}. Keep looking at the target."
        }
    }
    private fun recordCheckAttempt(now: Double, outcome: String) {
        val summary = String.format(Locale.US, "%s attempt %d: n=%d, elapsed=%.2f s, %s",
            checkSpec(verification).label, checkAttempt, checkPredictions.size,
            (now - checkStart) / 1000.0, outcome)
        summaries.add(summary)
        Log.i("MGazeNetCalibration", summary)
    }
    private fun finishCheck() {
        val spec = checkSpec(verification)
        if (checkPredictions.isEmpty()) {
            summaries.add("${spec.label}: no usable coordinates")
            return
        }
        val expected = checkTarget()
        val x = median(checkPredictions.map { it.first })
        val y = median(checkPredictions.map { it.second })
        val dxCm = (x-expected.x)/pxPerCmX
        val dyCm = (y-expected.y)/pxPerCmY
        val result = MgazeNetCalibrationQualityGate.Check(
            index = verification+1,
            label = spec.label,
            sampleCount = checkPredictions.size,
            dxCm = dxCm,
            dyCm = dyCm,
            repeatedFitTarget = spec.repeatedFitTarget,
        )
        checkResults.add(result)
        summaries.add(String.format(Locale.US,
            "%s: n=%d, dx=%.3f cm, dy=%.3f cm, 2D=%.3f cm",
            result.label,result.sampleCount,result.dxCm,result.dyCm,result.radialCm))
    }
    private fun finishChecks() {
        source?.pauseCapture()
        binding.root.keepScreenOn = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.calibrationView.hideTarget(); binding.statusText.text = ""
        val report = MgazeNetCalibrationQualityGate.evaluate(checkResults).also { qualityReport = it }
        Log.i("MGazeNetCalibration",String.format(Locale.US,
            "quality_pass=%s median_2d_cm=%.3f worst_2d_cm=%.3f median_vertical_cm=%.3f " +
                "worst_repeat_cm=%.3f posture_rejected=%d practice_restarts=%d candidates=%d selected=%d",
            report.passed,report.median2dCm,report.worst2dCm,report.medianVerticalCm,
            report.worstRepeatCm,session?.postureRejected ?: 0,session?.practiceRestarts ?: 0,
            session?.trainingCandidates ?: 0,CalibrationIdentity.FIT_TARGET_COUNT*CalibrationIdentity.SAMPLES_PER_TARGET))
        if (comparisonSpec != null && report.passed) {
            binding.progressText.text = "Finalizing the slot calibration…"
            binding.gatePanel.visibility = View.GONE
            saveComparisonCalibration()
            return
        }
        if (comparisonSpec != null) {
            stop("Calibration quality check failed. Nothing was saved. ${report.failures.joinToString("; ")}")
            return
        }
        binding.gatePanel.visibility = View.VISIBLE
        binding.gateText.text = qualitySummary(report)
        binding.btnAccept.visibility = if (report.passed) View.VISIBLE else View.GONE
        binding.btnAccept.isEnabled = report.passed
        binding.btnRedoAll.visibility = if (report.passed) View.GONE else View.VISIBLE
        binding.btnRedoAll.text = "Discard and recalibrate"
        binding.btnAccept.text = "Save MGazeNet calibration"
    }
    private fun save() {
        if (!active || verification != 6 || qualityReport?.passed != true || !geometryMatches()) return
        binding.btnAccept.isEnabled = false
        source!!.save(identity!!) { success ->
            stop(if (success) "MGazeNet calibration saved." else "Calibration could not be saved.")
            if (success) finish()
        }
    }

    private fun saveComparisonCalibration() {
        if (qualityReport?.passed != true) {
            stop("Calibration quality check failed. Nothing was saved.")
            return
        }
        val reservation = comparisonReservation ?: run {
            stop("Comparison reservation unavailable.")
            return
        }
        val savedIdentity = identity ?: run {
            stop("Calibration identity unavailable.")
            return
        }
        source?.save(savedIdentity) { success ->
            if (!success) {
                stop("Calibration could not be saved.")
                return@save
            }
            val fingerprint = MgazeNetCalibrationStore(this).fingerprint()
            val recorded = runCatching {
                require(fingerprint != null)
                ComparisonSlotStore(this).completeCalibration(
                    reservation,
                    fingerprint,
                    ComparisonCalibrationEvidence(
                        fitTargetCount = 16,
                        acceptedRowsPerTarget = 45,
                        featureCount = 258,
                        postFitCheckCount = 6,
                        excludedFitTargetCount = 0,
                        detailedTelemetryEnabled = false,
                        correctionApplied = false,
                    ),
                )
            }
            if (recorded.isFailure) {
                stop(recorded.exceptionOrNull()?.message ?: "Calibration receipt could not be written.")
                return@save
            }
            comparisonCalibrationCommitted = true
            active = false
            handler.removeCallbacksAndMessages(null)
            session?.close()
            session = null
            binding.root.keepScreenOn = false
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val closing = source
            source = null
            closing?.close {
                val reading = ComparisonRuntime.putLaunch(
                    Intent(this, ReadingValidationActivity::class.java),
                    reservation.runtime.spec,
                )
                startActivity(reading)
                finish()
            }
        }
    }
    private fun stop(message: String) {
        active = false; handler.removeCallbacksAndMessages(null)
        val reservation = comparisonReservation
        if (reservation != null && !comparisonCalibrationCommitted) {
            runCatching {
                ComparisonSlotStore(this).failCalibration(
                    reservation,
                    if (message.contains("interrupted", ignoreCase = true)) "interrupted" else "failed",
                    message,
                )
            }
            comparisonReservation = null
        }
        source?.close {}; source = null; session?.close()
        binding.calibrationView.hideTarget(); binding.countdownText.visibility = View.GONE
        binding.root.keepScreenOn = false; window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.progressText.text = message
    }
    override fun onStop() { permissionPending = false; if (active) stop("Calibration interrupted. Close and reopen to begin again."); super.onStop() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); source?.close {}; session?.close(); super.onDestroy() }

    private fun checkSpec(index: Int): CheckSpec = when (index) {
        0 -> CheckSpec("30% position repeat",true)
        1 -> CheckSpec("Centre repeat",true)
        2 -> CheckSpec("Upper-left held-out",false)
        3 -> CheckSpec("Upper-right held-out",false)
        4 -> CheckSpec("Lower-left held-out",false)
        5 -> CheckSpec("Lower-right held-out",false)
        else -> error("Invalid post-fit check index")
    }

    private fun qualitySummary(report: MgazeNetCalibrationQualityGate.Report): String {
        fun value(number: Double) = if (number.isFinite()) String.format(Locale.US,"%.3f",number) else "unavailable"
        val heading = if (report.passed) "Calibration quality: PASS" else "Calibration quality: RETRY REQUIRED"
        val metrics = "$heading\n" +
            "Median 2D: ${value(report.median2dCm)} cm (limit ${MgazeNetCalibrationQualityGate.MAX_MEDIAN_2D_CM} cm)\n" +
            "Worst 2D: ${value(report.worst2dCm)} cm (limit ${MgazeNetCalibrationQualityGate.MAX_WORST_2D_CM} cm)\n" +
            "Median |vertical|: ${value(report.medianVerticalCm)} cm (limit ${MgazeNetCalibrationQualityGate.MAX_MEDIAN_VERTICAL_CM} cm)\n" +
            "Worst repeat: ${value(report.worstRepeatCm)} cm (limit ${MgazeNetCalibrationQualityGate.MAX_WORST_REPEAT_CM} cm)\n" +
            "Posture-rejected frames: ${session?.postureRejected ?: 0}; practice restarts: ${session?.practiceRestarts ?: 0}\n" +
            "Stable training candidates: ${session?.trainingCandidates ?: 0}; selected: ${CalibrationIdentity.FIT_TARGET_COUNT*CalibrationIdentity.SAMPLES_PER_TARGET}\n" +
            "Post-fit capture: ${((MgazeNetPostFitTiming.CAPTURE_END_MS - MgazeNetPostFitTiming.SETTLE_MS) / 1000).toInt()} seconds after settling; " +
            if (comparisonSpec == null) "up to ${((MgazeNetPostFitTiming.EXTENDED_CAPTURE_END_MS - MgazeNetPostFitTiming.SETTLE_MS) / 1000).toInt()} seconds and one same-location retry for low sample count" else "fixed comparison timing"
        val decision = if (report.passed) {
            "This guardrail passed. Saving will replace the previous calibration. The separate nine-point check remains the accuracy evaluation."
        } else {
            "This run cannot be saved. The previous calibration remains untouched.\n" +
                report.failures.joinToString("\n") { "• $it" }
        }
        return "$metrics\n\n${summaries.joinToString("\n")}\n\n$decision\nNo correction or smoothing was applied."
    }

    private fun discardAndPrepareRetry() {
        if (qualityReport?.passed != false) return
        active = false
        handler.removeCallbacksAndMessages(null)
        session?.close(); session = null
        binding.gatePanel.visibility = View.GONE
        binding.progressText.text = "Discarding this run…"
        val closing = source
        source = null
        closing?.close {
            if (isFinishing || isDestroyed) return@close
            fitting = false
            verification = -1
            checkStart = Double.NaN
            checkAttempt = 1
            postFitPostureWarningUntil = Double.NEGATIVE_INFINITY
            checkPredictions.clear()
            checkResults.clear()
            summaries.clear()
            qualityReport = null
            identity = null
            binding.startButton.visibility = View.VISIBLE
            binding.startButton.isEnabled = true
            binding.startButton.text = "Start recalibration"
            binding.progressText.text = "Poor run discarded. The previous saved calibration was kept."
            binding.statusText.text = ""
        }
    }

    private fun median(values: List<Float>): Float = values.sorted().let {
        (it[(it.size-1)/2]+it[it.size/2])/2f
    }

    private data class CheckSpec(val label: String, val repeatedFitTarget: Boolean)
}
