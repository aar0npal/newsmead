package com.newsmead.gaze

import java.util.Locale

/** Fixed protocol identifiers written into every structured reading record. */
enum class ReadingValidationPhase {
    SETUP,
    VERTICAL_ALIGNMENT,
    LOCALIZATION,
    GUIDED_LINE_READING,
    COMPLETE,
}

enum class ReadingValidationTrialState { PREPARE, ACQUIRE, MEASURE }

enum class ReadingValidationFeedbackMode(
    val logValue: String,
    val visibleDuringScoredTrials: Boolean,
    val protocolVersion: Int,
    val schemaVersion: Int,
    val smoothForReading: Boolean,
) {
    /** Frozen matched-comparison behavior: visible in preview, hidden while scoring. */
    HIDDEN_AFTER_PREVIEW("hidden_after_preview", false, 4, 7, false),

    /** Historical raw-coordinate visible-feedback condition retained for log identity. */
    CONTINUOUS_ORANGE_DOT_RAW("continuous_orange_dot", true, 5, 8, false),

    /** Ordinary diagnostic condition matched to the article's One Euro gaze output. */
    CONTINUOUS_ORANGE_DOT_ARTICLE_SMOOTHED(
        "continuous_orange_dot_article_smoothed",
        true,
        6,
        9,
        true,
    ),

    ;

    fun requireCompatibleWithComparison(comparisonBound: Boolean) {
        require(!comparisonBound || this == HIDDEN_AFTER_PREVIEW) {
            "Frozen matched-comparison reading must keep gaze feedback hidden while scoring"
        }
    }
}

data class ReadingValidationWord(
    val text: String,
    val start: Int,
    val end: Int,
)

data class ReadingValidationLine(
    val lineIndex: Int,
    val words: List<ReadingValidationWord>,
)

data class ReadingValidationCheckpoint(
    val id: String,
    val region: String,
    val lineIndex: Int,
    val targetKind: TargetKind,
    val targetText: String,
    val targetStart: Int,
    val targetEnd: Int,
    /** Desired vertical position inside the visible reading area. */
    val viewportFraction: Float,
) {
    enum class TargetKind { WORD_FIXATION, LINE_READING }
}

/**
 * Selects known-word checkpoints from actual laid-out lines. The two variants
 * contain the same targets but start in different regions, separating a
 * recurring region error from an always-first-target error across two runs.
 */
object ReadingValidationProtocol {
    /** Frozen matched-comparison version. Do not change without replacing that protocol. */
    const val VERSION = 4
    const val COUNTDOWN_MS = 3_000L
    const val ALIGNMENT_ACQUIRE_MS = 2_000L
    const val ALIGNMENT_MEASURE_MS = 2_500L
    const val LOCALIZATION_ACQUIRE_MS = 3_000L
    const val LOCALIZATION_MEASURE_MS = 2_500L
    const val LINE_READING_ACQUIRE_MS = 2_500L
    const val LINE_READING_MEASURE_MS = 5_000L

