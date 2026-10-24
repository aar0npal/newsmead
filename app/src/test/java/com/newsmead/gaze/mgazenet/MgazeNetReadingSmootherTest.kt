package com.newsmead.gaze.mgazenet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MgazeNetReadingSmootherTest {
    @Test fun suppressesStationaryPixelJitterAtArticleFrameRate() {
        val smoother = MgazeNetReadingSmoother()
        val raw = ArrayList<Float>()
        val filtered = ArrayList<Float>()

        repeat(48) { index ->
            val value = 500f + if (index % 2 == 0) -60f else 60f
            raw += value
            filtered += smoother.filterX(value,index * 42L)
        }

        val rawRange = raw.takeLast(24).maxOrNull()!! - raw.takeLast(24).minOrNull()!!
        val filteredRange = filtered.takeLast(24).maxOrNull()!! - filtered.takeLast(24).minOrNull()!!
        assertTrue(filteredRange < rawRange * 0.2f)
    }

    @Test fun remainsResponsiveToDeliberateMovement() {
        val smoother = MgazeNetReadingSmoother()
        repeat(12) { index -> smoother.filterX(500f,index * 42L) }

        var output = 500f
        repeat(3) { index -> output = smoother.filterX(1000f,(12 + index) * 42L) }

        assertTrue(output > 950f)
    }

    @Test fun resetDoesNotBridgeAnUnavailableInterval() {
        val smoother = MgazeNetReadingSmoother()
        smoother.filterX(200f,0L)
        smoother.filterX(220f,42L)
        smoother.reset()

        assertEquals(900f,smoother.filterX(900f,1000L),0f)
        assertEquals(1400f,smoother.filterY(1400f,1000L),0f)
    }
}
