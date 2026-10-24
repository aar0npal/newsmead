package com.newsmead.gaze

enum class DetailedTelemetryMode {
    ON,
    OFF;

    val enabled: Boolean
        get() = this == ON

    val logLabel: String
        get() = if (enabled) "detailed_on" else "detailed_off"
}

data class FpsSummary(
    val sampleCount: Int,
    val mean: Float,
    val min: Float,
    val max: Float,
)

/** Constant-memory FPS evidence retained in both telemetry modes. */
class FpsSummaryAccumulator {
    private var count = 0
    private var sum = 0.0
    private var minimum = Float.POSITIVE_INFINITY
    private var maximum = Float.NEGATIVE_INFINITY

    fun add(fps: Float) {
        if (!fps.isFinite() || fps <= 0f) return
        count++
        sum += fps
        minimum = minOf(minimum, fps)
        maximum = maxOf(maximum, fps)
    }

    fun reset() {
        count = 0
        sum = 0.0
        minimum = Float.POSITIVE_INFINITY
        maximum = Float.NEGATIVE_INFINITY
    }

    fun snapshot(): FpsSummary? = if (count == 0) {
        null
    } else {
        FpsSummary(
            sampleCount = count,
            mean = (sum / count).toFloat(),
            min = minimum,
            max = maximum,
        )
    }
}
