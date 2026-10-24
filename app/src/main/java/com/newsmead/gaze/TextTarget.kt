package com.newsmead.gaze

/** A stable gaze target within the laid-out article text. */
data class TextTarget(
    val lineIndex: Int,
    val lineCount: Int,
    val wordStart: Int = NO_OFFSET,
    val wordEnd: Int = NO_OFFSET,
) {
    val isValid: Boolean get() = lineIndex >= 0 && lineCount > 0
    val hasWord: Boolean get() = wordStart >= 0 && wordEnd > wordStart

    companion object {
        const val NO_OFFSET = -1
        val INVALID = TextTarget(lineIndex = -1, lineCount = 0)
    }
}
