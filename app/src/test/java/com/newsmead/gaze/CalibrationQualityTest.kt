package com.newsmead.gaze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationQualityTest {

    /** 4x4 grid on a 1000x2000 screen with an exact linear feature relation. */
    private fun grid(): List<CalibrationSample> {
        val fractions = listOf(0.1f, 0.3667f, 0.6333f, 0.9f)
        return buildList {
            for (fy in fractions) for (fx in fractions) {
                add(CalibrationSample(1000f * fx, 2000f * fy, fx, fy))
            }
        }
    }

    @Test
    fun cleanGridHasSmallLooError() {
        val report = CalibrationQuality.leaveOneOut(grid())
        requireNotNull(report)
        assertEquals(16, report.errorsPx.size)
        assertEquals(16, report.dxPx.size)
        assertEquals(16, report.dyPx.size)
        // Ridge regularization biases the fit slightly, so LOO error is nonzero
        // even on perfect synthetic data - but it must stay small relative to
        // the 267px grid spacing.
        assertTrue("median=${report.medianPx}", report.medianPx < 80f)
        assertTrue("p95=${report.p95Px}", report.p95Px < 200f)
    }

    @Test
    fun corruptedPointIsWorst() {
        val samples = grid().toMutableList()
        val bad = samples[5]
        samples[5] = bad.copy(gazeX = bad.gazeX + 0.2f)
        val report = CalibrationQuality.leaveOneOut(samples)
        requireNotNull(report)
        assertEquals(5, report.worstIndices.first())
        assertTrue(
            "corrupt=${report.errorsPx[5]} median=${report.medianPx}",
            report.errorsPx[5] > report.medianPx * 2,
        )
    }

    @Test
    fun nullWhenTooFewPoints() {
        assertNull(CalibrationQuality.leaveOneOut(grid().take(6)))
    }
}
