package com.newsmead.gaze

import androidx.lifecycle.LifecycleOwner

/**
 * Local phone-side source of uncalibrated gaze features. A MediaPipe/iris
 * implementation should emit the same feature pair saved by 16-point
 * calibration: gaze_x,gaze_y before screen mapping.
 */
interface LocalRawGazeSource {
    fun interface OnRawGaze {
        fun onRawGaze(gazeX: Float, gazeY: Float, timestampMs: Long)
    }

    /** Optional monitor of the tracker's processing frame rate (results/sec). */
    fun interface OnFps {
        fun onFps(fps: Float)
    }

    data class BlinkStats(
        val totalResults: Long,
        val emittedSamples: Long,
        val blinkDroppedFrames: Long,
        val noFaceFrames: Long,
        val lastOpenness: Float,
        val blink: Boolean,
        val closeThreshold: Float,
        val openThreshold: Float,
    )

    /** Optional monitor for blink filtering and landmark-drop diagnostics. */
    fun interface OnBlinkStats {
        fun onBlinkStats(stats: BlinkStats)
    }

    fun setOnRawGaze(listener: OnRawGaze)
    fun setOnFps(listener: OnFps) {}
    fun setOnBlinkStats(listener: OnBlinkStats) {}
    fun start(owner: LifecycleOwner)
    fun stop()
}
