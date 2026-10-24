package com.newsmead.gaze

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Local 16-point calibration mapper. It fits the Stage 3 second-degree
 * polynomial over standardized gaze features:
 *
 * screen = b0 + b1*zx + b2*zy + b3*zx^2 + b4*zy^2 + b5*zx*zy
 *
 * Inside the calibrated feature range the polynomial is evaluated directly.
 * Outside it, the output continues linearly along the polynomial's gradient at
 * the range boundary, capped at [EXTRAPOLATION_LIMIT_Z] standardized units.
 * (An earlier version hard-clamped live inputs to the calibrated range, which
 * froze the gaze at an "invisible barrier" whenever head-pose drift shifted
 * the live feature range beyond the calibrated one - and the barrier's screen
 * position moved with the session's drift correction. Linear extension keeps
 * quadratic extrapolation from blowing up without creating a wall.)
 */
class GazeMapper(samples: List<CalibrationSample>) {

    private val betaX: DoubleArray
    private val betaY: DoubleArray
    private val meanX: Double
    private val meanY: Double
    private val stdX: Double
    private val stdY: Double
    private val minX: Float
    private val maxX: Float
    private val minY: Float
    private val maxY: Float
    private val zMinX: Double
    private val zMaxX: Double
    private val zMinY: Double
    private val zMaxY: Double

    init {
        require(samples.size >= MIN_SAMPLES) {
            "Need at least $MIN_SAMPLES calibration pairs, got ${samples.size}"
        }
        minX = samples.minOf { it.gazeX }
        maxX = samples.maxOf { it.gazeX }
        minY = samples.minOf { it.gazeY }
        maxY = samples.maxOf { it.gazeY }
        meanX = samples.map { it.gazeX.toDouble() }.average()
        meanY = samples.map { it.gazeY.toDouble() }.average()
        stdX = samples.standardDeviation { it.gazeX.toDouble() }.coerceAtLeast(MIN_STD)
        stdY = samples.standardDeviation { it.gazeY.toDouble() }.coerceAtLeast(MIN_STD)
        zMinX = (minX - meanX) / stdX
        zMaxX = (maxX - meanX) / stdX
        zMinY = (minY - meanY) / stdY
        zMaxY = (maxY - meanY) / stdY

        val features = Array(samples.size) { polynomialFeatures(samples[it].gazeX, samples[it].gazeY) }
        val screenX = DoubleArray(samples.size) { samples[it].screenX.toDouble() }
        val screenY = DoubleArray(samples.size) { samples[it].screenY.toDouble() }
        betaX = fitRidge(features, screenX)
        betaY = fitRidge(features, screenY)
    }

    /** Map a gaze feature to an on-screen point in pixels. */
    fun map(gazeX: Float, gazeY: Float): FloatArray {
        val zxRaw = (gazeX.toDouble() - meanX) / stdX
        val zyRaw = (gazeY.toDouble() - meanY) / stdY
        val zx = zxRaw.coerceIn(zMinX, zMaxX)
        val zy = zyRaw.coerceIn(zMinY, zMaxY)
        val features = doubleArrayOf(1.0, zx, zy, zx * zx, zy * zy, zx * zy)
        var screenX = dot(betaX, features)
        var screenY = dot(betaY, features)

        // Beyond the calibrated range: extend along the boundary gradient with a
        // smooth soft-limit (tanh) rather than a hard cap. Near the boundary this
        // is ~linear so out-of-range gaze keeps moving (no "invisible wall"); far
        // out it asymptotes, so a glitchy feature value stays bounded and can't
        // fling the estimate off-screen. A hard cap here produced exactly the
        // flat wall users hit when a fresh calibration captured little vertical
        // feature spread.
        val excessX = softLimit(zxRaw - zx)
        val excessY = softLimit(zyRaw - zy)
        if (excessX != 0.0 || excessY != 0.0) {
            screenX += gradZx(betaX, zx, zy) * excessX + gradZy(betaX, zx, zy) * excessY
            screenY += gradZx(betaY, zx, zy) * excessX + gradZy(betaY, zx, zy) * excessY
        }
        return floatArrayOf(screenX.toFloat(), screenY.toFloat())
    }

