package com.newsmead.gaze

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class EyeLocalGazeGeometryTest {

    @Test
    fun eyeCentreProducesHalfOnBothAxes() {
        val feature = EyeLocalGazeGeometry.feature(
            iris = point(5f, 2f),
            cornerA = point(0f, 2f),
            cornerB = point(10f, 2f),
        )

        assertEquals(0.5f, feature.horizontal, EPSILON)
        assertEquals(0.5f, feature.vertical, EPSILON)
    }

    @Test
    fun projectionIsInvariantToTranslationScaleAndRoll() {
        val left = point(0f, 0f)
        val right = point(10f, 0f)
        val iris = point(6f, 1f)
        val expected = EyeLocalGazeGeometry.feature(iris, left, right)

        val transformed = EyeLocalGazeGeometry.feature(
            iris = transform(iris, scale = 3f, angleDeg = 24f, tx = 80f, ty = 45f),
            cornerA = transform(left, scale = 3f, angleDeg = 24f, tx = 80f, ty = 45f),
            cornerB = transform(right, scale = 3f, angleDeg = 24f, tx = 80f, ty = 45f),
        )

        assertEquals(0.6f, expected.horizontal, EPSILON)
        assertEquals(0.6f, expected.vertical, EPSILON)
        assertEquals(expected.horizontal, transformed.horizontal, EPSILON)
        assertEquals(expected.vertical, transformed.vertical, EPSILON)
    }

    @Test
    fun cornerIndexOrderDoesNotChangeTheFeature() {
        val iris = point(4f, -1f)
        val a = point(0f, 0f)
        val b = point(10f, 0f)

        val forward = EyeLocalGazeGeometry.feature(iris, a, b)
        val reversed = EyeLocalGazeGeometry.feature(iris, b, a)

        assertEquals(forward.horizontal, reversed.horizontal, EPSILON)
        assertEquals(forward.vertical, reversed.vertical, EPSILON)
    }

    private fun transform(
        point: EyeLocalGazeGeometry.Point,
        scale: Float,
        angleDeg: Float,
        tx: Float,
        ty: Float,
    ): EyeLocalGazeGeometry.Point {
        val angle = Math.toRadians(angleDeg.toDouble())
        val c = cos(angle).toFloat()
        val s = sin(angle).toFloat()
        return EyeLocalGazeGeometry.Point(
            x = scale * (point.x * c - point.y * s) + tx,
            y = scale * (point.x * s + point.y * c) + ty,
        )
    }

    private fun point(x: Float, y: Float) = EyeLocalGazeGeometry.Point(x, y)

    companion object {
        private const val EPSILON = 1e-4f
    }
}
