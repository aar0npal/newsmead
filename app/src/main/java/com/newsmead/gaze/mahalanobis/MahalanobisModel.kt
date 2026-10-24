package com.newsmead.gaze.mahalanobis

import kotlin.math.abs

/** Feature order and units are always R/minute, dwell fraction, F/minute. */
data class RdfValues(val regressionRatePerMinute: Double, val dwellFraction: Double, val fixationRatePerMinute: Double) {
    operator fun get(index: Int): Double = when (index) { 0 -> regressionRatePerMinute; 1 -> dwellFraction; 2 -> fixationRatePerMinute; else -> error("RDF index") }
    operator fun minus(other: RdfValues) = RdfValues(get(0)-other[0], get(1)-other[1], get(2)-other[2])
    fun isFinite() = (0..2).all { get(it).isFinite() }
}

enum class FeatureQuality { VALID, MISSING, INSUFFICIENT, DISCONTINUITY }

/** Capture-time window, never a delivery clock. Partial windows cannot fit or score. */
data class RdfFeatureVector(
    val timestampMs: Long,
    val windowStartMs: Long,
    val windowEndMs: Long,
    val values: RdfValues,
    val quality: FeatureQuality = FeatureQuality.VALID,
    val validFraction: Double = 1.0,
    val validSampleCount: Int? = null,
    val totalSampleCount: Int? = null,
) {
    fun isUsable(config: MahalanobisConfig): Boolean = quality == FeatureQuality.VALID &&
        timestampMs >= 0 && windowStartMs >= 0 && windowEndMs == timestampMs &&
        windowEndMs >= windowStartMs && windowEndMs - windowStartMs >= config.windowMs &&
        windowEndMs - windowStartMs - config.windowMs <= config.maxWindowOverhangMs &&
        validFraction.isFinite() && validFraction in config.minimumValidFraction..1.0 &&
        ((validSampleCount == null && totalSampleCount == null) ||
            (validSampleCount != null && totalSampleCount != null && totalSampleCount > 0 &&
                validSampleCount in 0..totalSampleCount &&
                kotlin.math.abs(validFraction - validSampleCount.toDouble() / totalSampleCount) <= 1e-9)) &&
        values.isFinite() && values.regressionRatePerMinute >= 0 &&
        values.dwellFraction in 0.0..1.0 && values.fixationRatePerMinute >= 0
}

/** Immutable 3x3 matrix; no mutable arrays escape the frozen profile. */
data class Matrix3(
    val m00: Double, val m01: Double, val m02: Double,
    val m10: Double, val m11: Double, val m12: Double,
    val m20: Double, val m21: Double, val m22: Double,
) {
    operator fun get(row: Int, col: Int): Double = when (row * 3 + col) {
        0 -> m00; 1 -> m01; 2 -> m02; 3 -> m10; 4 -> m11; 5 -> m12; 6 -> m20; 7 -> m21; 8 -> m22; else -> error("Matrix index")
    }
    companion object {
        internal fun from(rows: Array<DoubleArray>) = Matrix3(rows[0][0], rows[0][1], rows[0][2], rows[1][0], rows[1][1], rows[1][2], rows[2][0], rows[2][1], rows[2][2])
    }
}

enum class BaselineSampling { ALL_VALID_WINDOWS, THINNED_WINDOWS, NON_OVERLAPPING_WINDOWS }
enum class ThresholdPercentile(val probability: Double?) { P90(0.90), P95(0.95), P99(0.99), CUSTOM(null) }

/** Defaults are exploratory software settings, not validated scientific cutoffs. */
data class MahalanobisConfig(
    val windowMs: Long = 10_000,
    val updateIntervalMs: Long = 500,
    val maxWindowOverhangMs: Long = updateIntervalMs,
    val minimumBaselineDurationMs: Long = 60_000,
    val minimumRetainedWindows: Int = 30,
    val lambda: Double = 1e-6,
    val sampling: BaselineSampling = BaselineSampling.ALL_VALID_WINDOWS,
    val thinningIntervalMs: Long = 10_000,
    val percentile: ThresholdPercentile = ThresholdPercentile.P95,
    val customThresholdMd2: Double? = null,
    val persistenceMs: Long = 1_500,
    val recoveryMs: Long = 3_500,
    val recoveryThresholdRatio: Double = 1.0,
    val maxGapMs: Long = 1_000,
    val nearTolerance: Double = 1e-9,
    val minimumValidFraction: Double = 0.65,
) {
    init {
        require(windowMs > 0 && updateIntervalMs > 0 && maxWindowOverhangMs >= 0 && minimumBaselineDurationMs >= 0 && minimumRetainedWindows >= 2)
        require(lambda.isFinite() && lambda >= 0 && thinningIntervalMs > 0)
        require(customThresholdMd2 == null || (customThresholdMd2.isFinite() && customThresholdMd2 >= 0))
        require(percentile != ThresholdPercentile.CUSTOM || customThresholdMd2 != null)
        require(persistenceMs >= 0 && recoveryMs >= 0 && maxGapMs > 0)
        require(recoveryThresholdRatio.isFinite() && recoveryThresholdRatio > 0 && recoveryThresholdRatio <= 1)
        require(nearTolerance.isFinite() && nearTolerance >= 0 && minimumValidFraction in 0.0..1.0)
    }
    val quantile: Double get() = percentile.probability ?: 0.95
}

enum class BaselineStatus { COLLECTING, READY, INSUFFICIENT_WINDOWS, INSUFFICIENT_DURATION, INVALID_INPUT, NUMERICAL_FAILURE, DEGENERATE_THRESHOLD }
data class SamplingMetadata(val policy: BaselineSampling, val receivedCount: Int, val validCount: Int, val retainedCount: Int, val firstTimestampMs: Long?, val lastTimestampMs: Long?, val thinningIntervalMs: Long, val coverageMs: Long = 0) {
    /** Includes invalid numeric/window/quality inputs and rejected timestamp order. */
    val invalidCount: Int get() = receivedCount - validCount
}
/** Ratio of largest/smallest Cholesky diagonal; a diagnostic, not a condition number. */
data class StabilityDiagnostic(val choleskyDiagonalRatio: Double)
enum class Direction { BELOW_BASELINE, NEAR_BASELINE, ABOVE_BASELINE }
internal fun direction(delta: Double, tolerance: Double) = if (abs(delta) <= tolerance) Direction.NEAR_BASELINE else if (delta < 0) Direction.BELOW_BASELINE else Direction.ABOVE_BASELINE

