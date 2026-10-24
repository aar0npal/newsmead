package com.newsmead.gaze.mgazenet

import com.newsmead.gaze.OneEuroFilter

/** Adaptive screen-pixel smoothing used only by the live article reading path. */
internal class MgazeNetReadingSmoother {
    private val xFilter = OneEuroFilter(minCutoff = MIN_CUTOFF, beta = BETA)
    private val yFilter = OneEuroFilter(minCutoff = MIN_CUTOFF, beta = BETA)

    fun filterX(value: Float, captureMs: Long): Float = xFilter.filter(value,captureMs)
    fun filterY(value: Float, captureMs: Long): Float = yFilter.filter(value,captureMs)

    fun reset() {
        xFilter.reset()
        yFilter.reset()
    }

    companion object {
        // Frozen article-output parameters; independent instruments keep their own declared stream.
        const val MIN_CUTOFF = 0.7
        const val BETA = 0.002
    }
}
