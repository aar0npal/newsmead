package com.newsmead.gaze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ReadingValidationProtocolTest {
    @Test fun feedbackModesKeepDistinctProtocolAndPipelineIdentities() {
        assertEquals(4, ReadingValidationFeedbackMode.HIDDEN_AFTER_PREVIEW.protocolVersion)
        assertEquals(7, ReadingValidationFeedbackMode.HIDDEN_AFTER_PREVIEW.schemaVersion)
        assertEquals(
            ReadingValidationSessionLog.SCHEMA_VERSION,
            ReadingValidationFeedbackMode.HIDDEN_AFTER_PREVIEW.schemaVersion,
        )
        assertFalse(ReadingValidationFeedbackMode.HIDDEN_AFTER_PREVIEW.visibleDuringScoredTrials)
        assertFalse(ReadingValidationFeedbackMode.HIDDEN_AFTER_PREVIEW.smoothForReading)

        assertEquals(5, ReadingValidationFeedbackMode.CONTINUOUS_ORANGE_DOT_RAW.protocolVersion)
        assertEquals(8, ReadingValidationFeedbackMode.CONTINUOUS_ORANGE_DOT_RAW.schemaVersion)
        assertEquals(
            ReadingValidationSessionLog.RAW_VISIBLE_FEEDBACK_SCHEMA_VERSION,
            ReadingValidationFeedbackMode.CONTINUOUS_ORANGE_DOT_RAW.schemaVersion,
        )
        assertTrue(ReadingValidationFeedbackMode.CONTINUOUS_ORANGE_DOT_RAW.visibleDuringScoredTrials)
        assertFalse(ReadingValidationFeedbackMode.CONTINUOUS_ORANGE_DOT_RAW.smoothForReading)

        assertEquals(6, ReadingValidationFeedbackMode.CONTINUOUS_ORANGE_DOT_ARTICLE_SMOOTHED.protocolVersion)
        assertEquals(9, ReadingValidationFeedbackMode.CONTINUOUS_ORANGE_DOT_ARTICLE_SMOOTHED.schemaVersion)
        assertEquals(
            ReadingValidationSessionLog.ARTICLE_SMOOTHED_VISIBLE_FEEDBACK_SCHEMA_VERSION,
            ReadingValidationFeedbackMode.CONTINUOUS_ORANGE_DOT_ARTICLE_SMOOTHED.schemaVersion,
        )
        assertTrue(ReadingValidationFeedbackMode.CONTINUOUS_ORANGE_DOT_ARTICLE_SMOOTHED.visibleDuringScoredTrials)
        assertTrue(ReadingValidationFeedbackMode.CONTINUOUS_ORANGE_DOT_ARTICLE_SMOOTHED.smoothForReading)
    }

    @Test fun frozenComparisonRejectsVisibleFeedback() {
        ReadingValidationFeedbackMode.HIDDEN_AFTER_PREVIEW.requireCompatibleWithComparison(true)
        val visibleModes = listOf(
            ReadingValidationFeedbackMode.CONTINUOUS_ORANGE_DOT_RAW,
            ReadingValidationFeedbackMode.CONTINUOUS_ORANGE_DOT_ARTICLE_SMOOTHED,
        )
        for (mode in visibleModes) {
            mode.requireCompatibleWithComparison(false)
            try {
                mode.requireCompatibleWithComparison(true)
                fail("Visible feedback must not enter the frozen matched-comparison path")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        }
    }

    private val lines = (0 until 20).map { line ->
        ReadingValidationLine(
            lineIndex = line,
            words = listOf(
                ReadingValidationWord("left$line", line * 30, line * 30 + 6),
                ReadingValidationWord("middle$line", line * 30 + 8, line * 30 + 16),
                ReadingValidationWord("right$line", line * 30 + 18, line * 30 + 25),
            ),
        )
    }

    @Test
    fun variantsUseSameTargetsButDifferentFirstRegion() {
        val a = ReadingValidationProtocol.localizationCheckpoints(lines, "A")
        val b = ReadingValidationProtocol.localizationCheckpoints(lines, "B")

        assertEquals(ReadingValidationProtocol.REQUIRED_CHECKPOINTS, a.size)
        assertEquals(a.map { it.id }.toSet(), b.map { it.id }.toSet())
        assertNotEquals(a.first().region, b.first().region)
        assertEquals("top_left", a.first().region)
    }

    @Test
    fun localizationMetricsUseOnlyMeasurementSamples() {
        val checkpoint = ReadingValidationProtocol.localizationCheckpoints(lines, "A").first()
        val metrics = ReadingValidationMetrics()
        metrics.record(
            checkpoint,
            TextTarget(
                checkpoint.lineIndex,
                lines.size,
                checkpoint.targetStart,
                checkpoint.targetEnd,
            ),
        )
        metrics.record(checkpoint, TextTarget(checkpoint.lineIndex + 1, lines.size))
        metrics.record(checkpoint, TextTarget.INVALID)

        val summary = metrics.summary()
        assertEquals(3, summary.measuredSamples)
        assertEquals(2, summary.validSamples)
        assertEquals(1.0 / 3.0, summary.exactLineAccuracy, 0.0001)
        assertEquals(2.0 / 3.0, summary.withinOneLineAccuracy, 0.0001)
        assertEquals(1.0 / 3.0, summary.exactWordAccuracy, 0.0001)
        assertTrue(summary.p95AbsoluteLineError!! >= summary.medianAbsoluteLineError!!)
    }

    @Test
    fun guidedLineTargetsHaveNoWordAccuracyDenominator() {
        val checkpoint = ReadingValidationProtocol.lineReadingCheckpoints(lines, "A").first()
        val metrics = ReadingValidationMetrics()
        metrics.record(checkpoint, TextTarget(checkpoint.lineIndex, lines.size))

        val summary = metrics.summary()
        assertEquals(ReadingValidationCheckpoint.TargetKind.LINE_READING, checkpoint.targetKind)
        assertEquals(1.0, summary.exactLineAccuracy, 0.0001)
        assertEquals(0, summary.wordMeasuredSamples)
        assertEquals(0.0, summary.exactWordAccuracy, 0.0001)
    }

    @Test
    fun orderVariantMustBeExact() {
        for (variant in listOf("", "a", "b", "C")) {
            try {
                ReadingValidationProtocol.localizationCheckpoints(lines, variant)
                fail("Expected rejection for $variant")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
            try {
                ReadingValidationProtocol.lineReadingCheckpoints(lines, variant)
                fail("Expected rejection for $variant")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        }
    }
}
