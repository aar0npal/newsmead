package com.newsmead.gaze.mgazenet

import org.junit.Assert.*
import org.junit.Test

class MgazeNetPostFitTimingTest {
    @Test fun slowButEligibleFramesGetFourSecondsWithoutRelaxingTheQualityGate() {
        val start = 1_000.0
        val captures = (0..15).map { start + MgazeNetPostFitTiming.SETTLE_MS + it * 250.0 }
        assertEquals(10, captures.count { it < start + 5_500.0 })
        val retained = captures.count { MgazeNetPostFitTiming.accepts(start, it, it + 100.0) }
        assertEquals(16, retained)
        assertTrue(retained >= MgazeNetCalibrationQualityGate.MIN_SAMPLES_PER_CHECK)
        assertFalse(MgazeNetPostFitTiming.accepts(start, start + 2_999.0, start + 3_099.0))
        assertFalse(MgazeNetPostFitTiming.accepts(start, start + 7_000.0, start + 7_100.0))
        assertFalse(MgazeNetPostFitTiming.accepts(start, start + 6_999.0, start + 7_250.0))
        assertFalse(MgazeNetPostFitTiming.finished(start, start + 7_249.0))
        assertTrue(MgazeNetPostFitTiming.finished(start, start + 7_250.0))
    }

    @Test fun ordinaryCheckExtendsAndRetriesOnlyForInsufficientSamples() {
        val start = 1_000.0
        val timing = MgazeNetPostFitTiming
        assertEquals(MgazeNetPostFitTiming.Decision.WAIT, timing.decision(start, start + 7_249.0, 10, 1, true))
        assertEquals(MgazeNetPostFitTiming.Decision.EXTEND, timing.decision(start, start + 7_250.0, 10, 1, true))
        assertTrue(timing.accepts(start, start + 9_000.0, start + 9_100.0, true))
        assertEquals(MgazeNetPostFitTiming.Decision.COMPLETE, timing.decision(start, start + 9_100.0, 12, 1, true))
        assertFalse(timing.accepts(start, start + 11_000.0, start + 11_100.0, true))
        assertEquals(MgazeNetPostFitTiming.Decision.RETRY, timing.decision(start, start + 11_250.0, 11, 1, true))
        assertEquals(MgazeNetPostFitTiming.Decision.FAILED, timing.decision(start, start + 11_250.0, 11, 2, true))
    }

    @Test fun comparisonCheckKeepsTheExistingFixedWindowAndQualityDecision() {
        val start = 1_000.0
        val timing = MgazeNetPostFitTiming
        assertEquals(MgazeNetPostFitTiming.Decision.COMPLETE, timing.decision(start, start + 7_250.0, 10, 1, false))
        assertFalse(timing.accepts(start, start + 7_000.0, start + 7_100.0, false))
        assertTrue(timing.accepts(start, start + 6_999.0, start + 7_249.0, false))
    }
}
