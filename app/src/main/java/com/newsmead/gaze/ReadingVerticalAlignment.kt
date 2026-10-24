package com.newsmead.gaze

import kotlin.math.abs

/** Whether the v4 control only records, or also applies, the vertical alignment fit. */
enum class ReadingVerticalAlignmentMode { OFF, ON }

data class ReadingVerticalReference(
    val id: String,
    val targetX: Float,
    val targetY: Float,
)

data class ReadingVerticalReferenceAggregate(
    val reference: ReadingVerticalReference,
    val sampleCount: Int,
    val observedMedianY: Float,
)

/** Session-local vertical screen-space layer; x is deliberately untouched. */
data class ReadingVerticalCorrection(
    val interceptPx: Double,
    val gain: Double,
) {
    fun applyY(y: Float): Float = (interceptPx + gain * y).toFloat()
}

data class ReadingVerticalAlignmentFit(
    val accepted: Boolean,
    val reason: String,
    val correction: ReadingVerticalCorrection?,
    val referenceCount: Int,
    val gain: Double?,
    val interceptPx: Double?,
    val maxResidualPx: Double?,
    val leaveOneOutMaxPx: Double?,
    val medianBeforePx: Double?,
    val medianAfterPx: Double?,
)

/**
 * Fits targetY = intercept + gain * observedY from three live-pipeline medians.
 * The same participant-independent guards are applied to every run. The result
 * is never persisted as the main calibration or the existing 2-D drift layer.
 */
object ReadingVerticalAlignment {
    const val MIN_SAMPLES_PER_REFERENCE = 15
    const val MIN_GAIN = 0.60
    const val MAX_GAIN = 1.80
    const val MIN_TARGET_SPAN_LINES = 4.0
    const val MAX_RESIDUAL_LINES = 0.75
    const val MAX_LEAVE_ONE_OUT_LINES = 1.50
    const val MAX_REFERENCE_CORRECTION_LINES = 4.0

    fun aggregate(
        reference: ReadingVerticalReference,
        observedYs: List<Float>,
    ): ReadingVerticalReferenceAggregate? {
        val finite = observedYs.filter { it.isFinite() }.sorted()
        if (finite.isEmpty()) return null
        val n = finite.size
        val median = if (n % 2 == 1) {
            finite[n / 2]
        } else {
            (finite[n / 2 - 1] + finite[n / 2]) / 2f
        }
        return ReadingVerticalReferenceAggregate(reference, n, median)
    }

    fun fit(
        aggregates: List<ReadingVerticalReferenceAggregate>,
        lineHeightPx: Float,
    ): ReadingVerticalAlignmentFit {
        if (!lineHeightPx.isFinite() || lineHeightPx <= 0f) {
            return rejected("invalid_line_height", aggregates.size)
        }
        if (aggregates.size != 3) return rejected("need_three_references", aggregates.size)
        if (aggregates.any { it.sampleCount < MIN_SAMPLES_PER_REFERENCE }) {
            return rejected("insufficient_samples", aggregates.size)
        }

        val ordered = aggregates.sortedBy { it.reference.targetY }
        val targetSpan = ordered.last().reference.targetY - ordered.first().reference.targetY
        if (targetSpan < MIN_TARGET_SPAN_LINES * lineHeightPx) {
            return rejected("target_span_too_small", aggregates.size)
        }
        if (!ordered.zipWithNext().all { (a, b) ->
                b.observedMedianY > a.observedMedianY
            }
        ) return rejected("observed_order_not_monotonic", aggregates.size)

        val correction = linearFit(ordered) ?: return rejected("degenerate_observed_span", aggregates.size)
        if (correction.gain !in MIN_GAIN..MAX_GAIN) {
            return rejected("gain_out_of_bounds", aggregates.size, correction)
        }

        val residuals = ordered.map {
            abs(correction.applyY(it.observedMedianY) - it.reference.targetY).toDouble()
        }
        val maxResidual = residuals.maxOrNull() ?: Double.POSITIVE_INFINITY
        if (maxResidual > MAX_RESIDUAL_LINES * lineHeightPx) {
            return rejected(
                "fit_residual_too_large", aggregates.size, correction, maxResidualPx = maxResidual,
            )
        }

        val looErrors = ordered.indices.map { heldIndex ->
            val training = ordered.filterIndexed { index, _ -> index != heldIndex }
            val fold = linearFit(training) ?: return rejected(
                "leave_one_out_degenerate", aggregates.size, correction, maxResidualPx = maxResidual,
            )
            abs(
                fold.applyY(ordered[heldIndex].observedMedianY) -
                    ordered[heldIndex].reference.targetY,
            ).toDouble()
        }
        val looMax = looErrors.maxOrNull() ?: Double.POSITIVE_INFINITY
        if (looMax > MAX_LEAVE_ONE_OUT_LINES * lineHeightPx) {
            return rejected(
                "leave_one_out_error_too_large",
                aggregates.size,
                correction,
                maxResidualPx = maxResidual,
                leaveOneOutMaxPx = looMax,
            )
        }

        val correctionMagnitudes = ordered.map {
            abs(correction.applyY(it.observedMedianY) - it.observedMedianY).toDouble()
        }
        if ((correctionMagnitudes.maxOrNull() ?: Double.POSITIVE_INFINITY) >
            MAX_REFERENCE_CORRECTION_LINES * lineHeightPx
        ) {
            return rejected(
                "reference_correction_too_large",
                aggregates.size,
                correction,
                maxResidualPx = maxResidual,
                leaveOneOutMaxPx = looMax,
            )
        }

        val before = ordered.map {
            abs(it.observedMedianY - it.reference.targetY).toDouble()
        }
        return ReadingVerticalAlignmentFit(
            accepted = true,
            reason = "accepted",
            correction = correction,
            referenceCount = ordered.size,
            gain = correction.gain,
            interceptPx = correction.interceptPx,
            maxResidualPx = maxResidual,
            leaveOneOutMaxPx = looMax,
            medianBeforePx = median(before),
            medianAfterPx = median(residuals),
        )
    }

    private fun linearFit(
        aggregates: List<ReadingVerticalReferenceAggregate>,
    ): ReadingVerticalCorrection? {
        if (aggregates.size < 2) return null
        val observedMean = aggregates.map { it.observedMedianY.toDouble() }.average()
        val targetMean = aggregates.map { it.reference.targetY.toDouble() }.average()
        var covariance = 0.0
        var observedVariance = 0.0
        for (item in aggregates) {
            val observedDelta = item.observedMedianY - observedMean
            covariance += observedDelta * (item.reference.targetY - targetMean)
            observedVariance += observedDelta * observedDelta
        }
        if (observedVariance < 1e-6) return null
        val gain = covariance / observedVariance
        val intercept = targetMean - gain * observedMean
        if (!gain.isFinite() || !intercept.isFinite()) return null
        return ReadingVerticalCorrection(interceptPx = intercept, gain = gain)
    }

    private fun rejected(
        reason: String,
        referenceCount: Int,
        correction: ReadingVerticalCorrection? = null,
        maxResidualPx: Double? = null,
        leaveOneOutMaxPx: Double? = null,
    ) = ReadingVerticalAlignmentFit(
        accepted = false,
        reason = reason,
        correction = null,
        referenceCount = referenceCount,
        gain = correction?.gain,
        interceptPx = correction?.interceptPx,
        maxResidualPx = maxResidualPx,
        leaveOneOutMaxPx = leaveOneOutMaxPx,
        medianBeforePx = null,
        medianAfterPx = null,
    )

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
    }
}
