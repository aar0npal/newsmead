package com.newsmead.gaze.scroll

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewTreeObserver
import androidx.core.view.doOnPreDraw
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.newsmead.activities.ReadingBaselineActivity
import com.newsmead.databinding.FragmentArticleBinding
import com.newsmead.fragments.article.ArticleTypography
import com.newsmead.gaze.getVisibleRectOnScreen
import com.newsmead.gaze.mgazenet.MgazeNetGazeProvider
import com.newsmead.gaze.physicalDisplaySize
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Supplied coordinates exercise attached navigation only, not human accuracy or comfort.
 * The replay host starts neither ArticleFragment nor a gaze/camera provider.
 */
@RunWith(AndroidJUnit4::class)
class ArticleAutoScrollAttachedLayoutTest {
    @Test fun gazeHeightControlsContinuousSpeedAndUpperGazeBrakesThenLowerRestarts() {
        AutoScrollAttachedFixture().use { f ->
            val before = f.scrollY()
            f.feed(750, .45)
            val slowEnd = f.scrollY()
            f.onMain {
                val firstChange = f.scrollChanges.firstOrNull { it.second > before }
                assertNotNull("No actual movement during slow input", firstChange)
                assertTrue("First actual motion exceeded 400 ms", firstChange!!.first - f.captureTimes.first() <= 400.0)
                assertTrue("Expected multiple actual scroll changes", f.scrollChanges.count { it.second > before } >= 3)
            }
            f.feed(750, .65)
            val fastEnd = f.scrollY()
            assertTrue("Expected slow phase movement", slowEnd > before)
            assertTrue("Lower gaze should move faster", fastEnd - slowEnd > slowEnd - before)
            f.onMain {
                assertTrue(f.controller.isMoving)
                assertEquals("auto_scroll_motion", f.controller.exclusion(f.nowMs()))
                assertEquals(f.originalText, f.binding.tvArticleText.text.toString())
                val config = f.controller.configuration()
                assertEquals("ARTICLE_AUTO_SCROLL_TRIAL_V3", config.getString("version"))
                assertTrue(config.getBoolean("trial_settings"))
                assertEquals(.40, config.getDouble("start_screen_fraction"), 0.0)
                assertEquals(.65, config.getDouble("full_speed_screen_fraction"), 0.0)
                assertEquals(.06, config.getDouble("max_speed_screen_fraction_per_second"), 0.0)
                assertEquals(180.0, config.getDouble("response_ms"), 0.0)
                assertEquals(150.0, config.getDouble("max_sample_gap_ms"), 0.0)
                assertEquals(250.0, config.getDouble("max_output_age_ms"), 0.0)
                assertEquals(50.0, config.getDouble("max_frame_delta_ms"), 0.0)
                assertTrue(f.journals.any { it.first == "setting" &&
                    it.second.getString("version") == "ARTICLE_AUTO_SCROLL_TRIAL_V3" })
                val detail = f.boundaries.first { it.start }.detail
                assertEquals("ARTICLE_AUTO_SCROLL_TRIAL_V3", detail.getString("policy"))
                assertTrue(detail.getDouble("source_capture_ms") <= detail.getDouble("source_delivery_ms"))
                assertFalse(detail.keys().asSequence().any { it.startsWith("source_line") })
            }
            f.feed(400, .30)
            f.onMain {
                assertFalse("Fresh upper gaze should finish braking", f.controller.isMoving)
                val end = f.boundaries.last()
                assertFalse(end.start)
                assertEquals("completed", end.detail.getString("result"))
                assertEquals("waiting_lower", end.detail.getString("reason"))
                assertEquals("auto_scroll_settling", f.controller.exclusion(end.timeMs + 100))
                assertNull(f.controller.exclusion(end.detail.getDouble("exclude_captures_through_ms") + 1))
            }
            val stoppedY = f.scrollY()
            f.feed(250, .30)
            assertEquals(stoppedY, f.scrollY())
            f.feed(350, .65)
            assertTrue("Lower gaze must restart without old dwell or cooldown", f.scrollY() > stoppedY)
            f.onMain { assertEquals(f.originalText, f.binding.tvArticleText.text.toString()) }
        }
    }

