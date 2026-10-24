package com.newsmead.gaze.mgazenet

import android.graphics.Bitmap
import org.opencv.android.Utils
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
    private val rgba = Mat()
    private val source = Mat()
    private val resized = Mat()
    private val face = FloatArray(3 * 224 * 224)
    private val left = FloatArray(3 * 112 * 112)
    private val right = FloatArray(3 * 112 * 112)
    private val rect = FloatArray(12)
    private val faceBytes = ByteArray(face.size)
    private val eyeBytes = ByteArray(left.size)

    /** Returned arrays are reused; caller must finish inference before the next call. */
    fun prepare(frame: RgbFrame, crops: GazeGeometry.Crops): Inputs {
        source.create(frame.height, frame.width, CvType.CV_8UC3)
        source.put(0, 0, frame.rgb)
        return prepareSource(frame.width, frame.height, crops)
    }

    /** Bitmap path used by the camera pipeline; avoids a full-frame Kotlin RGB copy. */
    fun prepare(bitmap: Bitmap, crops: GazeGeometry.Crops): Inputs {
        require(!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0)
        Utils.bitmapToMat(bitmap, rgba)
        Imgproc.cvtColor(rgba, source, Imgproc.COLOR_RGBA2RGB)
        return prepareSource(bitmap.width, bitmap.height, crops)
    }

    private fun prepareSource(width: Int, height: Int, crops: GazeGeometry.Crops): Inputs {
        patch(width, height, crops.face, 224, false, face, faceBytes)
        patch(width, height, crops.left, 112, false, left, eyeBytes)
        patch(width, height, crops.right, 112, true, right, eyeBytes)
        return Inputs(face, left, right, crops.rectangles(width, height, rect))
    }

    private fun patch(width: Int, height: Int, box: GazeGeometry.Box, edge: Int, flip: Boolean, out: FloatArray, bytes: ByteArray) {
        val b = requireNotNull(GazeGeometry.clip(box, width, height)) { "Invalid crop" }
        val roi = source.submat(b.y, b.y + b.height, b.x, b.x + b.width)
        try { Imgproc.resize(roi, resized, Size(edge.toDouble(), edge.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR) }
        finally { roi.release() }
        resized.get(0, 0, bytes)
        val planeSize = edge * edge
        for (y in 0 until edge) for (x in 0 until edge) {
            val src = (y * edge + if (flip) edge - 1 - x else x) * 3
            val dst = y * edge + x
            out[dst] = NORMALIZED[bytes[src].toInt() and 255]
            out[planeSize + dst] = NORMALIZED[bytes[src + 1].toInt() and 255]
            out[2 * planeSize + dst] = NORMALIZED[bytes[src + 2].toInt() and 255]
        }
    }
    override fun close() { face.fill(0f); left.fill(0f); right.fill(0f); rect.fill(0f); faceBytes.fill(0); eyeBytes.fill(0); rgba.release(); source.release(); resized.release() }

    companion object {
        private val NORMALIZED = FloatArray(256) { it / 255f }
    }
}
