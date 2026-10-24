package com.newsmead.mgazenetbenchmark

import java.security.MessageDigest
import kotlin.math.sqrt

/** Isolated MGazeNet calibration observability using NewsMead's 16-point structure. */
class CalibrationAuditSession(val layout: AccuracySession.Layout, val runLabel: String) {
    enum class Phase { CALIBRATION, FITTING, VERIFICATION, COMPLETE, STOPPED, FAILED }
    data class FitPoint(val id: String, val point: AccuracySession.Point, val practice: Boolean,
                        var shownMs: Double? = null, var accepted: Int = 0,
                        var firstAcceptedCaptureMs: Double? = null, var completedOutputMs: Double? = null,
                        var featureDispersionRms: Double? = null,
                        val acceptedOutputAgesMs: MutableList<Double> = mutableListOf(),
                        val rejected: MutableMap<String,Int> = linkedMapOf())
    data class Sample(val captureMs: Double, val outputMs: Double,
                      val point: AccuracySession.Point?, val reason: String)
    data class VerificationBlock(val id: String, val role: String, val point: AccuracySession.Point,
                                 var shownMs: Double? = null, var startMs: Double? = null,
                                 var endMs: Double? = null,
                                 val samples: MutableList<Sample> = mutableListOf())

    private val grid = activeGrid(layout)
    val fitPoints = listOf(FitPoint("practice",layout.fromFraction(.5,.5),true)) +
        grid.mapIndexed { index, point -> FitPoint("fit_${index+1}",point,false) }
    // Four grid points are mathematically tied around centre. Freeze the row-major
    // first candidate so floating-point/layout differences cannot change the protocol.
    val driftFitId = DRIFT_FIT_ID
    val verificationBlocks = buildList {
        add(VerificationBlock("drift_repeat_$driftFitId","drift_repeat",
            fitPoints.first { it.id == driftFitId }.point))
        VALIDATION_FRACTIONS.forEachIndexed { index, pair ->
            add(VerificationBlock("validation_${index+1}","held_out_validation",
                layout.fromFraction(pair.first,pair.second)))
        }
    }

    var phase = Phase.CALIBRATION; private set
    var failure: String? = null; private set
    var target: AccuracySession.Target? = targetForFit(0); private set
    var fitDigest: String? = null; private set
    var trainingRows = 0; private set
    var auditResult: CalibrationAuditEngine.Result? = null; private set
    var startedMs: Double? = null; private set
    var finishedMs: Double? = null; private set
    var discardedFrames = 0; private set
    private val rows = linkedMapOf<String,MutableList<CalibrationAuditEngine.Row>>()
    private var fitIndex = 0
    private var verificationIndex = 0
    private var token = 0
    private var fullAt: Double? = null
    private var previousCapture = -1.0
    private var previousOutput = -1.0

    init {
        require(runLabel.isNotBlank() && runLabel.length <= 120)
        require(grid.size == 16 && grid.distinct().size == 16)
        require(fitPoints.filterNot { it.practice }.map { it.id } == (1..16).map { "fit_$it" })
        require(fitPoints.first { it.id == driftFitId }.point == grid[5])
        require(verificationBlocks.map { it.point }.distinct().size == verificationBlocks.size)
        require(verificationBlocks.drop(1).none { block -> grid.any { it == block.point } })
        fitPoints.filterNot { it.practice }.forEach { rows[it.id] = mutableListOf() }
    }

    private fun targetForFit(index: Int) = fitPoints[index].let {
        AccuracySession.Target(token,it.id,it.point,it.practice)
    }

    fun presented(targetToken: Int, now: Double) {
        require(now.isFinite() && now >= 0)
        if (target?.token != targetToken || terminal()) return
        if (startedMs == null) startedMs = now
        when (phase) {
            Phase.CALIBRATION -> if (fitPoints[fitIndex].shownMs == null) fitPoints[fitIndex].shownMs = now
            Phase.VERIFICATION -> verificationBlocks[verificationIndex].let { block ->
                if (block.shownMs == null) {
                    block.shownMs = now
                    block.startMs = now + VERIFY_SETTLE_MS
                    block.endMs = now + VERIFY_SETTLE_MS + VERIFY_MEASURE_MS
                }
            }
            else -> Unit
        }
    }

