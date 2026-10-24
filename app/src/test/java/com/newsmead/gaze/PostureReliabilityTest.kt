package com.newsmead.gaze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostureReliabilityTest {

    private fun calibrationSamples(): List<CalibrationSample> = (0 until 16).map { index ->
        val offset = (index - 8) * 0.0005f
        CalibrationSample(
            screenX = index.toFloat(),
            screenY = index.toFloat(),
            gazeX = 0.5f,
            gazeY = 0.5f,
            faceCenterX = 0.5f + offset,
            faceCenterY = 0.35f - offset,
            faceScale = 0.18f + offset / 2f,
            headRollDeg = offset * 100f,
        )
    }

    @Test
    fun calibrationPoseIsInRange() {
        val profile = PostureProfile.fromCalibration(calibrationSamples())
        assertNotNull(profile)

        val assessment = profile!!.assess(
            PostureFeatures(0.5f, 0.35f, 0.18f, 0f),
        )

        assertEquals(PostureStatus.IN_RANGE, assessment.status)
        assertEquals(0f, assessment.maxNormalizedExcess)
    }

    @Test
    fun largePoseDepartureIsFlaggedWithoutChangingAnyCoordinate() {
        val profile = PostureProfile.fromCalibration(calibrationSamples())!!

        val assessment = profile.assess(
            PostureFeatures(0.62f, 0.35f, 0.18f, 0f),
        )

        assertEquals(PostureStatus.OUT_OF_RANGE, assessment.status)
        assertTrue("face_center_x" in assessment.outOfRangeAxes)
        assertTrue(assessment.maxNormalizedExcess > 0f)
    }

    @Test
    fun legacyCalibrationHasNoProfile() {
        val legacy = List(16) { CalibrationSample(0f, 0f, 0.5f, 0.5f) }

        assertEquals(null, PostureProfile.fromCalibration(legacy))
    }
}
