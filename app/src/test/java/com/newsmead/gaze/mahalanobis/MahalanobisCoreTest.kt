package com.newsmead.gaze.mahalanobis

import org.junit.Assert.*
import org.junit.Test

class MahalanobisCoreTest {
    private fun config(lambda: Double = 0.0) = MahalanobisConfig(minimumBaselineDurationMs = 0, minimumRetainedWindows = 6, lambda = lambda)
    private fun vector(t: Long, r: Double = 10.0, d: Double = .5, f: Double = 100.0) = RdfFeatureVector(t, t - 10_000, t, RdfValues(r, d, f))
    private fun axisVectors() = listOf(vector(10_000, 9.0), vector(10_500, 11.0), vector(11_000, d = .4), vector(11_500, d = .6), vector(12_000, f = 90.0), vector(12_500, f = 110.0))
    private fun fit(c: MahalanobisConfig = config(), vectors: List<RdfFeatureVector> = axisVectors()): BaselineFit {
        val collector = MahalanobisBaselineCollector(c)
        vectors.forEach { collector.add(it) }
        return collector.freeze()
    }
    @Test fun meanCovarianceDistancesAndTwoSidedDirections() {
        val p = fit().profile!!
        assertEquals(RdfValues(10.0, .5, 100.0), p.mean)
        assertEquals(.4, p.covariance.m00, 1e-12)
        assertEquals(.004, p.covariance.m11, 1e-12)
        assertEquals(40.0, p.covariance.m22, 1e-12)
        val scorer = MahalanobisScorer(p)
        assertEquals(0.0, scorer.score(vector(20_000)).md2!!, 1e-20)
        val above = scorer.score(vector(20_000, 12.0))
        val below = scorer.score(vector(20_000, 8.0))
        assertEquals(10.0, above.md2!!, 1e-10)
        assertEquals(above.md2!!, below.md2!!, 1e-10)
        assertEquals(Direction.ABOVE_BASELINE, above.directions!![0])
        assertEquals(Direction.BELOW_BASELINE, below.directions!![0])
        assertEquals(Direction.NEAR_BASELINE, above.directions!![1])
        assertEquals(Direction.NEAR_BASELINE, scorer.score(vector(20_000, 10.0 + 5e-10)).directions!![0])
        assertEquals(above.md2!!, above.md!! * above.md!!, 1e-10)
    }
    @Test fun unequalScalesAndCorrelationsUseFullCovariance() {
        val vs = axisVectors().map { it.copy(values = RdfValues(it.values[0], it.values[1], it.values[2] + 2 * (it.values[0] - 10))) }
        val p = fit(vectors = vs).profile!!
        assertEquals(.8, p.covariance.m02, 1e-12)
        assertEquals(p.covariance.m02, p.covariance.m20, 0.0)
        val s = MahalanobisScorer(p).score(vector(20_000, 12.0, f = 104.0))
        assertEquals(10.0, s.md2!!, 1e-10)
    }
    @Test fun singularRegularizesButAllConstantDistributionIsUnavailable() {
        val vs = (0..5).map { vector(10_000 + it * 500L, 10.0 + it, .5, 100.0 + it) }
        assertEquals(BaselineStatus.NUMERICAL_FAILURE, fit(vectors = vs).status)
        assertEquals(BaselineStatus.READY, fit(config(1e-6), vs).status)
        assertEquals(BaselineStatus.DEGENERATE_THRESHOLD, fit(config(1e-6), vs.map { it.copy(values = RdfValues(1.0, .5, 1.0)) }).status)
    }
    @Test fun insufficientInvalidAndFrozenBaseline() {
        assertEquals(BaselineStatus.INSUFFICIENT_WINDOWS, fit(vectors = axisVectors().take(5)).status)
        assertEquals(BaselineStatus.INSUFFICIENT_DURATION, fit(config().copy(minimumBaselineDurationMs = 60_000)).status)
        val collector = MahalanobisBaselineCollector(config())
        axisVectors().forEach { collector.add(it) }
        val result = collector.freeze()
        assertFalse(collector.add(vector(30_000)))
        assertSame(result, collector.freeze())
        val scorer = MahalanobisScorer(result.profile)
        assertEquals(ScoreStatus.INVALID_WINDOW, scorer.score(vector(20_000, Double.NaN)).status)
        assertNull(scorer.score(vector(20_000, Double.NaN)).md2)
        for (bad in listOf(vector(20_000, Double.POSITIVE_INFINITY), vector(20_000, -1.0), vector(20_000, d = 1.01), vector(20_000, f = -1.0), vector(20_000).copy(quality = FeatureQuality.DISCONTINUITY), vector(20_000).copy(validFraction = .5))) {
            assertEquals(ScoreStatus.INVALID_WINDOW, scorer.score(bad).status)
            assertNull(scorer.score(bad).md2)
        }
        assertEquals(ScoreStatus.BASELINE_UNAVAILABLE, MahalanobisScorer(null).score(vector(20_000)).status)
        val badOrder = MahalanobisBaselineCollector(config())
        badOrder.add(vector(20_000)); badOrder.add(vector(19_000))
        assertEquals(BaselineStatus.INVALID_INPUT, badOrder.freeze().status)
    }
    @Test fun quantilesThresholdSelectionSamplingAndWindowBoundaries() {
        assertEquals(2.5, empiricalQuantile(listOf(0.0, 10.0), .25), 0.0)
        for (percentile in listOf(ThresholdPercentile.P90, ThresholdPercentile.P95, ThresholdPercentile.P99)) {
            val p = fit(config().copy(percentile = percentile)).profile!!
            assertEquals(empiricalQuantile(p.baselineMd2, percentile.probability!!), p.thresholdMd2, 0.0)
        }
        assertEquals(7.0, fit(config().copy(percentile = ThresholdPercentile.CUSTOM, customThresholdMd2 = 7.0)).profile!!.thresholdMd2, 0.0)
        assertEquals(0.0, fit(config().copy(percentile = ThresholdPercentile.CUSTOM, customThresholdMd2 = 0.0)).profile!!.thresholdMd2, 0.0)
        val thin = MahalanobisBaselineCollector(config().copy(sampling = BaselineSampling.THINNED_WINDOWS, thinningIntervalMs = 1_000))
        axisVectors().forEach { thin.add(it) }
        assertEquals(3, thin.freeze().sampling.retainedCount)
        val nonoverlap = MahalanobisBaselineCollector(config().copy(sampling = BaselineSampling.NON_OVERLAPPING_WINDOWS))
        axisVectors().forEach { nonoverlap.add(it) }
        assertEquals(1, nonoverlap.freeze().sampling.retainedCount)
        assertTrue(vector(20_000).copy(windowStartMs = 9_500).isUsable(config()))
        assertFalse(vector(20_000).copy(windowStartMs = 9_499).isUsable(config()))
        assertFalse(vector(20_000).copy(windowStartMs = 10_001).isUsable(config()))
        assertFalse(vector(20_000).copy(validSampleCount = 1, totalSampleCount = 2).isUsable(config()))
    }
    private fun score(t: Long, md2: Double?, threshold: Double = 5.0) = MahalanobisScore(vector(t), if (md2 == null) ScoreStatus.INVALID_WINDOW else ScoreStatus.AVAILABLE, md2, null, threshold, null, null)
    @Test fun inclusiveThresholdPersistenceRecoveryAndEpisodes() {
        val controller = MahalanobisTemporalController(config().copy(persistenceMs = 1_000, recoveryMs = 1_000))
        assertEquals(TemporalStatus.PENDING, controller.accept(score(20_000, 5.0)).status)
        assertFalse(controller.accept(score(20_500, 7.0)).active)
        assertTrue(controller.accept(score(21_000, 6.0)).active)
        val low = controller.accept(score(21_500, 4.0))
        assertTrue(low.active)
        assertEquals(HighEpisode(20_000, 21_000, 1_000, 7.0, 6.0, 3, 5.0), low.completedEpisode)
        assertTrue(controller.accept(score(22_000, 4.0)).active)
        assertFalse(controller.accept(score(22_500, 4.0)).active)
    }
    @Test fun gapsInvalidOutOfOrderAndThresholdChangeBreakSupport() {
        for (breakScore in listOf(score(20_500, null), score(22_000, 5.0), score(19_999, 5.0), score(20_500, 5.0, 6.0))) {
            val controller = MahalanobisTemporalController(config().copy(persistenceMs = 1_000))
            controller.accept(score(20_000, 6.0))
            val decision = controller.accept(breakScore)
            assertFalse(decision.active)
            assertEquals(1, decision.completedEpisode!!.sampleCount)
            assertEquals(0L, decision.completedEpisode!!.durationMs)
        }
    }
    @Test fun replayUsesSameScorerAndClosesAtLastObservation() {
        val p = fit().profile!!
        val replay = MahalanobisReplay.run(p, listOf(vector(20_000, 15.0), vector(20_500, 15.0)))
        assertEquals(2, replay.scores.size)
        assertEquals(1, replay.episodes.size)
        assertEquals(500L, replay.episodes.single().durationMs)
    }
    @Test fun baselineDurationUsesIntervalUnionWithExactBoundaryAndNoGapCredit() {
        val overlapping = fit(config().copy(minimumBaselineDurationMs = 12_500))
        assertEquals(BaselineStatus.READY, overlapping.status)
        assertEquals(12_500L, overlapping.sampling.coverageMs)
        assertEquals(BaselineStatus.INSUFFICIENT_DURATION, fit(config().copy(minimumBaselineDurationMs = 12_501)).status)
        val separated = axisVectors().mapIndexed { i, v ->
            val timestamp = 10_000L + i * 20_000L
            v.copy(timestampMs = timestamp, windowStartMs = timestamp - 10_000, windowEndMs = timestamp)
        }
        val exact = fit(config().copy(minimumBaselineDurationMs = 60_000), separated)
        assertEquals(BaselineStatus.READY, exact.status)
        assertEquals(60_000L, exact.sampling.coverageMs)
        assertEquals(BaselineStatus.INSUFFICIENT_DURATION, fit(config().copy(minimumBaselineDurationMs = 60_001), separated).status)
        val collector = MahalanobisBaselineCollector(config())
        axisVectors().forEach { collector.add(it) }
        collector.add(vector(13_000, Double.NaN))
        val result = collector.freeze()
        assertEquals(7, result.sampling.receivedCount)
        assertEquals(6, result.sampling.validCount)
        assertEquals(1, result.sampling.invalidCount)
        assertEquals(12_500L, result.sampling.coverageMs)
    }
}
