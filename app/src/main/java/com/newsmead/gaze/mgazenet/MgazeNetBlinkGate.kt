package com.newsmead.gaze.mgazenet

/** Capture-ordered hysteresis guard. Thresholds are trial tuning, not validated blink labels. */
internal class MgazeNetBlinkGate(
    private val closeThreshold: Double = CLOSE_THRESHOLD,
    private val openThreshold: Double = OPEN_THRESHOLD,
    private val reopenFrames: Int = REOPEN_FRAMES,
) {
    init {
        require(closeThreshold.isFinite() && closeThreshold >= 0.0)
        require(openThreshold.isFinite() && openThreshold > closeThreshold)
        require(reopenFrames > 0)
    }

    private var lastCaptureMs = Double.NEGATIVE_INFINITY
    private var blocked = true
    private var openCount = 0

    /** Null permits this capture. Each non-null reason withholds this capture. */
    fun evaluate(captureMs: Double, eyes: MgazeNetEyelidGeometry.Sample?): String? {
        if (!captureMs.isFinite() || captureMs < 0.0 || captureMs <= lastCaptureMs) {
            openCount = 0
            return "invalid_clock"
        }
        lastCaptureMs = captureMs
        if (eyes == null || !eyes.leftAspect.isFinite() || !eyes.rightAspect.isFinite() ||
            eyes.leftAspect < 0.0 || eyes.rightAspect < 0.0) {
            blocked = true
            openCount = 0
            return "eye_state_unavailable"
        }
        if (eyes.leftAspect < closeThreshold || eyes.rightAspect < closeThreshold) {
            blocked = true
            openCount = 0
        } else if (blocked) {
            if (eyes.leftAspect > openThreshold && eyes.rightAspect > openThreshold) {
                openCount++
                if (openCount >= reopenFrames) {
                    blocked = false
                    openCount = 0
                }
            } else {
                openCount = 0
            }
        }
        return if (blocked) "blink" else null
    }

    fun reset() {
        lastCaptureMs = Double.NEGATIVE_INFINITY
        blocked = true
        openCount = 0
    }

    companion object {
        const val VERSION = "EYELID_RATIO_HYSTERESIS_V1"
        const val CLOSE_THRESHOLD = .04
        const val OPEN_THRESHOLD = .055
        const val REOPEN_FRAMES = 2
    }
}
