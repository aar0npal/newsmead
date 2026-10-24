package com.newsmead.gaze.aid

import org.junit.Assert.*
import org.junit.Test

class ReadingAidControlGateTest {
    @Test fun nestedMenuTransitionNeverResumesBetweenScopes() {
        val gate = ReadingAidControlGate()
        gate.open("options", 10.0)
        gate.open("aids", 20.0)
        assertFalse(gate.close("options", 21.0))
        assertTrue(gate.active)
        assertTrue(gate.excludes(30.0))
        assertTrue(gate.close("aids", 40.0))
        assertFalse(gate.active)
        assertFalse(gate.excludes(41.0))
    }

    @Test fun cancelRetainsInclusiveCaptureFloorAcrossLaterMenus() {
        val gate = ReadingAidControlGate()
        gate.open("options", 10.0)
        assertTrue(gate.close("options", 20.0))
        assertTrue(gate.excludes(19.0))
        assertTrue(gate.excludes(20.0))
        assertFalse(gate.excludes(20.001))
        gate.open("options", 30.0)
        assertTrue(gate.close("options", 40.0))
        assertTrue(gate.excludes(25.0))
        assertTrue(gate.excludes(40.0))
    }

    @Test fun unknownCapturesUseActiveScopeWithoutInventingMissingData() {
        val gate = ReadingAidControlGate()
        assertFalse(gate.excludes(null))
        gate.open("options", 10.0)
        assertTrue(gate.excludes(null))
        assertTrue(gate.excludes(Double.NaN))
        assertFalse(gate.close("absent", 11.0))
        assertTrue(gate.active)
        assertTrue(gate.close("options", 12.0))
        assertFalse(gate.excludes(null))
        assertFalse(gate.excludes(Double.NaN))
    }

    @Test(expected = IllegalArgumentException::class)
    fun boundariesCannotMoveBackwards() {
        val gate = ReadingAidControlGate()
        gate.open("options", 10.0)
        gate.close("options", 9.0)
    }
}
