package com.newsmead.mgazenetbenchmark

import android.content.Context
import android.os.SystemClock
import com.taobao.android.mnn.MNNNetNative as Native
import java.security.MessageDigest

/** Thread confined, CPU only. No calibration files, screen coordinates or callbacks. */
class MnnEstimator(context: Context, private val threads: Int = 4) : AutoCloseable {
    private var net = 0L
    private val session: Long
    private val inputs: LongArray
    private val output: Long
    private val values = FloatArray(258)

    init {
        require(threads in 1..8)
        val bytes = context.assets.open("base.mnn").use { it.readBytes() }
        check(sha256(bytes) == MODEL_SHA) { "Model hash mismatch" }
        net = Native.nativeCreateNetFromBuffer(bytes)
        check(net != 0L) { "MNN model load failed" }
        try {
            session = Native.nativeCreateSession(net, 0, threads, null, null)
            check(session != 0L) { "MNN CPU session failed" }
            inputs = arrayOf("face", "left", "right", "rect").map { name ->
                Native.nativeGetSessionInput(net, session, name).also { check(it != 0L) { "Missing $name" } }
            }.toLongArray()
            val shapes = arrayOf(intArrayOf(1,3,224,224), intArrayOf(1,3,112,112), intArrayOf(1,3,112,112), intArrayOf(1,12))
            inputs.indices.forEach { check(Native.nativeTensorGetDimensions(inputs[it]).contentEquals(shapes[it])) { "Unexpected input layout" } }
            output = Native.nativeGetSessionOutput(net, session, "output_0")
            check(output != 0L && Native.nativeTensorGetData(output, null) == 258) { "Unexpected output shape" }
        } catch (error: Throwable) { close(); throw error }
    }

    fun infer(input: Preprocessor.Inputs): FloatArray {
        check(net != 0L)
        val arrays = arrayOf(input.face, input.left, input.right, input.rect)
        val sizes = intArrayOf(150528, 37632, 37632, 12)
        arrays.indices.forEach {
            // JNI writes directly into CPU tensors; reject bad lengths before native code.
            require(arrays[it].size == sizes[it] && arrays[it].all { x -> x.isFinite() })
            Native.nativeSetInputFloatData(net, inputs[it], arrays[it])
        }
        check(Native.nativeRunSession(net, session) == 0) { "MNN inference failed" }
        check(Native.nativeTensorGetData(output, values) != 0)
        check(values.all { it.isFinite() }) { "Non-finite model output" }
        return values.copyOf()
    }

    data class ProfiledInference(val features: FloatArray, val validationMs: Double,
                                val inputCopyMs: Double, val nativeRunMs: Double,
                                val outputReadMs: Double, val totalMs: Double)

    /** Explicit synthetic profiling only; ordinary infer keeps its original timing path. */
    fun profileInference(input: Preprocessor.Inputs, rangeValidation: Boolean = false): ProfiledInference {
        check(net != 0L)
        val start = SystemClock.elapsedRealtimeNanos()
        val arrays = arrayOf(input.face, input.left, input.right, input.rect)
        val sizes = intArrayOf(150528, 37632, 37632, 12)
        arrays.indices.forEach {
            require(arrays[it].size == sizes[it])
            require(if (rangeValidation) FloatInputValidation.allFinite(arrays[it]) else arrays[it].all { x -> x.isFinite() })
        }
        val validated = SystemClock.elapsedRealtimeNanos()
        arrays.indices.forEach { Native.nativeSetInputFloatData(net, inputs[it], arrays[it]) }
        val copied = SystemClock.elapsedRealtimeNanos()
        check(Native.nativeRunSession(net, session) == 0) { "MNN inference failed" }
        val ran = SystemClock.elapsedRealtimeNanos()
        check(Native.nativeTensorGetData(output, values) != 0)
        check(values.all { it.isFinite() }) { "Non-finite model output" }
        val features = values.copyOf()
        val read = SystemClock.elapsedRealtimeNanos()
        return ProfiledInference(features, (validated-start)/1e6, (copied-validated)/1e6,
            (ran-copied)/1e6, (read-ran)/1e6, (read-start)/1e6)
    }

    override fun close() { if (net != 0L) { Native.nativeReleaseNet(net); net = 0L } }
    companion object {
        const val MODEL_SHA = "2f96b95275fe6d7b79e98df3237ebb96e15ef5522c96968f08167da7e1954a96"
        const val RUNTIME = "MNN 3.6.1 CPU Session, 4 threads, default precision (not upstream Module low precision)"
        fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
