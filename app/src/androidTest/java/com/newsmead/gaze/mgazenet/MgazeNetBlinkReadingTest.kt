package com.newsmead.gaze.mgazenet

import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.gaze.reading.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Production output gate and native text layout with synthetic eyes; no provider/camera starts. */
@RunWith(AndroidJUnit4::class)
class MgazeNetBlinkReadingTest {
    @Test fun closureAndReopeningStayExcludedBeforeFreshReadingInput() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var session: ArticleReadingSession
        lateinit var prediction: FloatArray
        val samples = mutableListOf<ReadingSample>()
        val boundaries = mutableListOf<String>()
        val output = MgazeNetLiveOutput(600, 800, smoothForReading = true, rejectBlinks = true)
        val open = MgazeNetEyelidGeometry.Sample(.12, .12)
        val shut = MgazeNetEyelidGeometry.Sample(.02, .02)
        var sequence = 0L
        var firstReturnCapture = 0.0
        instrumentation.runOnMainSync {
            val ctx = instrumentation.targetContext
            val host = FrameLayout(ctx)
            val body = TextView(ctx).apply { text = "Alpha beta\nGamma delta"; textSize = 20f }
            host.addView(body, FrameLayout.LayoutParams(600, 800))
            host.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, 600, 800)
            val geometry = ReadingTextGeometryReader().read(body)!!
            val row = body.layout.getLineForOffset(12)
            val x = geometry.originX + (body.layout.getPrimaryHorizontal(12) + body.layout.getPrimaryHorizontal(13)) / 2.0
            val y = geometry.originY + (body.layout.getLineTop(row) + body.layout.getLineBottom(row)) / 2.0
            prediction = floatArrayOf((x / 600).toFloat(), (y / 800).toFloat())
            session = ArticleReadingSession(body, onInput = { _, s -> samples.add(s) },
                onBoundary = { boundaries.add(it) }, motionCompensatedAids = false,
                coordinateStream = ReadingCoordinateStream.ARTICLE_SMOOTHED)
            session.checkGeometry()
        }
        fun emit(eyes: MgazeNetEyelidGeometry.Sample, predicted: FloatArray = prediction) {
            instrumentation.runOnMainSync {
                val capture = SystemClock.elapsedRealtimeNanos() / 1e6
                val result = output.evaluate(capture, SystemClock.elapsedRealtimeNanos() / 1e6,
                    true, predicted, "eye_area", eyes)
                val delivered = SystemClock.elapsedRealtimeNanos()
                session.record(MgazeNetGazeProvider.Observation(capture, delivered, ++sequence,
                    result.reason, result.x, result.y, result.rawX, result.rawY,
                    leftEyeAspect = eyes.leftAspect, rightEyeAspect = eyes.rightAspect, blinkFilterApplied = true))
            }
        }
        try {
            SystemClock.sleep(ArticleReadingSession.GEOMETRY_SETTLE_MS.toLong() + 20)
            repeat(4) { if (it > 0) SystemClock.sleep(50); emit(open) }
            instrumentation.runOnMainSync {
                assertTrue(samples.last().eligible)
            }
            SystemClock.sleep(50)
            emit(shut, floatArrayOf(.95f, .95f))
            instrumentation.runOnMainSync {
                val last = samples.last()
                assertEquals("blink", last.exclusion)
                assertFalse(samples.last().eligible); assertFalse(last.eligible)
                assertNull(last.processingX); assertNull(last.processingY)
                assertNull(last.line); assertNull(last.wordStartUtf16)
                assertNotNull(last.rawX)
            }
            SystemClock.sleep(50); emit(open)
            instrumentation.runOnMainSync { assertEquals("blink", samples.last().exclusion) }
            SystemClock.sleep(50); emit(open)
            instrumentation.runOnMainSync {
                assertTrue(samples.last().eligible)
                firstReturnCapture = checkNotNull(samples.last().captureMs)
            }
            repeat(2) { SystemClock.sleep(50); emit(open) }
            instrumentation.runOnMainSync {
                assertTrue(checkNotNull(samples.last().captureMs) > firstReturnCapture)
                assertEquals(1, samples.last().line)
                assertTrue(boundaries.contains("invalid_sample"))
                assertTrue(samples.all { checkNotNull(it.captureMs) <= checkNotNull(it.deliveryMs) })
            }
        } finally { instrumentation.runOnMainSync { session.close("blink-fixture") } }
    }
}
