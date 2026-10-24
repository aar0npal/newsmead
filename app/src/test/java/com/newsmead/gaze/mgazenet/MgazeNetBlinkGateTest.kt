package com.newsmead.gaze.mgazenet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MgazeNetBlinkGateTest {
    private fun eyes(left: Double = .08, right: Double = left) =
        MgazeNetEyelidGeometry.Sample(left, right)

    @Test fun startupRequiresTwoClearlyOpenCapturesAndEitherClosedEyeBlocks() {
        val gate = MgazeNetBlinkGate()
        assertEquals("blink", gate.evaluate(0.0, eyes()))
        assertNull(gate.evaluate(1.0, eyes()))
        assertEquals("blink", gate.evaluate(2.0, eyes(.039, .08)))
        assertEquals("blink", gate.evaluate(3.0, eyes()))
        assertNull(gate.evaluate(4.0, eyes()))
        assertEquals("blink", gate.evaluate(5.0, eyes(.08, .039)))
    }

    @Test fun thresholdEqualityAndDeadbandPreserveOpenButCannotReopen() {
        val gate = MgazeNetBlinkGate()
        assertEquals("blink", gate.evaluate(0.0, eyes(.055)))
        assertEquals("blink", gate.evaluate(1.0, eyes(.04)))
        assertEquals("blink", gate.evaluate(2.0, eyes()))
        assertEquals("blink", gate.evaluate(3.0, eyes(.08, .055)))
        assertEquals("blink", gate.evaluate(4.0, eyes()))
        assertNull(gate.evaluate(5.0, eyes()))
        assertNull(gate.evaluate(6.0, eyes(.04)))
        assertNull(gate.evaluate(7.0, eyes(.05)))
        assertNull(gate.evaluate(8.0, eyes(.055)))
    }

    @Test fun missingOrInvalidRatiosCloseGateAndBreakReopenSequence() {
        for (invalid in listOf(null, eyes(-.01), eyes(.08, -.01), eyes(Double.NaN),
            eyes(.08, Double.POSITIVE_INFINITY), eyes(Double.NEGATIVE_INFINITY))) {
            val gate = MgazeNetBlinkGate()
            gate.evaluate(0.0, eyes())
            assertNull(gate.evaluate(1.0, eyes()))
            assertEquals("eye_state_unavailable", gate.evaluate(2.0, invalid))
            assertEquals("blink", gate.evaluate(3.0, eyes()))
            assertEquals("eye_state_unavailable", gate.evaluate(4.0, invalid))
            assertEquals("blink", gate.evaluate(5.0, eyes()))
            assertNull(gate.evaluate(6.0, eyes()))
        }
    }

    @Test fun invalidClocksDoNotAdvanceClockAndBreakPendingReopen() {
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0, 10.0, 9.0)) {
            val gate = MgazeNetBlinkGate()
            assertEquals("blink", gate.evaluate(10.0, eyes()))
            assertEquals("invalid_clock", gate.evaluate(invalid, eyes()))
            assertEquals("blink", gate.evaluate(11.0, eyes()))
            assertNull(gate.evaluate(12.0, eyes()))
        }
    }

    @Test fun invalidClockWithClosedEyesDoesNotUpdateAcceptedOpenState() {
        val gate = MgazeNetBlinkGate()
        gate.evaluate(10.0, eyes())
        assertNull(gate.evaluate(11.0, eyes()))
        assertEquals("invalid_clock", gate.evaluate(11.0, eyes(0.0)))
        assertNull(gate.evaluate(12.0, eyes(.05)))
    }

    @Test fun missingEyeCaptureAdvancesClockAndResetRestartsBothStateAndClock() {
        val gate = MgazeNetBlinkGate()
        assertEquals("eye_state_unavailable", gate.evaluate(10.0, null))
        assertEquals("invalid_clock", gate.evaluate(10.0, eyes()))
        gate.evaluate(11.0, eyes())
        assertNull(gate.evaluate(12.0, eyes()))
        gate.reset()
        assertEquals("blink", gate.evaluate(0.0, eyes()))
        assertNull(gate.evaluate(1.0, eyes()))
        gate.reset()
        assertEquals("blink", gate.evaluate(0.0, eyes(.05)))
    }

    @Test fun configuredThresholdsAndReopenCountAreApplied() {
        val gate = MgazeNetBlinkGate(closeThreshold = .1, openThreshold = .2, reopenFrames = 3)
        assertEquals("blink", gate.evaluate(0.0, eyes(.3)))
        assertEquals("blink", gate.evaluate(1.0, eyes(.3)))
        assertNull(gate.evaluate(2.0, eyes(.3)))
        assertEquals("blink", gate.evaluate(3.0, eyes(.09)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidThresholdOrder() { MgazeNetBlinkGate(.1, .1) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonfiniteThreshold() { MgazeNetBlinkGate(openThreshold = Double.NaN) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeThreshold() { MgazeNetBlinkGate(closeThreshold = -.01) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonpositiveReopenCount() { MgazeNetBlinkGate(reopenFrames = 0) }
}
