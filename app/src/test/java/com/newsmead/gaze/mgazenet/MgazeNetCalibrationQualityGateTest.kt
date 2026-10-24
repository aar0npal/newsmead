package com.newsmead.gaze.mgazenet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MgazeNetCalibrationQualityGateTest {
    private fun checks(
        radial: List<Pair<Double,Double>> = listOf(
            .30 to .20,.40 to .20,.50 to .30,.60 to .30,.70 to .40,.80 to .40,
        ),
        samples: Int = 24,
    ) = radial.mapIndexed { index, (dx, dy) ->
        MgazeNetCalibrationQualityGate.Check(
            index = index + 1,
            label = "Check ${index + 1}",
            sampleCount = samples,
            dxCm = dx,
            dyCm = dy,
            repeatedFitTarget = index < 2,
        )
    }

    @Test fun stableCompleteCalibrationPasses() {
        assertTrue(MgazeNetCalibrationQualityGate.evaluate(checks()).passed)
    }

    @Test fun incompleteOrSparseChecksFail() {
        assertFalse(MgazeNetCalibrationQualityGate.evaluate(checks().dropLast(1)).passed)
        assertFalse(MgazeNetCalibrationQualityGate.evaluate(checks(samples = 11)).passed)
    }

    @Test fun medianWorstVerticalAndRepeatLimitsAreEnforced() {
        assertFalse(MgazeNetCalibrationQualityGate.evaluate(checks(List(6) { 1.01 to 0.0 })).passed)
        assertFalse(MgazeNetCalibrationQualityGate.evaluate(checks(
            listOf(.2 to .1,.3 to .1,.4 to .1,.5 to .1,.6 to .1,2.01 to 0.0),
        )).passed)
        assertFalse(MgazeNetCalibrationQualityGate.evaluate(checks(List(6) { .1 to .81 })).passed)
        assertFalse(MgazeNetCalibrationQualityGate.evaluate(checks(
            listOf(1.51 to 0.0,.3 to .1,.4 to .1,.5 to .1,.6 to .1,.7 to .1),
        )).passed)
    }
}
