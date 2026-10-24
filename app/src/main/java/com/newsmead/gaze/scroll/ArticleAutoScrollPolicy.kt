package com.newsmead.gaze.scroll

import kotlin.math.min

/** Experimental navigation settings, not validated reading or accuracy thresholds.
 * Coordinates are absolute physical-screen pixels; clocks are monotonic milliseconds.
 * The caller interrupts on missing gaze and controls that prevent article navigation.
 */
class ArticleAutoScrollPolicy(val config: Config = Config()) {
    data class Config(
        val version: String = VERSION,
        val startScreenFraction: Double = .40,
        val fullSpeedScreenFraction: Double = .65,
        val maxSpeedScreenFractionPerSecond: Double = .06,
        val responseMs: Double = 180.0,
        val maxSampleGapMs: Double = 150.0,
        val maxOutputAgeMs: Double = 250.0,
        val maxFrameDeltaMs: Double = 50.0,
    ) {
        init {
            require(version == VERSION)
            require(startScreenFraction.isFinite() && startScreenFraction > 0 && startScreenFraction < 1)
            require(fullSpeedScreenFraction.isFinite() && fullSpeedScreenFraction > startScreenFraction &&
                fullSpeedScreenFraction <= 1)
            require(listOf(maxSpeedScreenFractionPerSecond, responseMs, maxSampleGapMs,
                maxOutputAgeMs, maxFrameDeltaMs).all { it.isFinite() && it > 0 })
        }
    }

    /** Actual clipped body rectangle and reading viewport, excluding occluding controls. */
    data class Geometry(
        val screenHeightPx: Double,
        val textVisibleLeftPx: Double,
        val textVisibleTopPx: Double,
        val textVisibleRightPx: Double,
        val textVisibleBottomPx: Double,
        val viewportTopPx: Double,
        val viewportBottomPx: Double,
        val scrollYPx: Double,
        val maxScrollYPx: Double,
        val geometryKey: Long,
    )

    data class Sample(val captureTimeMs: Double, val deliveryTimeMs: Double, val xPx: Double, val yPx: Double)
    data class Status(val reason: String, val speedPxPerSecond: Double, val targetSpeedPxPerSecond: Double)
    data class Frame(
        val deltaPx: Double,
        val speedPxPerSecond: Double,
        val targetSpeedPxPerSecond: Double,
        val active: Boolean,
        val reason: String,
        val sourceCaptureTimeMs: Double?,
        val sourceDeliveryTimeMs: Double?,
        val sourceYPx: Double?,
    )

    var status = Status("waiting_lower", 0.0, 0.0)
        private set
    private var latest: Sample? = null
    private var frameTime: Double? = null
    private var lastCapture = Double.NEGATIVE_INFINITY
    private var lastDelivery = Double.NEGATIVE_INFINITY
    private var lastNow = Double.NEGATIVE_INFINITY

    /** Hard stop; replay rejection clocks survive ordinary missing-data boundaries. */
    fun interrupt(reason: String = "interrupted") {
        latest = null
        frameTime = null
        status = Status(reason, 0.0, 0.0)
    }

    /** Only a new enabled session/document may discard replay rejection clocks. */
    fun reset() {
        interrupt("waiting_lower")
        lastCapture = Double.NEGATIVE_INFINITY
        lastDelivery = Double.NEGATIVE_INFINITY
        lastNow = Double.NEGATIVE_INFINITY
    }

    fun onSample(sample: Sample, geometry: Geometry, nowMs: Double): Boolean {
        if (!acceptNow(nowMs)) return reject("invalid_clock")
        val capture = sample.captureTimeMs
        val delivery = sample.deliveryTimeMs
        if (!capture.isFinite() || !delivery.isFinite() || capture < 0 ||
            capture > delivery || delivery > nowMs || capture <= lastCapture || delivery <= lastDelivery) {
            return reject("invalid_clock")
        }
        val captureGap = capture - lastCapture
        val deliveryGap = delivery - lastDelivery
        lastCapture = capture
        lastDelivery = delivery
        if (nowMs - capture > config.maxOutputAgeMs || nowMs - delivery > config.maxSampleGapMs) {
            return reject("stale")
        }
        if (!validGeometry(geometry)) return reject("geometry_unavailable")
        if (!inside(sample, geometry)) return reject("outside_article")
        if (geometry.maxScrollYPx <= geometry.scrollYPx) return reject("no_scroll_room")
        // Missing intervals cannot contribute motion when a fresh point finally arrives.
        val old = latest
        if (captureGap > config.maxSampleGapMs || deliveryGap > config.maxSampleGapMs ||
            (old != null && (nowMs - old.captureTimeMs > config.maxOutputAgeMs ||
                nowMs - old.deliveryTimeMs > config.maxSampleGapMs))) interrupt("gaze_unavailable")
        val desired = target(sample, geometry)
        // Upper gaze can stay fresh while no animation frames are scheduled.
        // Its idle time must not accelerate the first frame of a lower restart.
        if (latest == null || (status.speedPxPerSecond == 0.0 &&
                status.targetSpeedPxPerSecond == 0.0 && desired > 0.0)) frameTime = nowMs
        latest = sample
        updateStatus(status.speedPxPerSecond, desired)
        return true
    }

