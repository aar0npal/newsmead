package com.newsmead.gaze

import androidx.lifecycle.LifecycleOwner

/**
 * The single boundary all gaze output flows through. Downstream stages (line
 * mapping, RSI) depend only on this interface and its [onGaze] stream, so the
 * tracker implementation can be swapped without touching anything downstream.
 * Current live implementation is local: phone tracker features are calibrated by
 * the saved 16-point calibration and emitted as full-screen phone pixels.
 */
interface GazeProvider {

    /** Receives smoothed on-screen gaze estimates in full-screen pixels. */
    fun interface OnGaze {
        fun onGaze(x: Float, y: Float)
    }

    fun setOnGaze(listener: OnGaze)

    /** Begin producing gaze, tied to [owner]'s lifecycle. */
    fun start(owner: LifecycleOwner)

    /** Release tracker resources. */
    fun stop()
}