    fun frame(frame: AccuracySession.Frame) {
        if (terminal() && phase != Phase.COMPLETE) return
        if (!frame.captureMs.isFinite() || !frame.outputMs.isFinite() || frame.captureMs < 0 ||
            frame.outputMs < frame.captureMs || frame.captureMs <= previousCapture ||
            frame.outputMs <= previousOutput) {
            fail("invalid_or_nonmonotonic_frame_clock",frame.outputMs.takeIf { it.isFinite() } ?: 0.0)
            return
        }
        previousCapture = frame.captureMs; previousOutput = frame.outputMs
        val block = verificationBlocks.firstOrNull { it.startMs != null &&
            frame.captureMs >= it.startMs!! && frame.captureMs < it.endMs!! }
        if (block != null) {
            val valid = eligible(frame) && frame.prediction?.let { it.size == 2 && it.all(Float::isFinite) } == true
            block.samples.add(Sample(frame.captureMs,frame.outputMs,
                if (valid) layout.pixels(frame.prediction!!) else null,
                if (valid) "coordinate" else rejection(frame)))
            return
        }
        if (phase != Phase.CALIBRATION) { discardedFrames++; return }
        val point = fitPoints[fitIndex]
        val shown = point.shownMs
        if (shown != null && frame.captureMs >= shown + FIT_TIMEOUT_MS && fullAt == null) {
            fail("calibration_target_timeout",frame.outputMs); return
        }
        if (shown == null || frame.captureMs < shown + FIT_SETTLE_MS || fullAt != null) {
            discardedFrames++; return
        }
        if (!eligible(frame)) {
            val key = rejection(frame); point.rejected[key] = (point.rejected[key] ?: 0) + 1; return
        }
        point.accepted++
        if (point.firstAcceptedCaptureMs == null) point.firstAcceptedCaptureMs = frame.captureMs
        point.acceptedOutputAgesMs.add(frame.outputMs-frame.captureMs)
        if (!point.practice) rows.getValue(point.id).add(CalibrationAuditEngine.Row(
            frame.features!!.copyOf(),layout.label(point.point),frame.captureMs,frame.outputMs))
        if (point.accepted == FIT_SAMPLES) {
            fullAt = frame.outputMs; point.completedOutputMs = frame.outputMs
        }
    }

    fun tick(now: Double) {
        require(now.isFinite() && now >= 0)
        if (terminal()) return
        when (phase) {
            Phase.CALIBRATION -> {
                val shown = fitPoints[fitIndex].shownMs ?: return
                if (fullAt != null && now >= fullAt!! + FIT_WAIT_MS) {
                    fullAt = null; fitIndex++
                    if (fitIndex == fitPoints.size) { phase = Phase.FITTING; target = null }
                    else { token++; target = targetForFit(fitIndex) }
                } else if (now >= shown + FIT_TIMEOUT_MS) fail("calibration_target_timeout",now)
            }
            Phase.VERIFICATION -> {
                val end = verificationBlocks[verificationIndex].endMs ?: return
                if (now >= end + VERIFY_DRAIN_MS) {
                    verificationIndex++
                    if (verificationIndex == verificationBlocks.size) {
                        phase = Phase.COMPLETE; target = null; finishedMs = now
                    } else nextVerification()
                }
            }
            else -> Unit
        }
    }

    fun training(): CalibrationAuditEngine.Training {
        check(phase == Phase.FITTING && fitDigest == null)
        check(rows.values.all { it.size == FIT_SAMPLES })
        trainingRows = rows.values.sumOf { it.size }
        rows.forEach { (id, targetRows) ->
            fitPoints.first { it.id == id }.featureDispersionRms = dispersion(targetRows)
        }
        val digest = MessageDigest.getInstance("SHA-256")
        rows.values.flatten().forEach { row -> (row.features + row.normalizedLabel).forEach { value ->
            val bits = value.toRawBits(); repeat(4) { digest.update((bits ushr (it*8)).toByte()) }
        } }
        fitDigest = digest.digest().joinToString("") { "%02x".format(it) }
        return CalibrationAuditEngine.Training(rows.map { (id, values) ->
            CalibrationAuditEngine.Group(id,values.map { row -> row.copy(
                features=row.features.copyOf(),normalizedLabel=row.normalizedLabel.copyOf()) })
        })
    }

