package com.newsmead.gaze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GazeMapperTest {

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
    fun mapsCalibrationPointsInsideRange() {
        val mapper = GazeMapper(grid())
        // Ridge bias keeps this from being exact; it must stay small relative
        // to the 267px grid spacing.
        for (s in grid()) {
            val p = mapper.map(s.gazeX, s.gazeY)
            assertEquals("x at (${s.gazeX}, ${s.gazeY})", s.screenX, p[0], 80f)
            assertEquals("y at (${s.gazeX}, ${s.gazeY})", s.screenY, p[1], 80f)
        }
    }

    @Test
    fun extendsBeyondCalibratedRangeInsteadOfSaturating() {
        val mapper = GazeMapper(grid())
        val atBoundary = mapper.map(0.5f, 0.9f)   // bottom edge of calibrated range
        val beyond = mapper.map(0.5f, 0.95f)      // looking lower than any dot captured
        // The old hard clamp returned identical outputs here (the "invisible
        // barrier"). The gradient extension must keep moving downward by
        // roughly the local px-per-feature slope (~2500 px/unit * 0.05).
        assertTrue(
            "beyond=${beyond[1]} boundary=${atBoundary[1]}",
            beyond[1] > atBoundary[1] + 50f,
        )

        val aboveBoundary = mapper.map(0.5f, 0.1f)
        val aboveBeyond = mapper.map(0.5f, 0.05f)
        assertTrue(
            "aboveBeyond=${aboveBeyond[1]} aboveBoundary=${aboveBoundary[1]}",
            aboveBeyond[1] < aboveBoundary[1] - 50f,
        )
    }

    @Test
    fun extrapolationIsCapped() {
        val mapper = GazeMapper(grid())
        // Far beyond the cap, wildly different inputs must map identically -
        // a garbage feature value cannot fling the estimate to infinity.
        val far = mapper.map(0.5f, 5f)
        val farther = mapper.map(0.5f, 50f)
        assertEquals(far[1], farther[1], 0.5f)
        // And the capped output stays within one screen-height of the edge.
        assertTrue("capped y=${far[1]}", far[1] < 2000f * 2.5f)
    }
}
