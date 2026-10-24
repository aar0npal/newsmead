package com.newsmead.gaze.reading

import android.graphics.Rect
import android.text.Layout
import android.widget.TextView
import com.newsmead.gaze.TextTarget
import com.newsmead.gaze.getVisibleRectOnScreen
import java.text.BreakIterator

/** A snapshot uses physical screen pixels and the body Layout's UTF-16 offsets. */
internal data class ReadingTextGeometry(
    val originX: Double,
    val originY: Double,
    val visible: ReadingRect,
    val lineHeightPx: Double,
    val lines: List<ReadingLine>,
    val direction: ReadingTextDirection,
) {
    fun snapshot(snapshotId: String) = ReadingGeometry(
        snapshotId, originX, originY, visible, lineHeightPx, lines, direction,
    )

    fun contains(x: Double, y: Double): Boolean =
        x >= visible.left && x < visible.right && y >= visible.top && y < visible.bottom
}

/** Scrolling updates screen bounds; line and bidi scans run only when layout changes. */
internal class ReadingTextGeometryReader {
    private var cachedLayout: Layout? = null
    private var cachedLineHeight = 0
    private var cachedLines: List<ReadingLine> = emptyList()
    private var cachedDirection = ReadingTextDirection.UNKNOWN

    fun invalidate() { cachedLayout = null }

    fun read(view: TextView): ReadingTextGeometry? {
        val layout = view.layout ?: return null
        val visible = Rect()
        if (!view.getVisibleRectOnScreen(visible)) visible.setEmpty()
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        if (layout !== cachedLayout || view.lineHeight != cachedLineHeight) {
            cachedLayout = layout
            cachedLineHeight = view.lineHeight
            cachedLines = (0 until layout.lineCount).map { line ->
                ReadingLine(
                    layout.getLineStart(line), layout.getLineEnd(line),
                    layout.getLineLeft(line).toDouble(), layout.getLineRight(line).toDouble(),
                    layout.getLineTop(line).toDouble(), layout.getLineBottom(line).toDouble(),
                )
            }
            // Mixed bidi runs do not establish a simple left-to-right return sweep.
            val directions = (0 until layout.lineCount).filter {
                layout.getLineEnd(it) > layout.getLineStart(it)
            }.map { line ->
                if (!layout.isRtlCharAt(layout.getLineStart(line)) &&
                    layout.getParagraphDirection(line) == Layout.DIR_LEFT_TO_RIGHT &&
                    (layout.getLineStart(line) until layout.getLineEnd(line)).none(layout::isRtlCharAt)
                ) ReadingTextDirection.LTR
                else if (layout.getParagraphDirection(line) == Layout.DIR_RIGHT_TO_LEFT)
                    ReadingTextDirection.RTL else ReadingTextDirection.UNKNOWN
            }.distinct()
            cachedDirection = directions.singleOrNull() ?: ReadingTextDirection.UNKNOWN
        }
        return ReadingTextGeometry(
            (location[0] + view.totalPaddingLeft - view.scrollX).toDouble(),
            (location[1] + view.totalPaddingTop - view.scrollY).toDouble(),
            ReadingRect(visible.left.toDouble(), visible.top.toDouble(),
                visible.right.toDouble(), visible.bottom.toDouble()),
            view.lineHeight.toDouble(), cachedLines, cachedDirection,
        )
    }
}

/** Same BreakIterator/letter-or-digit contract as the schema-1 reading logger. */
internal class ReadingWordMapper(private val body: String, view: TextView) {
    private val iterator = BreakIterator.getWordInstance(view.textLocale).apply { setText(body) }
    val ranges: List<WordRange> = buildList {
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            if ((start until end).any { body[it].isLetterOrDigit() }) add(WordRange(start, end))
            start = end
            end = iterator.next()
        }
    }

    fun targetAt(view: TextView, geometry: ReadingTextGeometry, x: Double, y: Double): TextTarget {
        if (!geometry.contains(x, y)) return TextTarget.INVALID
        val layout = view.layout ?: return TextTarget.INVALID
        val localY = y - geometry.originY
        if (localY < 0 || localY >= layout.height) return TextTarget.INVALID
        val line = layout.getLineForVertical(localY.toInt())
        val row = geometry.lines.getOrNull(line) ?: return TextTarget.INVALID
        val localX = x - geometry.originX
        if (localX < minOf(row.left, row.right) || localX > maxOf(row.left, row.right) || body.isEmpty())
            return TextTarget(line, layout.lineCount)
        val offset = layout.getOffsetForHorizontal(line, localX.toFloat())
            .coerceIn(row.startUtf16, (row.endUtf16 - 1).coerceAtLeast(row.startUtf16))
        if (offset !in body.indices || !body[offset].isLetterOrDigit()) return TextTarget(line, layout.lineCount)
        val start = if (iterator.isBoundary(offset)) offset else iterator.preceding(offset + 1)
        val end = iterator.following(offset)
        return if (start >= 0 && end > start) TextTarget(line, layout.lineCount, start, end)
        else TextTarget(line, layout.lineCount)
    }
}
