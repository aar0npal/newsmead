package com.newsmead.gaze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailedTelemetryModeTest {

    @Test
    fun modesHaveStableArtifactLabels() {
        assertTrue(DetailedTelemetryMode.ON.enabled)
        assertEquals("detailed_on", DetailedTelemetryMode.ON.logLabel)
        assertFalse(DetailedTelemetryMode.OFF.enabled)
        assertEquals("detailed_off", DetailedTelemetryMode.OFF.logLabel)
    }

    @Test
    fun fpsAccumulatorKeepsConstantSizeSummaryAndIgnoresInvalidValues() {
        val accumulator = FpsSummaryAccumulator()
        accumulator.add(Float.NaN)
        accumulator.add(0f)
        assertNull(accumulator.snapshot())

        accumulator.add(20f)
        accumulator.add(30f)
        accumulator.add(25f)

        val summary = accumulator.snapshot()!!
        assertEquals(3, summary.sampleCount)
        assertEquals(25f, summary.mean, 0.001f)
        assertEquals(20f, summary.min, 0.001f)
        assertEquals(30f, summary.max, 0.001f)

        accumulator.reset()
        assertNull(accumulator.snapshot())
    }
}
