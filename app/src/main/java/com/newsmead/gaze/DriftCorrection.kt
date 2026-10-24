package com.newsmead.gaze

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Affine drift correction fitted from gaze accuracy-test data: the test yields
 * (predicted screen px, true target px) pairs, and mid-session drift from
 * posture shift is mostly bias/gain, so a screen-space affine layer on top of
 * the untouched 16-point polynomial fit recovers it without re-calibrating:
 *
 *   x' = cx[0] + cx[1]*x + cx[2]*y
 *   y' = cy[0] + cy[1]*x + cy[2]*y
 *
 * Applied by LocalCalibratedGazeProvider after GazeMapper; cleared whenever a
 * new full calibration is accepted (CalibrationStore.save).
 */
class DriftCorrection(val coeffX: DoubleArray, val coeffY: DoubleArray) {

    /** One accuracy-test observation: where the pipeline said vs. ground truth. */
    data class Observation(
        val predictedX: Float,
        val predictedY: Float,
        val targetX: Float,
        val targetY: Float,
    )

    fun apply(x: Float, y: Float): FloatArray = floatArrayOf(
        (coeffX[0] + coeffX[1] * x + coeffX[2] * y).toFloat(),
        (coeffY[0] + coeffY[1] * x + coeffY[2] * y).toFloat(),
    )

    companion object {
        /** Minimum observations: 3 solves exactly; require one extra for slack. */
        const val MIN_OBSERVATIONS = 4

        private const val SINGULAR_EPSILON = 1e-9

        /**
         * Least-squares fit of target ≈ affine(predicted) per axis. Null when
         * there are too few observations or their spread is degenerate
         * (e.g. collinear points).
         */
        fun fit(observations: List<Observation>): DriftCorrection? {
            if (observations.size < MIN_OBSERVATIONS) return null
            val features = observations.map {
                doubleArrayOf(1.0, it.predictedX.toDouble(), it.predictedY.toDouble())
            }
            val coeffX = solveNormalEquations(features, observations.map { it.targetX.toDouble() })
                ?: return null
            val coeffY = solveNormalEquations(features, observations.map { it.targetY.toDouble() })
                ?: return null
            return DriftCorrection(coeffX, coeffY)
        }

        /**
         * Correction equivalent to applying [inner] first, then [outer]. Used when
         * a new fit is measured through a pipeline that already had a correction
         * active: the new fit maps corrected->truth, so the stored correction must
         * be outer ∘ inner.
         */
        fun compose(outer: DriftCorrection, inner: DriftCorrection): DriftCorrection {
            fun composeAxis(c: DoubleArray): DoubleArray = doubleArrayOf(
                c[0] + c[1] * inner.coeffX[0] + c[2] * inner.coeffY[0],
                c[1] * inner.coeffX[1] + c[2] * inner.coeffY[1],
                c[1] * inner.coeffX[2] + c[2] * inner.coeffY[2],
            )
            return DriftCorrection(composeAxis(outer.coeffX), composeAxis(outer.coeffY))
        }

        /**
         * Honest post-correction estimate: refit the affine correction leaving
         * one observation out each time, predict the held-out point, and return
         * the median px error. Unlike [medianResidualPx] (in-sample, optimistic),
         * this estimates how the correction generalizes to gaze it was not fitted
         * on. Null if there are too few observations to leave one out (needs
         * MIN_OBSERVATIONS + 1) or any refit is degenerate.
         */
        fun leaveOneOutMedianPx(observations: List<Observation>): Float? {
            if (observations.size < MIN_OBSERVATIONS + 1) return null
            val errors = ArrayList<Float>(observations.size)
            for (held in observations.indices) {
                val training = observations.filterIndexed { i, _ -> i != held }
                val fit = fit(training) ?: return null
                val corrected = fit.apply(observations[held].predictedX, observations[held].predictedY)
                errors.add(
                    hypot(corrected[0] - observations[held].targetX, corrected[1] - observations[held].targetY),
                )
            }
            val sorted = errors.sorted()
            val n = sorted.size
            return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2f
        }

        /** Median px error of [correction] over [observations] (in-sample). */
        fun medianResidualPx(
            correction: DriftCorrection,
            observations: List<Observation>,
        ): Float {
            val residuals = observations.map {
                val corrected = correction.apply(it.predictedX, it.predictedY)
                hypot(corrected[0] - it.targetX, corrected[1] - it.targetY)
            }.sorted()
            val n = residuals.size
            if (n == 0) return Float.NaN
            return if (n % 2 == 1) residuals[n / 2] else (residuals[n / 2 - 1] + residuals[n / 2]) / 2f
        }

        /** 3x3 normal-equations solve with partial pivoting; null if singular. */
        private fun solveNormalEquations(
            features: List<DoubleArray>,
            target: List<Double>,
        ): DoubleArray? {
            val n = 3
            val a = Array(n) { DoubleArray(n) }
            val b = DoubleArray(n)
            for (row in features.indices) {
                val f = features[row]
                for (i in 0 until n) {
                    b[i] += f[i] * target[row]
                    for (j in 0 until n) a[i][j] += f[i] * f[j]
                }
            }

            for (pivot in 0 until n) {
                var best = pivot
                for (row in pivot + 1 until n) {
                    if (abs(a[row][pivot]) > abs(a[best][pivot])) best = row
                }
                if (abs(a[best][pivot]) < SINGULAR_EPSILON) return null
                if (best != pivot) {
                    val tmpRow = a[pivot]; a[pivot] = a[best]; a[best] = tmpRow
                    val tmpVal = b[pivot]; b[pivot] = b[best]; b[best] = tmpVal
                }
                val pivotValue = a[pivot][pivot]
                for (col in pivot until n) a[pivot][col] /= pivotValue
                b[pivot] /= pivotValue
                for (row in 0 until n) {
                    if (row == pivot) continue
                    val factor = a[row][pivot]
                    if (factor == 0.0) continue
                    for (col in pivot until n) a[row][col] -= factor * a[pivot][col]
                    b[row] -= factor * b[pivot]
                }
            }
            return b
        }
    }
}
