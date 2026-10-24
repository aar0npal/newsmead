package com.newsmead.gaze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridEyeGeometryTest {
    @Test
    fun cropPairPreservesAnatomicalEyesAndUsesScreenOrderedRoll() {
        val pair = HybridEyeGeometry.cropPair(
            rightEye = HybridEyeGeometry.Point(80f, 44f),
            leftEye = HybridEyeGeometry.Point(20f, 38f),
            faceWidthPx = 150f,
        )

        assertNotNull(pair)
        pair!!
        assertEquals(80f, pair.rightEye.center.x, 0f)
        assertEquals(20f, pair.leftEye.center.x, 0f)
        assertTrue(pair.rightEye.flipHorizontally)
        assertFalse(pair.leftEye.flipHorizontally)
        assertEquals(63.3143f, pair.rightEye.sidePx, 0.001f)
        assertEquals(5.7106f, pair.rightEye.rotationDegrees, 0.001f)
    }

    @Test
    fun cropPairRejectsDegenerateEyeCentres() {
        assertNull(
            HybridEyeGeometry.cropPair(
                rightEye = HybridEyeGeometry.Point(10f, 10f),
                leftEye = HybridEyeGeometry.Point(14f, 10f),
                faceWidthPx = 100f,
            ),
        )
    }

    @Test
    fun decodeEyeMatchesActiveEyeLocalGeometry() {
        val eye = landmarks(HybridEyeGeometry.EYE_LANDMARK_COUNT)
        setPoint(eye, 0, 12f, 32f)
        setPoint(eye, 8, 52f, 32f)
        setPoint(eye, 12, 32f, 26f)
        setPoint(eye, 4, 32f, 38f)
        val iris = landmarks(HybridEyeGeometry.IRIS_LANDMARK_COUNT)
        repeat(HybridEyeGeometry.IRIS_LANDMARK_COUNT) { setPoint(iris, it, 38f, 36f) }

        val sample = HybridEyeGeometry.decodeEye(eye, iris, wasFlipped = false)

        assertNotNull(sample)
        sample!!
        assertEquals(0.65f, sample.feature.horizontal, 0.0001f)
        assertEquals(0.60f, sample.feature.vertical, 0.0001f)
        assertEquals(0.30f, sample.openness, 0.0001f)
    }

    @Test
    fun flippedRightEyeIsUnflippedBeforeFeatureCalculation() {
        val unflippedEye = landmarks(HybridEyeGeometry.EYE_LANDMARK_COUNT)
        setPoint(unflippedEye, 0, 12f, 32f)
        setPoint(unflippedEye, 8, 52f, 32f)
        setPoint(unflippedEye, 12, 32f, 26f)
        setPoint(unflippedEye, 4, 32f, 38f)
        val unflippedIris = landmarks(HybridEyeGeometry.IRIS_LANDMARK_COUNT)
        repeat(HybridEyeGeometry.IRIS_LANDMARK_COUNT) { setPoint(unflippedIris, it, 38f, 36f) }

        val flippedEye = flipX(unflippedEye)
        val flippedIris = flipX(unflippedIris)
        val expected = HybridEyeGeometry.decodeEye(unflippedEye, unflippedIris, false)!!
        val actual = HybridEyeGeometry.decodeEye(flippedEye, flippedIris, true)!!

        assertEquals(expected.feature.horizontal, actual.feature.horizontal, 0.0001f)
        assertEquals(expected.feature.vertical, actual.feature.vertical, 0.0001f)
        assertEquals(expected.openness, actual.openness, 0.0001f)
    }

    @Test
    fun detectorAnchoredRefinementUsesEyeCornersAndOfficialScale() {
        val eye = decodedEye(cornerAX = 20f, cornerAY = 32f, cornerBX = 44f, cornerBY = 32f)
        val previous = HybridEyeGeometry.CropSpec(
            center = HybridEyeGeometry.Point(100f, 80f),
            sidePx = 64f,
            rotationDegrees = 0f,
            flipHorizontally = false,
        )

        val refined = HybridEyeGeometry.detectorAnchoredRefinement(previous, eye)

        assertNotNull(refined)
        refined!!
        assertEquals(100f, refined.crop.center.x, 0.0001f)
        assertEquals(80f, refined.crop.center.y, 0.0001f)
        assertEquals(55.2f, refined.crop.sidePx, 0.0001f)
        assertEquals(0f, refined.crop.rotationDegrees, 0.0001f)
        assertEquals(0f, refined.centerShiftPx, 0.0001f)
        assertEquals(0.8625f, refined.sideRatio, 0.0001f)
    }

    @Test
    fun detectorAnchoredRefinementProjectsCentreThroughDetectorRoll() {
        val eye = decodedEye(cornerAX = 24f, cornerAY = 32f, cornerBX = 48f, cornerBY = 32f)
        val previous = HybridEyeGeometry.CropSpec(
            center = HybridEyeGeometry.Point(100f, 80f),
            sidePx = 64f,
            rotationDegrees = 90f,
            flipHorizontally = true,
        )

        val refined = HybridEyeGeometry.detectorAnchoredRefinement(previous, eye)

        assertNotNull(refined)
        refined!!
        assertEquals(100f, refined.crop.center.x, 0.0001f)
        assertEquals(84f, refined.crop.center.y, 0.0001f)
        assertEquals(4f, refined.centerShiftPx, 0.0001f)
        assertEquals(90f, refined.crop.rotationDegrees, 0.0001f)
        assertTrue(refined.crop.flipHorizontally)
    }

    @Test
    fun detectorAnchoredRefinementBoundsASevereSizeOutlier() {
        val eye = decodedEye(cornerAX = 0f, cornerAY = 0f, cornerBX = 64f, cornerBY = 64f)
        val previous = HybridEyeGeometry.CropSpec(
            center = HybridEyeGeometry.Point(100f, 80f),
            sidePx = 64f,
            rotationDegrees = 0f,
            flipHorizontally = false,
        )

        val refined = HybridEyeGeometry.detectorAnchoredRefinement(previous, eye)

        assertNotNull(refined)
        refined!!
        assertEquals(83.2f, refined.crop.sidePx, 0.0001f)
        assertEquals(1.3f, refined.sideRatio, 0.0001f)
        assertEquals(0f, refined.crop.rotationDegrees, 0.0001f)
    }

    @Test
    fun detectorAnchoredRefinementRejectsASevereCentreOutlier() {
        val eye = decodedEye(cornerAX = 48f, cornerAY = 48f, cornerBX = 62f, cornerBY = 62f)
        val previous = HybridEyeGeometry.CropSpec(
            center = HybridEyeGeometry.Point(100f, 80f),
            sidePx = 64f,
            rotationDegrees = 0f,
            flipHorizontally = false,
        )

        assertNull(HybridEyeGeometry.detectorAnchoredRefinement(previous, eye))
    }

    private fun landmarks(count: Int): FloatArray = FloatArray(count * 3)

    private fun setPoint(values: FloatArray, index: Int, x: Float, y: Float) {
        values[index * 3] = x
        values[index * 3 + 1] = y
    }

    private fun flipX(values: FloatArray): FloatArray = values.copyOf().also { flipped ->
        for (offset in flipped.indices step 3) {
            flipped[offset] = HybridEyeGeometry.MODEL_SIZE - flipped[offset]
        }
    }


    private fun decodedEye(
        cornerAX: Float,
        cornerAY: Float,
        cornerBX: Float,
        cornerBY: Float,
    ): HybridEyeGeometry.EyeSample {
        val eye = landmarks(HybridEyeGeometry.EYE_LANDMARK_COUNT)
        setPoint(eye, 0, cornerAX, cornerAY)
        setPoint(eye, 8, cornerBX, cornerBY)
        setPoint(eye, 12, 32f, 27f)
        setPoint(eye, 4, 32f, 37f)
        val iris = landmarks(HybridEyeGeometry.IRIS_LANDMARK_COUNT)
        repeat(HybridEyeGeometry.IRIS_LANDMARK_COUNT) { setPoint(iris, it, 32f, 32f) }
        return HybridEyeGeometry.decodeEye(eye, iris, false)!!
    }
}