    @Test fun absentAndNoncoordinateGazeHardStopWithoutTimerDrivenContinuation() {
        AutoScrollAttachedFixture().use { f ->
            f.feed(500, .65)
            f.onMain { assertTrue(f.controller.isMoving) }
            f.waitPosted(300)
            f.onMain {
                assertFalse(f.controller.isMoving)
                assertEquals("stale", f.boundaries.last().detail.getString("reason"))
            }
            val staleY = f.scrollY()
            f.waitPosted(300)
            assertEquals(staleY, f.scrollY())
            f.feed(400, .65)
            f.onMain {
                assertTrue(f.controller.isMoving)
                f.emit(.65, reason = "blink")
                assertFalse(f.controller.isMoving)
                assertEquals("gaze_unavailable", f.controller.statusReason)
            }
            val missingY = f.scrollY()
            f.waitPosted(300)
            assertEquals(missingY, f.scrollY())
            f.feed(350, .65)
            assertTrue("Fresh coordinates must resume after missing gaze", f.scrollY() > missingY)
        }
    }
}

/** Shared attached fixture; all callbacks and mutable collections belong to the UI thread.
 * Only bounded latches are awaited on the instrumentation thread.
 */
internal class AutoScrollAttachedFixture : Closeable {
    data class Boundary(val start: Boolean, val timeMs: Double, val detail: JSONObject)
    private val scenario = ActivityScenario.launch(ReadingBaselineActivity::class.java)
    private val handler = Handler(Looper.getMainLooper())
    lateinit var binding: FragmentArticleBinding
        private set
    lateinit var controller: ArticleAutoScrollController
        private set
    var blockedReason: String? = null
    val boundaries = mutableListOf<Boundary>()
    val journals = mutableListOf<Pair<String, JSONObject>>()
    val scrollChanges = mutableListOf<Pair<Double, Int>>()
    val captureTimes = mutableListOf<Double>()
    var screenHeight = 0
        private set
    private var ordinal = 0L
    private var feedTask: Runnable? = null
    private var listenerAdded = false
    private val preDraw = ViewTreeObserver.OnPreDrawListener { controller.onPreDraw(); true }
    private val scrollListener = ViewTreeObserver.OnScrollChangedListener {
        val y = binding.nsvArticleText.scrollY
        if (scrollChanges.lastOrNull()?.second != y) scrollChanges += nowMs() to y
    }
    val originalText = (1..160).joinToString("\n\n") {
        "Paragraph $it. Readers follow this synthetic article through changing screen regions. " +
            "The same body text remains present while its screen position changes."
    }

