package com.newsmead.gaze.mgazenet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MgazeNetTemporalSamplerTest {
    @Test fun exactCountKeepsEveryCandidate() {
        assertArrayEquals(IntArray(45) { it },MgazeNetTemporalSampler.indices(45,45))
    }

    @Test fun surplusCandidatesSpanWholeWindowInOrder() {
        val selected = MgazeNetTemporalSampler.indices(73,45)
        assertEquals(45,selected.size)
        assertEquals(0,selected.first())
        assertEquals(72,selected.last())
        assertEquals(45,selected.distinct().size)
        assertTrue(selected.asList().zipWithNext().all { (a, b) -> b > a && b-a in 1..2 })
    }
}
