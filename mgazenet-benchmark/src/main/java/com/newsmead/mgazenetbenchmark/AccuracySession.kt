package com.newsmead.mgazenetbenchmark

import java.security.MessageDigest
import kotlin.math.abs

/** Pure, single-owner session state. Source-family calibration; independent test windows.
 * GazeFollower target indices/timing adapted under CC BY-NC-SA 4.0; see NOTICE.md.
 * Coordinates refer to the physical display, never an implicit view origin.
 */
class AccuracySession(val layout: Layout, val runLabel: String, val validationOrder: ValidationOrder) {
    data class Point(val x: Double, val y: Double)
    data class Rect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
        fun contains(p: Point) = p.x >= left && p.x < right && p.y >= top && p.y < bottom
        fun values() = listOf(left, top, right, bottom)
    }
    data class Layout(val screenWidth: Int, val screenHeight: Int, val viewport: Rect,
                      val lineHeight: Double, val lines: List<Rect>) {
        init {
            require(screenWidth > 0 && screenHeight > 0 && lineHeight.isFinite() && lineHeight > 0)
            require(viewport.values().all(Double::isFinite) && viewport.left >= 0 && viewport.top >= 0 &&
                viewport.right <= screenWidth && viewport.bottom <= screenHeight &&
                viewport.right > viewport.left && viewport.bottom > viewport.top)
            require(lines.size >= 5)
            lines.forEachIndexed { i, r ->
                require(r.values().all(Double::isFinite) && r.left >= viewport.left && r.right <= viewport.right &&
                    r.top >= viewport.top && r.bottom <= viewport.bottom && r.right > r.left && r.bottom > r.top)
                require(i == 0 || lines[i-1].bottom <= r.top)
            }
        }
        fun fromFraction(x: Double, y: Double) = Point(viewport.left + x*(viewport.right-viewport.left),
            viewport.top + y*(viewport.bottom-viewport.top))
        fun label(p: Point) = floatArrayOf((p.x/screenWidth).toFloat(), (p.y/screenHeight).toFloat())
        fun pixels(p: FloatArray) = Point(p[0].toDouble()*screenWidth, p[1].toDouble()*screenHeight)
    }
    enum class Phase { CALIBRATION, FITTING, VALIDATION, COMPLETE, STOPPED, FAILED }
    enum class SweepDirection(val code: String) { FORWARD("forward"), REVERSE("reverse") }
    enum class ValidationOrder(val code: String, val directions: List<SweepDirection>) {
        FORWARD_THEN_REVERSE("forward_then_reverse", listOf(SweepDirection.FORWARD, SweepDirection.REVERSE)),
        REVERSE_THEN_FORWARD("reverse_then_forward", listOf(SweepDirection.REVERSE, SweepDirection.FORWARD))
    }
    data class Target(val token: Int, val id: String, val point: Point, val practice: Boolean,
                      val testIndex: Int? = null)
    data class Frame(val captureMs: Double, val outputMs: Double, val features: FloatArray?,
                     val leftArea: Double, val rightArea: Double, val prediction: FloatArray?, val reason: String,
                     val cropSizes: List<List<Int>>? = null)
    data class Sample(val captureMs: Double, val outputMs: Double, val point: Point?, val reason: String)
    data class TestBlock(val id: String, val locationId: String, val gridIndex: Int,
                         val sweep: Int, val sweepDirection: String, val orderInSweep: Int,
                         val region: String, val point: Point, val line: Int,
                         var shownMs: Double? = null, var startMs: Double? = null, var endMs: Double? = null,
                         val samples: MutableList<Sample> = mutableListOf())
    data class FitPoint(val id: String, val point: Point, val practice: Boolean,
                        var shownMs: Double? = null, var accepted: Int = 0,
                        val rejected: MutableMap<String, Int> = linkedMapOf())
    data class Training(val features: Array<FloatArray>, val labels: Array<FloatArray>)

    val fitPoints = (listOf(23) + FIT_INDICES).mapIndexed { index, grid ->
        FitPoint(if (index == 0) "practice" else "fit_$grid", gridPoint(grid), index == 0)
    }
    val blocks = validationOrder.directions.flatMapIndexed { sweepIndex, direction ->
        val sequence = if (direction == SweepDirection.FORWARD) TEST_LOCATIONS else TEST_LOCATIONS.reversed()
        sequence.mapIndexed { orderIndex, grid ->
            val p = gridPoint(grid)
            val line = layout.lines.indices.minByOrNull { abs((layout.lines[it].top+layout.lines[it].bottom)/2-p.y) }!!
            val r = layout.lines[line]
            val x = p.x.coerceIn(r.left + (r.right-r.left)*.02, r.right - (r.right-r.left)*.02)
            TestBlock("sweep_${sweepIndex+1}_test_$grid", "test_$grid", grid, sweepIndex+1,
                direction.code, orderIndex+1, "grid_$grid", Point(x,(r.top+r.bottom)/2),line)
        }
    }
    var phase = Phase.CALIBRATION; private set
    var failure: String? = null; private set
    var target: Target? = targetForFit(0); private set
    var fitDigest: String? = null; private set
    var finishedMs: Double? = null; private set
    var startedMs: Double? = null; private set
    private var fitIndex = 0
    private var testIndex = 0
    private var token = 0
    private var fullAt: Double? = null
    private var previousCapture = -1.0
    private var previousOutput = -1.0
    private val rows = ArrayList<FloatArray>()
    private val labels = ArrayList<FloatArray>()
    var trainingRows = 0; private set
    var discardedFrames = 0; private set

    init {
        require(runLabel.isNotBlank() && runLabel.length <= 120)
        require(blocks.map { it.id }.distinct().size == blocks.size)
        require(blocks.groupBy { it.locationId }.let { groups ->
            groups.size == TEST_LOCATIONS.size && groups.values.all { repeated ->
                repeated.size == 2 && repeated.map { it.point }.distinct().size == 1
            }
        })
        require(blocks.distinctBy { it.locationId }.map { it.point }.distinct().size == TEST_LOCATIONS.size)
        require(blocks.none { b -> fitPoints.drop(1).any { it.point == b.point } })
    }
    private fun gridPoint(index: Int): Point {
        // Exact source grid fractions, adapted to the measured drawable viewport.
        val col = (index-1)%9; val row = (index-1)/9
        return layout.fromFraction(50.0/1920 + col*(1820.0/1920)/8,
            50.0/1080 + row*(980.0/1080)/4)
    }
    private fun targetForFit(index: Int) = fitPoints[index].let { Target(index,it.id,it.point,it.practice) }
    fun presented(targetToken: Int, now: Double) {
        require(now.isFinite() && now >= 0)
        if (target?.token != targetToken || terminal()) return
        if (startedMs == null) startedMs = now
        when (phase) {
            Phase.CALIBRATION -> if (fitPoints[fitIndex].shownMs == null) fitPoints[fitIndex].shownMs = now
            Phase.VALIDATION -> blocks[testIndex].let {
                if (it.shownMs == null) {
                    it.shownMs = now; it.startMs = now + TEST_SETTLE_MS; it.endMs = now + TEST_SETTLE_MS + TEST_MEASURE_MS
                }
            }
            else -> Unit
        }
    }
    fun frame(frame: Frame) {
        if (terminal() && phase != Phase.COMPLETE) return
        if (!frame.captureMs.isFinite() || !frame.outputMs.isFinite() || frame.captureMs < 0 ||
            frame.outputMs < frame.captureMs || frame.captureMs <= previousCapture || frame.outputMs <= previousOutput) {
            fail("invalid_or_nonmonotonic_frame_clock", frame.outputMs.takeIf { it.isFinite() } ?: 0.0); return
        }
        previousCapture = frame.captureMs; previousOutput = frame.outputMs
        // Route by capture window, including a delayed result for the preceding test.
        val block = blocks.firstOrNull { it.startMs != null && frame.captureMs >= it.startMs!! && frame.captureMs < it.endMs!! }
        if (block != null) {
            val valid = eligible(frame) && frame.prediction?.let { it.size == 2 && it.all(Float::isFinite) } == true
            block.samples.add(Sample(frame.captureMs,frame.outputMs,
                if (valid) layout.pixels(frame.prediction!!) else null, if (valid) "coordinate" else rejection(frame)))
            return
        }
        if (phase != Phase.CALIBRATION) { discardedFrames++; return }
        val point = fitPoints[fitIndex]
        val shown = point.shownMs
        if (shown != null && frame.captureMs >= shown + FIT_TIMEOUT_MS && fullAt == null) {
            fail("calibration_target_timeout",frame.outputMs); return
        }
        if (shown == null || frame.captureMs < shown + FIT_SETTLE_MS || fullAt != null) { discardedFrames++; return }
        if (!eligible(frame)) {
            val key = rejection(frame); point.rejected[key] = (point.rejected[key] ?: 0) + 1; return
        }
        point.accepted++
        if (!point.practice) { rows.add(frame.features!!.copyOf()); labels.add(layout.label(point.point)) }
        if (point.accepted == FIT_SAMPLES) fullAt = frame.outputMs
    }
    private fun eligible(f: Frame) = f.features?.let { it.size == 258 && it.all(Float::isFinite) } == true &&
        f.leftArea.isFinite() && f.rightArea.isFinite() && f.leftArea > 10 && f.rightArea > 10
    private fun rejection(f: Frame): String = when {
        f.features == null -> f.reason
        f.features.size != 258 || !f.features.all(Float::isFinite) -> "invalid_features"
        !f.leftArea.isFinite() || !f.rightArea.isFinite() || f.leftArea <= 10 || f.rightArea <= 10 -> "eye_area_rejected"
        else -> "prediction_unavailable"
    }
    fun tick(now: Double) {
        require(now.isFinite() && now >= 0)
        if (terminal()) return
        if (phase == Phase.CALIBRATION) {
            val shown = fitPoints[fitIndex].shownMs ?: return
            if (fullAt != null && now >= fullAt!! + FIT_WAIT_MS) {
                fullAt = null; fitIndex++
                if (fitIndex == fitPoints.size) { phase = Phase.FITTING; target = null }
                else { token++; target = targetForFit(fitIndex).copy(token=token) }
            } else if (now >= shown + FIT_TIMEOUT_MS) fail("calibration_target_timeout",now)
        } else if (phase == Phase.VALIDATION) {
            val end = blocks[testIndex].endMs ?: return
            // One callback may be in flight. Keep the target stable briefly, never relabel it.
            if (now >= end + TEST_DRAIN_MS) {
                testIndex++
                if (testIndex == blocks.size) { phase = Phase.COMPLETE; target = null; finishedMs = now }
                else nextTest()
            }
        }
    }
    fun training(): Training {
        check(phase == Phase.FITTING && fitDigest == null)
        check(rows.size == FIT_INDICES.size * FIT_SAMPLES && fitPoints.drop(1).all { it.accepted == FIT_SAMPLES })
        trainingRows = rows.size
        val digest = MessageDigest.getInstance("SHA-256")
        (rows + labels).forEach { row -> row.forEach { value ->
            val bits = value.toRawBits(); repeat(4) { digest.update((bits ushr (it*8)).toByte()) }
        } }
        fitDigest = digest.digest().joinToString("") { "%02x".format(it) }
        return Training(rows.map { it.copyOf() }.toTypedArray(), labels.map { it.copyOf() }.toTypedArray())
    }
    fun fitted(success: Boolean, now: Double) {
        if (phase != Phase.FITTING) return
        rows.forEach { it.fill(0f) }; labels.forEach { it.fill(0f) }; rows.clear(); labels.clear()
        if (!success || fitDigest == null) { fail("svr_fit_failed",now); return }
        phase = Phase.VALIDATION; nextTest()
    }
    private fun nextTest() {
        token++
        val b = blocks[testIndex]; target = Target(token,b.id,b.point,false,testIndex)
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
        finishedMs = now; target = null
        rows.forEach { it.fill(0f) }; labels.forEach { it.fill(0f) }; rows.clear(); labels.clear()
    }
    fun terminal() = phase in listOf(Phase.COMPLETE,Phase.STOPPED,Phase.FAILED)
    companion object {
        const val VERSION = "mgazenet_stationary_viewport_v2"
        const val FIT_SETTLE_MS = 1500.0
        const val FIT_WAIT_MS = 500.0
        const val FIT_SAMPLES = 45
        const val FIT_TIMEOUT_MS = 30_000.0
        const val TEST_SETTLE_MS = 3000.0
        const val TEST_MEASURE_MS = 2500.0
        const val TEST_DRAIN_MS = 250.0
        val FIT_INDICES = listOf(1,5,9,12,16,19,27,30,34,37,41,45,23)
        val TEST_LOCATIONS = listOf(2,8,13,15,22,24,31,33,38,44)
        val ANALYSIS_AGE_LIMITS_MS = listOf(50,100,200,500)
    }
}