    fun localizationCheckpoints(
        lines: List<ReadingValidationLine>,
        variant: String,
    ): List<ReadingValidationCheckpoint> {
        require(variant == "A" || variant == "B") { "Reading order must be exactly A or B" }
        val usable = lines.filter { it.words.isNotEmpty() }
        require(usable.size >= REQUIRED_CHECKPOINTS) {
            "Structured reading validation requires at least $REQUIRED_CHECKPOINTS laid-out text lines"
        }

        val allWords = usable.flatMap { it.words }
        val frequencies = allWords.groupingBy { it.text.lowercase(Locale.ROOT) }.eachCount()
        val fractions = floatArrayOf(0.12f, 0.25f, 0.38f, 0.50f, 0.62f, 0.75f, 0.88f, 0.95f)
        val regions = listOf(
            "top_left", "middle_center", "bottom_right", "top_right",
            "bottom_left", "middle_right", "top_center", "bottom_center",
        )
        val verticalFractions = floatArrayOf(0.22f, 0.50f, 0.76f, 0.22f, 0.76f, 0.50f, 0.22f, 0.76f)
        val horizontalChoices = intArrayOf(0, 1, 2, 2, 0, 2, 1, 1)

        val selected = fractions.mapIndexed { index, fraction ->
            val line = usable[((usable.lastIndex * fraction).toInt()).coerceIn(0, usable.lastIndex)]
            val unique = line.words.filter { frequencies[it.text.lowercase(Locale.ROOT)] == 1 }
                .ifEmpty { line.words }
            val position = when (horizontalChoices[index]) {
                0 -> 0
                2 -> unique.lastIndex
                else -> unique.lastIndex / 2
            }
            ReadingValidationCheckpoint(
                id = "localization_${index + 1}",
                region = regions[index],
                lineIndex = line.lineIndex,
                targetKind = ReadingValidationCheckpoint.TargetKind.WORD_FIXATION,
                targetText = unique[position].text,
                targetStart = unique[position].start,
                targetEnd = unique[position].end,
                viewportFraction = verticalFractions[index],
            )
        }

        val order = if (variant == "B") {
            intArrayOf(1, 3, 0, 2, 5, 7, 4, 6)
        } else {
            intArrayOf(0, 1, 2, 3, 4, 5, 6, 7)
        }
        return order.map { selected[it] }
    }

    /** Full physical lines for guided reading, including positions after scrolling. */
    fun lineReadingCheckpoints(
        lines: List<ReadingValidationLine>,
        variant: String,
    ): List<ReadingValidationCheckpoint> {
        require(variant == "A" || variant == "B") { "Reading order must be exactly A or B" }
        val usable = lines.filter { it.words.size >= 3 }
        require(usable.size >= REQUIRED_LINE_CHECKPOINTS) {
            "Guided line validation requires at least $REQUIRED_LINE_CHECKPOINTS laid-out text lines"
        }
        val fractions = floatArrayOf(0.18f, 0.34f, 0.50f, 0.66f, 0.82f, 0.94f)
        val verticalFractions = floatArrayOf(0.25f, 0.50f, 0.75f, 0.25f, 0.50f, 0.75f)
        val selected = fractions.mapIndexed { index, fraction ->
            val line = usable[((usable.lastIndex * fraction).toInt()).coerceIn(0, usable.lastIndex)]
            ReadingValidationCheckpoint(
                id = "line_reading_${index + 1}",
                region = when (index % 3) {
                    0 -> "top_line"
                    1 -> "middle_line"
                    else -> "bottom_line"
                },
                lineIndex = line.lineIndex,
                targetKind = ReadingValidationCheckpoint.TargetKind.LINE_READING,
                targetText = line.words.joinToString(" ") { it.text },
                targetStart = line.words.first().start,
                targetEnd = line.words.last().end,
                viewportFraction = verticalFractions[index],
            )
        }
        return if (variant == "B") {
            listOf(selected[1], selected[2], selected[0], selected[4], selected[5], selected[3])
        } else {
            selected
        }
    }

    const val REQUIRED_CHECKPOINTS = 8
    const val REQUIRED_LINE_CHECKPOINTS = 6
}

/** Constant-memory accuracy summary; the full sample trace remains in JSONL. */
class ReadingValidationMetrics {
    private var measuredSamples = 0
    private var validSamples = 0
    private var exactLineSamples = 0
    private var adjacentLineSamples = 0
    private var exactWordSamples = 0
    private var wordMeasuredSamples = 0
    private var wordExactLineSamples = 0
    private var wordAdjacentLineSamples = 0
    private var lineMeasuredSamples = 0
    private var lineExactLineSamples = 0
    private var lineAdjacentLineSamples = 0
    private val absoluteLineErrors = ArrayList<Int>()
    private val checkpointsWithSamples = LinkedHashSet<String>()

