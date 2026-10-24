package com.newsmead.gaze.reading

import org.junit.Assert.*
import org.junit.Test

class ConsultationCaptureGateTest {
    @Test fun consultationAndLateFramesStayOutsideArticleSequence() {
        val gate = ConsultationCaptureGate()
        assertNull(gate.exclusion(99.0))
        gate.change(true, 100.0)
        assertEquals("definition_boundary_late_capture", gate.exclusion(99.9))
        assertEquals("definition_consultation", gate.exclusion(100.0))
        assertEquals("definition_consultation", gate.exclusion(200.0))
        gate.change(false, 300.0)
        assertEquals("definition_boundary_late_capture", gate.exclusion(250.0))
        assertNull(gate.exclusion(300.0))
        gate.change(true, 400.0)
        assertEquals("definition_boundary_late_capture", gate.exclusion(350.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun boundaryClockCannotMoveBackwards() {
        val gate = ConsultationCaptureGate()
        gate.change(true, 100.0)
        gate.change(false, 99.0)
    }
}
