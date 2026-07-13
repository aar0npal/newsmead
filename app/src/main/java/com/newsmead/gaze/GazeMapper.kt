package com.newsmead.gaze

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Local 16-point calibration mapper. It fits the Stage 3 second-degree
 * polynomial over standardized gaze features:
 *
 * screen = b0 + b1*zx + b2*zy + b3*zx^2 + b4*zy^2 + b5*zx*zy
 *
 * Live inputs are clamped to the calibrated feature range before standardizing,
 * which avoids large extrapolation jumps when landmarks drift slightly outside
 * the sampled range.
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

        val features = Array(samples.size) { polynomialFeatures(samples[it].gazeX, samples[it].gazeY) }
        val screenX = DoubleArray(samples.size) { samples[it].screenX.toDouble() }
        val screenY = DoubleArray(samples.size) { samples[it].screenY.toDouble() }
        betaX = fitRidge(features, screenX)
        betaY = fitRidge(features, screenY)
    }

    /** Map a gaze feature to an on-screen point in pixels. */
    fun map(gazeX: Float, gazeY: Float): FloatArray =
        polynomialFeatures(gazeX, gazeY).let { features ->
            floatArrayOf(evaluate(betaX, features), evaluate(betaY, features))
        }

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

    private fun evaluate(beta: DoubleArray, features: DoubleArray): Float =
        beta.indices.sumOf { beta[it] * features[it] }.toFloat()

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
    }
}
