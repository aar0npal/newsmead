package com.newsmead.gaze.mgazenet

/** Equivalent finite check: NaN fails ordered comparisons; infinities exceed finite limits. */
object FloatInputValidation {
    fun allFinite(values: FloatArray): Boolean {
        for (value in values) {
            if (!(value <= Float.MAX_VALUE && value >= -Float.MAX_VALUE)) return false
        }
        return true
    }
}
