package com.newsmead.gaze.mgazenet

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Conservative operational guardrail for a fitted calibration.
 *
 * This is not an accuracy correction and does not replace the independent nine-point check. It
 * only prevents an incomplete or clearly poor post-fit check from replacing a saved calibration.
 */
object MgazeNetCalibrationQualityGate {
    data class Check(
        val index: Int,
        val label: String,
        val sampleCount: Int,
        val dxCm: Double,
        val dyCm: Double,
        val repeatedFitTarget: Boolean,
    ) {
        val radialCm: Double get() = hypot(dxCm,dyCm)
        val absoluteVerticalCm: Double get() = abs(dyCm)
        val valid: Boolean get() = index > 0 && label.isNotBlank() && sampleCount >= 0 &&
            dxCm.isFinite() && dyCm.isFinite()
    }

    data class Report(
        val passed: Boolean,
        val median2dCm: Double,
        val worst2dCm: Double,
        val medianVerticalCm: Double,
        val worstRepeatCm: Double,
        val failures: List<String>,
    )

    fun evaluate(checks: List<Check>): Report {
        val failures = ArrayList<String>()
        if (checks.size != EXPECTED_CHECK_COUNT) {
            failures += "Completed ${checks.size} of $EXPECTED_CHECK_COUNT post-fit locations"
        }
        if (checks.map { it.index }.distinct().size != checks.size || checks.any { !it.valid }) {
            failures += "Post-fit results were malformed"
        }
        checks.filter { it.sampleCount < MIN_SAMPLES_PER_CHECK }.forEach {
            failures += "${it.label} had ${it.sampleCount} usable samples; at least $MIN_SAMPLES_PER_CHECK are required"
        }

        val valid = checks.filter(Check::valid)
        val median2d = median(valid.map(Check::radialCm))
        val worst2d = valid.maxOfOrNull(Check::radialCm) ?: Double.NaN
        val medianVertical = median(valid.map(Check::absoluteVerticalCm))
        val repeats = valid.filter(Check::repeatedFitTarget)
        val worstRepeat = repeats.maxOfOrNull(Check::radialCm) ?: Double.NaN
        if (repeats.size != EXPECTED_REPEAT_COUNT) failures +=
            "Completed ${repeats.size} of $EXPECTED_REPEAT_COUNT repeated calibration locations"

        if (!median2d.isFinite() || median2d > MAX_MEDIAN_2D_CM) failures +=
            "Median 2D error must be at most ${format(MAX_MEDIAN_2D_CM)} cm"
        if (!worst2d.isFinite() || worst2d > MAX_WORST_2D_CM) failures +=
            "Worst 2D error must be at most ${format(MAX_WORST_2D_CM)} cm"
        if (!medianVertical.isFinite() || medianVertical > MAX_MEDIAN_VERTICAL_CM) failures +=
            "Median vertical error must be at most ${format(MAX_MEDIAN_VERTICAL_CM)} cm"
        if (!worstRepeat.isFinite() || worstRepeat > MAX_WORST_REPEAT_CM) failures +=
            "Repeated-location error must be at most ${format(MAX_WORST_REPEAT_CM)} cm"

        return Report(
            passed = failures.isEmpty(),
            median2dCm = median2d,
            worst2dCm = worst2d,
            medianVerticalCm = medianVertical,
            worstRepeatCm = worstRepeat,
            failures = failures.distinct(),
        )
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return Double.NaN
        val sorted = values.sorted()
        return (sorted[(sorted.size - 1) / 2] + sorted[sorted.size / 2]) / 2.0
    }

    private fun format(value: Double) = "%.2f".format(java.util.Locale.US,value)

    const val EXPECTED_CHECK_COUNT = 6
    const val EXPECTED_REPEAT_COUNT = 2
    const val MIN_SAMPLES_PER_CHECK = 12

    // Initial engineering guardrails chosen to separate the observed good and clearly poor runs.
    // Freeze them before collecting a formal evaluation dataset.
    const val MAX_MEDIAN_2D_CM = 1.00
    const val MAX_WORST_2D_CM = 2.00
    const val MAX_MEDIAN_VERTICAL_CM = 0.80
    const val MAX_WORST_REPEAT_CM = 1.50
}
