package com.newsmead.gaze.mgazenet

/** Post-fit validation uses capture time; output may arrive shortly after the last capture. */
object MgazeNetPostFitTiming {
    const val SETTLE_MS = 3_000.0
    const val CAPTURE_END_MS = 7_000.0
    const val EXTENDED_CAPTURE_END_MS = 11_000.0
    const val OUTPUT_GRACE_MS = 250.0
    const val MAX_ATTEMPTS = 2

    enum class Decision { WAIT, EXTEND, COMPLETE, RETRY, FAILED }

    fun accepts(checkStartMs: Double, captureMs: Double, outputMs: Double,
        allowLocalRetry: Boolean = false): Boolean {
        val endMs = if (allowLocalRetry) EXTENDED_CAPTURE_END_MS else CAPTURE_END_MS
        return checkStartMs.isFinite() && captureMs.isFinite() && outputMs.isFinite() &&
            captureMs >= checkStartMs + SETTLE_MS &&
            captureMs < checkStartMs + endMs &&
            outputMs < checkStartMs + endMs + OUTPUT_GRACE_MS
    }

    fun finished(checkStartMs: Double, nowMs: Double): Boolean =
        checkStartMs.isFinite() && nowMs >= checkStartMs + CAPTURE_END_MS + OUTPUT_GRACE_MS

    /** Comparison runs retain their fixed check; ordinary runs may extend/retry for sample count only. */
    fun decision(checkStartMs: Double, nowMs: Double, usableSamples: Int, attempt: Int,
        allowLocalRetry: Boolean): Decision {
        require(usableSamples >= 0 && attempt in 1..MAX_ATTEMPTS)
        if (!checkStartMs.isFinite() || !nowMs.isFinite() || !finished(checkStartMs, nowMs)) return Decision.WAIT
        if (!allowLocalRetry || usableSamples >= MgazeNetCalibrationQualityGate.MIN_SAMPLES_PER_CHECK) return Decision.COMPLETE
        if (nowMs < checkStartMs + EXTENDED_CAPTURE_END_MS + OUTPUT_GRACE_MS) return Decision.EXTEND
        return if (attempt < MAX_ATTEMPTS) Decision.RETRY else Decision.FAILED
    }
}
