package com.newsmead.mgazenetbenchmark

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

data class RgbFrame(val width: Int, val height: Int, val rgb: ByteArray) {
    init { require(width > 0 && height > 0 && rgb.size == width * height * 3) }
}

/** GazeFollower ordering: uint8 linear resize, float /255, right-eye flip.
 * RGB is packed to NCHW for the stock MNN CPU Session JNI, not NHWC Module API.
 */
class Preprocessor : AutoCloseable {
    data class Inputs(val face: FloatArray, val left: FloatArray, val right: FloatArray, val rect: FloatArray)
    private val source = Mat()
    private val resized = Mat()
    private val face = FloatArray(3 * 224 * 224)
    private val left = FloatArray(3 * 112 * 112)
    private val right = FloatArray(3 * 112 * 112)
    private val faceBytes = ByteArray(face.size)
    private val eyeBytes = ByteArray(left.size)

    /** Returned arrays are reused; caller must finish inference before the next call. */
    fun prepare(frame: RgbFrame, crops: GazeGeometry.Crops): Inputs {
        source.create(frame.height, frame.width, CvType.CV_8UC3)
        source.put(0, 0, frame.rgb)
        patch(frame, crops.face, 224, false, face, faceBytes)
        patch(frame, crops.left, 112, false, left, eyeBytes)
        patch(frame, crops.right, 112, true, right, eyeBytes)
        return Inputs(face, left, right, crops.rectangles(frame.width, frame.height))
    }

    private fun patch(frame: RgbFrame, box: GazeGeometry.Box, edge: Int, flip: Boolean, out: FloatArray, bytes: ByteArray) {
        val b = requireNotNull(GazeGeometry.clip(box, frame.width, frame.height)) { "Invalid crop" }
        val roi = source.submat(b.y, b.y + b.height, b.x, b.x + b.width)
        try { Imgproc.resize(roi, resized, Size(edge.toDouble(), edge.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR) }
        finally { roi.release() }
        resized.get(0, 0, bytes)
        for (y in 0 until edge) for (x in 0 until edge) {
            val src = (y * edge + if (flip) edge - 1 - x else x) * 3
            val dst = y * edge + x
            for (c in 0..2) out[c * edge * edge + dst] = (bytes[src + c].toInt() and 255) / 255f
        }
    }
    override fun close() { source.release(); resized.release() }
}
