package com.newsmead.mgazenetbenchmark

import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

/** Isolated direct-screen confirmation. No active-tracker or calibration-store access. */
class ConfirmationSession(val layout: AccuracySession.Layout, val runLabel: String,
                          val validationOrder: AccuracySession.ValidationOrder) {
    enum class Phase { CALIBRATION, FITTING, SCREEN, CONFIRMATION, COMPLETE, STOPPED, FAILED }
    data class FitPoint(val id: String, val point: AccuracySession.Point, val practice: Boolean,
                        var shownMs: Double? = null, var accepted: Int = 0,
                        var firstAcceptedCaptureMs: Double? = null, var completedOutputMs: Double? = null,
                        var featureDispersionRms: Double? = null,
                        val acceptedOutputAgesMs: MutableList<Double> = mutableListOf(),
                        val rejected: MutableMap<String,Int> = linkedMapOf())
    data class Sample(val captureMs: Double, val outputMs: Double,
                      val point: AccuracySession.Point?, val reason: String)
    data class ScreenBlock(val id: String, val point: AccuracySession.Point,
                           var shownMs: Double? = null, var startMs: Double? = null,
                           var endMs: Double? = null,
                           val samples: MutableList<Sample> = mutableListOf())
    data class ConfirmationBlock(val id: String, val locationId: String, val gridIndex: Int,
                                 val sweep: Int, val sweepDirection: String, val orderInSweep: Int,
                                 val region: String, val point: AccuracySession.Point, val line: Int,
                                 var shownMs: Double? = null, var startMs: Double? = null,
                                 var endMs: Double? = null,
                                 val samples: MutableList<Sample> = mutableListOf())
    data class ScreenTargetResult(val id: String, val coordinateSamples: Int,
                                  val medianAbsoluteVerticalLines: Double?,
                                  val p95AbsoluteVerticalLines: Double?,
                                  val maxAbsoluteVerticalLines: Double?)
    data class ScreenResult(val method: String = SCREEN_METHOD, val sealedMs: Double,
                            val targets: List<ScreenTargetResult>,
                            val minimumCoordinateSamples: Int,
                            val meanTargetMedianAbsoluteVerticalLines: Double?,
                            val meanTargetP95AbsoluteVerticalLines: Double?,
                            val worstTargetMedianAbsoluteVerticalLines: Double?,
                            val allFiveTargetsContribute: Boolean,
                            val minimumCoordinatesPass: Boolean,
                            val aggregateVerticalPass: Boolean,
                            val regionalVerticalPass: Boolean,
                            val candidatePass: Boolean)

    private val grid = CalibrationAuditSession.activeGrid(layout)
    val fitPoints = listOf(FitPoint("practice",layout.fromFraction(.5,.5),true)) +
        grid.mapIndexed { index, point -> FitPoint("fit_${index+1}",point,false) }
    val screenBlocks = CalibrationAuditSession.VALIDATION_FRACTIONS.mapIndexed { index, pair ->
        ScreenBlock("screen_validation_${index+1}",layout.fromFraction(pair.first,pair.second))
    }
    val confirmationBlocks = validationOrder.directions.flatMapIndexed { sweepIndex, direction ->
        val sequence = if (direction == AccuracySession.SweepDirection.FORWARD)
            AccuracySession.TEST_LOCATIONS else AccuracySession.TEST_LOCATIONS.reversed()
        sequence.mapIndexed { orderIndex, gridIndex ->
            val original = stationaryGridPoint(gridIndex)
            val line = layout.lines.indices.minByOrNull {
                abs((layout.lines[it].top+layout.lines[it].bottom)/2-original.y)
            }!!
            val band = layout.lines[line]
            val x = original.x.coerceIn(band.left+(band.right-band.left)*.02,
                band.right-(band.right-band.left)*.02)
            ConfirmationBlock("confirmation_sweep_${sweepIndex+1}_test_$gridIndex",
                "test_$gridIndex",gridIndex,sweepIndex+1,direction.code,orderIndex+1,
                "grid_$gridIndex",AccuracySession.Point(x,(band.top+band.bottom)/2),line)
        }
    }

    var phase = Phase.CALIBRATION; private set
    var failure: String? = null; private set
    var target: AccuracySession.Target? = targetForFit(0); private set
    var fitDigest: String? = null; private set
    var trainingRows = 0; private set
    var auditResult: CalibrationAuditEngine.Result? = null; private set
    var screenResult: ScreenResult? = null; private set
    var startedMs: Double? = null; private set
    var finishedMs: Double? = null; private set
    var discardedFrames = 0; private set
    private val rows = linkedMapOf<String,MutableList<CalibrationAuditEngine.Row>>()
    private var fitIndex = 0
    private var screenIndex = 0
    private var confirmationIndex = 0
    private var token = 0
    private var fullAt: Double? = null
    private var previousCapture = -1.0
    private var previousOutput = -1.0

    init {
        require(runLabel.isNotBlank() && runLabel.length <= 120)
        require(grid.size == 16 && grid.distinct().size == 16)
        require(fitPoints.filterNot { it.practice }.map { it.id } == (1..16).map { "fit_$it" })
        require(screenBlocks.map { it.point }.distinct().size == 5)
        require(screenBlocks.none { screen -> grid.any { it == screen.point } })
        require(confirmationBlocks.map { it.id }.distinct().size == 20)
        require(confirmationBlocks.groupBy { it.locationId }.let { groups ->
            groups.size == 10 && groups.values.all { repeated ->
                repeated.size == 2 && repeated.map { it.point }.distinct().size == 1
            }
        })
        val locations = confirmationBlocks.distinctBy { it.locationId }
        require(locations.map { it.point }.distinct().size == 10)
        require(locations.none { block -> grid.any { it == block.point } })
        require(locations.none { block -> screenBlocks.any { it.point == block.point } })
        fitPoints.filterNot { it.practice }.forEach { rows[it.id] = mutableListOf() }
    }

    private fun stationaryGridPoint(index: Int): AccuracySession.Point {
        val col = (index-1)%9
        val row = (index-1)/9
        return layout.fromFraction(50.0/1920+col*(1820.0/1920)/8,
            50.0/1080+row*(980.0/1080)/4)
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
            Phase.SCREEN -> screenBlocks[screenIndex].let { block ->
                if (block.shownMs == null) {
                    block.shownMs = now
                    block.startMs = now+SETTLE_MS
                    block.endMs = now+SETTLE_MS+MEASURE_MS
                }
            }
            Phase.CONFIRMATION -> confirmationBlocks[confirmationIndex].let { block ->
                if (block.shownMs == null) {
                    block.shownMs = now
                    block.startMs = now+SETTLE_MS
                    block.endMs = now+SETTLE_MS+MEASURE_MS
                }
            }
            else -> Unit
        }
    }

    fun frame(frame: AccuracySession.Frame) {
        if (terminal()) return
        if (!frame.captureMs.isFinite() || !frame.outputMs.isFinite() || frame.captureMs < 0 ||
            frame.outputMs < frame.captureMs || frame.captureMs <= previousCapture ||
            frame.outputMs <= previousOutput) {
            fail("invalid_or_nonmonotonic_frame_clock",frame.outputMs.takeIf { it.isFinite() } ?: 0.0)
            return
        }
        previousCapture = frame.captureMs
        previousOutput = frame.outputMs

        val sampleTarget: Pair<MutableList<Sample>,String>? = when (phase) {
            Phase.SCREEN -> screenBlocks.firstOrNull { it.startMs != null &&
                frame.captureMs >= it.startMs!! && frame.captureMs < it.endMs!! }
                ?.let { it.samples to it.id }
            Phase.CONFIRMATION -> confirmationBlocks.firstOrNull { it.startMs != null &&
                frame.captureMs >= it.startMs!! && frame.captureMs < it.endMs!! }
                ?.let { it.samples to it.id }
            else -> null
        }
        if (sampleTarget != null) {
            val valid = eligible(frame) &&
                frame.prediction?.let { it.size == 2 && it.all(Float::isFinite) } == true
            sampleTarget.first.add(Sample(frame.captureMs,frame.outputMs,
                if (valid) layout.pixels(frame.prediction!!) else null,
                if (valid) "coordinate" else rejection(frame)))
            return
        }
        if (phase != Phase.CALIBRATION) {
            discardedFrames++
            return
        }
        val point = fitPoints[fitIndex]
        val shown = point.shownMs
        if (shown != null && frame.captureMs >= shown+FIT_TIMEOUT_MS && fullAt == null) {
            fail("calibration_target_timeout",frame.outputMs)
            return
        }
        if (shown == null || frame.captureMs < shown+FIT_SETTLE_MS || fullAt != null) {
            discardedFrames++
            return
        }
        if (!eligible(frame)) {
            val key = rejection(frame)
            point.rejected[key] = (point.rejected[key] ?: 0)+1
            return
        }
        point.accepted++
        if (point.firstAcceptedCaptureMs == null) point.firstAcceptedCaptureMs = frame.captureMs
        point.acceptedOutputAgesMs.add(frame.outputMs-frame.captureMs)
        if (!point.practice) rows.getValue(point.id).add(CalibrationAuditEngine.Row(
            frame.features!!.copyOf(),layout.label(point.point),frame.captureMs,frame.outputMs))
        if (point.accepted == FIT_SAMPLES) {
            fullAt = frame.outputMs
            point.completedOutputMs = frame.outputMs
        }
    }

    fun tick(now: Double) {
        require(now.isFinite() && now >= 0)
        if (terminal()) return
        when (phase) {
            Phase.CALIBRATION -> {
                val shown = fitPoints[fitIndex].shownMs ?: return
                if (fullAt != null && now >= fullAt!!+FIT_WAIT_MS) {
                    fullAt = null
                    fitIndex++
                    if (fitIndex == fitPoints.size) {
                        phase = Phase.FITTING
                        target = null
                    } else {
                        token++
                        target = targetForFit(fitIndex)
                    }
                } else if (now >= shown+FIT_TIMEOUT_MS) fail("calibration_target_timeout",now)
            }
            Phase.SCREEN -> {
                val end = screenBlocks[screenIndex].endMs ?: return
                if (now >= end+DRAIN_MS) {
                    screenIndex++
                    if (screenIndex == screenBlocks.size) {
                        sealScreen(now)
                        phase = Phase.CONFIRMATION
                        nextConfirmation()
                    } else nextScreen()
                }
            }
            Phase.CONFIRMATION -> {
                val end = confirmationBlocks[confirmationIndex].endMs ?: return
                if (now >= end+DRAIN_MS) {
                    confirmationIndex++
                    if (confirmationIndex == confirmationBlocks.size) {
                        phase = Phase.COMPLETE
                        target = null
                        finishedMs = now
                    } else nextConfirmation()
                }
            }
            else -> Unit
        }
    }

    fun training(): CalibrationAuditEngine.Training {
        check(phase == Phase.FITTING && fitDigest == null)
        check(rows.values.all { it.size == FIT_SAMPLES })
        trainingRows = rows.values.sumOf { it.size }
        rows.forEach { (id,targetRows) ->
            fitPoints.first { it.id == id }.featureDispersionRms = dispersion(targetRows)
        }
        val digest = MessageDigest.getInstance("SHA-256")
        rows.values.flatten().forEach { row ->
            (row.features+row.normalizedLabel).forEach { value ->
                val bits = value.toRawBits()
                repeat(4) { digest.update((bits ushr (it*8)).toByte()) }
            }
        }
        fitDigest = digest.digest().joinToString("") { "%02x".format(it) }
        return CalibrationAuditEngine.Training(rows.map { (id,values) ->
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
                                prediction.normalizedPoint.size == 2 &&
                                prediction.normalizedPoint.all(Float::isFinite)
                        }
                }
        } == true
        if (!finalFitSuccess || !validAudit || fitDigest == null) {
            fail("calibration_audit_or_fit_failed",now)
            return
        }
        auditResult = result
        phase = Phase.SCREEN
        nextScreen()
    }

    private fun nextScreen() {
        token++
        val block = screenBlocks[screenIndex]
        target = AccuracySession.Target(token,block.id,block.point,false,screenIndex)
    }

    private fun nextConfirmation() {
        token++
        val block = confirmationBlocks[confirmationIndex]
        target = AccuracySession.Target(token,block.id,block.point,false,
            screenBlocks.size+confirmationIndex)
    }

    private fun sealScreen(now: Double) {
        check(screenResult == null && screenIndex == screenBlocks.size)
        val targets = screenBlocks.map { block ->
            val values = block.samples.mapNotNull { sample -> sample.point?.let {
                abs(it.y-block.point.y)/layout.lineHeight
            } }.sorted()
            ScreenTargetResult(block.id,values.size,percentile(values,.5),percentile(values,.95),
                values.lastOrNull())
        }
        val contributing = targets.filter { it.medianAbsoluteVerticalLines != null }
        val means = contributing.map { it.medianAbsoluteVerticalLines!! }
        val p95s = contributing.map { it.p95AbsoluteVerticalLines!! }
        val minimum = targets.minOf { it.coordinateSamples }
        val allContribute = contributing.size == screenBlocks.size
        val meanMedian = means.takeIf { it.isNotEmpty() }?.average()
        val meanP95 = p95s.takeIf { it.isNotEmpty() }?.average()
        val worst = means.maxOrNull()
        val samplePass = minimum >= MIN_COORDINATES_PER_TARGET
        val aggregatePass = meanMedian != null && meanMedian <= MAX_MEAN_TARGET_MEDIAN_LINES
        val regionalPass = allContribute && worst != null && worst <= MAX_WORST_TARGET_MEDIAN_LINES
        screenResult = ScreenResult(sealedMs=now,targets=targets,
            minimumCoordinateSamples=minimum,
            meanTargetMedianAbsoluteVerticalLines=meanMedian,
            meanTargetP95AbsoluteVerticalLines=meanP95,
            worstTargetMedianAbsoluteVerticalLines=worst,
            allFiveTargetsContribute=allContribute,minimumCoordinatesPass=samplePass,
            aggregateVerticalPass=aggregatePass,regionalVerticalPass=regionalPass,
            candidatePass=allContribute && samplePass && aggregatePass && regionalPass)
    }

    private fun percentile(values: List<Double>, fraction: Double): Double? {
        if (values.isEmpty()) return null
        val position = (values.size-1)*fraction
        val lower = position.toInt()
        val upper = ceil(position).toInt()
        return if (lower == upper) values[lower]
        else values[lower]+(values[upper]-values[lower])*(position-lower)
    }

    fun stop(reason: String, now: Double) {
        if (terminal()) return
        failure = reason
        phase = Phase.STOPPED
        finish(now)
    }

    fun fail(reason: String, now: Double) {
        if (terminal()) return
        failure = reason
        phase = Phase.FAILED
        finish(now)
    }

    private fun finish(now: Double) {
        finishedMs = now
        target = null
        clearRows()
    }

    private fun clearRows() {
        rows.values.flatten().forEach { row ->
            row.features.fill(0f)
            row.normalizedLabel.fill(0f)
        }
        rows.values.forEach { it.clear() }
    }

    private fun dispersion(targetRows: List<CalibrationAuditEngine.Row>): Double {
        require(targetRows.size > 1)
        val means = DoubleArray(FEATURE_COUNT)
        targetRows.forEach { row -> row.features.forEachIndexed { index,value ->
            means[index] = means[index]+value.toDouble()
        } }
        means.indices.forEach { means[it] = means[it]/targetRows.size }
        var squared = 0.0
        targetRows.forEach { row -> row.features.forEachIndexed { index,value ->
            val delta = value-means[index]
            squared += delta*delta
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
        const val VERSION = "mgazenet_direct_validation_confirmation_v1"
        const val SCREEN_METHOD = "direct_five_point_vertical_candidate_v1"
        const val FIT_SETTLE_MS = 1500.0
        const val FIT_WAIT_MS = 500.0
        const val FIT_SAMPLES = 45
        const val FIT_TARGETS = 16
        const val FIT_TIMEOUT_MS = 30_000.0
        const val SETTLE_MS = 3000.0
        const val MEASURE_MS = 2500.0
        const val DRAIN_MS = 250.0
        const val FEATURE_COUNT = 258
        const val EYE_AREA_MIN = 10.0
        const val MIN_COORDINATES_PER_TARGET = 10
        const val MAX_MEAN_TARGET_MEDIAN_LINES = 1.0
        const val MAX_WORST_TARGET_MEDIAN_LINES = 1.2
    }
}