    fun advance(nowMs: Double, geometry: Geometry): Frame {
        if (!acceptNow(nowMs)) return stoppedFrame("invalid_clock")
        if (!validGeometry(geometry)) return stoppedFrame("geometry_unavailable")
        val source = latest ?: return frame(0.0)
        if (nowMs - source.captureTimeMs > config.maxOutputAgeMs ||
            nowMs - source.deliveryTimeMs > config.maxSampleGapMs) return stoppedFrame("stale")
        if (!inside(source, geometry)) return stoppedFrame("outside_article")
        val room = geometry.maxScrollYPx - geometry.scrollYPx
        if (room <= 0) return stoppedFrame("no_scroll_room")
        val elapsed = min(nowMs - (frameTime ?: nowMs), config.maxFrameDeltaMs)
        frameTime = nowMs
        val desired = target(source, geometry)
        val previous = status.speedPxPerSecond
        val maxSpeed = geometry.screenHeightPx * config.maxSpeedScreenFractionPerSecond
        val change = maxSpeed * (elapsed / config.responseMs)
        val speed = previous + (desired - previous).coerceIn(-change, change)
        val delta = min((previous * .5 + speed * .5) * (elapsed / 1000.0), room)
        updateStatus(speed, desired)
        // Preserve final movement/source for journaling, then stop at the end boundary.
        if (delta >= room) {
            val result = Frame(delta, 0.0, 0.0, false, "no_scroll_room",
                source.captureTimeMs, source.deliveryTimeMs, source.yPx)
            interrupt("no_scroll_room")
            return result
        }
        return frame(delta)
    }

    private fun acceptNow(nowMs: Double): Boolean {
        if (!nowMs.isFinite() || nowMs < 0 || nowMs < lastNow) return false
        lastNow = nowMs
        return true
    }

    private fun reject(reason: String): Boolean {
        interrupt(reason)
        return false
    }

    private fun stoppedFrame(reason: String): Frame {
        interrupt(reason)
        return frame(0.0)
    }

    private fun frame(delta: Double): Frame = Frame(delta, status.speedPxPerSecond,
        status.targetSpeedPxPerSecond, status.speedPxPerSecond > 0 || status.targetSpeedPxPerSecond > 0,
        status.reason, latest?.captureTimeMs, latest?.deliveryTimeMs, latest?.yPx)

    private fun updateStatus(speed: Double, target: Double) {
        val reason = when {
            speed > target -> "slowing"
            speed > 0 || target > 0 -> "moving"
            else -> "waiting_lower"
        }
        status = Status(reason, speed, target)
    }

    private fun target(sample: Sample, g: Geometry): Double {
        val fraction = ((sample.yPx / g.screenHeightPx - config.startScreenFraction) /
            (config.fullSpeedScreenFraction - config.startScreenFraction)).coerceIn(0.0, 1.0)
        return fraction * (g.screenHeightPx * config.maxSpeedScreenFractionPerSecond)
    }

    private fun inside(sample: Sample, g: Geometry): Boolean =
        sample.xPx.isFinite() && sample.yPx.isFinite() &&
            sample.xPx >= g.textVisibleLeftPx && sample.xPx < g.textVisibleRightPx &&
            sample.yPx >= g.textVisibleTopPx && sample.yPx < g.textVisibleBottomPx

    private fun validGeometry(g: Geometry): Boolean = with(g) {
        listOf(screenHeightPx, textVisibleLeftPx, textVisibleTopPx, textVisibleRightPx,
            textVisibleBottomPx, viewportTopPx, viewportBottomPx, scrollYPx, maxScrollYPx).all { it.isFinite() } &&
            screenHeightPx > 0 && textVisibleLeftPx >= 0 && textVisibleRightPx > textVisibleLeftPx &&
            viewportTopPx >= 0 && viewportBottomPx <= screenHeightPx && viewportBottomPx > viewportTopPx &&
            textVisibleTopPx >= viewportTopPx && textVisibleBottomPx <= viewportBottomPx &&
            textVisibleBottomPx > textVisibleTopPx && scrollYPx >= 0 && maxScrollYPx >= scrollYPx &&
            viewportTopPx < config.startScreenFraction * screenHeightPx &&
            config.startScreenFraction * screenHeightPx < viewportBottomPx &&
            (screenHeightPx * config.maxSpeedScreenFractionPerSecond).isFinite()
    }

    companion object {
        const val VERSION = "ARTICLE_AUTO_SCROLL_TRIAL_V3"
    }
}
