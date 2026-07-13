package com.newsmead.gaze

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingStateInferencerTest {

    @Test
    fun emitsFixationAfterDwellingOnSameLine() {
        val events = RecordingListener()
        val inferencer = ReadingStateInferencer(events, fixationThresholdMs = 300, minDwellMs = 120)

        inferencer.onLine(lineIndex = 4, lineCount = 20, timestampMs = 1_000)
        inferencer.onLine(lineIndex = 4, lineCount = 20, timestampMs = 1_350)
        inferencer.onLine(lineIndex = 4, lineCount = 20, timestampMs = 1_600)

        assertEquals(1, events.fixations.size)
        assertEquals(4, events.fixations.single().lineIndex)
        assertEquals(350, events.fixations.single().durationMs)
    }

    @Test
    fun emitsDwellWhenLeavingLine() {
        val events = RecordingListener()
        val inferencer = ReadingStateInferencer(events, fixationThresholdMs = 300, minDwellMs = 120)

        inferencer.onLine(lineIndex = 2, lineCount = 20, timestampMs = 1_000)
        inferencer.onLine(lineIndex = 3, lineCount = 20, timestampMs = 1_180)

        assertEquals(1, events.dwells.size)
        assertEquals(2, events.dwells.single().lineIndex)
        assertEquals(180, events.dwells.single().durationMs)
    }

    @Test
    fun emitsRegressionWhenReturningToEarlierLineAndHoldingThere() {
        val events = RecordingListener()
        val inferencer = ReadingStateInferencer(events, fixationThresholdMs = 300, minDwellMs = 120)

        inferencer.onLine(lineIndex = 8, lineCount = 20, timestampMs = 1_000)
        inferencer.onLine(lineIndex = 9, lineCount = 20, timestampMs = 1_200)
        inferencer.onLine(lineIndex = 5, lineCount = 20, timestampMs = 1_400)
        inferencer.onLine(lineIndex = 5, lineCount = 20, timestampMs = 1_700)

        assertEquals(1, events.regressions.size)
        assertEquals(9, events.regressions.single().fromLine)
        assertEquals(5, events.regressions.single().toLine)
    }

    @Test
    fun emitsRegressionForConfirmedOneLineReread() {
        val events = RecordingListener()
        val inferencer = ReadingStateInferencer(events, fixationThresholdMs = 300, minDwellMs = 120)

        inferencer.onLine(lineIndex = 8, lineCount = 20, timestampMs = 1_000)
        inferencer.onLine(lineIndex = 9, lineCount = 20, timestampMs = 1_200)
        inferencer.onLine(lineIndex = 8, lineCount = 20, timestampMs = 1_400)
        inferencer.onLine(lineIndex = 8, lineCount = 20, timestampMs = 1_700)

        assertEquals(1, events.regressions.size)
        assertEquals(9, events.regressions.single().fromLine)
        assertEquals(8, events.regressions.single().toLine)
    }

    @Test
    fun countsOnlyOneRegressionDuringSameBackwardEpisode() {
        val events = RecordingListener()
        val inferencer = ReadingStateInferencer(events, fixationThresholdMs = 300, minDwellMs = 120)

        inferencer.onLine(lineIndex = 8, lineCount = 20, timestampMs = 1_000)
        inferencer.onLine(lineIndex = 9, lineCount = 20, timestampMs = 1_200)
        inferencer.onLine(lineIndex = 5, lineCount = 20, timestampMs = 1_400)
        inferencer.onLine(lineIndex = 5, lineCount = 20, timestampMs = 1_700)
        inferencer.onLine(lineIndex = 6, lineCount = 20, timestampMs = 1_900)
        inferencer.onLine(lineIndex = 5, lineCount = 20, timestampMs = 2_100)
        inferencer.onLine(lineIndex = 5, lineCount = 20, timestampMs = 2_500)

        assertEquals(1, events.regressions.size)
        assertEquals(9, events.regressions.single().fromLine)
        assertEquals(5, events.regressions.single().toLine)
    }

    @Test
    fun ignoresBriefUpwardLineBounceAsRegression() {
        val events = RecordingListener()
        val inferencer = ReadingStateInferencer(events, fixationThresholdMs = 300, minDwellMs = 120)

        inferencer.onLine(lineIndex = 8, lineCount = 20, timestampMs = 1_000)
        inferencer.onLine(lineIndex = 9, lineCount = 20, timestampMs = 1_200)
        inferencer.onLine(lineIndex = 7, lineCount = 20, timestampMs = 1_400)
        inferencer.onLine(lineIndex = 9, lineCount = 20, timestampMs = 1_480)

        assertEquals(0, events.regressions.size)
    }

    @Test
    fun ignoresInvalidLineSamples() {
        val events = RecordingListener()
        val inferencer = ReadingStateInferencer(events)

        inferencer.onLine(lineIndex = -1, lineCount = 20, timestampMs = 1_000)
        inferencer.onLine(lineIndex = 0, lineCount = 0, timestampMs = 1_100)

        assertEquals(0, events.lines.size)
        assertEquals(0, events.fixations.size)
        assertEquals(0, events.dwells.size)
        assertEquals(0, events.regressions.size)
    }

    @Test
    fun emitsCompositeRsiScoreFromRegressionDwellAndFixationComponents() {
        val events = RecordingListener()
        val inferencer = ReadingStateInferencer(events, fixationThresholdMs = 300, minDwellMs = 120)

        inferencer.onLine(lineIndex = 1, lineCount = 20, timestampMs = 0)
        inferencer.onLine(lineIndex = 1, lineCount = 20, timestampMs = 30_000)
        inferencer.onLine(lineIndex = 4, lineCount = 20, timestampMs = 60_000)
        inferencer.onLine(lineIndex = 1, lineCount = 20, timestampMs = 90_000)
        inferencer.onLine(lineIndex = 1, lineCount = 20, timestampMs = 90_300)

        val score = events.scores.last()
        assertEquals(1.0 / 5.0, score.regressionComponent, 0.0001)
        assertEquals(90_000.0 / 90_300.0, score.dwellComponent, 0.0001)
        assertEquals((3.0 / (90_300.0 / 60_000.0)) / 120.0, score.fixationComponent, 0.0001)
        assertEquals(43.30, score.score, 0.01)
    }

    private class RecordingListener : ReadingStateInferencer.Listener {
        val lines = mutableListOf<ReadingStateInferencer.LineSample>()
        val fixations = mutableListOf<ReadingStateInferencer.FixationEvent>()
        val dwells = mutableListOf<ReadingStateInferencer.DwellEvent>()
        val regressions = mutableListOf<ReadingStateInferencer.RegressionEvent>()
        val scores = mutableListOf<ReadingStateInferencer.RsiScore>()

        override fun onLineSample(sample: ReadingStateInferencer.LineSample) {
            lines.add(sample)
        }

        override fun onFixation(event: ReadingStateInferencer.FixationEvent) {
            fixations.add(event)
        }

        override fun onDwell(event: ReadingStateInferencer.DwellEvent) {
            dwells.add(event)
        }

        override fun onRegression(event: ReadingStateInferencer.RegressionEvent) {
            regressions.add(event)
        }

        override fun onScore(score: ReadingStateInferencer.RsiScore) {
            scores.add(score)
        }
    }
}
