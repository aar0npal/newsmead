package com.newsmead.gaze.mgazenet

import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MgazeNetEyelidGeometryTest {
    private fun fixture(
        width: Int = 640,
        height: Int = 480,
        scale: Double = 1.0,
        angle: Double = 0.0,
        leftGap: Double = 10.0,
        rightGap: Double = 20.0,
    ): Pair<DoubleArray, DoubleArray> {
        val x = DoubleArray(478)
        val y = DoubleArray(478)
        fun point(index: Int, px: Double, py: Double) {
            x[index] = (250.0 + scale * (px * cos(angle) - py * sin(angle))) / width
            y[index] = (200.0 + scale * (px * sin(angle) + py * cos(angle))) / height
        }
        fun eye(a: Int, b: Int, upper: Int, lower: Int, offset: Double, gap: Double) {
            point(a, offset, 0.0)
            point(b, offset + 100.0, 0.0)
            point(upper, offset + 45.0, -gap / 2)
            // Along-axis displacement must not inflate the perpendicular lid gap.
            point(lower, offset + 52.0, gap / 2)
        }
        eye(33, 133, 159, 145, 0.0, leftGap)
        eye(263, 362, 386, 374, 140.0, rightGap)
        return x to y
    }

    @Test fun pixelRatioIsInvariantToScaleRollAndFrameAspect() {
        for ((width, height) in listOf(640 to 480, 480 to 960, 1600 to 400)) {
            for (scale in listOf(.25, 1.0, 2.0)) {
                for (angle in listOf(0.0, .6, 1.5707963267948966, 2.9)) {
                    val (x, y) = fixture(width, height, scale, angle)
                    val result = MgazeNetEyelidGeometry.from(x, y, width, height)
                    assertNotNull(result)
                    assertEquals(.1, result!!.leftAspect, 1e-12)
                    assertEquals(.2, result.rightAspect, 1e-12)
                }
            }
        }
    }

    @Test fun collapsedAndReversedLidsProduceNonnegativeRatios() {
        val (x, y) = fixture(leftGap = 0.0, rightGap = -20.0)
        val result = MgazeNetEyelidGeometry.from(x, y, 640, 480)!!
        assertEquals(0.0, result.leftAspect, 0.0)
        assertEquals(.2, result.rightAspect, 1e-12)
    }

    @Test fun rejectsWrongArraysDimensionsAndSelectedNonfiniteCoordinates() {
        val (x, y) = fixture()
        assertNull(MgazeNetEyelidGeometry.from(x.copyOf(477), y, 640, 480))
        assertNull(MgazeNetEyelidGeometry.from(x, y.copyOf(479), 640, 480))
        assertNull(MgazeNetEyelidGeometry.from(x, y, 0, 480))
        assertNull(MgazeNetEyelidGeometry.from(x, y, 640, -1))
        for (index in listOf(33, 133, 159, 145, 263, 362, 386, 374)) {
            assertNull(MgazeNetEyelidGeometry.from(x.copyOf().also { it[index] = Double.NaN }, y, 640, 480))
            assertNull(MgazeNetEyelidGeometry.from(x, y.copyOf().also { it[index] = Double.POSITIVE_INFINITY }, 640, 480))
        }
        // Geometry consumes selected points only.
        assertNotNull(MgazeNetEyelidGeometry.from(x.copyOf().also { it[0] = Double.NaN }, y, 640, 480))
    }

    @Test fun rejectsEitherDegenerateCornerAxisAndArithmeticOverflow() {
        val (x, y) = fixture()
        for ((a, b) in listOf(33 to 133, 263 to 362)) {
            val collapsedX = x.copyOf().also { it[b] = it[a] }
            val collapsedY = y.copyOf().also { it[b] = it[a] }
            assertNull(MgazeNetEyelidGeometry.from(collapsedX, collapsedY, 640, 480))
        }
        assertNull(MgazeNetEyelidGeometry.from(x.copyOf().also { it[33] = Double.MAX_VALUE }, y, 640, 480))
        assertNull(MgazeNetEyelidGeometry.from(x, y.copyOf().also { it[145] = Double.MAX_VALUE }, 640, 480))
    }
}