    /** d(screen)/d(zx) of the fitted polynomial at (zx, zy). */
    private fun gradZx(beta: DoubleArray, zx: Double, zy: Double): Double =
        beta[1] + 2.0 * beta[3] * zx + beta[5] * zy

    /** d(screen)/d(zy) of the fitted polynomial at (zx, zy). */
    private fun gradZy(beta: DoubleArray, zx: Double, zy: Double): Double =
        beta[2] + 2.0 * beta[4] * zy + beta[5] * zx

    private fun dot(beta: DoubleArray, features: DoubleArray): Double =
        beta.indices.sumOf { beta[it] * features[it] }

    /** ~identity near 0; smoothly asymptotes to +/-EXTRAPOLATION_LIMIT_Z. */
    private fun softLimit(excess: Double): Double =
        EXTRAPOLATION_LIMIT_Z * tanh(excess / EXTRAPOLATION_LIMIT_Z)

    private fun polynomialFeatures(gazeX: Float, gazeY: Float): DoubleArray {
        val zx = (gazeX.coerceIn(minX, maxX).toDouble() - meanX) / stdX
        val zy = (gazeY.coerceIn(minY, maxY).toDouble() - meanY) / stdY
        return doubleArrayOf(1.0, zx, zy, zx * zx, zy * zy, zx * zy)
    }

    private fun fitRidge(features: Array<DoubleArray>, target: DoubleArray): DoubleArray {
        val normal = Array(FEATURE_COUNT) { DoubleArray(FEATURE_COUNT) }
        val rhs = DoubleArray(FEATURE_COUNT)

        for (row in features.indices) {
            val x = features[row]
            for (i in 0 until FEATURE_COUNT) {
                rhs[i] += x[i] * target[row]
                for (j in 0 until FEATURE_COUNT) {
                    normal[i][j] += x[i] * x[j]
                }
            }
        }

        for (i in 1 until FEATURE_COUNT) {
            normal[i][i] += RIDGE_LAMBDA
        }

        return solve(normal, rhs)
    }

    private fun solve(matrix: Array<DoubleArray>, values: DoubleArray): DoubleArray {
        val n = values.size
        val a = Array(n) { row -> matrix[row].copyOf() }
        val b = values.copyOf()

        for (pivot in 0 until n) {
            var best = pivot
            for (row in pivot + 1 until n) {
                if (abs(a[row][pivot]) > abs(a[best][pivot])) best = row
            }
            require(abs(a[best][pivot]) > SINGULAR_EPSILON) {
                "Calibration fit is singular; collect a wider spread of gaze samples"
            }
            if (best != pivot) {
                val tmpRow = a[pivot]
                a[pivot] = a[best]
                a[best] = tmpRow
                val tmpValue = b[pivot]
                b[pivot] = b[best]
                b[best] = tmpValue
            }

            val pivotValue = a[pivot][pivot]
            for (col in pivot until n) a[pivot][col] /= pivotValue
            b[pivot] /= pivotValue

            for (row in 0 until n) {
                if (row == pivot) continue
                val factor = a[row][pivot]
                if (factor == 0.0) continue
                for (col in pivot until n) {
                    a[row][col] -= factor * a[pivot][col]
                }
                b[row] -= factor * b[pivot]
            }
        }

        return b
    }

    private fun List<CalibrationSample>.standardDeviation(selector: (CalibrationSample) -> Double): Double {
        val mean = map(selector).average()
        return sqrt(sumOf { sample ->
            val delta = selector(sample) - mean
            delta * delta
        } / size)
    }

    companion object {
        private const val FEATURE_COUNT = 6
        private const val MIN_SAMPLES = 6
        private const val RIDGE_LAMBDA = 1.0
        private const val MIN_STD = 1e-6
        private const val SINGULAR_EPSILON = 1e-9

        /**
         * Soft-limit scale for out-of-range extension, in standardized units.
         * The tanh is ~linear well within +/-this, and asymptotes beyond it. Set
         * generously (the 4x4 grid spans ~+/-1.35 z) so normal head-pose drift and
         * the screen area past the outer dot rows never approach the flat region;
         * it exists only to bound glitchy feature values.
         */
        private const val EXTRAPOLATION_LIMIT_Z = 2.5
    }
}
