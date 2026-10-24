package com.newsmead.gaze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactFaceIrisGeometryTest {
    @Test
    fun faceCropUsesExpandedSquareBoxAndEyeLineRoll() {
        val crop = CompactFaceIrisGeometry.faceCrop(
            rightEye = HybridEyeGeometry.Point(180f, 110f),
            leftEye = HybridEyeGeometry.Point(100f, 90f),
            boxLeft = 60f,
            boxTop = 40f,
            boxRight = 220f,
            boxBottom = 180f,
        )

        assertNotNull(crop)
        crop!!
        assertEquals(140f, crop.center.x, 1e-4f)
        assertEquals(110f, crop.center.y, 1e-4f)
        assertEquals(240f, crop.sidePx, 1e-4f)
        assertEquals(14.036f, crop.rotationDegrees, 1e-3f)
    }

    @Test
    fun faceCropRejectsDegenerateEyeGeometry() {
        assertNull(
            CompactFaceIrisGeometry.faceCrop(
                rightEye = HybridEyeGeometry.Point(100f, 100f),
                leftEye = HybridEyeGeometry.Point(101f, 100f),
                boxLeft = 50f,
                boxTop = 50f,
                boxRight = 200f,
                boxBottom = 200f,
            ),
        )
    }

    @Test
    fun projectMapsFaceTensorThroughScaleAndRoll() {
        val landmarks = FloatArray(CompactFaceIrisGeometry.FACE_LANDMARK_FLOATS)
        setPoint(landmarks, 33, 106f, 96f)
        val crop = CompactFaceIrisGeometry.FaceCrop(
            center = HybridEyeGeometry.Point(300f, 400f),
            sidePx = 384f,
            rotationDegrees = 90f,
        )

        val projected = CompactFaceIrisGeometry.project(landmarks, 33, crop)

        assertNotNull(projected)
        assertEquals(300f, projected!!.x, 1e-3f)
        assertEquals(420f, projected.y, 1e-3f)
    }

    @Test
    fun eyeCropsUseCompactFaceCornersAndOfficialScale() {
        val landmarks = FloatArray(CompactFaceIrisGeometry.FACE_LANDMARK_FLOATS)
        setPoint(landmarks, 33, 120f, 80f)
        setPoint(landmarks, 133, 140f, 80f)
        setPoint(landmarks, 362, 52f, 80f)
        setPoint(landmarks, 263, 72f, 80f)
        val faceCrop = CompactFaceIrisGeometry.FaceCrop(
            center = HybridEyeGeometry.Point(96f, 96f),
            sidePx = 192f,
            rotationDegrees = 0f,
        )

        val crops = CompactFaceIrisGeometry.eyeCrops(landmarks, faceCrop)

        assertNotNull(crops)
        crops!!
        assertEquals(130f, crops.rightEye.center.x, 1e-4f)
        assertEquals(80f, crops.rightEye.center.y, 1e-4f)
        assertEquals(46f, crops.rightEye.sidePx, 1e-4f)
        assertTrue(crops.rightEye.flipHorizontally)
        assertEquals(62f, crops.leftEye.center.x, 1e-4f)
        assertEquals(80f, crops.leftEye.center.y, 1e-4f)
        assertEquals(46f, crops.leftEye.sidePx, 1e-4f)
        assertFalse(crops.leftEye.flipHorizontally)
    }

    @Test
    fun eyeCropsRejectMissingLandmarkVector() {
        val crop = CompactFaceIrisGeometry.FaceCrop(
            center = HybridEyeGeometry.Point(96f, 96f),
            sidePx = 192f,
            rotationDegrees = 0f,
        )

        assertNull(CompactFaceIrisGeometry.eyeCrops(FloatArray(10), crop))
    }

    private fun setPoint(values: FloatArray, index: Int, x: Float, y: Float) {
        val offset = index * CompactFaceIrisGeometry.LANDMARK_DIMS
        values[offset] = x
        values[offset + 1] = y
        values[offset + 2] = 0f
    }
}
