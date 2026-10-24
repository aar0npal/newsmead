package com.newsmead.gaze

import android.os.SystemClock
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.R
import com.newsmead.activities.ReadingValidationActivity
import com.newsmead.gaze.mgazenet.MgazeNetGazeProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Synthetic coordinates on an idle layout. Does not start the camera or change calibration. */
@RunWith(AndroidJUnit4::class)
class ReadingMeasurementLogTest {
    @Test
    fun dotOnlyDrawsTheDotWithoutDiagnosticCaption() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val overlay = GazeOverlayView(instrumentation.targetContext)
            overlay.layout(0, 0, 300, 250)
            overlay.setDebugVisualsEnabled(false)
            overlay.setScaffoldCaption("Hidden caption")
            overlay.setFps(24f)
            overlay.setGazeScreen(180f, 180f)
            overlay.setDotOnly(true)
            val bitmap = Bitmap.createBitmap(300, 250, Bitmap.Config.ARGB_8888)
            try {
                overlay.draw(Canvas(bitmap))
                assertTrue(Color.alpha(bitmap.getPixel(180, 180)) > 0)
                for (y in 0 until 120) for (x in 0 until 300) {
                    assertEquals(0, Color.alpha(bitmap.getPixel(x, y)))
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun recordsRawCoordinatesExcludesMovementAndClosesCompleteFile() {
        val closed = CountDownLatch(1)
        var file: File? = null
        var complete = false
        ActivityScenario.launch(ReadingValidationActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val text = activity.findViewById<TextView>(R.id.tvValidationText)
                val log = ReadingMeasurementLog(activity, text, "synthetic-test") { saved, ok ->
                    file = saved
                    complete = ok
                    closed.countDown()
                }
                val location = IntArray(2)
                text.getLocationOnScreen(location)
                val x = location[0] + text.totalPaddingLeft + text.layout.getPrimaryHorizontal(2)
                val y = location[1] + text.totalPaddingTop + text.lineHeight / 2f
                val now = SystemClock.elapsedRealtimeNanos() / 1e6
                log.record(MgazeNetGazeProvider.Observation(now + 200, 1, 1, "coordinate",
                    x + 50, y + 50, x, y))
                text.translationY += 30f
                log.checkGeometry()
                log.record(MgazeNetGazeProvider.Observation(now, 2, 2, "coordinate", x, y, x, y))
                log.record(MgazeNetGazeProvider.Observation(now + 250, 3, 3, "stale"))
                log.finish("synthetic_test")
                log.finish("duplicate_close")
            }
            assertTrue("Writer must finish", closed.await(5, TimeUnit.SECONDS))
            try {
                assertTrue(complete)
                val rows = file!!.readLines().map(::JSONObject)
                assertEquals("session_start", rows.first().getString("type"))
                assertEquals("synthetic-test", rows.first().getString("calibration_sha256"))
                assertFalse(rows.first().getBoolean("camera_frames_retained"))
                assertTrue(rows.first().getBoolean("diagnostic_dot_visible"))
                assertFalse(rows.first().getBoolean("scaffolds_enabled"))
                assertTrue(rows.first().getJSONArray("word_ranges").length() > 0)
                assertEquals(2, rows.count { it.getString("type") == "geometry" })
                val samples = rows.filter { it.getString("type") == "sample" }
                assertEquals(3, samples.size)
                assertTrue(samples[0].getBoolean("measurement_valid"))
                assertEquals(50.0, samples[0].getDouble("display_x") - samples[0].getDouble("raw_x"), 0.01)
                assertFalse(samples[1].getBoolean("measurement_valid"))
                assertFalse(samples[2].getBoolean("measurement_valid"))
                val end = rows.last()
                assertTrue(end.getBoolean("complete"))
                assertEquals(3, end.getInt("sample_records"))
                assertEquals("synthetic_test", end.getString("reason"))
            } finally {
                // Delete this test's one synthetic file only.
                file?.delete()
            }
        }
    }
}
