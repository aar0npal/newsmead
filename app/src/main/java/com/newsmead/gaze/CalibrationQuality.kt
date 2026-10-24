package com.newsmead.gaze

import kotlin.math.ceil
import kotlin.math.hypot

/**
 * Post-fit calibration quality metrics (docs/calibration-design.md §7).
 *
 * Leave-one-out: refit the mapper N times, each time holding out one point and
 * measuring the px error at that held-out point. Pure Kotlin so it is
 * JVM-testable; px -> line-height conversion is left to the caller, which knows
 * the device line geometry.
 *
 * Note: beyond its training feature range GazeMapper extends linearly along the
 * boundary gradient, so held-out grid-edge points read only slightly pessimistic
 * (grid rows/columns share feature extremes, so this rarely bites anyway).
 */
object CalibrationQuality {

    /** GazeMapper needs 6 points; one more so a point can be held out. */
    const val MIN_POINTS = 7

    data class Report(
        /** Index-aligned with the input samples. */
        val errorsPx: List<Float>,
        /** Signed predicted-minus-target residuals, index-aligned with samples. */
        val dxPx: List<Float>,
        val dyPx: List<Float>,
        val medianPx: Float,
        val p95Px: Float,
        /** Sample indices sorted worst-first. */
        val worstIndices: List<Int>,
    )

    /** Returns null when there are too few points or any refit is singular. */
    fun leaveOneOut(samples: List<CalibrationSample>): Report? {
        if (samples.size < MIN_POINTS) return null
        val errors = ArrayList<Float>(samples.size)
        val dx = ArrayList<Float>(samples.size)
        val dy = ArrayList<Float>(samples.size)
        for (held in samples.indices) {
            val training = samples.filterIndexed { i, _ -> i != held }
            val mapper = try {
                GazeMapper(training)
            } catch (e: Exception) {
                return null
            }
            val predicted = mapper.map(samples[held].gazeX, samples[held].gazeY)
            val residualX = predicted[0] - samples[held].screenX
            val residualY = predicted[1] - samples[held].screenY
            dx.add(residualX)
            dy.add(residualY)
            errors.add(hypot(residualX, residualY))
        }
        val sorted = errors.sorted()
        val n = sorted.size
        val median = if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2f
        val p95 = sorted[ceil(0.95 * n).toInt() - 1]
        val worst = errors.indices.sortedByDescending { errors[it] }
        return Report(errors, dx, dy, median, p95, worst)
    }
}
