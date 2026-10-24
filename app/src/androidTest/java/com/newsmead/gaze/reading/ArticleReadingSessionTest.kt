package com.newsmead.gaze.reading

import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.gaze.mgazenet.MgazeNetGazeProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic existing provider outputs only; no camera or second smoothing pass. */
@RunWith(AndroidJUnit4::class)
class ArticleReadingSessionTest {
    @Test fun stationaryTextAcceptsDelayedCaptureWithoutRecentGeometrySnapshot() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val body = TextView(ctx).apply { text = "Alpha beta\nGamma delta"; textSize = 20f }
            val host = FrameLayout(ctx)
            host.addView(body, FrameLayout.LayoutParams(600, 800))
            host.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, 600, 800)
            val stationaryRows = mutableListOf<ReadingSample>()
            val movingRows = mutableListOf<ReadingSample>()
            val stationary = ArticleReadingSession(body, onInput = { _, sample -> stationaryRows.add(sample) },
                motionCompensatedAids = false)
            val moving = ArticleReadingSession(body, onInput = { _, sample -> movingRows.add(sample) })
            try {
                stationary.checkGeometry()
                moving.checkGeometry()
                val geometry = ReadingTextGeometryReader().read(body)!!
                val x = (geometry.originX + body.layout.getPrimaryHorizontal(2)).toFloat()
                val y = (geometry.originY +
                    (body.layout.getLineTop(0) + body.layout.getLineBottom(0)) / 2.0).toFloat()
                // This resembles a slow camera inference: capture is 180 ms before delivery,
                // with no redraw/snapshot in the intervening stationary interval.
                SystemClock.sleep(400)
                val capture = SystemClock.elapsedRealtimeNanos() / 1e6 - 180.0
                val observation = MgazeNetGazeProvider.Observation(capture,
                    SystemClock.elapsedRealtimeNanos(), 1L, "coordinate", x, y)
                stationary.record(observation)
                moving.record(observation)
                assertTrue(stationaryRows.single().eligible)
                assertTrue(movingRows.single().eligible)
                moving.autoScrollMotion(true)
                SystemClock.sleep(400)
                val movingCapture = SystemClock.elapsedRealtimeNanos() / 1e6 - 180.0
                moving.record(MgazeNetGazeProvider.Observation(movingCapture,
                    SystemClock.elapsedRealtimeNanos(), 2L, "coordinate", x, y))
                assertEquals("scroll_geometry_unavailable", movingRows.last().exclusion)
            } finally { stationary.close("fixture"); moving.close("fixture") }
        }
    }

    @Test fun mappingUsesFilteredPointAndMissingnessConsultationAndScrollBreakContinuity() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val host = FrameLayout(ctx)
            val body = TextView(ctx).apply { text = "Alpha beta\nGamma delta"; textSize = 20f }
            host.addView(body, FrameLayout.LayoutParams(600, 800))
            host.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, 600, 800)
            val rows = mutableListOf<ReadingSample>()
            val rawRows = mutableListOf<ReadingSample>()
            val boundaries = mutableListOf<String>()
            val session = ArticleReadingSession(body, onInput = { _, s -> rows.add(s) },
                onBoundary = { boundaries.add(it) }, motionCompensatedAids = false,
                coordinateStream = ReadingCoordinateStream.ARTICLE_SMOOTHED)
            val rawSession = ArticleReadingSession(body, motionCompensatedAids = false,
                coordinateStream = ReadingCoordinateStream.CALIBRATED_RAW, onInput = { _, s -> rawRows.add(s) })
            try {
                session.checkGeometry(); rawSession.checkGeometry()
                val g = ReadingTextGeometryReader().read(body)!!
                fun point(offset: Int): Pair<Float, Float> {
                    val line = body.layout.getLineForOffset(offset)
                    val x = g.originX + (body.layout.getPrimaryHorizontal(offset) +
                        body.layout.getPrimaryHorizontal(offset + 1)) / 2.0
                    val y = g.originY + (body.layout.getLineTop(line) + body.layout.getLineBottom(line)) / 2.0
                    return x.toFloat() to y.toFloat()
                }
                val raw = point(1); val filtered = point(12)
                val base = SystemClock.elapsedRealtimeNanos() / 1e6 + 200.0
                fun observation(t: Double, x: Float? = filtered.first, y: Float? = filtered.second,
                                reason: String = "coordinate") = MgazeNetGazeProvider.Observation(
                    t, ((t + 10) * 1e6).toLong(), rows.size.toLong(), reason,
                    x, y, raw.first, raw.second)
                for (dt in listOf(0.0, 50.0, 100.0)) session.record(observation(base + dt))
                val s = rows.last()
                assertEquals(1, s.line)
                assertEquals(11, s.wordStartUtf16)
                assertEquals(16, s.wordEndUtf16)
                assertEquals(raw.first.toDouble(), s.rawX!!, 0.0)
                assertEquals(filtered.first.toDouble(), s.processingX!!, 0.0)
                assertTrue(s.eligible)
                rawSession.record(observation(base))
                for (dt in listOf(50.0, 100.0)) rawSession.record(observation(base + dt))
                assertEquals(0, rawRows.last().line)
                for ((i, value) in listOf<Float?>(null, Float.NaN).withIndex()) {
                    session.record(observation(base + 150 + i * 50, x = value))
                    assertFalse(rows.last().eligible)
                    assertNull(rows.last().line)
                }
                session.record(observation(base + 250, reason = "stale"))
                assertFalse(rows.last().eligible)
                session.record(observation(base + 275, x = -100f))
                assertEquals("outside_visible_text", rows.last().exclusion)
                assertFalse(rows.last().eligible)
                session.setConsultation(true, base + 300, "fixture")
                session.record(observation(base + 350))
                assertEquals("definition_consultation", rows.last().exclusion)
                assertFalse(rows.last().eligible)
                session.setConsultation(false, base + 400, "fixture")
                session.record(observation(base + 375))
                assertEquals("definition_boundary_late_capture", rows.last().exclusion)
                assertFalse(rows.last().eligible)
                session.record(observation(base + 450))
                assertTrue(rows.last().eligible)
                session.record(observation(base + 450))
                assertFalse(rows.last().eligible)
                assertEquals("non_monotonic_capture", rows.last().exclusion)
                assertTrue(boundaries.contains("non_monotonic_capture"))
                session.record(observation(base + 440))
                assertEquals("non_monotonic_capture", rows.last().exclusion)
                session.record(observation(base + 500))
                assertTrue(rows.last().eligible)
                val oldId = rows.last().geometrySnapshotId
                body.scrollTo(0, 10)
                session.checkGeometry()
                assertEquals("reading_scroll", boundaries.last())
                session.record(observation(0.0))
                assertNotEquals(oldId, rows.last().geometrySnapshotId)
                assertEquals("geometry_unsettled", rows.last().exclusion)
                assertFalse(rows.last().eligible)
                session.pause("fixture_pause")
                val count = rows.size
                session.record(observation(base + 550))
                assertEquals(count, rows.size)
                session.resume()
                assertTrue(boundaries.contains("fixture_pause"))
            } finally { session.close("fixture"); rawSession.close("fixture") }
        }
    }

    @Test fun controlledTranslationRetainsLayoutButExternalScrollAndTextChangeBreakIt() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val body = TextView(ctx).apply { text = "Alpha beta\nGamma delta"; textSize = 20f }
            fun layoutBody() {
                body.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
                body.layout(0, 0, 600, 800)
            }
            layoutBody()
            val geometries = mutableListOf<Pair<Int, ReadingGeometry?>>()
            val boundaries = mutableListOf<String>()
            val session = ArticleReadingSession(body,
                onGeometry = { generation, g -> geometries.add(generation to g) },
                onBoundary = { boundaries.add(it) })
            try {
                assertEquals(ReadingCoordinateStream.ARTICLE_SMOOTHED, session.coordinateStream)
                assertTrue(session.motionCompensatedAids)
                session.checkGeometry()
                val initialId = geometries.last().second!!.snapshotId
                session.autoScrollMotion(true)
                body.scrollTo(0, 10)
                session.checkGeometry()
                assertEquals(initialId, geometries.last().second!!.snapshotId)
                session.autoScrollMotion(false, "external_scroll")
                assertTrue(boundaries.contains("uncontrolled_scroll"))
                assertNotEquals(initialId, geometries.last().second!!.snapshotId)
                body.text = "Changed article body"
                layoutBody()
                session.checkGeometry()
                assertEquals(1, session.generation)
                assertEquals(1, geometries.last().first)
                assertTrue(boundaries.contains("text_changed"))
            } finally { session.close("fixture") }
        }
    }

    @Test fun manualArticleScrollIsAContinuityBreakRatherThanAReflow() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val body = TextView(ctx).apply { text = "Alpha beta\nGamma delta"; textSize = 20f }
            body.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            body.layout(0, 0, 600, 800)
            val boundaries = mutableListOf<String>()
            val session = ArticleReadingSession(body, onBoundary = { boundaries.add(it) })
            try {
                session.checkGeometry()
                body.scrollTo(0, 10)
                session.checkGeometry()
                assertEquals(listOf("geometry_change", "reading_scroll"), boundaries)
            } finally { session.close("fixture") }
        }
    }

}
