package com.newsmead.gaze.mgazenet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MgazeNetPostureGateTest {
    private fun sample(
        centerX: Double = .50,
        centerY: Double = .48,
        width: Double = .40,
        height: Double = .52,
        eyeX: Double = .50,
        eyeY: Double = .38,
        separation: Double = .55,
        roll: Double = 1.0,
    ) = MgazeNetPostureGate.Sample(centerX,centerY,width,height,eyeX,eyeY,separation,roll)

    private fun reference() = MgazeNetPostureGate.reference(List(45) { index ->
        sample(centerX = .50+(index%3-1)*.002,centerY = .48+(index%5-2)*.001)
    })!!

    @Test fun cropGeometryCreatesFiniteNormalizedSample() {
        val crops = GazeGeometry.Crops(
            GazeGeometry.Box(120,60,280,320),
            GazeGeometry.Box(180,150,70,50),
            GazeGeometry.Box(300,154,70,50),
            100.0,100.0,
        )
        val result = MgazeNetPostureGate.from(crops,640,480)
        assertNotNull(result)
        assertTrue(result!!.valid)
    }

    @Test fun stablePracticeBuildsReferenceAndNearbySamplePasses() {
        val reference = reference()
        assertTrue(reference.inlierCount >= MgazeNetPostureGate.MIN_REFERENCE_INLIERS)
        assertTrue(MgazeNetPostureGate.accepts(reference,sample(centerX = .53,centerY = .51,roll = 6.0)))
    }

    @Test fun translationScaleFaceGeometryAndRollDriftFail() {
        val reference = reference()
        assertFalse(MgazeNetPostureGate.accepts(reference,sample(centerX = .59)))
        assertFalse(MgazeNetPostureGate.accepts(reference,sample(width = .31)))
        assertFalse(MgazeNetPostureGate.accepts(reference,sample(eyeX = .66)))
        assertFalse(MgazeNetPostureGate.accepts(reference,sample(separation = .70)))
        assertFalse(MgazeNetPostureGate.accepts(reference,sample(roll = 13.1)))
    }

    @Test fun unstablePracticeDoesNotCreateReference() {
        val unstable = List(45) { index -> sample(centerX = if (index%2 == 0) .35 else .65) }
        assertTrue(MgazeNetPostureGate.reference(unstable) == null)
    }
}
