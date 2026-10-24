package com.newsmead.mgazenetbenchmark

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/** Upright RGB without selfie mirroring. Media image wrappers may consume the returned bitmap. */
class CameraFrames : AutoCloseable {
    private var row: Bitmap? = null
    private var upright: Bitmap? = null
    private var pixels = IntArray(0)
    private var rgb = ByteArray(0)
    private var rgba = ByteArray(0)
    private val matrix = Matrix()
    private val paint = Paint()

    fun copy(image: ImageProxy): Bitmap {
        val plane = image.planes[0]
        require(plane.pixelStride == 4)
        return copyRgba(plane.buffer, image.width, image.height, plane.rowStride, image.imageInfo.rotationDegrees)
    }

    internal fun copyRgba(input: ByteBuffer, width: Int, height: Int, stride: Int, rotation: Int): Bitmap {
        require(width > 0 && height > 0 && stride >= width * 4 && rotation in listOf(0,90,180,270))
        if (row?.width != width || row?.height != height) {
            row?.recycle(); row = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        // Last row may omit stride padding; copy only valid RGBA pixels explicitly.
        if (pixels.size != width * height) pixels = IntArray(width * height)
        if (rgba.size != width * height * 4) rgba = ByteArray(width * height * 4)
        val buffer = input.duplicate()
        for (y in 0 until height) {
            buffer.position(y * stride)
            buffer.get(rgba, y * width * 4, width * 4)
        }
        for (i in pixels.indices) {
            val r = rgba[i * 4].toInt() and 255
            val g = rgba[i * 4 + 1].toInt() and 255
            val b = rgba[i * 4 + 2].toInt() and 255
            val a = rgba[i * 4 + 3].toInt() and 255
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        row!!.setPixels(pixels, 0, width, 0, 0, width, height)
        matrix.reset(); matrix.postRotate(rotation.toFloat())
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        matrix.mapRect(bounds)
        val w = bounds.width().toInt(); val h = bounds.height().toInt()
        if (upright == null || upright!!.isRecycled || upright!!.width != w || upright!!.height != h) {
            if (upright?.isRecycled == false) upright?.recycle()
            upright = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }
        Canvas(upright!!).apply {
            save(); translate(-bounds.left, -bounds.top); concat(this@CameraFrames.matrix)
            clipRect(0, 0, width, height)
            drawBitmap(row!!, 0f, 0f, paint); restore()
        }
        return upright!!
    }
    fun rgb(bitmap: Bitmap): RgbFrame {
        val count = bitmap.width * bitmap.height
        if (pixels.size != count) pixels = IntArray(count)
        if (rgb.size != count * 3) rgb = ByteArray(count * 3)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (i in pixels.indices) {
            rgb[i * 3] = (pixels[i] ushr 16).toByte()
            rgb[i * 3 + 1] = (pixels[i] ushr 8).toByte()
            rgb[i * 3 + 2] = pixels[i].toByte()
        }
        return RgbFrame(bitmap.width, bitmap.height, rgb)
    }
    override fun close() { row?.recycle(); upright?.recycle(); row = null; upright = null }
}
