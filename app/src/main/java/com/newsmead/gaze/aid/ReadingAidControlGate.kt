package com.newsmead.gaze.aid

/** Main-thread owner; elapsed capture milliseconds share the provider's monotonic time base. */
class ReadingAidControlGate {
    private val tags = mutableSetOf<String>()
    private var boundaryMs = Double.NEGATIVE_INFINITY
    val active: Boolean get() = tags.isNotEmpty()

    fun open(tag: String, atMs: Double) {
        require(tag.isNotBlank() && atMs.isFinite() && atMs >= boundaryMs)
        tags.add(tag)
        boundaryMs = atMs
    }

    /** True only after the final scope closes; callers still check lifecycle/stream identities. */
    fun close(tag: String, atMs: Double): Boolean {
        require(atMs.isFinite() && atMs >= boundaryMs)
        if (!tags.remove(tag)) return false
        boundaryMs = atMs
        return !active
    }

    fun excludes(captureMs: Double?): Boolean = active ||
        (captureMs != null && captureMs.isFinite() && captureMs <= boundaryMs)
}
