package com.newsmead.gaze

/**
 * Debounces line and word targets before they drive RSI or a visible scaffold.
 * Line confirmation is deliberately shorter than word confirmation because the
 * tracker is substantially more reliable vertically than horizontally.
 */
class GazeTargetStabilizer(
    private val lineConfirmMs: Long = DEFAULT_LINE_CONFIRM_MS,
    private val wordConfirmMs: Long = DEFAULT_WORD_CONFIRM_MS,
    private val offTextGraceMs: Long = DEFAULT_OFF_TEXT_GRACE_MS,
) {
    private var stable = TextTarget.INVALID
    private var candidate = TextTarget.INVALID
    private var candidateSinceMs = 0L
    private var offTextSinceMs: Long? = null
    private var wordCandidateStart = TextTarget.NO_OFFSET
    private var wordCandidateEnd = TextTarget.NO_OFFSET
    private var wordCandidateSinceMs = 0L

    fun update(raw: TextTarget, timestampMs: Long): TextTarget {
        if (!raw.isValid) {
            val startedAt = offTextSinceMs ?: timestampMs.also { offTextSinceMs = it }
            if (timestampMs - startedAt >= offTextGraceMs) {
                stable = TextTarget.INVALID
                candidate = TextTarget.INVALID
                clearWordCandidate()
            }
            return stable
        }

        offTextSinceMs = null
        if (!stable.isValid) {
            stable = raw.withoutWord()
            candidate = stable
            acceptOrDebounceWord(raw, timestampMs)
            return stable
        }

        if (raw.lineIndex != stable.lineIndex) {
            if (candidate.lineIndex != raw.lineIndex) {
                candidate = raw.withoutWord()
                candidateSinceMs = timestampMs
            } else if (timestampMs - candidateSinceMs >= lineConfirmMs) {
                stable = raw.withoutWord()
                clearWordCandidate()
            }
            return stable
        }

        candidate = raw.withoutWord()
        acceptOrDebounceWord(raw, timestampMs)
        return stable
    }

    fun reset() {
        stable = TextTarget.INVALID
        candidate = TextTarget.INVALID
        offTextSinceMs = null
        clearWordCandidate()
    }

    private fun acceptOrDebounceWord(raw: TextTarget, timestampMs: Long) {
        if (!raw.hasWord) {
            stable = stable.withoutWord()
            clearWordCandidate()
            return
        }
        if (stable.wordStart == raw.wordStart && stable.wordEnd == raw.wordEnd) return
        if (wordCandidateStart != raw.wordStart || wordCandidateEnd != raw.wordEnd) {
            wordCandidateStart = raw.wordStart
            wordCandidateEnd = raw.wordEnd
            wordCandidateSinceMs = timestampMs
            return
        }
        if (timestampMs - wordCandidateSinceMs >= wordConfirmMs) {
            stable = raw
            clearWordCandidate()
        }
    }

    private fun TextTarget.withoutWord(): TextTarget = copy(
        wordStart = TextTarget.NO_OFFSET,
        wordEnd = TextTarget.NO_OFFSET,
    )

    private fun clearWordCandidate() {
        wordCandidateStart = TextTarget.NO_OFFSET
        wordCandidateEnd = TextTarget.NO_OFFSET
        wordCandidateSinceMs = 0L
    }

    companion object {
        const val DEFAULT_LINE_CONFIRM_MS = 120L
        const val DEFAULT_WORD_CONFIRM_MS = 300L
        const val DEFAULT_OFF_TEXT_GRACE_MS = 200L
    }
}
