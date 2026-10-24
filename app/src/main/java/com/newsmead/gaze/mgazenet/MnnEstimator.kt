package com.newsmead.gaze.mgazenet

import android.content.Context
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
        setInput(FACE_INPUT, input.face, FACE_SIZE)
        setInput(LEFT_INPUT, input.left, EYE_SIZE)
        setInput(RIGHT_INPUT, input.right, EYE_SIZE)
        setInput(RECT_INPUT, input.rect, RECT_SIZE)
        check(Native.nativeRunSession(net, session) == 0) { "MNN inference failed" }
        check(Native.nativeTensorGetData(output, values) != 0)
        check(FloatInputValidation.allFinite(values)) { "Non-finite model output" }
        return values.copyOf().also { values.fill(0f) }
    }

    private fun setInput(index: Int, values: FloatArray, expectedSize: Int) {
        // JNI writes directly into CPU tensors; reject malformed data before native code.
        require(values.size == expectedSize && FloatInputValidation.allFinite(values))
        Native.nativeSetInputFloatData(net, inputs[index], values)
    }

    override fun close() { values.fill(0f); if (net != 0L) { Native.nativeReleaseNet(net); net = 0L } }
    companion object {
        private const val FACE_INPUT = 0
        private const val LEFT_INPUT = 1
        private const val RIGHT_INPUT = 2
        private const val RECT_INPUT = 3
        private const val FACE_SIZE = 3 * 224 * 224
        private const val EYE_SIZE = 3 * 112 * 112
        private const val RECT_SIZE = 12
        const val MODEL_SHA = "2f96b95275fe6d7b79e98df3237ebb96e15ef5522c96968f08167da7e1954a96"
        const val RUNTIME = "MNN 3.6.1 CPU Session, 4 threads, default precision (not upstream Module low precision)"
        fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
