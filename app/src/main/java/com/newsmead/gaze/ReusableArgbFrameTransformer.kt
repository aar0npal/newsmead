package com.newsmead.gaze

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import kotlin.math.roundToInt

/**
 * Copies CameraX RGBA frames into reusable bitmaps and applies the same upright,
 * front-camera mirror transform that was previously performed with
 * [Bitmap.createBitmap] for every frame.
 *
 * The caller must keep at most one transformed bitmap in flight. NewsMead's raw
 * gaze source already enforces that contract with its `busy` guard.
 */
internal class ReusableArgbFrameTransformer {
    private var rowBitmap: Bitmap? = null
    private var croppedBitmap: Bitmap? = null
    private var transformedBitmap: Bitmap? = null
    private val cropPaint = Paint()
    private val transformPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val transformMatrix = Matrix()
    private val transformedBounds = RectF()

    fun copyAndTransform(imageProxy: ImageProxy, rotationDegrees: Int): Bitmap {
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        buffer.rewind()

        val pixelStride = plane.pixelStride.coerceAtLeast(1)
        val rowWidth = plane.rowStride / pixelStride
        val row = reusableBitmap(rowBitmap, rowWidth, imageProxy.height).also { rowBitmap = it }
        row.copyPixelsFromBuffer(buffer)

        val source = if (rowWidth == imageProxy.width) {
            row
        } else {
            val cropped = reusableBitmap(croppedBitmap, imageProxy.width, imageProxy.height)
                .also { croppedBitmap = it }
            Canvas(cropped).drawBitmap(
                row,
                Rect(0, 0, imageProxy.width, imageProxy.height),
                Rect(0, 0, imageProxy.width, imageProxy.height),
                cropPaint,
            )
            cropped
        }

        return transform(source, rotationDegrees)
    }

    /** Visible to the instrumentation test that compares this with the old path. */
    internal fun transform(source: Bitmap, rotationDegrees: Int): Bitmap {
        transformMatrix.reset()
        transformMatrix.postRotate(rotationDegrees.toFloat())
        transformMatrix.postScale(-1f, 1f)

        transformedBounds.set(0f, 0f, source.width.toFloat(), source.height.toFloat())
        transformMatrix.mapRect(transformedBounds)
        val outputWidth = transformedBounds.width().roundToInt().coerceAtLeast(1)
        val outputHeight = transformedBounds.height().roundToInt().coerceAtLeast(1)
        val output = reusableBitmap(transformedBitmap, outputWidth, outputHeight)
            .also { transformedBitmap = it }

        val canvas = Canvas(output)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.save()
        canvas.translate(-transformedBounds.left, -transformedBounds.top)
        canvas.concat(transformMatrix)
        canvas.drawBitmap(source, 0f, 0f, transformPaint)
        canvas.restore()
        return output
    }

    private fun reusableBitmap(current: Bitmap?, width: Int, height: Int): Bitmap {
        if (current != null && current.width == width && current.height == height && !current.isRecycled) {
            return current
        }
        current?.recycle()
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }
}
