package com.newsmead.mgazenetbenchmark

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.TermCriteria
import org.opencv.ml.Ml
import org.opencv.ml.SVM

/** In-memory reference-family SVR only. No load/save or access to NewsMead data. */
class SvrCalibration : CalibrationRegressor {
    private val x = create()
    private val y = create()
    private val row = Mat(1, 258, CvType.CV_32F)
    var trained = false; private set
    val supportVectorCounts: List<Int> get() = listOf(x, y).map { svm ->
        val vectors = svm.supportVectors
        try { vectors.rows() } finally { vectors.release() }
    }
    override fun fit(features: Array<FloatArray>, normalizedLabels: Array<FloatArray>) {
        trained = false
        require(features.isNotEmpty() && features.size == normalizedLabels.size)
        require(features.all { it.size == 258 && it.all(Float::isFinite) })
        require(normalizedLabels.all { it.size == 2 && it.all { v -> v.isFinite() && v in 0f..1f } })
        val samples = Mat(features.size, 258, CvType.CV_32F)
        val labelsX = Mat(features.size, 1, CvType.CV_32F)
        val labelsY = Mat(features.size, 1, CvType.CV_32F)
        try {
            samples.put(0, 0, features.flatMap { it.asIterable() }.toFloatArray())
            labelsX.put(0, 0, normalizedLabels.map { it[0] }.toFloatArray())
            labelsY.put(0, 0, normalizedLabels.map { it[1] }.toFloatArray())
            check(x.train(samples, Ml.ROW_SAMPLE, labelsX) && y.train(samples, Ml.ROW_SAMPLE, labelsY))
            trained = true
        } finally { samples.release(); labelsX.release(); labelsY.release() }
    }
    override fun predict(features: FloatArray): FloatArray {
        check(trained)
        require(features.size == 258 && features.all(Float::isFinite))
        row.put(0, 0, features)
        return floatArrayOf(x.predict(row), y.predict(row))
    }
    override fun close() { x.clear(); y.clear(); row.release(); trained = false }
    private fun create() = SVM.create().apply {
        type = SVM.EPS_SVR; setKernel(SVM.RBF)
        c = 1.0; gamma = .005; p = .001
        termCriteria = TermCriteria(TermCriteria.MAX_ITER, 10000, 1e-4)
    }
}
