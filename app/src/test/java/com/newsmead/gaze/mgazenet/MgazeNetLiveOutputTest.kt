package com.newsmead.gaze.mgazenet

import org.junit.Assert.*
import org.junit.Test

class MgazeNetLiveOutputTest {
    private val open = MgazeNetEyelidGeometry.Sample(.12, .12)
    private val shut = MgazeNetEyelidGeometry.Sample(.02, .02)
    private fun reading() = MgazeNetLiveOutput(1000, 2000, smoothForReading = true, rejectBlinks = true)
    private fun output(p: MgazeNetLiveOutput, at: Double, eyes: MgazeNetEyelidGeometry.Sample? = open,
                       prediction: FloatArray? = floatArrayOf(.5f, .5f), eligible: Boolean = true,
                       reason: String = "eye_area") = p.evaluate(at, at + 10.0, eligible, prediction, reason, eyes)

    @Test fun closedEyePredictionsNeverReachTheDotOrSelectedReadingPoint() {
        val p = reading()
        assertEquals("blink", output(p, 100.0).reason)
        assertTrue(output(p, 140.0).emitted)
        val blocked = output(p, 180.0, shut, floatArrayOf(.9f, .99f))
        assertEquals("blink", blocked.reason)
        assertNull(blocked.x); assertNull(blocked.y); assertFalse(blocked.emitted)
        // Untrusted model predictions remain identifiable as raw diagnostics only.
        assertEquals(900f, blocked.rawX!!, .001f)
        assertEquals(1980f, blocked.rawY!!, .001f)
        assertEquals("blink", output(p, 220.0, shut).reason)
        val opening = output(p, 260.0, open, floatArrayOf(.01f, .99f))
        assertFalse(opening.emitted); assertNull(opening.x); assertNull(opening.y)
        val restored = output(p, 300.0, open, floatArrayOf(.3f, .2f))
        assertTrue(restored.emitted)
        assertEquals(300f, restored.x!!, .001f)
        assertEquals(400f, restored.y!!, .001f) // Closed/reopening predictions never entered the filters.
    }

    @Test fun oneClosedEyeAndUnmeasurableLidsAreAlsoExcluded() {
        val p = reading()
        output(p, 100.0); output(p, 140.0)
        assertEquals("blink", output(p, 180.0, MgazeNetEyelidGeometry.Sample(.12, .02)).reason)
        val unavailable = output(p, 220.0, null)
        assertEquals("eye_state_unavailable", unavailable.reason)
        assertNull(unavailable.x); assertNull(unavailable.y)
        assertFalse(output(p, 260.0).emitted)
        assertTrue(output(p, 300.0).emitted)
    }

    @Test fun knownClosedSmallEyeAreasAreBlinksWhileOtherSourceFailuresKeepTheirReason() {
        val p = reading()
        output(p, 100.0); output(p, 140.0)
        assertEquals("blink", output(p, 180.0, shut, eligible = false).reason)
        assertEquals("no_face", output(p, 220.0, null, null, false, "no_face").reason)
        assertEquals("invalid_crops", output(p, 260.0, null, null, false, "invalid_crops").reason)
        assertFalse(output(p, 300.0).emitted)
        assertTrue(output(p, 340.0).emitted)
    }

    @Test fun duplicateCaptureCannotReopenAndExpiryNeverReplaysAPoint() {
        val p = reading()
        output(p, 100.0, shut)
        assertFalse(output(p, 140.0).emitted)
        assertEquals("invalid_clock", output(p, 140.0).reason)
        assertFalse(output(p, 180.0).emitted)
        assertTrue(output(p, 220.0).emitted)
        val stale = p.evaluate(260.0, 761.0, true, floatArrayOf(.5f, .5f), "eye_area", open)
        assertEquals("stale", stale.reason); assertNull(stale.x); assertNull(stale.y)
        p.invalidate()
        assertEquals("invalid_clock", output(p, 260.0).reason)
        assertFalse(output(p, 300.0).emitted)
        assertTrue(output(p, 340.0).emitted)
    }

    @Test fun expiredOpenEyeFrameAdvancesBlinkRecoveryWithoutEmittingItsCoordinate() {
        val p = reading()
        assertEquals("blink", output(p, 100.0, shut).reason)
        val expired = p.evaluate(140.0, 800.0, true, floatArrayOf(.9f, .9f), "eye_area", open)
        assertEquals("stale", expired.reason)
        assertNull(expired.x)
        assertNull(expired.y)
        assertTrue(output(p, 180.0, open).emitted)
    }

    @Test fun deliveryGapDoesNotRequireTwoNewOpenFramesButStillRejectsClosedEyes() {
        val p = reading()
        assertEquals("blink", output(p, 100.0).reason)
        assertTrue(output(p, 140.0).emitted)
        p.expire()
        assertTrue(output(p, 800.0, open).emitted)
        p.expire()
        assertEquals("blink", output(p, 1_500.0, shut).reason)
        assertFalse(output(p, 1_540.0, open).emitted)
        assertTrue(output(p, 1_580.0, open).emitted)
    }

    @Test fun optOutRetainsTheFrozenRawOutputAndOptionalSmoothingContracts() {
        val raw = MgazeNetLiveOutput(1000, 2000, smoothForReading = false, rejectBlinks = false)
        val first = output(raw, 100.0, null, floatArrayOf(-.1f, 1.1f))
        assertTrue(first.emitted); assertEquals(-100f, first.x!!, .001f)
        assertEquals(2200f, first.y!!, .001f)
        assertTrue(output(raw, 140.0, shut).emitted)
        val smoothed = MgazeNetLiveOutput(1000, 2000, smoothForReading = true, rejectBlinks = false)
        assertTrue(output(smoothed, 100.0, null).emitted)
        assertTrue(output(smoothed, 140.0, shut).emitted)
    }
}
