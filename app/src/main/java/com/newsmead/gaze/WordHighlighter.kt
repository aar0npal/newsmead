package com.newsmead.gaze

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import android.widget.TextView
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.newsmead.R

@Deprecated(
    message = "Use LineAoiMapper.targetAt plus GazeOverlayView scaffold rendering; this class mutates TextView content.",
)
class WordHighlighter(
    private val textView: TextView
) {

    private var previousStart = -1
    private var previousEnd = -1

    fun update(screenX: Float, screenY: Float) {

        val layout = textView.layout ?: return

        val location = IntArray(2)
        textView.getLocationOnScreen(location)

        val localX = screenX - location[0]
        val localY = screenY - location[1]

        if (localY < 0f || localY > textView.height)
            return

        val line = layout.getLineForVertical(localY.toInt())

        val offset = layout.getOffsetForHorizontal(
            line,
            localX
        )

        val text = textView.text.toString()

        if (offset < 0 || offset >= text.length)
            return

        if (text[offset].isWhitespace())
            return

        var start = offset
        while (start > 0 && !text[start - 1].isWhitespace())
            start--

        var end = offset
        while (end < text.length && !text[end].isWhitespace())
            end++

        if (start == previousStart && end == previousEnd)
            return

        previousStart = start
        previousEnd = end

        val span = SpannableString(text)

        span.setSpan(
            StyleSpan(Typeface.BOLD),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        span.setSpan(
            BackgroundColorSpan(Color.YELLOW),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        textView.text = span
    }

}
