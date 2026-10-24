package com.newsmead.gaze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationCsvCodecTest {

    @Test
    fun readsLegacyFourColumnCalibration() {
        val sample = CalibrationCsvCodec.decode("100.0,200.0,0.4,0.6")

        assertEquals(100f, sample.screenX)
        assertEquals(0.4f, sample.gazeX)
        assertFalse(sample.hasPerEye)
    }

    @Test
    fun roundTripsPerEyeAndPostureCalibration() {
        val original = CalibrationSample(
            screenX = 100f,
            screenY = 200f,
            gazeX = 0.4f,
            gazeY = 0.6f,
            eye1X = 0.35f,
            eye1Y = 0.55f,
            eye2X = 0.45f,
            eye2Y = 0.65f,
            faceCenterX = 0.51f,
            faceCenterY = 0.32f,
            faceScale = 0.19f,
            headRollDeg = -2.5f,
        )

        val decoded = CalibrationCsvCodec.decode(CalibrationCsvCodec.encode(original))

        assertEquals(original, decoded)
        assertTrue(decoded.hasPerEye)
        assertTrue(decoded.posture.isAvailable)
    }

    @Test
    fun readsExistingEightColumnCalibrationWithoutPosture() {
        val sample = CalibrationCsvCodec.decode("100,200,0.4,0.6,0.35,0.55,0.45,0.65")

        assertTrue(sample.hasPerEye)
        assertFalse(sample.posture.isAvailable)
    }
}
