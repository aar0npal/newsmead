package com.newsmead.gaze

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.R
import com.newsmead.activities.GazeCalibrationActivity
import com.newsmead.activities.ReadingValidationActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.max
import kotlin.math.min

/** Layout only. Never presses Start or writes a real calibration/session. */
@RunWith(AndroidJUnit4::class)
class GazeScreenCoordinatesTest {
    @Test
    fun idleReadingLayoutUsesTheSameScreenContract() {
        ActivityScenario.launch(ReadingValidationActivity::class.java).use {
            onView(withId(R.id.nsvValidationText)).check { view, error ->
                if (error != null) throw error
                val frame = view.gazeCoordinateFrame()
                val local = Rect()
                assertTrue(view.getLocalVisibleRect(local))
                local.offset(frame.originX, frame.originY)
                val screen = Rect()
                assertTrue(view.getVisibleRectOnScreen(screen))
                assertEquals(local, screen)
                Log.i("CoordinateCheck", "reading root=${view.rootView.gazeCoordinateFrame()} viewport=$frame visible_screen=$screen")
                assertEquals(View.VISIBLE, view.rootView.findViewById<View>(R.id.btnValidationAction).visibility)
            }
        }
    }

    @Test
    fun actualCalibrationAndInsetScrolledReadingUseScreenCoordinates() {
        ActivityScenario.launch(GazeCalibrationActivity::class.java).use { scenario ->
            onView(withId(R.id.calibrationView)).check { view, error ->
                if (error != null) throw error
                val frame = view.gazeCoordinateFrame()
                val local = Rect()
                assertTrue(view.getLocalVisibleRect(local))
                val screen = Rect()
                assertTrue(view.getVisibleRectOnScreen(screen))
                local.offset(frame.originX, frame.originY)
                assertEquals(local, screen)
                val center = frame.toScreen(frame.width / 2f, frame.height / 2f)
                val display = view.physicalDisplaySize()
                Log.i("CoordinateCheck", "calibration frame=$frame display=$display center_screen=$center")
                assertTrue(frame.width > 0 && frame.height > 0)
                assertEquals(View.VISIBLE, view.rootView.findViewById<View>(R.id.startButton).visibility)
            }

            lateinit var content: FrameLayout
            lateinit var scroll: ScrollView
            lateinit var text: TextView
            lateinit var dot: GazeDotView
            lateinit var overlay: GazeOverlayView
            lateinit var popup: PopupWindow
            scenario.onActivity { activity ->
                content = FrameLayout(activity)
                scroll = ScrollView(activity)
                text = TextView(activity).apply {
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 28f)
                    setPadding(13, 17, 13, 17)
                    this.text = (0..49).joinToString("\n") { "row $it known word" }
                }
                scroll.addView(text, ViewGroup.LayoutParams(500, ViewGroup.LayoutParams.WRAP_CONTENT))
                content.addView(scroll, FrameLayout.LayoutParams(500, 600).apply {
                    leftMargin = 37
                    topMargin = 83
                })
                dot = GazeDotView(activity)
                overlay = GazeOverlayView(activity)
                content.addView(dot, FrameLayout.LayoutParams(650, 850))
                content.addView(overlay, FrameLayout.LayoutParams(650, 850))
                popup = PopupWindow(content, 650, 850, false).apply {
                    isClippingEnabled = false
                    showAtLocation(activity.window.decorView, Gravity.TOP or Gravity.LEFT, 91, 171)
                }
            }
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.waitForIdleSync()
            try {
                scenario.onActivity {
                    val rootFrame = content.rootView.gazeCoordinateFrame()
                    assertTrue("exercise nonzero x origin: $rootFrame", rootFrame.originX > 0)
                    assertTrue("exercise nonzero y origin: $rootFrame", rootFrame.originY > 0)
                    assertTrue(text.layout.lineCount > 20)
                    assertClippingAndAoi(scroll, text)
                    scroll.scrollTo(0, text.lineHeight * 5)
                    assertTrue(scroll.scrollY > 0)
                    assertClippingAndAoi(scroll, text)

                    // Rendering must subtract this overlay's own screen origin.
                    val point = dot.gazeCoordinateFrame().toScreen(260f, 330f)
                    dot.setGazeScreen(point.x, point.y)
                    assertDotAtLocalCenter(dot)
                    overlay.setDebugVisualsEnabled(true)
                    overlay.setGazeScreen(point.x, point.y)
                    assertDotAtLocalCenter(overlay)
                    Log.i("CoordinateCheck", "inset root=$rootFrame scroll_y=${scroll.scrollY}; AOI and dots passed")
                }
            } finally {
                scenario.onActivity { popup.dismiss() }
            }
        }
    }

    private fun assertClippingAndAoi(scroll: ScrollView, text: TextView) {
        val frame = text.gazeCoordinateFrame()
        val viewport = scroll.gazeCoordinateFrame()
        val expectedVisible = Rect(
            max(frame.originX, viewport.originX), max(frame.originY, viewport.originY),
            min(frame.originX + frame.width, viewport.originX + viewport.width),
            min(frame.originY + frame.height, viewport.originY + viewport.height),
        )
        val actual = Rect()
        assertTrue(text.getVisibleRectOnScreen(actual))
        assertEquals(expectedVisible, actual)
        val aoi = LineAoiMapper(text)
        assertEquals(-1, aoi.lineAt(actual.top - 1f))
        assertEquals(-1, aoi.lineAt(actual.bottom.toFloat()))
        val layout = text.layout
        var checked = 0
        for (line in 0 until layout.lineCount) {
            val y = frame.originY + text.totalPaddingTop + (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
            if (y >= actual.top && y < actual.bottom) {
                assertEquals(line, aoi.lineAt(y))
                val x = frame.originX + text.totalPaddingLeft + layout.getPrimaryHorizontal(layout.getLineStart(line) + 1)
                val target = aoi.targetAt(x, y)
                assertEquals(line, target.lineIndex)
                assertEquals("row", text.text.substring(target.wordStart, target.wordEnd))
                checked++
            }
        }
        assertTrue("must check multiple visible lines", checked > 5)
    }

    private fun assertDotAtLocalCenter(view: View) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            view.draw(Canvas(bitmap))
            assertTrue(Color.alpha(bitmap.getPixel(260, 330)) > 0)
            assertEquals(0, Color.alpha(bitmap.getPixel(260, 420)))
        } finally {
            bitmap.recycle()
        }
    }
}
