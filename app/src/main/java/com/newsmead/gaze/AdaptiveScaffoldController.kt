package com.newsmead.gaze

import com.newsmead.gaze.mahalanobis.MahalanobisScore
import com.newsmead.gaze.mahalanobis.ScoreStatus
import com.newsmead.gaze.mahalanobis.TemporalResult

enum class ScaffoldLevel { NONE, WORD, LINE, FOCUS, REENTRY }
enum class ScaffoldTargetStatus { INACTIVE, WAITING_FOR_TARGET, FRESH, INVALID, REENTRY }

/** Presentation timing is separate from Mahalanobis scoring and temporal confirmation. */
data class AdaptiveScaffoldConfig(
    val supportLevel: ScaffoldLevel = ScaffoldLevel.LINE,
    val lossOffTextMs: Long = 800,
    val minimumLossLineJump: Int = 2,
    val reentryEvidenceMs: Long = 10_000,
    val targetConfirmMs: Long = 120,
    val targetInvalidGraceMs: Long = 200,
) {
    init {
        require(supportLevel != ScaffoldLevel.NONE && supportLevel != ScaffoldLevel.REENTRY)
        require(lossOffTextMs >= 0 && minimumLossLineJump >= 1 && reentryEvidenceMs >= 0)
        require(targetConfirmMs >= 0 && targetInvalidGraceMs >= 0)
    }
}

data class ScaffoldState(
    val level: ScaffoldLevel = ScaffoldLevel.NONE,
    val currentLine: Int = -1,
    val wordStart: Int = TextTarget.NO_OFFSET,
    val wordEnd: Int = TextTarget.NO_OFFSET,
    val reentryTargetLine: Int = -1,
    val targetStatus: ScaffoldTargetStatus = ScaffoldTargetStatus.INACTIVE,
    val mappedLine: Int = -1,
    val stabilizedLine: Int = -1,
    val targetCaptureMs: Long? = null,
    val confirmationTimestampMs: Long? = null,
    val targetNotBeforeMs: Long? = null,
) {
    val renderedTargetLine: Int
        get() = when (level) {
            ScaffoldLevel.REENTRY -> reentryTargetLine
            ScaffoldLevel.NONE -> -1
            else -> currentLine
        }
}

data class ScaffoldTransition(
    val from: ScaffoldLevel,
    val to: ScaffoldLevel,
    val timestampMs: Long,
    val reason: String,
    val md2: Double?,
    val confidence: Double,
    val currentLine: Int,
    val reentryTargetLine: Int,
)

data class ScaffoldUpdate(
    val state: ScaffoldState,
    val transition: ScaffoldTransition? = null,
    /** Non-null only when target acquisition or presentation state materially changes. */
    val targetReason: String? = null,
)

/**
 * Mahalanobis decides whether support is active. A separate post-confirmation target gate
 * decides where normal spatial support is rendered. A line captured before confirmation
 * can never seed a normal LINE/WORD/FOCUS scaffold. REENTRY remains an explicit independent
 * displaced-return policy and intentionally targets an earlier stable line.
 */
