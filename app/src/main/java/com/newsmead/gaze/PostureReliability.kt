package com.newsmead.gaze

import kotlin.math.abs

/**
 * Lightweight head/face geometry observed alongside a gaze feature. These
 * values are evidence only: they never enter the gaze mapper or its filters.
 * [faceCenterX]/[faceCenterY] are the midpoint between the two eye centres,
 * [faceScale] is eye-centre separation in frame-width units, and
 * [headRollDeg] is the aspect-correct eye-line angle.
 */
data class PostureFeatures(
    val faceCenterX: Float = Float.NaN,
    val faceCenterY: Float = Float.NaN,
    val faceScale: Float = Float.NaN,
    val headRollDeg: Float = Float.NaN,
) {
    val isAvailable: Boolean
        get() = faceCenterX.isFinite() && faceCenterY.isFinite() &&
            faceScale.isFinite() && headRollDeg.isFinite()
}

data class PostureAxisRange(
    val median: Float,
    val minimum: Float,
    val maximum: Float,
    val margin: Float,
) {
    val lowerBound: Float get() = minimum - margin
    val upperBound: Float get() = maximum + margin

    /** Zero in range; otherwise distance beyond the allowed bound in margins. */
    fun normalizedExcess(value: Float): Float = when {
        value < lowerBound -> (lowerBound - value) / margin
        value > upperBound -> (value - upperBound) / margin
        else -> 0f
    }
}

data class PostureProfile(
    val calibrationPointCount: Int,
    val faceCenterX: PostureAxisRange,
    val faceCenterY: PostureAxisRange,
    val faceScale: PostureAxisRange,
    val headRollDeg: PostureAxisRange,
) {
    fun assess(features: PostureFeatures): PostureAssessment {
        if (!features.isAvailable) return PostureAssessment.unavailable()
        val excesses = linkedMapOf(
            "face_center_x" to faceCenterX.normalizedExcess(features.faceCenterX),
            "face_center_y" to faceCenterY.normalizedExcess(features.faceCenterY),
            "face_scale" to faceScale.normalizedExcess(features.faceScale),
            "head_roll_deg" to headRollDeg.normalizedExcess(features.headRollDeg),
        )
        return PostureAssessment(
            status = if (excesses.values.any { it > 0f }) {
                PostureStatus.OUT_OF_RANGE
            } else {
                PostureStatus.IN_RANGE
            },
            outOfRangeAxes = excesses.filterValues { it > 0f }.keys.toList(),
            maxNormalizedExcess = excesses.values.maxOrNull() ?: 0f,
        )
    }

    companion object {
        /**
         * Builds a conservative calibration-pose envelope. The observed min/max
         * is expanded by three MADs, with fixed minimum margins for near-static
         * calibrations. The same rule is used for every participant and device.
         */
        fun fromCalibration(samples: List<CalibrationSample>): PostureProfile? {
            val posture = samples.map { it.posture }.filter { it.isAvailable }
            if (posture.size < MIN_PROFILE_POINTS) return null
            return PostureProfile(
                calibrationPointCount = posture.size,
                faceCenterX = range(posture.map { it.faceCenterX }, MIN_CENTER_MARGIN),
                faceCenterY = range(posture.map { it.faceCenterY }, MIN_CENTER_MARGIN),
                faceScale = range(posture.map { it.faceScale }, MIN_SCALE_MARGIN),
                headRollDeg = range(posture.map { it.headRollDeg }, MIN_ROLL_MARGIN_DEG),
            )
        }

        private fun range(values: List<Float>, minimumMargin: Float): PostureAxisRange {
            val median = median(values)
            val mad = median(values.map { abs(it - median) })
            return PostureAxisRange(
                median = median,
                minimum = values.minOrNull() ?: median,
                maximum = values.maxOrNull() ?: median,
                margin = maxOf(MAD_MARGIN_MULTIPLIER * mad, minimumMargin),
            )
        }

        private fun median(values: List<Float>): Float {
            val sorted = values.sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 1) {
                sorted[middle]
            } else {
                (sorted[middle - 1] + sorted[middle]) / 2f
            }
        }

        private const val MIN_PROFILE_POINTS = 8
        private const val MAD_MARGIN_MULTIPLIER = 3f
        private const val MIN_CENTER_MARGIN = 0.02f
        private const val MIN_SCALE_MARGIN = 0.015f
        private const val MIN_ROLL_MARGIN_DEG = 5f
    }
}

enum class PostureStatus { IN_RANGE, OUT_OF_RANGE, UNAVAILABLE }

data class PostureAssessment(
    val status: PostureStatus,
    val outOfRangeAxes: List<String>,
    val maxNormalizedExcess: Float,
) {
    companion object {
        fun unavailable() = PostureAssessment(PostureStatus.UNAVAILABLE, emptyList(), Float.NaN)
    }
}

data class PostureSummary(
    val sampleCount: Int,
    val inRangeCount: Int,
    val outOfRangeCount: Int,
    val unavailableCount: Int,
    val maxNormalizedExcess: Float,
    val outOfRangeAxisCounts: Map<String, Int>,
) {
    val assessedCount: Int get() = inRangeCount + outOfRangeCount
    val outOfRangeFraction: Float
        get() = if (assessedCount == 0) Float.NaN else outOfRangeCount.toFloat() / assessedCount
}

/** Thread-safe lightweight summary used even when detailed telemetry is OFF. */
class PostureSummaryAccumulator {
    private var sampleCount = 0
    private var inRangeCount = 0
    private var outOfRangeCount = 0
    private var unavailableCount = 0
    private var maxNormalizedExcess = 0f
    private val axisCounts = LinkedHashMap<String, Int>()

    @Synchronized
    fun add(assessment: PostureAssessment) {
        sampleCount++
        when (assessment.status) {
            PostureStatus.IN_RANGE -> inRangeCount++
            PostureStatus.OUT_OF_RANGE -> {
                outOfRangeCount++
                maxNormalizedExcess = maxOf(maxNormalizedExcess, assessment.maxNormalizedExcess)
                assessment.outOfRangeAxes.forEach { axis ->
                    axisCounts[axis] = (axisCounts[axis] ?: 0) + 1
                }
            }
            PostureStatus.UNAVAILABLE -> unavailableCount++
        }
    }

    @Synchronized
    fun reset() {
        sampleCount = 0
        inRangeCount = 0
        outOfRangeCount = 0
        unavailableCount = 0
        maxNormalizedExcess = 0f
        axisCounts.clear()
    }

    @Synchronized
    fun snapshot(): PostureSummary = PostureSummary(
        sampleCount,
        inRangeCount,
        outOfRangeCount,
        unavailableCount,
        if (outOfRangeCount > 0) maxNormalizedExcess else 0f,
        axisCounts.toMap(),
    )
}
