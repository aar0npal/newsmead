package com.newsmead.gaze

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Rejection + aggregation for one calibration point's SAMPLE window
 * (docs/calibration-design.md §4). Operates purely on timestamped raw gaze
 * feature samples (normalized iris-in-eye ratios, NOT pixels), so thresholds
 * can be tuned and re-run against logged data on the JVM without re-collecting.
 *
 * Pipeline: drop the lead-in, reject per-axis MAD outliers, take the median of
 * what remains. This is the old robust median with a light outlier trim added.
 *
 * NOTE: earlier this class also gated on absolute dispersion (0.02 feature units)
 * and, when that failed, selected the most-stable sub-window. Both were removed:
 * the tracker's own per-frame noise floor is ~0.04 horizontally and ~0.05-0.10
 * vertically, so a 0.02 gate never passed and forced timeouts/exclusions, while
 * sub-window selection preferred stable-but-off-target moments over the
 * target-centred average. Dispersion is still COMPUTED and returned for the
 * session log, just not used to reject.
 */
class FixationWindowFilter(
    private val leadInMs: Long = LEAD_IN_MS,
    private val madK: Float = MAD_K,
    private val minSamples: Int = MIN_SAMPLES,
) {

    data class Sample(
        val timestampMs: Long,
        val x: Float,
        val y: Float,
        val eye1X: Float = Float.NaN,
        val eye1Y: Float = Float.NaN,
        val eye2X: Float = Float.NaN,
        val eye2Y: Float = Float.NaN,
        val faceCenterX: Float = Float.NaN,
        val faceCenterY: Float = Float.NaN,
        val faceScale: Float = Float.NaN,
        val headRollDeg: Float = Float.NaN,
    )

    enum class Status { ACCEPTED, TOO_FEW_SAMPLES }

    data class Result(
        val status: Status,
        val medianX: Float,
        val medianY: Float,
        val dispersionX: Float,
        val dispersionY: Float,
        val rawCount: Int,
        val retainedCount: Int,
        val medianEye1X: Float = Float.NaN,
        val medianEye1Y: Float = Float.NaN,
        val medianEye2X: Float = Float.NaN,
        val medianEye2Y: Float = Float.NaN,
        val medianFaceCenterX: Float = Float.NaN,
        val medianFaceCenterY: Float = Float.NaN,
        val medianFaceScale: Float = Float.NaN,
        val medianHeadRollDeg: Float = Float.NaN,
    )

    fun filter(samples: List<Sample>): Result {
        val ordered = samples.sortedBy { it.timestampMs }
        val rawCount = ordered.size
        if (ordered.isEmpty()) {
            return Result(Status.TOO_FEW_SAMPLES, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 0, 0)
        }

        // Unconditionally discard the lead-in: residual settle after the SETTLE state.
        val startMs = ordered.first().timestampMs
        val trimmed = ordered.filter { it.timestampMs - startMs >= leadInMs }
        if (trimmed.size < minSamples) return diagnostic(Status.TOO_FEW_SAMPLES, trimmed, rawCount)

        // Per-axis MAD outlier rejection: removes the stray saccade/twitch without
        // assuming Gaussian noise. MAD is wide when the fixation is genuinely noisy,
        // so this trims tails without discarding usable data.
        val retained = rejectMadOutliers(trimmed)
        if (retained.size < minSamples) return diagnostic(Status.TOO_FEW_SAMPLES, retained, rawCount)

        return Result(
            Status.ACCEPTED,
            median(retained.map { it.x }), median(retained.map { it.y }),
            standardDeviation(retained.map { it.x }), standardDeviation(retained.map { it.y }),
            rawCount, retained.size,
            medianFinite(retained.map { it.eye1X }), medianFinite(retained.map { it.eye1Y }),
            medianFinite(retained.map { it.eye2X }), medianFinite(retained.map { it.eye2Y }),
            medianFinite(retained.map { it.faceCenterX }), medianFinite(retained.map { it.faceCenterY }),
            medianFinite(retained.map { it.faceScale }), medianFinite(retained.map { it.headRollDeg }),
        )
    }

    /** Result carrying whatever medians/dispersions are computable, for logging. */
    private fun diagnostic(status: Status, samples: List<Sample>, rawCount: Int): Result {
        if (samples.isEmpty()) {
            return Result(status, Float.NaN, Float.NaN, Float.NaN, Float.NaN, rawCount, 0)
        }
        return Result(
            status,
            median(samples.map { it.x }), median(samples.map { it.y }),
            standardDeviation(samples.map { it.x }), standardDeviation(samples.map { it.y }),
            rawCount, samples.size,
            medianFinite(samples.map { it.eye1X }), medianFinite(samples.map { it.eye1Y }),
            medianFinite(samples.map { it.eye2X }), medianFinite(samples.map { it.eye2Y }),
            medianFinite(samples.map { it.faceCenterX }), medianFinite(samples.map { it.faceCenterY }),
            medianFinite(samples.map { it.faceScale }), medianFinite(samples.map { it.headRollDeg }),
        )
    }

    /** Drop samples farther than madK * MAD from the median on either axis. */
    private fun rejectMadOutliers(samples: List<Sample>): List<Sample> {
        val medX = median(samples.map { it.x })
        val medY = median(samples.map { it.y })
        val madX = median(samples.map { abs(it.x - medX) })
        val madY = median(samples.map { abs(it.y - medY) })
        return samples.filter { s ->
            (madX < MAD_EPSILON || abs(s.x - medX) <= madK * madX) &&
                (madY < MAD_EPSILON || abs(s.y - medY) <= madK * madY)
        }
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2f
    }

    private fun medianFinite(values: List<Float>): Float {
        val finite = values.filter { it.isFinite() }
        return if (finite.isEmpty()) Float.NaN else median(finite)
    }

    private fun standardDeviation(values: List<Float>): Float {
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance).toFloat()
    }

    companion object {
        // Starting values per docs/calibration-design.md §4; provisional pending pilot.
        private const val LEAD_IN_MS = 120L
        private const val MAD_K = 2.5f
        private const val MIN_SAMPLES = 10
        private const val MAD_EPSILON = 1e-6f
    }
}
