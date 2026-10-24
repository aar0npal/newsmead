package com.newsmead.fragments.article

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.R
import com.newsmead.databinding.FragmentArticleBinding
import com.newsmead.gaze.mgazenet.MgazeNetGazeProvider
import com.newsmead.gaze.reading.ArticleReadingSession
import com.newsmead.gaze.reading.ReadingCoordinateStream
import com.newsmead.gaze.reading.ReadingGeometry
import com.newsmead.gaze.reading.ReadingSample
import com.newsmead.gaze.reading.ReadingTextGeometryReader
import com.newsmead.gaze.reading.ReadingWordMapper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/** Real Android Layout and synthetic observations only; no camera or gaze provider is started. */
@RunWith(AndroidJUnit4::class)
class ArticleTypographyTest {
    @Test fun sizesNormalizeAndStepSymmetricallyAtTheBounds() {
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(22f, ArticleTypography.normalizeSp(invalid), 0f)
        }
        assertEquals(14f, ArticleTypography.normalizeSp(-100f), 0f)
        assertEquals(60f, ArticleTypography.normalizeSp(100f), 0f)
        for (size in 14..58 step 2) {
            val larger = ArticleTypography.stepSp(size.toFloat(), 1)
            assertEquals(size + 2f, larger, 0f)
            assertEquals(size.toFloat(), ArticleTypography.stepSp(larger, -1), 0f)
        }
        assertEquals(14f, ArticleTypography.stepSp(14f, -1), 0f)
        assertEquals(60f, ArticleTypography.stepSp(60f, 1), 0f)
    }

    @Test fun arialReflowsAndSpacingScalesWhileUtf16WordAnchorsSurvive() = onMain {
        val fixture = Fixture()
        val view = fixture.body
        val reader = ReadingTextGeometryReader()
        val mapper = ReadingWordMapper(view.text.toString(), view)
        val heights = mutableMapOf<Float, Int>()
        val lineCounts = mutableMapOf<Float, Int>()
        var defaultLines: List<com.newsmead.gaze.reading.ReadingLine>? = null
        for (size in listOf(14f, 22f, 60f, 22f, 14f)) {
            fixture.resize(size)
            val expectedPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size,
                view.resources.displayMetrics)
            assertEquals(expectedPx, view.textSize, 0.01f)
            assertEquals(1.6f, view.lineSpacingMultiplier, 0f)
            assertEquals(view.textSize / 22f, view.lineSpacingExtra, 0.001f)
            heights[size]?.let { assertEquals(it, view.lineHeight) }
            lineCounts[size]?.let { assertEquals(it, view.layout.lineCount) }
            heights[size] = view.lineHeight
            lineCounts[size] = view.layout.lineCount
            val geometry = checkNotNull(reader.read(view))
            if (size == 22f) {
                defaultLines?.let { assertEquals(it, geometry.lines) }
                defaultLines = geometry.lines
            }
            val point = fixture.wordPoint()
            val target = mapper.targetAt(view, geometry, point.first.toDouble(), point.second.toDouble())
            assertEquals(fixture.wordStart, target.wordStart)
            assertEquals(fixture.wordStart + 5, target.wordEnd)
            assertEquals(view.layout.getLineForOffset(fixture.wordStart + 2), target.lineIndex)
        }
        assertTrue(heights.getValue(14f) < heights.getValue(22f))
        assertTrue(heights.getValue(22f) < heights.getValue(60f))
        assertTrue(lineCounts.getValue(14f) < lineCounts.getValue(60f))
        // The emoji occupies two UTF-16 code units before the retained word anchor.
        assertEquals(9, fixture.wordStart)
    }

    @Test fun sessionRebuildsAfterResizeAndRequiresFreshSettledGeometry() {
        lateinit var fixture: Fixture
        lateinit var session: ArticleReadingSession
        val samples = mutableListOf<ReadingSample>()
        val geometries = mutableListOf<ReadingGeometry>()
        onMain {
            fixture = Fixture()
            fixture.resize(22f)
            session = ArticleReadingSession(fixture.body,
                onGeometry = { _, geometry -> geometry?.let(geometries::add) },
                onInput = { _, sample -> samples.add(sample) },
                coordinateStream = ReadingCoordinateStream.CALIBRATED_RAW, motionCompensatedAids = false)
            session.checkGeometry()
        }
        // Called only inside runOnMainSync: capture and delivery use real elapsed clocks.
        fun record() {
            val point = fixture.wordPoint()
            val capture = elapsedMs()
            session.record(MgazeNetGazeProvider.Observation(capture,
                SystemClock.elapsedRealtimeNanos(), samples.size.toLong(), "coordinate",
                point.first, point.second, point.first, point.second))
        }
        fun recordSettledSamples() {
            // All waits run on the instrumentation thread, allowing UI work to continue.
            SystemClock.sleep(ArticleReadingSession.GEOMETRY_SETTLE_MS.toLong() + 10L)
            repeat(4) { index ->
                if (index > 0) SystemClock.sleep(40L)
                onMain { record() }
            }
        }
        try {
            recordSettledSamples()
            lateinit var old: ReadingGeometry
            onMain {
                old = geometries.last()
            }
            for (size in listOf(60f, 14f, 22f)) {
                lateinit var current: ReadingGeometry
                onMain {
                    fixture.resize(size)
                    session.checkGeometry()
                    current = geometries.last()
                    assertNotEquals(samples.last().geometrySnapshotId, current.snapshotId)
                    record()
                    assertEquals("geometry_unsettled", samples.last().exclusion)
                    assertFalse(samples.last().eligible)
                    assertFalse(samples.last().eligible)
                    assertNull(samples.last().wordStartUtf16)
                }
                recordSettledSamples()
                onMain {
                    assertTrue(samples.last().eligible)
                    assertEquals(current.snapshotId, samples.last().geometrySnapshotId)
                    assertEquals(fixture.wordStart, samples.last().wordStartUtf16)
                    assertEquals(fixture.wordStart + 5, samples.last().wordEndUtf16)
                    assertEquals(0, session.generation)
                }
            }
            onMain {
                assertEquals(old.lines, geometries.last().lines)
                assertEquals(old.lineHeightPx, geometries.last().lineHeightPx, 0.0)
                for (sample in samples) {
                    assertTrue(checkNotNull(sample.captureMs) <= checkNotNull(sample.deliveryMs))
                }
                for ((previous, next) in samples.zipWithNext()) {
                    assertTrue(checkNotNull(previous.captureMs) < checkNotNull(next.captureMs))
                }
            }
        } finally {
            onMain { session.close("typography_fixture") }
        }
    }

    @Test fun bottomBarControlsFitWithoutOverlapAtCompactWidths() = onMain {
        val context = ContextThemeWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext, R.style.Theme_NewsMead)
        val density = context.resources.displayMetrics.density
        for (widthDp in listOf(320, 360, 400)) {
            val binding = FragmentArticleBinding.inflate(LayoutInflater.from(context))
            val width = (widthDp * density).toInt()
            val height = (800 * density).toInt()
            binding.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            binding.root.layout(0, 0, width, height)
            val bar = binding.bottomAppBar
            assertEquals(View.VISIBLE, bar.visibility)
            val controls = listOf(binding.btnArticleClrLight, binding.btnArticleClrDark,
                binding.btnArticleClrSepia, binding.btnArticleTextSmaller,
                binding.btnArticleTextLarger, binding.btnArticleReadingAids)
            val bounds = controls.map { control ->
                val label = "${widthDp}dp: ${context.resources.getResourceEntryName(control.id)}"
                assertEquals(label, View.VISIBLE, control.visibility)
                var ancestor = control.parent
                while (ancestor is View && ancestor !== bar) {
                    val ancestorView: View = ancestor
                    assertEquals(label, View.VISIBLE, ancestorView.visibility)
                    ancestor = ancestorView.parent
                }
                assertSame(label, bar, ancestor)
                assertTrue("$label hit width", control.width >= 48f * density - 1f)
                assertTrue("$label hit height", control.height >= 48f * density - 1f)
                Rect(0, 0, control.width, control.height).also { rect ->
                    bar.offsetDescendantRectToMyCoords(control, rect)
                    assertTrue("$label left", rect.left >= -1)
                    assertTrue("$label top", rect.top >= -1)
                    assertTrue("$label right", rect.right <= bar.width + 1)
                    assertTrue("$label bottom", rect.bottom <= bar.height + 1)
                }
            }
            for (first in bounds.indices) {
                for (second in first + 1 until bounds.size) {
                    assertFalse("${widthDp}dp: controls $first and $second overlap",
                        Rect.intersects(bounds[first], bounds[second]))
                }
            }
            // Synthetic widget preview for visual QA; no article, screen or camera capture.
            // Detached TextViews retain their own floating observers. Run their normal
            // pre-draw work too, so the preview includes text rather than just icons.
            controls.forEach { it.viewTreeObserver.dispatchOnPreDraw() }
            val preview = Bitmap.createBitmap(bar.width, bar.height, Bitmap.Config.ARGB_8888)
            try {
                bar.draw(Canvas(preview))
                File(context.cacheDir, "article-footer-preview-$widthDp.png").outputStream().use {
                    preview.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally {
                preview.recycle()
            }
        }
    }

    private class Fixture {
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        private val width = (320 * context.resources.displayMetrics.density).toInt()
        private val height = (2400 * context.resources.displayMetrics.density).toInt()
        private val host = FrameLayout(context)
        val body = TextView(context).apply {
            text = "Alpha 😀 gamma\n" + "Article words wrap across the available width. ".repeat(12)
            textLocale = Locale.ENGLISH
            typeface = checkNotNull(ResourcesCompat.getFont(context, R.font.arial_regular))
        }
        val wordStart = body.text.toString().indexOf("gamma")

        init { host.addView(body, FrameLayout.LayoutParams(width, height)) }

        fun resize(sizeSp: Float) {
            ArticleTypography.apply(body, sizeSp)
            host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, width, height)
        }

        fun wordPoint(): Pair<Float, Float> {
            val geometry = checkNotNull(ReadingTextGeometryReader().read(body))
            val layout = body.layout
            val offset = wordStart + 2
            val line = layout.getLineForOffset(offset)
            val x = geometry.originX + (layout.getPrimaryHorizontal(offset) +
                layout.getPrimaryHorizontal(offset + 1)) / 2.0
            val y = geometry.originY + (layout.getLineTop(line) + layout.getLineBottom(line)) / 2.0
            return x.toFloat() to y.toFloat()
        }
    }

    private fun onMain(block: () -> Unit) =
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private fun elapsedMs() = SystemClock.elapsedRealtimeNanos() / 1e6
}
