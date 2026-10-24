package com.newsmead.gaze.reading

/** Monotonic elapsed milliseconds. Late frames cannot cross a displayed-help boundary. */
class ConsultationCaptureGate {
    var active = false; private set
    private var boundaryMs = Double.NEGATIVE_INFINITY

    fun change(value: Boolean, atMs: Double) {
        require(atMs.isFinite() && atMs >= boundaryMs)
        active = value
        boundaryMs = atMs
    }

    fun exclusion(captureMs: Double?): String? = when {
        captureMs == null || !captureMs.isFinite() -> if (active) "definition_consultation" else null
        captureMs < boundaryMs -> "definition_boundary_late_capture"
        active -> "definition_consultation"
        else -> null
    }
}
