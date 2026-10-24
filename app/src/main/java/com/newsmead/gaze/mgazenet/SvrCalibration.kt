package com.newsmead.gaze.mgazenet

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.TermCriteria
import org.opencv.ml.Ml
import org.opencv.ml.SVM
import java.io.File

/** Thread-confined, pinned X/Y regressors. Only native trained models are serialized. */
class SvrCalibration private constructor(private val x: SVM, private val y: SVM) : AutoCloseable {
    constructor() : this(create(), create())
    private val row = Mat(1,258,CvType.CV_32F)
    var trained = false; private set
    fun fit(features: Array<FloatArray>, labels: Array<FloatArray>) {
        trained = false
        require(features.isNotEmpty() && features.size == labels.size)
        require(features.all { it.size == 258 && it.all(Float::isFinite) })
        require(labels.all { it.size == 2 && it.all { v -> v.isFinite() && v in 0f..1f } })
        val samples = Mat(features.size,258,CvType.CV_32F)
        val lx = Mat(features.size,1,CvType.CV_32F); val ly = Mat(features.size,1,CvType.CV_32F)
        try {
            features.forEachIndexed { i, f -> samples.put(i,0,f) }
            val xs = labels.map { it[0] }.toFloatArray(); val ys = labels.map { it[1] }.toFloatArray()
            try { lx.put(0,0,xs); ly.put(0,0,ys) } finally { xs.fill(0f); ys.fill(0f) }
            check(x.train(samples,Ml.ROW_SAMPLE,lx) && y.train(samples,Ml.ROW_SAMPLE,ly))
            trained = true
        } finally { samples.release(); lx.release(); ly.release() }
    }
    fun predict(features: FloatArray): FloatArray {
        check(trained); require(features.size == 258 && features.all(Float::isFinite))
        row.put(0,0,features)
        return floatArrayOf(x.predict(row),y.predict(row)).also { check(it.all(Float::isFinite)) }
    }
    fun save(xFile: File, yFile: File) { check(trained); x.save(xFile.path); y.save(yFile.path) }
    override fun close() { trained = false; x.clear(); y.clear(); row.release() }
    companion object {
        private fun create() = SVM.create().apply {
            type = SVM.EPS_SVR; setKernel(SVM.RBF); c = 1.0; gamma = .005; p = .001
            termCriteria = TermCriteria(TermCriteria.MAX_ITER,10000,1e-4)
        }
        fun load(xFile: File, yFile: File): SvrCalibration {
            var x: SVM? = null; var y: SVM? = null
            try {
                x = SVM.load(xFile.path); y = SVM.load(yFile.path)
                listOf(x,y).forEach { model ->
                    require(model.isTrained && model.varCount == 258 && model.type == SVM.EPS_SVR &&
                        model.kernelType == SVM.RBF && model.c == 1.0 && model.gamma == .005 && model.p == .001)
                    require(model.termCriteria.type == TermCriteria.MAX_ITER && model.termCriteria.maxCount == 10000)
                    val vectors = model.supportVectors
                    try {
                        require(vectors.rows() in 1..720 && vectors.cols() == 258)
                        val data = FloatArray(vectors.rows()*258)
                        try { vectors.get(0,0,data); require(data.all(Float::isFinite)) } finally { data.fill(0f) }
                    } finally { vectors.release() }
                    val alpha = Mat(); val indices = Mat()
                    try {
                        require(model.getDecisionFunction(0,alpha,indices).isFinite())
                        val coefficients = DoubleArray(alpha.total().toInt())
                        try { alpha.get(0,0,coefficients); require(coefficients.isNotEmpty() && coefficients.all(Double::isFinite)) }
                        finally { coefficients.fill(0.0) }
                    } finally { alpha.release(); indices.release() }
                }
                val restored = SvrCalibration(x,y)
                try { restored.trained = true; restored.predict(FloatArray(258)); return restored }
                catch (e: Throwable) { restored.close(); throw e }
            } catch (e: Throwable) { x?.clear(); y?.clear(); throw e }
        }
    }
}
