package com.newsmead.gaze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingVerticalAlignmentTest {
    private val targets = listOf(450f, 1_100f, 1_750f)

    private fun aggregates(observed: List<Float>, samples: Int = 35) =
        targets.indices.map { index ->
            ReadingVerticalReferenceAggregate(
                reference = ReadingVerticalReference("reference_${index + 1}", 540f, targets[index]),
                sampleCount = samples,
                observedMedianY = observed[index],
            )
        }

    @Test
    fun recoversCompressedVerticalRange() {
        val observed = targets.map { 280f + 0.70f * it }
        val fit = ReadingVerticalAlignment.fit(aggregates(observed), lineHeightPx = 130f)

        assertTrue(fit.reason, fit.accepted)
        val correction = requireNotNull(fit.correction)
        assertEquals(1.0 / 0.70, correction.gain, 0.001)
        assertEquals(-280.0 / 0.70, correction.interceptPx, 0.5)
        targets.indices.forEach { index ->
            assertEquals(targets[index], correction.applyY(observed[index]), 0.5f)
        }
    }

    @Test
    fun acceptsIdentityWithoutInventingCorrection() {
        val fit = ReadingVerticalAlignment.fit(aggregates(targets), lineHeightPx = 130f)
        assertTrue(fit.reason, fit.accepted)
        assertEquals(1.0, requireNotNull(fit.correction).gain, 0.001)
    }

    @Test
    fun rejectsNonMonotonicReferences() {
        val fit = ReadingVerticalAlignment.fit(
            aggregates(listOf(700f, 1_300f, 1_100f)),
            lineHeightPx = 130f,
        )
        assertFalse(fit.accepted)
        assertEquals("observed_order_not_monotonic", fit.reason)
    }

    @Test
    fun rejectsImplausibleGain() {
        val observed = targets.map { 900f + 0.25f * it }
        val fit = ReadingVerticalAlignment.fit(aggregates(observed), lineHeightPx = 130f)
        assertFalse(fit.accepted)
        assertEquals("gain_out_of_bounds", fit.reason)
    }

    @Test
    fun rejectsNonlinearMiddleReference() {
        val fit = ReadingVerticalAlignment.fit(
            aggregates(listOf(450f, 1_430f, 1_750f)),
            lineHeightPx = 130f,
        )
        assertFalse(fit.accepted)
        assertTrue(
            fit.reason == "fit_residual_too_large" ||
                fit.reason == "leave_one_out_error_too_large",
        )
    }

    @Test
    fun rejectsInsufficientSamples() {
        val fit = ReadingVerticalAlignment.fit(
            aggregates(targets, ReadingVerticalAlignment.MIN_SAMPLES_PER_REFERENCE - 1),
            lineHeightPx = 130f,
        )
        assertFalse(fit.accepted)
        assertEquals("insufficient_samples", fit.reason)
    }

    @Test
    fun aggregateUsesMedianAndDropsNonFiniteValues() {
        val reference = ReadingVerticalReference("top", 540f, 450f)
        val result = ReadingVerticalAlignment.aggregate(
            reference,
            listOf(300f, Float.NaN, 320f, 310f, Float.POSITIVE_INFINITY),
        )
        requireNotNull(result)
        assertEquals(3, result.sampleCount)
        assertEquals(310f, result.observedMedianY, 0.001f)
    }
}
