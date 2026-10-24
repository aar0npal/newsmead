package com.newsmead.gaze

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * Reading-oriented spatial summaries shared by the 16-point calibration gate
 * and the 9-point live-pipeline check. Vertical error is primary because a
 * line-height is the relevant unit for assigning gaze to article lines; total
 * 2-D error remains visible rather than being discarded.
 */
object ReadingSpatialMetrics {

    const val PROVISIONAL_REFERENCE_LINES = 1.2f

    data class Observation(
        val id: Int,
        val label: String,
        val targetX: Float,
        val targetY: Float,
        val predictedX: Float,
        val predictedY: Float,
    )

    data class PointMetric(
        val id: Int,
        val label: String,
        val dxPx: Float,
        val dyPx: Float,
        val errorPx: Float,
        val verticalLines: Float,
    )

    data class Summary(
        val points: List<PointMetric>,
        val medianErrorPx: Float,
        val p95ErrorPx: Float,
        val maxErrorPx: Float,
        val medianVerticalPx: Float,
        val p95VerticalPx: Float,
        val maxVerticalPx: Float,
        val withinHalfLine: Int,
        val withinOneLine: Int,
        val withinReference: Int,
        val worstVertical: PointMetric,
        val worstEuclidean: PointMetric,
    ) {
        val meetsProvisionalReference: Boolean
            get() = points.isNotEmpty() && withinReference == points.size
    }

    fun summarize(observations: List<Observation>, lineHeightPx: Float): Summary? {
        if (observations.isEmpty() || !lineHeightPx.isFinite() || lineHeightPx <= 0f) return null
        val points = observations.map { observation ->
            val dx = observation.predictedX - observation.targetX
            val dy = observation.predictedY - observation.targetY
            PointMetric(
                id = observation.id,
                label = observation.label,
                dxPx = dx,
                dyPx = dy,
                errorPx = hypot(dx, dy),
                verticalLines = abs(dy) / lineHeightPx,
            )
        }
        val errors = points.map { it.errorPx }
        val verticalPx = points.map { abs(it.dyPx) }
        return Summary(
            points = points,
            medianErrorPx = median(errors),
            p95ErrorPx = percentile(errors, 0.95),
            maxErrorPx = errors.maxOrNull() ?: Float.NaN,
            medianVerticalPx = median(verticalPx),
            p95VerticalPx = percentile(verticalPx, 0.95),
            maxVerticalPx = verticalPx.maxOrNull() ?: Float.NaN,
            withinHalfLine = points.count { it.verticalLines <= 0.5f },
            withinOneLine = points.count { it.verticalLines <= 1.0f },
            withinReference = points.count { it.verticalLines <= PROVISIONAL_REFERENCE_LINES },
            worstVertical = points.maxByOrNull { it.verticalLines }!!,
            worstEuclidean = points.maxByOrNull { it.errorPx }!!,
        )
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2f
    }

    private fun percentile(values: List<Float>, p: Double): Float {
        val sorted = values.sorted()
        return sorted[(ceil(p * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)]
    }
}
