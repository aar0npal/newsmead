package com.newsmead.gaze.reading

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** Synthetic Layout checks only. No gaze provider, camera, calibration or recordings. */
@RunWith(AndroidJUnit4::class)
class ReadingTextGeometryTest {
    private fun text(body: String) = TextView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
        // Real article views receive layout params from their parent before text is replaced.
        layoutParams = ViewGroup.LayoutParams(600, 800)
        text = body
        textLocale = Locale.ENGLISH
        textSize = 20f
        setPadding(12, 8, 12, 8)
        measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        layout(0, 0, 600, 800)
    }

    @Test fun wordOffsetsStayUtf16AndTargetsUsePhysicalScreenOrigin() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = text("Alpha 42 café 😀 gamma\nDelta epsilon")
            val mapper = ReadingWordMapper(view.text.toString(), view)
            assertTrue(mapper.ranges.contains(WordRange(17, 22)))
            assertFalse(mapper.ranges.contains(WordRange(14, 16)))
            val original = checkNotNull(ReadingTextGeometryReader().read(view))
            val geometry = original.copy(originX = 100.0, originY = 200.0,
                visible = ReadingRect(88.0, 192.0, 688.0, 992.0))
            val offset = 19
            val line = view.layout.getLineForOffset(offset)
            val x = geometry.originX + view.layout.getPrimaryHorizontal(offset)
            val y = geometry.originY + (view.layout.getLineTop(line) + view.layout.getLineBottom(line)) / 2.0
            val target = mapper.targetAt(view, geometry, x, y)
            assertEquals(line, target.lineIndex)
            assertEquals(17, target.wordStart)
            assertEquals(22, target.wordEnd)
            assertFalse(mapper.targetAt(view, geometry, x, 190.0).isValid)
            val margin = mapper.targetAt(view, geometry, geometry.originX + 560.0, y)
            assertTrue(margin.isValid)
            assertFalse(margin.hasWord)
        }
    }

    @Test fun unchangedLayoutReusesLineDataAndMixedBidiAbstains() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = text("Alpha beta\nGamma delta")
            val reader = ReadingTextGeometryReader()
            val first = checkNotNull(reader.read(view))
            val second = checkNotNull(reader.read(view))
            assertSame(first.lines, second.lines)
            assertEquals(ReadingTextDirection.LTR, first.direction)
            view.text = "Alpha אבג beta"
            reader.invalidate()
            view.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 600, 800)
            val mixed = checkNotNull(reader.read(view))
            assertNotSame(first.lines, mixed.lines)
            assertEquals(ReadingTextDirection.UNKNOWN, mixed.direction)
        }
    }
}