    fun fitted(result: CalibrationAuditEngine.Result?, finalFitSuccess: Boolean, now: Double) {
        if (phase != Phase.FITTING) return
        clearRows()
        val expectedIds = (1..FIT_TARGETS).map { "fit_$it" }
        val validAudit = result?.let { audit ->
            audit.method == CalibrationAuditEngine.METHOD &&
                audit.folds.map { it.heldOutId } == expectedIds &&
                audit.folds.zip(fitPoints.drop(1)).all { (fold,fitPoint) ->
                    fold.normalizedTarget.contentEquals(layout.label(fitPoint.point)) &&
                        fold.predictions.size == FIT_SAMPLES &&
                        fold.predictions.all { prediction ->
                            prediction.captureMs.isFinite() && prediction.sourceOutputMs.isFinite() &&
                                prediction.sourceOutputMs >= prediction.captureMs &&
                                prediction.normalizedPoint.size == 2 && prediction.normalizedPoint.all(Float::isFinite)
                        }
                }
        } == true
        if (!finalFitSuccess || !validAudit || fitDigest == null) {
            fail("calibration_audit_or_fit_failed",now); return
        }
        auditResult = result
        phase = Phase.VERIFICATION
        nextVerification()
    }

    private fun nextVerification() {
        token++
        val block = verificationBlocks[verificationIndex]
        target = AccuracySession.Target(token,block.id,block.point,false,verificationIndex)
    }

    fun stop(reason: String, now: Double) {
        if (terminal()) return
        failure = reason; phase = Phase.STOPPED; finish(now)
    }
    fun fail(reason: String, now: Double) {
        if (terminal()) return
        failure = reason; phase = Phase.FAILED; finish(now)
    }
    private fun finish(now: Double) {
        finishedMs = now; target = null; clearRows()
    }
    private fun clearRows() {
        rows.values.flatten().forEach { row -> row.features.fill(0f); row.normalizedLabel.fill(0f) }
        rows.values.forEach { it.clear() }
    }
    private fun dispersion(targetRows: List<CalibrationAuditEngine.Row>): Double {
        require(targetRows.size > 1)
        val means = DoubleArray(FEATURE_COUNT)
        targetRows.forEach { row -> row.features.forEachIndexed { index, value ->
            means[index] = means[index]+value.toDouble()
        } }
        means.indices.forEach { means[it] = means[it]/targetRows.size }
        var squared = 0.0
        targetRows.forEach { row -> row.features.forEachIndexed { index, value ->
            val delta = value-means[index]; squared += delta*delta
        } }
        return sqrt(squared/((targetRows.size-1)*FEATURE_COUNT))
    }
    fun terminal() = phase in listOf(Phase.COMPLETE,Phase.STOPPED,Phase.FAILED)

    private fun eligible(frame: AccuracySession.Frame) =
        frame.features?.let { it.size == FEATURE_COUNT && it.all(Float::isFinite) } == true &&
            frame.leftArea.isFinite() && frame.rightArea.isFinite() &&
            frame.leftArea > EYE_AREA_MIN && frame.rightArea > EYE_AREA_MIN
    private fun rejection(frame: AccuracySession.Frame): String = when {
        frame.features == null -> frame.reason
        frame.features.size != FEATURE_COUNT || !frame.features.all(Float::isFinite) -> "invalid_features"
        !frame.leftArea.isFinite() || !frame.rightArea.isFinite() ||
            frame.leftArea <= EYE_AREA_MIN || frame.rightArea <= EYE_AREA_MIN -> "eye_area_rejected"
        else -> "prediction_unavailable"
    }

    companion object {
        const val VERSION = "mgazenet_calibration_observability_v3"
        const val FIT_SETTLE_MS = 1500.0
        const val FIT_WAIT_MS = 500.0
        const val FIT_SAMPLES = 45
        const val FIT_TARGETS = 16
        const val FIT_TIMEOUT_MS = 30_000.0
        const val VERIFY_SETTLE_MS = 3000.0
        const val VERIFY_MEASURE_MS = 2500.0
        const val VERIFY_DRAIN_MS = 250.0
        const val FEATURE_COUNT = 258
        const val EYE_AREA_MIN = 10.0
        const val DRIFT_FIT_ID = "fit_6"
        val GRID_FRACTIONS = listOf(.1,.3667,.6333,.9)
        val VALIDATION_FRACTIONS = listOf(.5 to .5,.25 to .25,.75 to .25,.25 to .75,.75 to .75)

        fun activeGrid(layout: AccuracySession.Layout) = GRID_FRACTIONS.flatMap { y ->
            GRID_FRACTIONS.map { x -> layout.fromFraction(x,y) }
        }
    }
}
