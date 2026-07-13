package com.newsmead.gaze

import android.widget.TextView

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

    /** @return the 0-based line index under [screenY], or -1 if outside the text. */
    fun lineAt(screenY: Float): Int {
        val layout = textView.layout ?: return -1
        textView.getLocationOnScreen(loc)
        val localY = screenY - loc[1] - textView.totalPaddingTop
        if (localY < 0f || localY > layout.height.toFloat()) return -1
        return layout.getLineForVertical(localY.toInt())
    }

    /** Total number of laid-out lines in the body, or 0 before layout. */
    val lineCount: Int get() = textView.layout?.lineCount ?: 0
}
