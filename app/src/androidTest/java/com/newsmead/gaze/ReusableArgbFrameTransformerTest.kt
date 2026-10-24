package com.newsmead.gaze

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReusableArgbFrameTransformerTest {
    @Test
    fun transform_matchesPreviousPixelOutputForCameraRotations() {
        val source = patternedBitmap(width = 8, height = 6)
        val transformer = ReusableArgbFrameTransformer()

        for (rotation in listOf(0, 90, 180, 270)) {
            val expected = legacyTransform(source, rotation)
            val actual = transformer.transform(source, rotation)

            assertEquals("width at rotation $rotation", expected.width, actual.width)
            assertEquals("height at rotation $rotation", expected.height, actual.height)
            assertArrayEquals(
                "pixels at rotation $rotation",
                expected.readPixels(),
                actual.readPixels(),
            )
            expected.recycle()
        }
    }

    @Test
    fun transform_reusesOutputBufferWhenGeometryIsUnchanged() {
        val source = patternedBitmap(width = 8, height = 6)
        val transformer = ReusableArgbFrameTransformer()

        val first = transformer.transform(source, 270)
        val second = transformer.transform(source, 270)

        assertEquals(first, second)
    }

    private fun legacyTransform(source: Bitmap, rotationDegrees: Int): Bitmap {
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
            postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun patternedBitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    setPixel(
                        x,
                        y,
                        Color.argb(
                            255,
                            (x * 31 + y * 7) and 0xff,
                            (x * 11 + y * 37) and 0xff,
                            (x * 19 + y * 13) and 0xff,
                        ),
                    )
                }
            }
        }

    private fun Bitmap.readPixels(): IntArray = IntArray(width * height).also {
        getPixels(it, 0, width, 0, 0, width, height)
    }
}