    init {
        try {
            onMain { activity ->
                binding = FragmentArticleBinding.inflate(activity.layoutInflater)
                binding.tvArticleText.text = originalText
                ArticleTypography.apply(binding.tvArticleText, ArticleTypography.DEFAULT_SIZE_SP)
                activity.setContentView(binding.root)
                assertEquals(0, binding.articleAutoScrollTail.layoutParams.height)
                controller = ArticleAutoScrollController(binding.tvArticleText, binding.nsvArticleText,
                    binding.bottomAppBar, binding.articleAutoScrollTail,
                    blocked = { blockedReason },
                    onBoundary = { active, time, detail -> boundaries += Boundary(active, time, detail) },
                    onJournal = { kind, _, detail -> journals += kind to detail },
                    onState = { _, _ -> })
                binding.nsvArticleText.onUserTouch = { event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) controller.pauseByReader("manual_touch")
                }
                binding.root.viewTreeObserver.addOnPreDrawListener(preDraw)
                binding.root.viewTreeObserver.addOnScrollChangedListener(scrollListener)
                listenerAdded = true
            }
            awaitDraw()
            onMain { binding.nsvArticleText.scrollTo(0, binding.tvArticleText.top) }
            awaitDraw()
            onMain { controller.setEnabled(true) }
            awaitDraw()
            awaitDraw()
            waitPosted(200)
            onMain {
                assertTrue(binding.tvArticleText.isAttachedToWindow)
                screenHeight = binding.tvArticleText.physicalDisplaySize().y
                assertTrue(screenHeight > 0)
                listOf(.30, .45, .65).forEach { point(it) }
                scrollChanges.clear()
            }
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    fun nowMs(): Double = SystemClock.elapsedRealtimeNanos() / 1e6
    fun onMain(block: (ReadingBaselineActivity) -> Unit) = scenario.onActivity(block)
    fun scrollY(): Int {
        var y = 0
        onMain { y = binding.nsvArticleText.scrollY }
        return y
    }

    /** Real capture <= delivery <= controller now; no synthetic future timestamps. */
    fun emit(fraction: Double, reason: String = "coordinate", captureMs: Double = nowMs()) {
        val point = point(fraction)
        captureTimes += captureMs
        controller.onObservation(MgazeNetGazeProvider.Observation(captureMs,
            SystemClock.elapsedRealtimeNanos(), ++ordinal, reason,
            if (reason == "coordinate") point.first else null,
            if (reason == "coordinate") point.second else null))
    }

    private fun point(fraction: Double): Pair<Float, Float> {
        val visible = Rect()
        val viewport = Rect()
        val footer = Rect()
        assertTrue(binding.tvArticleText.getVisibleRectOnScreen(visible))
        assertTrue(binding.nsvArticleText.getVisibleRectOnScreen(viewport))
        assertTrue(visible.intersect(viewport))
        if (binding.bottomAppBar.getVisibleRectOnScreen(footer)) visible.bottom = minOf(visible.bottom, footer.top)
        val y = screenHeight * fraction
        assertTrue("Synthetic point must be in attached clipped article: $visible, y=$y",
            y >= visible.top && y < visible.bottom)
        return visible.exactCenterX() to y.toFloat()
    }

    fun feed(durationMs: Long, fraction: Double, captureLagMs: Double = 80.0) {
        val finished = CountDownLatch(1)
        onMain {
            check(feedTask == null)
            val deadline = SystemClock.elapsedRealtime() + durationMs
            val task = object : Runnable {
                override fun run() {
                    if (SystemClock.elapsedRealtime() >= deadline) {
                        feedTask = null
                        finished.countDown()
                        return
                    }
                    // A lagged real source must catch up after a settling/control boundary.
                    emit(fraction, captureMs = nowMs() - captureLagMs)
                    handler.postDelayed(this, 40)
                }
            }
            feedTask = task
            handler.post(task)
        }
        assertTrue("Bounded gaze phase did not complete", finished.await(durationMs + 3000, TimeUnit.MILLISECONDS))
    }

    fun waitPosted(durationMs: Long) {
        val finished = CountDownLatch(1)
        onMain { handler.postDelayed({ finished.countDown() }, durationMs) }
        assertTrue("UI delay did not complete", finished.await(durationMs + 3000, TimeUnit.MILLISECONDS))
    }

    private fun awaitDraw() {
        val finished = CountDownLatch(1)
        onMain {
            binding.root.doOnPreDraw { finished.countDown() }
            binding.root.requestLayout()
            binding.root.invalidate()
        }
        assertTrue("Attached layout did not draw", finished.await(3, TimeUnit.SECONDS))
    }

    fun touchBody() {
        val downTime = SystemClock.uptimeMillis()
        val x = binding.nsvArticleText.width / 2f
        val y = binding.nsvArticleText.height / 2f
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            try { binding.nsvArticleText.dispatchTouchEvent(event) } finally { event.recycle() }
        }
    }

    override fun close() {
        try {
            onMain {
                handler.removeCallbacksAndMessages(null)
                feedTask = null
                if (::binding.isInitialized) {
                    binding.nsvArticleText.onUserTouch = null
                    if (listenerAdded && binding.root.viewTreeObserver.isAlive) {
                        binding.root.viewTreeObserver.removeOnPreDrawListener(preDraw)
                        binding.root.viewTreeObserver.removeOnScrollChangedListener(scrollListener)
                    }
                }
                if (::controller.isInitialized) controller.close()
            }
        } finally { scenario.close() }
    }
}
