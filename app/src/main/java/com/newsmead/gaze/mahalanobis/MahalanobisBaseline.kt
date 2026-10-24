package com.newsmead.gaze.mahalanobis

import kotlin.math.floor
import kotlin.math.sqrt

/** Fitting is explicit and terminal. Calling freeze again returns the same result. */
class MahalanobisBaselineCollector(val config: MahalanobisConfig = MahalanobisConfig()) {
    private val retained = mutableListOf<RdfFeatureVector>()
    private var received = 0
    private var valid = 0
    private var lastTimestamp: Long? = null
    private var invalidOrder = false
    private var frozen: BaselineFit? = null
    val status: BaselineStatus get() = frozen?.status ?: BaselineStatus.COLLECTING
    val coveredDurationMs: Long get() = retainedCoverageMs()
    /** Snapshot for serialization; reloading/refitting is explicit, never online adaptation. */
    val baselineVectors: List<RdfFeatureVector> get() = java.util.Collections.unmodifiableList(ArrayList(retained))

    fun add(vector: RdfFeatureVector): Boolean {
        if (frozen != null) return false
        received++
        val previous = lastTimestamp
        if (previous != null && vector.timestampMs <= previous) { invalidOrder = true; return false }
        lastTimestamp = vector.timestampMs
        if (!vector.isUsable(config)) return false
        valid++
        val last = retained.lastOrNull()
        val retain = last == null || when (config.sampling) {
            BaselineSampling.ALL_VALID_WINDOWS -> true
            BaselineSampling.THINNED_WINDOWS -> vector.timestampMs - last.timestampMs >= config.thinningIntervalMs
            BaselineSampling.NON_OVERLAPPING_WINDOWS -> vector.windowStartMs >= last.windowEndMs
        }
        if (retain) retained.add(vector)
        return retain
    }

    fun freeze(): BaselineFit {
        frozen?.let { return it }
        val coverageMs = retainedCoverageMs()
        val metadata = SamplingMetadata(config.sampling, received, valid, retained.size, retained.firstOrNull()?.timestampMs, retained.lastOrNull()?.timestampMs, config.thinningIntervalMs, coverageMs)
        fun fail(status: BaselineStatus): BaselineFit = BaselineFit(status, null, metadata).also { frozen = it }
        if (invalidOrder) return fail(BaselineStatus.INVALID_INPUT)
        if (retained.size < config.minimumRetainedWindows) return fail(BaselineStatus.INSUFFICIENT_WINDOWS)
        // Union of observed valid intervals: overlap counts once, unobserved gaps never count.
        if (coverageMs < config.minimumBaselineDurationMs) return fail(BaselineStatus.INSUFFICIENT_DURATION)
        val mean = DoubleArray(3)
        val cross = Array(3) { DoubleArray(3) }
        retained.forEachIndexed { index, v ->
            val delta = DoubleArray(3) { v.values[it] - mean[it] }
            for (i in 0..2) mean[i] += delta[i] / (index + 1)
            for (i in 0..2) for (j in 0..2) cross[i][j] += delta[i] * (v.values[j] - mean[j])
        }
        val covariance = Array(3) { i -> DoubleArray(3) { j -> (cross[i][j] + cross[j][i]) / (2.0 * (retained.size - 1)) } }
        val regularized = Array(3) { i -> DoubleArray(3) { j -> covariance[i][j] + if (i == j) config.lambda else 0.0 } }
        if (mean.any { !it.isFinite() } || regularized.any { row -> row.any { !it.isFinite() } }) return fail(BaselineStatus.NUMERICAL_FAILURE)
        val lower = cholesky(regularized) ?: return fail(BaselineStatus.NUMERICAL_FAILURE)
        val mu = RdfValues(mean[0], mean[1], mean[2])
        val distribution = retained.map { squaredDistance(it.values - mu, lower) ?: return fail(BaselineStatus.NUMERICAL_FAILURE) }.sorted()
        if (empiricalQuantile(distribution, 0.95) <= 0.0) return fail(BaselineStatus.DEGENERATE_THRESHOLD)
        val threshold = if (config.percentile == ThresholdPercentile.CUSTOM) config.customThresholdMd2!! else empiricalQuantile(distribution, config.quantile)
        if (!threshold.isFinite() || threshold < 0.0 || (threshold == 0.0 && config.percentile != ThresholdPercentile.CUSTOM)) return fail(BaselineStatus.DEGENERATE_THRESHOLD)
        val diagonal = (0..2).map { lower[it, it] }
        val diagonalRatio = diagonal.maxOrNull()!! / diagonal.minOrNull()!!
        if (!diagonalRatio.isFinite()) return fail(BaselineStatus.NUMERICAL_FAILURE)
        val profile = FrozenMahalanobisProfile(config, mu, Matrix3.from(covariance), Matrix3.from(regularized), lower, distribution, threshold, metadata, StabilityDiagnostic(diagonalRatio))
        return BaselineFit(BaselineStatus.READY, profile, metadata).also { frozen = it }
    }

