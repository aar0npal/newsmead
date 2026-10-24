package com.newsmead.fragments.article

import android.util.TypedValue
import android.widget.TextView

/** Typography for ordinary articles; frozen measurement views retain their own settings. */
internal object ArticleTypography {
    const val DEFAULT_SIZE_SP = 22f
    const val MIN_SIZE_SP = 14f
    const val MAX_SIZE_SP = 60f
    const val STEP_SP = 2f
    const val LINE_SPACING_MULTIPLIER = 1.6f

    fun normalizeSp(sizeSp: Float): Float =
        if (sizeSp.isFinite()) sizeSp.coerceIn(MIN_SIZE_SP, MAX_SIZE_SP) else DEFAULT_SIZE_SP

    fun stepSp(sizeSp: Float, direction: Int): Float {
        require(direction == -1 || direction == 1)
        return normalizeSp(normalizeSp(sizeSp) + direction * STEP_SP)
    }

    fun apply(textView: TextView, sizeSp: Float) {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, normalizeSp(sizeSp))
        // Scale the old default's extra spacing with the actual drawn font, including
        // Android's system font scaling. setLineSpacing expects physical pixels.
        textView.setLineSpacing(textView.textSize / DEFAULT_SIZE_SP, LINE_SPACING_MULTIPLIER)
    }
}