class AdaptiveScaffoldController(
    private val config: AdaptiveScaffoldConfig = AdaptiveScaffoldConfig(),
) {
    private var state = ScaffoldState()
    private var detectorActive = false
    private var confirmationTimestampMs: Long? = null
    private var targetNotBeforeMs: Long? = null
    private var candidateLine = -1
    private var candidateSinceMs = -1L
    private var invalidSinceMs: Long? = null
    private var latestMapped = TextTarget.INVALID
    private var latestCaptureMs: Long? = null
    private var lastGazeTimestampMs: Long? = null

    private var offTextSinceMs: Long? = null
    private var lastStableLine = -1
    private var reentryTarget = -1
    private var evidenceUntil = -1L

    private var lastMd2: Double? = null
    private var lastConfidence = 0.0

    val currentState: ScaffoldState get() = state

    /**
     * Consumes capture-time mapped gaze. While confirmed support is active, this may return
     * an immediate presentation update without waiting for the next 500 ms detector tick.
     */
    fun onGazeTarget(target: TextTarget, timestampMs: Long): ScaffoldUpdate? {
        val previousTimestamp = lastGazeTimestampMs
        if (timestampMs < 0 || previousTimestamp?.let { timestampMs <= it } == true) return null
        lastGazeTimestampMs = timestampMs

        updateReentryEvidence(target, timestampMs)
        latestMapped = target
        latestCaptureMs = timestampMs
        if (!detectorActive) return null

        if (reentryTarget >= 0 && timestampMs <= evidenceUntil) {
            clearCandidate()
            invalidSinceMs = null
            return changeState(
                reentryState(timestampMs), timestampMs,
                if (state.level == ScaffoldLevel.REENTRY) null else "independent_reentry_evidence",
            )
        }

        if (state.level == ScaffoldLevel.REENTRY) {
            beginTargetAcquisition(timestampMs)
            return changeState(waitingState(target, timestampMs), timestampMs, "reentry_evidence_expired")
        }

        if (!target.isValid) {
            val started = invalidSinceMs ?: timestampMs.also { invalidSinceMs = it }
            if (timestampMs - started < config.targetInvalidGraceMs) return null
            clearCandidate()
            return changeState(
                baseState(ScaffoldTargetStatus.INVALID, target, timestampMs), timestampMs,
                "target_invalid_after_grace",
            )
        }
        invalidSinceMs = null

        val notBefore = targetNotBeforeMs ?: return null
        if (timestampMs < notBefore) {
            return changeState(waitingState(target, timestampMs), timestampMs, "pre_confirmation_target_rejected")
        }

        if (candidateLine != target.lineIndex) {
            candidateLine = target.lineIndex
            candidateSinceMs = timestampMs
            // Clear the prior line while the new mapped line is being stabilized. Keeping it
            // visible here is precisely how a spatially stale scaffold can outlive the gaze.
            return changeState(waitingState(target, timestampMs), timestampMs,
                "fresh_target_candidate_started")
        }

        if (timestampMs - candidateSinceMs < config.targetConfirmMs) return null
        val next = baseState(ScaffoldTargetStatus.FRESH, target, timestampMs).copy(
            level = config.supportLevel,
            currentLine = target.lineIndex,
            wordStart = target.wordStart,
            wordEnd = target.wordEnd,
            stabilizedLine = target.lineIndex,
        )
        val reason = if (state.targetStatus == ScaffoldTargetStatus.FRESH &&
            state.currentLine == target.lineIndex) null else "fresh_post_confirmation_target"
        return changeState(next, timestampMs, reason)
    }

    fun update(
        score: MahalanobisScore,
        temporal: TemporalResult,
        target: TextTarget,
        enabled: Boolean = true,
    ): ScaffoldUpdate {
        val timestampMs = score.vector.timestampMs
        lastMd2 = score.md2
        lastConfidence = score.vector.validFraction
        val scoreUsable = score.status == ScoreStatus.AVAILABLE

        if (scoreUsable && !temporal.active && temporal.high == false && target.isValid) {
            lastStableLine = target.lineIndex
        }

        if (!enabled || !scoreUsable || !temporal.active) {
            detectorActive = false
            confirmationTimestampMs = null
            targetNotBeforeMs = null
            invalidSinceMs = null
            clearCandidate()
            val reason = when {
                !enabled -> "support_disabled"
                !scoreUsable -> "score_unavailable"
                else -> "detector_recovered"
            }
            return changeState(ScaffoldState(), timestampMs, if (state != ScaffoldState()) reason else null)
        }

        if (!detectorActive) {
            detectorActive = true
            confirmationTimestampMs = timestampMs
            beginTargetAcquisition(timestampMs)
            val next = if (reentryTarget >= 0 && timestampMs <= evidenceUntil) {
                reentryState(latestCaptureMs ?: timestampMs)
            } else {
                waitingState(latestMapped, latestCaptureMs)
            }
            val reason = if (next.level == ScaffoldLevel.REENTRY) {
                "confirmed_with_reentry_evidence"
            } else {
                "confirmed_waiting_for_post_confirmation_target"
            }
            return changeState(next, timestampMs, reason)
        }

        if (reentryTarget >= 0 && timestampMs <= evidenceUntil) {
            return changeState(reentryState(latestCaptureMs ?: timestampMs), timestampMs,
                if (state.level == ScaffoldLevel.REENTRY) null else "independent_reentry_evidence")
        }
        if (state.level == ScaffoldLevel.REENTRY) {
            beginTargetAcquisition(timestampMs)
            return changeState(waitingState(latestMapped, latestCaptureMs), timestampMs,
                "reentry_evidence_expired")
        }

        val invalidAt = invalidSinceMs
        if (invalidAt != null && timestampMs - invalidAt >= config.targetInvalidGraceMs) {
            clearCandidate()
            return changeState(baseState(ScaffoldTargetStatus.INVALID, latestMapped, latestCaptureMs),
                timestampMs, "target_invalid_after_grace")
        }
        return ScaffoldUpdate(state)
    }

    fun reset(): ScaffoldUpdate {
        val timestampMs = latestCaptureMs ?: 0L
        detectorActive = false
        confirmationTimestampMs = null
        targetNotBeforeMs = null
        invalidSinceMs = null
        clearCandidate()
        offTextSinceMs = null
        lastStableLine = -1
        reentryTarget = -1
        evidenceUntil = -1L
        latestMapped = TextTarget.INVALID
        latestCaptureMs = null
        lastGazeTimestampMs = null
        val update = changeState(ScaffoldState(), timestampMs,
            if (state != ScaffoldState()) "controller_reset" else null)
        lastMd2 = null
        lastConfidence = 0.0
        return update
    }

    private fun updateReentryEvidence(target: TextTarget, timestampMs: Long) {
        if (!target.isValid) {
            if (offTextSinceMs == null) offTextSinceMs = timestampMs
            return
        }
        val away = offTextSinceMs
        if (away != null && timestampMs - away >= config.lossOffTextMs && lastStableLine >= 0 &&
            kotlin.math.abs(target.lineIndex - lastStableLine) >= config.minimumLossLineJump) {
            reentryTarget = lastStableLine
            evidenceUntil = timestampMs + config.reentryEvidenceMs
        }
        offTextSinceMs = null
    }

    private fun beginTargetAcquisition(notBeforeMs: Long) {
        targetNotBeforeMs = notBeforeMs
        invalidSinceMs = null
        clearCandidate()
    }

    private fun clearCandidate() {
        candidateLine = -1
        candidateSinceMs = -1L
    }

    private fun waitingState(target: TextTarget, captureMs: Long?) =
        baseState(ScaffoldTargetStatus.WAITING_FOR_TARGET, target, captureMs)

    private fun reentryState(captureMs: Long) = baseState(
        ScaffoldTargetStatus.REENTRY, latestMapped, captureMs,
    ).copy(
        level = ScaffoldLevel.REENTRY,
        currentLine = latestMapped.lineIndex.takeIf { latestMapped.isValid } ?: -1,
        reentryTargetLine = reentryTarget,
    )

    private fun baseState(status: ScaffoldTargetStatus, target: TextTarget, captureMs: Long?) = ScaffoldState(
        targetStatus = status,
        mappedLine = target.lineIndex.takeIf { target.isValid } ?: -1,
        targetCaptureMs = captureMs,
        confirmationTimestampMs = confirmationTimestampMs,
        targetNotBeforeMs = targetNotBeforeMs,
    )

    private fun changeState(next: ScaffoldState, timestampMs: Long, reason: String?): ScaffoldUpdate {
        val previous = state
        state = next
        val transition = if (previous.level == next.level) null else ScaffoldTransition(
            previous.level,
            next.level,
            timestampMs,
            reason ?: if (next.level == ScaffoldLevel.NONE) "presentation_cleared" else "presentation_target_acquired",
            lastMd2,
            lastConfidence,
            next.currentLine,
            next.reentryTargetLine,
        )
        return ScaffoldUpdate(next, transition, reason)
    }
}
