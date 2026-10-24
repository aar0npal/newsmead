package com.newsmead.gaze.mgazenet

/** Shared output for the reading dot, observations and aid input; no replay of a hidden point. */
internal class MgazeNetLiveOutput(width: Int, height: Int,
    private val smoothForReading: Boolean, private val rejectBlinks: Boolean,
    outputExpiryMs: Double = MgazeNetOutputGate.EXPIRY_MS) {
    data class Result(val reason: String, val rawX: Float?, val rawY: Float?,
        val x: Float?, val y: Float?) {
        val emitted get() = reason == "coordinate"
    }
    private val outputGate = MgazeNetOutputGate(width, height, outputExpiryMs)
    private val blinkGate = MgazeNetBlinkGate()
    private val smoother = MgazeNetReadingSmoother()

    fun evaluate(captureMs: Double, deliveryMs: Double, eligible: Boolean,
                 prediction: FloatArray?, sourceReason: String,
                 eyes: MgazeNetEyelidGeometry.Sample?): Result {
        val gated = outputGate.evaluate(captureMs, deliveryMs, eligible, prediction, sourceReason)
        // A duplicate capture cannot advance eye state.
        if (gated.reason == "invalid_clock") {
            invalidate()
            return Result(gated.reason, gated.rawX, gated.rawY, null, null)
        }
        // A very small eye area may itself be a measured closure. Preserve no-face/
        // crop failures, while identifying known closed lids as blink rather than look-away.
        val checkEyes = rejectBlinks && (eligible || (sourceReason == "eye_area" && eyes != null))
        val eyeReason = if (checkEyes) blinkGate.evaluate(captureMs, eyes) else null
        if (rejectBlinks && !checkEyes) blinkGate.reset()
        // An expired coordinate remains unusable, but its capture-ordered eyelid
        // measurement can contribute to reopening. Otherwise one slow frame can
        // repeatedly reset the blink gate before two open frames arrive.
        if (gated.reason == "stale") {
            smoother.reset()
            return Result("stale", gated.rawX, gated.rawY, null, null)
        }
        val reason = eyeReason ?: gated.reason
        if (reason != "coordinate") {
            smoother.reset()
            return Result(reason, gated.rawX, gated.rawY, null, null)
        }
        val x = if (smoothForReading) smoother.filterX(gated.rawX!!, captureMs.toLong()) else gated.rawX
        val y = if (smoothForReading) smoother.filterY(gated.rawY!!, captureMs.toLong()) else gated.rawY
        return Result(reason, gated.rawX, gated.rawY, x, y)
    }

    /** A delivery gap invalidates the displayed point, but the next eyelid
     * measurement still determines whether a new coordinate may pass. */
    fun expire() { smoother.reset() }

    /** Stop or invalid clock starts a new capture-ordered eye sequence. */
    fun invalidate() { smoother.reset(); blinkGate.reset() }
}