    fun record(
        checkpoint: ReadingValidationCheckpoint,
        stableTarget: TextTarget,
    ) {
        measuredSamples += 1
        checkpointsWithSamples += checkpoint.id
        val wordTrial = checkpoint.targetKind == ReadingValidationCheckpoint.TargetKind.WORD_FIXATION
        if (wordTrial) wordMeasuredSamples += 1 else lineMeasuredSamples += 1
        if (!stableTarget.isValid) return
        validSamples += 1
        val lineError = kotlin.math.abs(stableTarget.lineIndex - checkpoint.lineIndex)
        absoluteLineErrors += lineError
        if (lineError == 0) exactLineSamples += 1
        if (lineError <= 1) adjacentLineSamples += 1
        if (wordTrial && lineError == 0) wordExactLineSamples += 1
        if (wordTrial && lineError <= 1) wordAdjacentLineSamples += 1
        if (!wordTrial && lineError == 0) lineExactLineSamples += 1
        if (!wordTrial && lineError <= 1) lineAdjacentLineSamples += 1
        if (wordTrial &&
            stableTarget.wordStart == checkpoint.targetStart &&
            stableTarget.wordEnd == checkpoint.targetEnd
        ) exactWordSamples += 1
    }

    fun summary(): Summary = Summary(
        measuredSamples = measuredSamples,
        validSamples = validSamples,
        checkpointCount = checkpointsWithSamples.size,
        exactLineSamples = exactLineSamples,
        withinOneLineSamples = adjacentLineSamples,
        exactWordSamples = exactWordSamples,
        wordMeasuredSamples = wordMeasuredSamples,
        wordExactLineSamples = wordExactLineSamples,
        wordWithinOneLineSamples = wordAdjacentLineSamples,
        lineMeasuredSamples = lineMeasuredSamples,
        lineExactLineSamples = lineExactLineSamples,
        lineWithinOneLineSamples = lineAdjacentLineSamples,
        medianAbsoluteLineError = percentile(absoluteLineErrors, 0.50),
        p95AbsoluteLineError = percentile(absoluteLineErrors, 0.95),
    )

    private fun percentile(values: List<Int>, fraction: Double): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val rank = fraction * (sorted.size - 1)
        val low = rank.toInt()
        val high = kotlin.math.ceil(rank).toInt()
        if (low == high) return sorted[low].toDouble()
        return sorted[low] + (sorted[high] - sorted[low]) * (rank - low)
    }

    data class Summary(
        val measuredSamples: Int,
        val validSamples: Int,
        val checkpointCount: Int,
        val exactLineSamples: Int,
        val withinOneLineSamples: Int,
        val exactWordSamples: Int,
        val wordMeasuredSamples: Int,
        val wordExactLineSamples: Int,
        val wordWithinOneLineSamples: Int,
        val lineMeasuredSamples: Int,
        val lineExactLineSamples: Int,
        val lineWithinOneLineSamples: Int,
        val medianAbsoluteLineError: Double?,
        val p95AbsoluteLineError: Double?,
    ) {
        val validFraction: Double get() = ratio(validSamples)
        val exactLineAccuracy: Double get() = ratio(exactLineSamples)
        val withinOneLineAccuracy: Double get() = ratio(withinOneLineSamples)
        val exactWordAccuracy: Double get() =
            if (wordMeasuredSamples == 0) 0.0 else exactWordSamples.toDouble() / wordMeasuredSamples
        val wordExactLineAccuracy: Double get() = kindRatio(wordExactLineSamples, wordMeasuredSamples)
        val wordWithinOneLineAccuracy: Double get() = kindRatio(wordWithinOneLineSamples, wordMeasuredSamples)
        val guidedLineExactAccuracy: Double get() = kindRatio(lineExactLineSamples, lineMeasuredSamples)
        val guidedLineWithinOneAccuracy: Double get() = kindRatio(lineWithinOneLineSamples, lineMeasuredSamples)

        private fun ratio(numerator: Int): Double =
            if (measuredSamples == 0) 0.0 else numerator.toDouble() / measuredSamples

        private fun kindRatio(numerator: Int, denominator: Int): Double =
            if (denominator == 0) 0.0 else numerator.toDouble() / denominator
    }
}
