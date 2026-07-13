package com.newsmead.gaze

/**
 * Stage 5 RSI feed: converts the Stage 4 line-index stream into the events the
 * reading-state layer can consume. It intentionally uses only line dwell,
 * fixation, and regression signals; consumer camera sampling is not reliable
 * enough for saccade-velocity or microsaccade features.
 */
class ReadingStateInferencer(
    private val listener: Listener,
    private val fixationThresholdMs: Long = DEFAULT_FIXATION_THRESHOLD_MS,
    private val minDwellMs: Long = DEFAULT_MIN_DWELL_MS,
    private val regressionConfirmMs: Long = DEFAULT_REGRESSION_CONFIRM_MS,
    private val regressionMinLineJump: Int = DEFAULT_REGRESSION_MIN_LINE_JUMP,
) {
    private var sessionStartedAtMs: Long? = null
    private var activeLine: Int? = null
    private var activeLineCount = 0
    private var activeStartedAtMs = 0L
    private var fixationEmitted = false
    private var fixationCount = 0
    private var totalDwellMs = 0L
    private var regressionCount = 0
    private var maxLineReached = -1
    private var pendingRegression: PendingRegression? = null
    private var inRegressionEpisode = false

    fun onLine(lineIndex: Int, lineCount: Int, timestampMs: Long = System.currentTimeMillis()) {
        if (lineIndex < 0 || lineCount <= 0) return

        if (sessionStartedAtMs == null) {
            sessionStartedAtMs = timestampMs
        }
        val highWaterBeforeSample = maxLineReached
        maxLineReached = maxOf(maxLineReached, lineIndex)
        if (lineIndex >= highWaterBeforeSample && highWaterBeforeSample >= 0) {
            inRegressionEpisode = false
        }

        listener.onLineSample(LineSample(lineIndex, lineCount, timestampMs))

        val currentLine = activeLine
        if (currentLine == null) {
            startLine(lineIndex, lineCount, timestampMs)
            emitScore(timestampMs)
            return
        }

        if (lineIndex == currentLine) {
            maybeEmitFixation(timestampMs)
            maybeEmitPendingRegression(timestampMs)
            emitScore(timestampMs)
            return
        }

        val dwellMs = timestampMs - activeStartedAtMs
        if (!fixationEmitted && dwellMs >= fixationThresholdMs) {
            emitFixation(currentLine, activeLineCount, activeStartedAtMs, dwellMs)
        }
        if (dwellMs >= minDwellMs) {
            emitDwell(currentLine, activeLineCount, activeStartedAtMs, dwellMs)
        }
        updatePendingRegression(highWaterBeforeSample, lineIndex, timestampMs)

        startLine(lineIndex, lineCount, timestampMs)
        emitScore(timestampMs)
    }

    fun flush(timestampMs: Long = System.currentTimeMillis()) {
        val currentLine = activeLine ?: return
        val dwellMs = timestampMs - activeStartedAtMs
        if (!fixationEmitted && dwellMs >= fixationThresholdMs) {
            emitFixation(currentLine, activeLineCount, activeStartedAtMs, dwellMs)
            fixationEmitted = true
        }
        if (dwellMs >= minDwellMs) {
            emitDwell(currentLine, activeLineCount, activeStartedAtMs, dwellMs)
        }
        emitScore(timestampMs)
    }

    private fun startLine(lineIndex: Int, lineCount: Int, timestampMs: Long) {
        activeLine = lineIndex
        activeLineCount = lineCount
        activeStartedAtMs = timestampMs
        fixationEmitted = false
    }

    private fun maybeEmitFixation(timestampMs: Long) {
        if (fixationEmitted) return
        val currentLine = activeLine ?: return
        val dwellMs = timestampMs - activeStartedAtMs
        if (dwellMs >= fixationThresholdMs) {
            emitFixation(currentLine, activeLineCount, activeStartedAtMs, dwellMs)
            fixationEmitted = true
        }
    }

    private fun emitFixation(lineIndex: Int, lineCount: Int, startedAtMs: Long, durationMs: Long) {
        fixationCount += 1
        listener.onFixation(FixationEvent(lineIndex, lineCount, startedAtMs, durationMs))
    }

    private fun emitDwell(lineIndex: Int, lineCount: Int, startedAtMs: Long, durationMs: Long) {
        totalDwellMs += durationMs
        listener.onDwell(DwellEvent(lineIndex, lineCount, startedAtMs, durationMs))
    }

    private fun emitRegression(fromLine: Int, toLine: Int, timestampMs: Long) {
        regressionCount += 1
        inRegressionEpisode = true
        listener.onRegression(RegressionEvent(fromLine, toLine, timestampMs))
    }

    private fun updatePendingRegression(highWaterLine: Int, toLine: Int, timestampMs: Long) {
        pendingRegression =
            if (!inRegressionEpisode && highWaterLine - toLine >= regressionMinLineJump) {
                PendingRegression(highWaterLine, toLine, timestampMs)
            } else {
                null
            }
    }

    private fun maybeEmitPendingRegression(timestampMs: Long) {
        val pending = pendingRegression ?: return
        if (activeLine != pending.toLine) {
            pendingRegression = null
            return
        }
        if (timestampMs - pending.startedAtMs >= regressionConfirmMs) {
            emitRegression(pending.fromLine, pending.toLine, timestampMs)
            pendingRegression = null
        }
    }

    private fun emitScore(timestampMs: Long) {
        val startedAtMs = sessionStartedAtMs ?: return
        val elapsedMs = (timestampMs - startedAtMs).coerceAtLeast(1L)
        val elapsedMinutes = elapsedMs / 60_000.0
        val linesRead = (maxLineReached + 1).coerceAtLeast(1)

        val regressionComponent = (regressionCount.toDouble() / linesRead).coerceIn(0.0, 1.0)
        val dwellComponent = (totalDwellMs.toDouble() / elapsedMs).coerceIn(0.0, 1.0)
        val fixationRatePerMinute = fixationCount / elapsedMinutes
        val fixationComponent = (fixationRatePerMinute / EXPECTED_FIXATIONS_PER_MINUTE).coerceIn(0.0, 1.0)

        val score = 100.0 * (
            REGRESSION_WEIGHT * regressionComponent +
                DWELL_WEIGHT * dwellComponent +
                FIXATION_WEIGHT * fixationComponent
            )

        listener.onScore(
            RsiScore(
                score = score,
                regressionComponent = regressionComponent,
                dwellComponent = dwellComponent,
                fixationComponent = fixationComponent,
                fixationCount = fixationCount,
                totalDwellMs = totalDwellMs,
                regressionCount = regressionCount,
                elapsedMs = elapsedMs,
                timestampMs = timestampMs,
            )
        )
    }

    interface Listener {
        fun onLineSample(sample: LineSample)
        fun onFixation(event: FixationEvent)
        fun onDwell(event: DwellEvent)
        fun onRegression(event: RegressionEvent)
        fun onScore(score: RsiScore) {}
    }

    data class LineSample(val lineIndex: Int, val lineCount: Int, val timestampMs: Long)
    data class FixationEvent(
        val lineIndex: Int,
        val lineCount: Int,
        val startedAtMs: Long,
        val durationMs: Long,
    )
    data class DwellEvent(
        val lineIndex: Int,
        val lineCount: Int,
        val startedAtMs: Long,
        val durationMs: Long,
    )
    data class RegressionEvent(val fromLine: Int, val toLine: Int, val timestampMs: Long)
    private data class PendingRegression(val fromLine: Int, val toLine: Int, val startedAtMs: Long)
    data class RsiScore(
        val score: Double,
        val regressionComponent: Double,
        val dwellComponent: Double,
        val fixationComponent: Double,
        val fixationCount: Int,
        val totalDwellMs: Long,
        val regressionCount: Int,
        val elapsedMs: Long,
        val timestampMs: Long,
    )

    companion object {
        const val DEFAULT_FIXATION_THRESHOLD_MS = 300L
        const val DEFAULT_MIN_DWELL_MS = 120L
        const val DEFAULT_REGRESSION_CONFIRM_MS = 300L
        const val DEFAULT_REGRESSION_MIN_LINE_JUMP = 1
        const val EXPECTED_FIXATIONS_PER_MINUTE = 120.0
        const val REGRESSION_WEIGHT = 0.40
        const val DWELL_WEIGHT = 0.35
        const val FIXATION_WEIGHT = 0.25
    }
}
