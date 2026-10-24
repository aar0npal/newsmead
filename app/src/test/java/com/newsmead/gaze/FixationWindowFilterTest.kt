package com.newsmead.gaze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FixationWindowFilterTest {

    private val filter = FixationWindowFilter()

    /** Tight cluster at (x, y) with deterministic alternating jitter. */
    private fun steady(
        n: Int,
        startMs: Long,
        x: Float,
        y: Float,
        stepMs: Long = 33,
        jitter: Float = 0.001f,
    ): List<FixationWindowFilter.Sample> = (0 until n).map { i ->
        val sign = if (i % 2 == 0) 1f else -1f
        FixationWindowFilter.Sample(startMs + i * stepMs, x + sign * jitter, y + sign * jitter)
    }

    @Test
    fun acceptsSteadyFixation() {
        val result = filter.filter(steady(30, 0, 0.5f, 0.3f))
        assertEquals(FixationWindowFilter.Status.ACCEPTED, result.status)
        assertEquals(0.5f, result.medianX, 0.005f)
        assertEquals(0.3f, result.medianY, 0.005f)
        assertTrue(result.retainedCount >= 10)
        // Lead-in (first 120ms = 4 samples at 33ms) must have been discarded.
        assertTrue(result.retainedCount <= 26)
    }

    @Test
    fun rejectsSaccadeSpikeViaMad() {
        val samples = steady(30, 0, 0.5f, 0.3f).toMutableList()
        samples[15] = FixationWindowFilter.Sample(15 * 33L, 0.9f, 0.8f)
        val result = filter.filter(samples)
        assertEquals(FixationWindowFilter.Status.ACCEPTED, result.status)
        assertEquals(0.5f, result.medianX, 0.005f)
        assertEquals(0.3f, result.medianY, 0.005f)
        // 26 samples survive the lead-in trim; the spike is MAD-rejected.
        assertEquals(25, result.retainedCount)
    }

    @Test
    fun aggregatesPerEyeFeaturesUsingTheSameRetainedSamples() {
        val samples = steady(30, 0, 0.5f, 0.3f).map { sample ->
            sample.copy(
                eye1X = sample.x - 0.04f,
                eye1Y = sample.y - 0.03f,
                eye2X = sample.x + 0.04f,
                eye2Y = sample.y + 0.03f,
            )
        }.toMutableList()
        samples[15] = FixationWindowFilter.Sample(
            timestampMs = 15 * 33L,
            x = 0.9f,
            y = 0.8f,
            eye1X = 9f,
            eye1Y = 9f,
            eye2X = 9f,
            eye2Y = 9f,
        )

        val result = filter.filter(samples)

        assertEquals(FixationWindowFilter.Status.ACCEPTED, result.status)
        assertEquals(25, result.retainedCount)
        assertEquals(0.46f, result.medianEye1X, 0.005f)
        assertEquals(0.27f, result.medianEye1Y, 0.005f)
        assertEquals(0.54f, result.medianEye2X, 0.005f)
        assertEquals(0.33f, result.medianEye2Y, 0.005f)
    }

    @Test
    fun tooFewSamples() {
        val result = filter.filter(steady(5, 0, 0.5f, 0.3f))
        assertEquals(FixationWindowFilter.Status.TOO_FEW_SAMPLES, result.status)
    }

    @Test
    fun emptyInput() {
        val result = filter.filter(emptyList())
        assertEquals(FixationWindowFilter.Status.TOO_FEW_SAMPLES, result.status)
        assertEquals(0, result.rawCount)
    }

    @Test
    fun acceptsNoisyFixationAboveOldDispersionGate() {
        // Vertical feature noise on this tracker is ~0.05-0.10 - well above the
        // removed 0.02 gate. A noisy but unbiased fixation must still be ACCEPTED
        // (the fix for the always-redo/exclusion regression), with the median
        // centred on the true point and the noise reported as dispersion.
        val samples = (0 until 30).map { i ->
            val sign = if (i % 2 == 0) 1f else -1f
            FixationWindowFilter.Sample(i * 33L, 0.5f + sign * 0.05f, 0.3f + sign * 0.08f)
        }
        val result = filter.filter(samples)
        assertEquals(FixationWindowFilter.Status.ACCEPTED, result.status)
        assertEquals(0.5f, result.medianX, 0.02f)
        assertEquals(0.3f, result.medianY, 0.02f)
        assertTrue("dispersion reported: ${result.dispersionY}", result.dispersionY > 0.02f)
    }
}
