package com.newsmead.gaze

import com.newsmead.gaze.mahalanobis.FeatureQuality
import com.newsmead.gaze.mahalanobis.MahalanobisConfig
import org.junit.Assert.*
import org.junit.Test

class RollingReadingFeaturesTest {
    private val config = MahalanobisConfig()
    private fun score(t: Long, r: Int, d: Long, f: Int) = ReadingStateInferencer.RsiScore(
        0.0, 0.0, 0.0, 0.0, f, d, r, t.coerceAtLeast(1), t)

    @Test fun latestTenSecondsUpdateEveryHalfSecond() {
        val rolling = RollingReadingFeatures(config)
        var last = rolling.tick(0)!!
        rolling.onScore(score(0, 0, 0, 0))
        for (t in 0L..10_500L step 500L) {
            rolling.recordQuality(t, true)
            rolling.onScore(if (t < 10_000) score(t, 0, 0, 0) else if (t == 10_000L)
                score(t, 1, 2_000, 2) else score(t, 2, 2_500, 3))
            last = rolling.tick(t) ?: last
            if (t == 9_500L) assertEquals(FeatureQuality.INSUFFICIENT, last.quality)
            if (t == 10_000L) {
                assertEquals(FeatureQuality.VALID, last.quality)
                assertEquals(0L, last.windowStartMs)
                assertEquals(6.0, last.values.regressionRatePerMinute, 1e-12)
                assertEquals(.2, last.values.dwellFraction, 1e-12)
                assertEquals(12.0, last.values.fixationRatePerMinute, 1e-12)
            }
        }
        assertEquals(500L, last.windowStartMs)
        assertEquals(10_500L, last.windowEndMs)
        assertEquals(12.0, last.values.regressionRatePerMinute, 1e-12)
        assertEquals(.25, last.values.dwellFraction, 1e-12)
        assertEquals(18.0, last.values.fixationRatePerMinute, 1e-12)
        assertEquals(last.totalSampleCount, last.validSampleCount)
    }

    @Test fun invalidCurrentWindowNeverLooksLikeZeroDeviation() {
        val rolling = RollingReadingFeatures(config)
        for (t in 0L..10_000L step 500L) {
            rolling.recordQuality(t, true)
            rolling.onScore(score(t, 0, 0, 0))
            rolling.tick(t)
        }
        rolling.recordQuality(10_500, false)
        val invalid = rolling.tick(10_500)!!
        assertEquals(FeatureQuality.MISSING, invalid.quality)
        assertFalse(invalid.isUsable(config))
        assertTrue(invalid.validFraction < 1.0)
    }

    @Test fun scrollBreakKeepsRollingTenSecondWindow() {
        val pipeline = ReadingFeaturePipeline(config)
        for (t in 0L..10_000L step 500L) {
            pipeline.sample(TextTarget(0, 2), true, t)
            if (t == 5_000L) pipeline.breakContinuity()
            val vector = pipeline.tick(t)!!
            if (t == 10_000L) {
                assertEquals(0L, vector.windowStartMs)
                assertEquals(FeatureQuality.VALID, vector.quality)
            }
        }
    }
}
