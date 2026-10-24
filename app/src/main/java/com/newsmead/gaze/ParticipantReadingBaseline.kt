package com.newsmead.gaze

import com.newsmead.gaze.mahalanobis.FrozenMahalanobisProfile

/** Session-scoped: app restart, participant/session change or recalibration requires a new baseline. */
object ParticipantReadingBaseline {
    data class Entry(val participantCode: String, val researchSessionId: String, val calibrationFingerprint: String,
        val profile: FrozenMahalanobisProfile, val baselineLogName: String)
    private var entry: Entry? = null
    fun freeze(value: Entry) { check(entry == null) { "Baseline already frozen for this session" }; entry = value }
    fun current(sessionId: String?, fingerprint: String?): Entry? = entry?.takeIf {
        it.researchSessionId == sessionId && it.calibrationFingerprint == fingerprint
    }
    fun beginNewBaseline() { entry = null }
}