    private fun retainedCoverageMs(): Long {
        if (retained.isEmpty()) return 0
        val intervals = retained.sortedBy { it.windowStartMs }
        var start = intervals.first().windowStartMs
        var end = intervals.first().windowEndMs
        var coverage = 0L
        for (v in intervals.drop(1)) {
            if (v.windowStartMs <= end) end = maxOf(end, v.windowEndMs)
            else { coverage += end - start; start = v.windowStartMs; end = v.windowEndMs }
        }
        return coverage + (end - start)
    }
}

data class BaselineFit(val status: BaselineStatus, val profile: FrozenMahalanobisProfile?, val sampling: SamplingMetadata)

/** Construction is restricted to a successful fit; cached factor and distribution cannot mutate. */
class FrozenMahalanobisProfile internal constructor(
    val config: MahalanobisConfig,
    val mean: RdfValues,
    val covariance: Matrix3,
    val regularizedCovariance: Matrix3,
    internal val cholesky: Matrix3,
    baselineMd2: List<Double>,
    val thresholdMd2: Double,
    val sampling: SamplingMetadata,
    val stability: StabilityDiagnostic,
) {
    val baselineMd2: List<Double> = java.util.Collections.unmodifiableList(ArrayList(baselineMd2))
    val p90Md2: Double = empiricalQuantile(this.baselineMd2, 0.90)
    val p95Md2: Double = empiricalQuantile(this.baselineMd2, 0.95)
    val p99Md2: Double = empiricalQuantile(this.baselineMd2, 0.99)
}

/** Type-7/linear interpolation: h=(n-1)p, interpolate adjacent sorted observations. */
fun empiricalQuantile(sortedValues: List<Double>, probability: Double): Double {
    require(sortedValues.isNotEmpty() && probability.isFinite() && probability in 0.0..1.0)
    require(sortedValues.all { it.isFinite() } && sortedValues.zipWithNext().all { it.first <= it.second })
    val position = (sortedValues.size - 1) * probability
    val lo = floor(position).toInt()
    val hi = minOf(lo + 1, sortedValues.lastIndex)
    return sortedValues[lo] + (sortedValues[hi] - sortedValues[lo]) * (position - lo)
}

private fun cholesky(matrix: Array<DoubleArray>): Matrix3? {
    val l = Array(3) { DoubleArray(3) }
    for (i in 0..2) for (j in 0..i) {
        var sum = matrix[i][j]
        for (k in 0 until j) sum -= l[i][k] * l[j][k]
        if (!sum.isFinite()) return null
        l[i][j] = if (i == j) { if (sum <= 0) return null; sqrt(sum) } else sum / l[j][j]
        if (!l[i][j].isFinite()) return null
    }
    return Matrix3.from(l)
}

internal fun squaredDistance(delta: RdfValues, lower: Matrix3): Double? {
    val y = DoubleArray(3)
    for (i in 0..2) {
        var value = delta[i]
        for (j in 0 until i) value -= lower[i, j] * y[j]
        y[i] = value / lower[i, i]
    }
    val result = y.sumOf { it * it }
    return result.takeIf { it.isFinite() && it >= 0 }
}

enum class ScoreStatus { AVAILABLE, BASELINE_UNAVAILABLE, INVALID_WINDOW, NUMERICAL_FAILURE }
data class MahalanobisScore(val vector: RdfFeatureVector, val status: ScoreStatus, val md2: Double?, val md: Double?, val thresholdMd2: Double?, val delta: RdfValues?, val directions: List<Direction>?)

class MahalanobisScorer(private val profile: FrozenMahalanobisProfile?) {
    fun score(vector: RdfFeatureVector): MahalanobisScore {
        fun unavailable(status: ScoreStatus) = MahalanobisScore(vector, status, null, null, profile?.thresholdMd2, null, null)
        val model = profile ?: return unavailable(ScoreStatus.BASELINE_UNAVAILABLE)
        if (!vector.isUsable(model.config)) return unavailable(ScoreStatus.INVALID_WINDOW)
        val delta = vector.values - model.mean
        val md2 = squaredDistance(delta, model.cholesky) ?: return unavailable(ScoreStatus.NUMERICAL_FAILURE)
        return MahalanobisScore(vector, ScoreStatus.AVAILABLE, md2, sqrt(md2), model.thresholdMd2, delta, (0..2).map { direction(delta[it], model.config.nearTolerance) })
    }
}

