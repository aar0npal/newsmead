package com.newsmead.gaze.mgazenet

/** Operational expiry only. Returns every raw finite prediction for observation, never replays it. */
class MgazeNetOutputGate(private val width: Int, private val height: Int,
    private val expiryMs: Double = EXPIRY_MS) {
    init { require(expiryMs.isFinite() && expiryMs > 0.0) }
    data class Result(val reason: String, val rawX: Float?, val rawY: Float?) {
        val emitted get() = reason == "coordinate"
    }
    private var lastCapture = -1.0
    fun evaluate(capture: Double, delivery: Double, eligible: Boolean, prediction: FloatArray?, sourceReason: String): Result {
        val x = prediction?.takeIf { it.size == 2 }?.get(0)?.times(width)?.takeIf(Float::isFinite)
        val y = prediction?.takeIf { it.size == 2 }?.get(1)?.times(height)?.takeIf(Float::isFinite)
        val reason = when {
            !capture.isFinite() || !delivery.isFinite() || capture < 0 || delivery < capture || capture <= lastCapture -> "invalid_clock"
            delivery-capture > expiryMs -> "stale"
            !eligible -> sourceReason
            x == null || y == null -> "invalid_prediction"
            else -> "coordinate"
        }
        if (capture.isFinite() && capture > lastCapture) lastCapture = capture
        return Result(reason,x,y)
    }
    companion object { const val EXPIRY_MS = 500.0 }
}
