package com.newsmead.gaze.mgazenet

import kotlin.math.abs
import kotlin.math.hypot

/** Image-free eyelid geometry in the supplied landmark frame; eye names match crop identities. */
object MgazeNetEyelidGeometry {
    data class Sample(val leftAspect: Double, val rightAspect: Double)

    fun from(x: DoubleArray, y: DoubleArray, width: Int, height: Int): Sample? {
        if (width <= 0 || height <= 0 || x.size != 478 || y.size != 478) return null
        fun aspect(cornerA: Int, cornerB: Int, upper: Int, lower: Int): Double? {
            val indices = intArrayOf(cornerA, cornerB, upper, lower)
            if (indices.any { !x[it].isFinite() || !y[it].isFinite() }) return null
            val dx = (x[cornerB] - x[cornerA]) * width
            val dy = (y[cornerB] - y[cornerA]) * height
            val cornerWidth = hypot(dx, dy)
            if (!cornerWidth.isFinite() || cornerWidth <= 0.0) return null
            val lidX = (x[lower] - x[upper]) * width
            val lidY = (y[lower] - y[upper]) * height
            // Project the lid gap onto the perpendicular unit corner axis, then divide by width.
            val ratio = abs(lidX * (dy / cornerWidth) - lidY * (dx / cornerWidth)) / cornerWidth
            return ratio.takeIf { it.isFinite() && it >= 0.0 }
        }
        val left = aspect(33, 133, 159, 145) ?: return null
        val right = aspect(263, 362, 386, 374) ?: return null
        return Sample(left, right)
    }
}
