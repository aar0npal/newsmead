package com.newsmead.gaze.scroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleAutoScrollPolicyTest {
    private val body = ArticleAutoScrollPolicy.Geometry(
        screenHeightPx = 1000.0, textVisibleLeftPx = 20.0, textVisibleTopPx = 100.0,
        textVisibleRightPx = 580.0, textVisibleBottomPx = 900.0,
        viewportTopPx = 100.0, viewportBottomPx = 900.0,
        scrollYPx = 100.0, maxScrollYPx = 2000.0, geometryKey = 1L,
    )
    private fun sample(policy: ArticleAutoScrollPolicy, capture: Double = 100.0,
                       y: Double = 650.0, x: Double = 200.0,
                       delivery: Double = capture + 10.0, now: Double = delivery,
                       geometry: ArticleAutoScrollPolicy.Geometry = body): Boolean =
        policy.onSample(ArticleAutoScrollPolicy.Sample(capture, delivery, x, y), geometry, now)

    @Test fun targetSpeedIncreasesLinearlyWithScreenYAndCapsBelowFullSpeed() {
        listOf(100.0 to 0.0, 400.0 to 0.0, 525.0 to 30.0, 650.0 to 60.0,
            899.0 to 60.0).forEach { (y, expected) ->
            val policy = ArticleAutoScrollPolicy()
            assertTrue(sample(policy, y = y))
            assertEquals(expected, policy.status.targetSpeedPxPerSecond, 1e-9)
            assertEquals(0.0, policy.status.speedPxPerSecond, 0.0)
        }
        assertEquals("ARTICLE_AUTO_SCROLL_TRIAL_V3", ArticleAutoScrollPolicy.Config().version)
    }

    @Test fun firstFrameUsesAcceptanceBaselineAndMovesImmediatelyWithoutDwell() {
        val policy = ArticleAutoScrollPolicy()
        assertTrue(sample(policy, delivery = 120.0, now = 140.0))
        val sameTime = policy.advance(140.0, body)
        assertEquals(0.0, sameTime.deltaPx, 0.0)
        assertTrue(sameTime.active)
        val first = policy.advance(170.0, body)
        assertEquals(10.0, first.speedPxPerSecond, 1e-9)
        assertEquals(.15, first.deltaPx, 1e-9)
        assertEquals(100.0, first.sourceCaptureTimeMs!!, 0.0)
        assertEquals(120.0, first.sourceDeliveryTimeMs!!, 0.0)
        assertEquals(650.0, first.sourceYPx!!, 0.0)
    }

    @Test fun lowerAcceleratesAndUpperBrakesContinuouslyToZeroWithoutReverse() {
        val policy = ArticleAutoScrollPolicy()
        assertTrue(sample(policy))
        var frame = policy.advance(160.0, body)
        assertEquals(60.0 * 50 / 180, frame.speedPxPerSecond, 1e-9)
        assertTrue(sample(policy, 150.0, y = 800.0, x = 570.0))
        frame = policy.advance(210.0, body)
        assertEquals(60.0 * 100 / 180, frame.speedPxPerSecond, 1e-9)
        assertTrue(sample(policy, 200.0, y = 300.0, x = 21.0))
        assertEquals("slowing", policy.status.reason)
        frame = policy.advance(260.0, body)
        assertEquals(60.0 * 50 / 180, frame.speedPxPerSecond, 1e-9)
        assertTrue(frame.deltaPx > 0)
        assertTrue(sample(policy, 250.0, y = 400.0))
        frame = policy.advance(310.0, body)
        assertEquals(0.0, frame.speedPxPerSecond, 1e-9)
        assertTrue(frame.deltaPx >= 0)
        assertFalse(frame.active)
        assertEquals("waiting_lower", frame.reason)
        assertEquals(0.0, policy.advance(320.0, body).deltaPx, 0.0)
    }

    @Test fun frameClampDiscardsStalledUiTimeAndDoesNotAccumulateCatchup() {
        val policy = ArticleAutoScrollPolicy()
        assertTrue(sample(policy))
        val clamped = policy.advance(250.0, body)
        assertEquals(60.0 * 50 / 180, clamped.speedPxPerSecond, 1e-9)
        assertEquals((60.0 * 50 / 180) * .5 * .05, clamped.deltaPx, 1e-9)
        assertEquals(0.0, policy.advance(250.0, body).deltaPx, 0.0)
        val next = policy.advance(260.0, body)
        assertEquals(20.0, next.speedPxPerSecond, 1e-9)
        assertEquals((60.0 * 50 / 180 + 20.0) * .5 * .01, next.deltaPx, 1e-9)
    }

    @Test fun freshUpperIdleTimeDoesNotAccelerateTheFirstLowerRestartFrame() {
        val policy = ArticleAutoScrollPolicy()
        assertTrue(sample(policy, y = 300.0))
        assertFalse(policy.advance(120.0, body).active)
        for (capture in 150..450 step 50) assertTrue(sample(policy, capture.toDouble(), y = 300.0))
        assertTrue(sample(policy, 500.0, y = 650.0))
        val restarted = policy.advance(520.0, body)
        assertEquals(60.0 * 10 / 180, restarted.speedPxPerSecond, 1e-9)
        assertEquals((60.0 * 10 / 180) * .5 * .01, restarted.deltaPx, 1e-9)
        assertTrue(restarted.active)
    }

    @Test fun continuousTranslationScrollAndKeyChangesPreserveSpeedAndAbsoluteY() {
        val policy = ArticleAutoScrollPolicy()
        assertTrue(sample(policy))
        policy.advance(140.0, body)
        val shifted = body.copy(textVisibleTopPx = 110.0, textVisibleLeftPx = 25.0,
            scrollYPx = 110.0, geometryKey = 2L)
        assertTrue(sample(policy, 140.0, geometry = shifted))
        val frame = policy.advance(170.0, shifted)
        assertEquals(20.0, frame.speedPxPerSecond, 1e-9)
        assertEquals(60.0, frame.targetSpeedPxPerSecond, 1e-9)
        assertEquals(650.0, frame.sourceYPx!!, 0.0)
    }

    @Test fun missingBoundaryRetainsReplayClocksButFreshPointResumesImmediately() {
        val policy = ArticleAutoScrollPolicy()
        assertTrue(sample(policy))
        policy.advance(140.0, body)
        policy.interrupt("gaze_unavailable")
        val stopped = policy.advance(150.0, body)
        assertFalse(stopped.active)
        assertEquals(0.0, stopped.deltaPx, 0.0)
        assertNull(stopped.sourceYPx)
        assertFalse(sample(policy, delivery = 151.0))
        assertEquals("invalid_clock", policy.status.reason)
        assertTrue(sample(policy, 160.0))
        assertTrue(policy.advance(180.0, body).deltaPx > 0)
        policy.reset()
        assertTrue(sample(policy))
    }

    @Test fun captureGapAndDeliveryGapBreakSpeedBeforeAcceptingFreshPoint() {
        listOf(ArticleAutoScrollPolicy.Sample(261.0, 271.0, 200.0, 650.0),
            ArticleAutoScrollPolicy.Sample(200.0, 271.0, 200.0, 650.0)).forEach { fresh ->
            val policy = ArticleAutoScrollPolicy()
            assertTrue(sample(policy))
            policy.advance(140.0, body)
            assertTrue(policy.onSample(fresh, body, fresh.deliveryTimeMs))
            assertEquals(0.0, policy.status.speedPxPerSecond, 0.0)
            assertEquals(0.0, policy.advance(fresh.deliveryTimeMs, body).deltaPx, 0.0)
            assertTrue(policy.advance(fresh.deliveryTimeMs + 10, body).deltaPx > 0)
        }
    }

    @Test fun repeatedFreshPointsReachSpeedCapWithoutCooldownOrRowIdentity() {
        val policy = ArticleAutoScrollPolicy()
        assertTrue(sample(policy))
        for (capture in 150..350 step 50) {
            assertTrue(sample(policy, capture.toDouble(), y = 800.0, x = 30.0 + capture))
            val frame = policy.advance(capture + 10.0, body)
            assertTrue(frame.deltaPx > 0)
            assertTrue(frame.speedPxPerSecond <= 60.0)
        }
        assertEquals(60.0, policy.status.speedPxPerSecond, 1e-9)
    }

    @Test fun delayedAcceptanceCannotBridgeExpiredPriorSupport() {
        val policy = ArticleAutoScrollPolicy()
        assertTrue(sample(policy))
        policy.advance(140.0, body)
        assertTrue(sample(policy, 200.0, delivery = 210.0, now = 300.0))
        assertEquals(0.0, policy.status.speedPxPerSecond, 0.0)
        assertEquals(0.0, policy.advance(300.0, body).deltaPx, 0.0)
        assertTrue(policy.advance(310.0, body).deltaPx > 0)
    }

    @Test fun deliveryAndCaptureFreshnessBoundariesAreInclusiveAndTimersCannotRunForever() {
        val policy = ArticleAutoScrollPolicy()
        assertTrue(sample(policy))
        assertTrue(policy.advance(260.0, body).active) // delivery age exactly 150
        assertEquals("stale", policy.advance(260.001, body).reason)
        assertEquals(0.0, policy.advance(1000.0, body).deltaPx, 0.0)
        assertEquals(0.0, policy.advance(2000.0, body).deltaPx, 0.0)
        val delayed = ArticleAutoScrollPolicy()
        assertTrue(sample(delayed, delivery = 200.0, now = 350.0)) // capture age 250
        assertEquals("stale", delayed.advance(350.001, body).reason)
        val tooOld = ArticleAutoScrollPolicy()
        assertFalse(sample(tooOld, delivery = 200.0, now = 350.001))
        assertEquals("stale", tooOld.status.reason)
        assertFalse(sample(ArticleAutoScrollPolicy(), now = 260.001))
    }

    @Test fun invalidClocksHardStopAndEqualNowIsAllowedButCaptureAndDeliveryMustIncrease() {
        val invalid = listOf(
            ArticleAutoScrollPolicy.Sample(100.0, 120.0, 200.0, 650.0) to 120.0,
            ArticleAutoScrollPolicy.Sample(105.0, 110.0, 200.0, 650.0) to 120.0,
            ArticleAutoScrollPolicy.Sample(120.0, 119.0, 200.0, 650.0) to 120.0,
            ArticleAutoScrollPolicy.Sample(120.0, 130.0, 200.0, 650.0) to 120.0,
            ArticleAutoScrollPolicy.Sample(-1.0, 120.0, 200.0, 650.0) to 120.0,
            ArticleAutoScrollPolicy.Sample(Double.NaN, 120.0, 200.0, 650.0) to 120.0,
            ArticleAutoScrollPolicy.Sample(120.0, Double.POSITIVE_INFINITY, 200.0, 650.0) to 120.0,
            ArticleAutoScrollPolicy.Sample(120.0, 130.0, 200.0, 650.0) to Double.NaN,
        )
        invalid.forEach { (bad, now) ->
            val policy = ArticleAutoScrollPolicy()
            assertTrue(sample(policy))
            assertFalse(policy.onSample(bad, body, now))
            assertEquals(ArticleAutoScrollPolicy.Status("invalid_clock", 0.0, 0.0), policy.status)
        }
        val policy = ArticleAutoScrollPolicy()
        assertTrue(sample(policy, now = 150.0))
        assertTrue(sample(policy, 120.0, delivery = 130.0, now = 150.0))
        assertEquals(0.0, policy.advance(150.0, body).deltaPx, 0.0)
        assertEquals("invalid_clock", policy.advance(149.0, body).reason)
        assertEquals("invalid_clock", policy.advance(Double.POSITIVE_INFINITY, body).reason)
    }

    @Test fun invalidOrOutsideSampleConsumesItsCausalClocksAndCannotReplay() {
        val policy = ArticleAutoScrollPolicy()
        assertFalse(sample(policy, x = Double.NaN))
        assertFalse(sample(policy, delivery = 120.0))
        assertEquals("invalid_clock", policy.status.reason)
        assertTrue(sample(policy, 130.0))
        assertTrue(policy.advance(150.0, body).active)
        assertEquals("outside_article", policy.advance(160.0,
            body.copy(textVisibleBottomPx = 600.0)).reason)
        assertEquals(0.0, policy.advance(170.0, body).deltaPx, 0.0)
        listOf(19.0 to 650.0, 580.0 to 650.0, 200.0 to 99.0,
            200.0 to 900.0, Double.NaN to 650.0, 200.0 to Double.POSITIVE_INFINITY).forEach { (x, y) ->
            val outside = ArticleAutoScrollPolicy()
            assertFalse(sample(outside, x = x, y = y))
            assertEquals("outside_article", outside.status.reason)
        }
    }

    @Test fun geometryNeedsUsableStopBandButFullSpeedNeedNotBeVisible() {
        val short = body.copy(viewportBottomPx = 600.0, textVisibleBottomPx = 600.0)
        assertTrue(sample(ArticleAutoScrollPolicy(), y = 550.0, geometry = short))
        listOf(body.copy(viewportTopPx = 400.0, textVisibleTopPx = 400.0),
            body.copy(viewportBottomPx = 400.0, textVisibleBottomPx = 400.0),
            body.copy(screenHeightPx = Double.NaN), body.copy(maxScrollYPx = 99.0),
            body.copy(textVisibleBottomPx = 100.0), body.copy(textVisibleRightPx = 20.0),
            body.copy(textVisibleTopPx = 99.0), body.copy(viewportBottomPx = 1001.0)).forEach { g ->
            val policy = ArticleAutoScrollPolicy()
            assertFalse(sample(policy, geometry = g))
            assertEquals("geometry_unavailable", policy.status.reason)
        }
        val policy = ArticleAutoScrollPolicy()
        assertTrue(sample(policy))
        assertEquals("geometry_unavailable", policy.advance(130.0,
            body.copy(viewportTopPx = Double.NaN)).reason)
    }

    @Test fun finalFrameClampsFractionalScrollRoomAndStops() {
        val policy = ArticleAutoScrollPolicy()
        val end = body.copy(maxScrollYPx = 100.1)
        assertTrue(sample(policy, geometry = end))
        val frame = policy.advance(160.0, end)
        assertEquals(.1, frame.deltaPx, 1e-9)
        assertFalse(frame.active)
        assertEquals("no_scroll_room", frame.reason)
        assertEquals(100.0, frame.sourceCaptureTimeMs!!, 0.0)
        assertEquals(0.0, policy.advance(170.0, end).deltaPx, 0.0)
        assertFalse(sample(policy, 170.0, geometry = body.copy(maxScrollYPx = 100.0)))
    }

    @Test fun configRejectsNonfiniteNonpositiveAndUnorderedSettings() {
        val bad = listOf<() -> ArticleAutoScrollPolicy.Config>(
            { ArticleAutoScrollPolicy.Config(version = "future") },
            { ArticleAutoScrollPolicy.Config(startScreenFraction = Double.NaN) },
            { ArticleAutoScrollPolicy.Config(startScreenFraction = 0.0) },
            { ArticleAutoScrollPolicy.Config(startScreenFraction = 1.0) },
            { ArticleAutoScrollPolicy.Config(fullSpeedScreenFraction = .4) },
            { ArticleAutoScrollPolicy.Config(fullSpeedScreenFraction = 1.1) },
            { ArticleAutoScrollPolicy.Config(fullSpeedScreenFraction = Double.NaN) },
            { ArticleAutoScrollPolicy.Config(maxSpeedScreenFractionPerSecond = 0.0) },
            { ArticleAutoScrollPolicy.Config(responseMs = -1.0) },
            { ArticleAutoScrollPolicy.Config(maxSampleGapMs = 0.0) },
            { ArticleAutoScrollPolicy.Config(maxOutputAgeMs = Double.POSITIVE_INFINITY) },
            { ArticleAutoScrollPolicy.Config(maxFrameDeltaMs = Double.NaN) },
        )
        bad.forEach { factory ->
            var rejected = false
            try { factory() } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
        }
    }
}
