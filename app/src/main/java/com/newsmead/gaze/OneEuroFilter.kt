package com.newsmead.gaze

import kotlin.math.PI
import kotlin.math.abs

/**
 * One Euro Filter (Casiez et al.) for one scalar signal: smooths jitter when the
 * value is steady but stays responsive when it moves fast. Tuning constants may
 * need adjustment on-device.
 *
 * Ported verbatim (except package) from gaze-thesis-prototype.
 */
class OneEuroFilter(
    private val minCutoff: Double = 1.0,
    private val beta: Double = 0.007,
    private val dCutoff: Double = 1.0,
) {
    private var started = false
    private var lastTimeMs = 0L
    private var xPrev = 0.0
    private var dxPrev = 0.0

    fun filter(value: Float, timestampMs: Long): Float {
        val v = value.toDouble()
        if (!started) {
            started = true
            lastTimeMs = timestampMs
            xPrev = v
            dxPrev = 0.0
            return value
        }
        val dt = (timestampMs - lastTimeMs) / 1000.0
        lastTimeMs = timestampMs
        if (dt <= 0.0) return xPrev.toFloat()

        val rate = 1.0 / dt
        val dx = (v - xPrev) * rate
        val edx = lowpass(dx, dxPrev, alpha(dCutoff, rate))
        dxPrev = edx

        val cutoff = minCutoff + beta * abs(edx)
        val x = lowpass(v, xPrev, alpha(cutoff, rate))
        xPrev = x
        return x.toFloat()
    }

    fun reset() {
        started = false
    }

    private fun alpha(cutoff: Double, rate: Double): Double {
        val tau = 1.0 / (2.0 * PI * cutoff)
        val te = 1.0 / rate
        return 1.0 / (1.0 + tau / te)
    }

    private fun lowpass(x: Double, prev: Double, a: Double): Double = a * x + (1 - a) * prev
}
