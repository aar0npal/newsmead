package com.newsmead.gaze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class DriftCorrectionTest {

    /** 3x3 grid of predicted positions like the accuracy test's 20/50/80%. */
    private fun predictedGrid(): List<Pair<Float, Float>> = buildList {
        for (fy in listOf(0.2f, 0.5f, 0.8f)) {
            for (fx in listOf(0.2f, 0.5f, 0.8f)) {
                add(1080f * fx to 2340f * fy)
            }
        }
    }

    private fun observations(transform: (Float, Float) -> Pair<Float, Float>) =
        predictedGrid().map { (px, py) ->
            val (tx, ty) = transform(px, py)
            DriftCorrection.Observation(px, py, tx, ty)
        }

    @Test
    fun recoversPureTranslation() {
        val obs = observations { x, y -> x + 30f to y - 50f }
        val correction = DriftCorrection.fit(obs)
        requireNotNull(correction)
        for (o in obs) {
            val corrected = correction.apply(o.predictedX, o.predictedY)
            assertEquals(o.targetX, corrected[0], 0.5f)
            assertEquals(o.targetY, corrected[1], 0.5f)
        }
        assertTrue(DriftCorrection.medianResidualPx(correction, obs) < 1f)
    }

    @Test
    fun recoversGainTiltAndOffset() {
        val obs = observations { x, y ->
            (1.1f * x + 0.05f * y + 20f) to (-0.03f * x + 0.95f * y - 40f)
        }
        val correction = DriftCorrection.fit(obs)
        requireNotNull(correction)
        for (o in obs) {
            val corrected = correction.apply(o.predictedX, o.predictedY)
            assertEquals(o.targetX, corrected[0], 0.5f)
            assertEquals(o.targetY, corrected[1], 0.5f)
        }
    }

    @Test
    fun correctionReducesResidualOnDriftedData() {
        val obs = observations { x, y -> x + 80f to y + 120f }
        val before = obs.map {
            hypot(it.predictedX - it.targetX, it.predictedY - it.targetY)
        }.sorted()[obs.size / 2]
        val correction = DriftCorrection.fit(obs)
        requireNotNull(correction)
        val after = DriftCorrection.medianResidualPx(correction, obs)
        assertTrue("before=$before after=$after", after < before / 10f)
    }

    @Test
    fun composeMatchesSequentialApplication() {
        val inner = DriftCorrection.fit(observations { x, y -> x + 30f to y - 20f })!!
        val outer = DriftCorrection.fit(
            observations { x, y -> (1.05f * x + 10f) to (0.97f * y + 5f) },
        )!!
        val composed = DriftCorrection.compose(outer, inner)
        for ((px, py) in predictedGrid()) {
            val sequential = inner.apply(px, py).let { outer.apply(it[0], it[1]) }
            val direct = composed.apply(px, py)
            assertEquals(sequential[0], direct[0], 0.5f)
            assertEquals(sequential[1], direct[1], 0.5f)
        }
    }

    @Test
    fun leaveOneOutIsSmallForConsistentAffineDrift() {
        // A clean affine drift generalizes: LOO error stays tiny.
        val obs = observations { x, y -> (1.05f * x + 20f) to (0.98f * y - 30f) }
        val loo = DriftCorrection.leaveOneOutMedianPx(obs)
        requireNotNull(loo)
        assertTrue("loo=$loo", loo < 2f)
    }

    @Test
    fun leaveOneOutPunishesNonAffineNoise() {
        // Add a large per-point perturbation the affine model can't capture: the
        // in-sample residual understates the true error; LOO exposes it.
        val base = observations { x, y -> x + 20f to y - 20f }.toMutableList()
        base[4] = base[4].copy(targetX = base[4].targetX + 250f, targetY = base[4].targetY - 250f)
        val fit = DriftCorrection.fit(base)!!
        val inSample = DriftCorrection.medianResidualPx(fit, base)
        val loo = DriftCorrection.leaveOneOutMedianPx(base)!!
        assertTrue("loo=$loo inSample=$inSample", loo >= inSample)
    }

    @Test
    fun leaveOneOutNullWhenTooFew() {
        val obs = observations { x, y -> x to y }.take(4)
        assertNull(DriftCorrection.leaveOneOutMedianPx(obs))
    }

    @Test
    fun nullWhenTooFewObservations() {
        val obs = observations { x, y -> x to y }.take(3)
        assertNull(DriftCorrection.fit(obs))
    }

    @Test
    fun nullWhenDegenerate() {
        // All predicted points identical: no spread to fit gain/tilt from.
        val obs = (0 until 9).map {
            DriftCorrection.Observation(500f, 500f, 500f + it, 600f + it)
        }
        assertNull(DriftCorrection.fit(obs))
    }
}
