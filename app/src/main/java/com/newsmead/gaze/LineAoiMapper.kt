package com.newsmead.gaze

import android.graphics.Rect
import android.text.Layout
import android.widget.TextView
import java.text.BreakIterator

/**
 * Stage 4 AOI mapping: resolves a full-screen gaze/touch y-coordinate to the
 * article body's text-line index.
 *
 * Scroll-awareness is free: the body [TextView]'s on-screen position (from
 * [getLocationOnScreen]) already moves as the article scrolls, so
 * `screenY - textViewTop` is the offset into the text [android.text.Layout]
 * regardless of scroll position — no separate scroll-offset tracking needed.
 * The font size is locked (see StudyConfig.ARTICLE_FONT_SIZE_DP), so line
 * heights and therefore these bands are stable within a session.
 */
class LineAoiMapper(private val textView: TextView) {

    private val loc = IntArray(2)
    private val visibleRect = Rect()
    private var cachedTextSource: CharSequence? = null
    private var cachedText = ""
    private var wordIterator: BreakIterator? = null

    /** @return the 0-based line index under [screenY], or -1 if outside the text. */
    fun lineAt(screenY: Float): Int {
        val layout = textView.layout ?: return -1
        return lineAt(layout, screenY)
    }

    private fun lineAt(layout: Layout, screenY: Float): Int {
        if (!textView.getVisibleRectOnScreen(visibleRect, loc) ||
            screenY < visibleRect.top || screenY >= visibleRect.bottom
        ) return -1
        textView.getLocationOnScreen(loc)
        val localY = screenY - loc[1] - textView.totalPaddingTop
        if (localY < 0f || localY >= layout.height.toFloat()) return -1
        return layout.getLineForVertical(localY.toInt())
    }

    /** Resolve both the robust line target and, when possible, a word range. */
    fun targetAt(screenX: Float, screenY: Float): TextTarget {
        val layout = textView.layout ?: return TextTarget.INVALID
        val line = lineAt(screenY)
        if (line !in 0 until layout.lineCount) return TextTarget.INVALID

        val localX = screenX - loc[0] - textView.totalPaddingLeft
        val lineLeft = layout.getLineLeft(line)
        val lineRight = layout.getLineRight(line)
        if (localX < minOf(lineLeft, lineRight) || localX > maxOf(lineLeft, lineRight)) {
            return TextTarget(line, layout.lineCount)
        }

        val textSource = textView.text
        if (cachedTextSource !== textSource || wordIterator == null) {
            cachedTextSource = textSource
            cachedText = textSource.toString()
            wordIterator = BreakIterator.getWordInstance(textView.textLocale).apply { setText(cachedText) }
        }
        val text = cachedText
        if (text.isEmpty()) return TextTarget(line, layout.lineCount)
        val offset = layout.getOffsetForHorizontal(line, localX)
            .coerceIn(layout.getLineStart(line), (layout.getLineEnd(line) - 1).coerceAtLeast(0))
        if (offset !in text.indices || !text[offset].isLetterOrDigit()) {
            return TextTarget(line, layout.lineCount)
        }

        val iterator = wordIterator ?: return TextTarget(line, layout.lineCount)
        val start = if (iterator.isBoundary(offset)) offset else iterator.preceding(offset + 1)
        val end = iterator.following(offset)
        if (start == BreakIterator.DONE || end == BreakIterator.DONE || start >= end ||
            !containsLetterOrDigit(text, start, end)
        ) return TextTarget(line, layout.lineCount)

        return TextTarget(
            lineIndex = line,
            lineCount = layout.lineCount,
            wordStart = start,
            wordEnd = end,
        )
    }

    /** Total number of laid-out lines in the body, or 0 before layout. */
    val lineCount: Int get() = textView.layout?.lineCount ?: 0

    private fun containsLetterOrDigit(text: String, start: Int, end: Int): Boolean {
        for (index in start until end) if (text[index].isLetterOrDigit()) return true
        return false
    }
}
