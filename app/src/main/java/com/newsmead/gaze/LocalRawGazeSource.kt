package com.newsmead.gaze

import androidx.lifecycle.LifecycleOwner

/**
 * Local phone-side source of uncalibrated gaze features. A MediaPipe/iris
 * implementation should emit the same feature pair saved by 16-point
 * calibration: gaze_x,gaze_y before screen mapping.
 */
interface LocalRawGazeSource {
    data class Sample(
        val gazeX: Float,
        val gazeY: Float,
        val timestampMs: Long,
        val eye1X: Float = Float.NaN,
        val eye1Y: Float = Float.NaN,
        val eye2X: Float = Float.NaN,
        val eye2Y: Float = Float.NaN,
        val faceCenterX: Float = Float.NaN,
        val faceCenterY: Float = Float.NaN,
        val faceScale: Float = Float.NaN,
        val headRollDeg: Float = Float.NaN,
    ) {
        val hasPerEye: Boolean
            get() = eye1X.isFinite() && eye1Y.isFinite() && eye2X.isFinite() && eye2Y.isFinite()

        val posture: PostureFeatures
            get() = PostureFeatures(faceCenterX, faceCenterY, faceScale, headRollDeg)
    }

    fun interface OnRawGaze {
        fun onRawGaze(sample: Sample)
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

    enum class DiagnosticOutcome {
        EMITTED,
        BLINK_DROPPED,
        NO_FACE,
        INSUFFICIENT_LANDMARKS,
        BUSY_DROPPED,
    }

    /**
     * Passive per-result telemetry for diagnostics. None of these values are used
     * to produce gaze; consumers may log them without changing tracker behavior.
     */
    data class Diagnostics(
        val sequence: Long,
        val outcome: DiagnosticOutcome,
        val captureTimestampNs: Long,
        val submittedElapsedNs: Long,
        val resultElapsedNs: Long,
        val frameWidth: Int,
        val frameHeight: Int,
        val rotationDegrees: Int,
        val eye1X: Float = Float.NaN,
        val eye1Y: Float = Float.NaN,
        val eye2X: Float = Float.NaN,
        val eye2Y: Float = Float.NaN,
        val gazeX: Float = Float.NaN,
        val gazeY: Float = Float.NaN,
        val eye1Openness: Float = Float.NaN,
        val eye2Openness: Float = Float.NaN,
        val faceCenterX: Float = Float.NaN,
        val faceCenterY: Float = Float.NaN,
        val faceScale: Float = Float.NaN,
        val headRollDeg: Float = Float.NaN,
        val busyDroppedFrames: Long,
    )

    fun interface OnDiagnostics {
        fun onDiagnostics(diagnostics: Diagnostics)
    }

    /** Development-only same-frame comparison; it never replaces [OnRawGaze]. */
    fun interface OnHybridEyeDiagnostics {
        fun onDiagnostics(sample: HybridEyeShadowSample)
    }

    fun setOnRawGaze(listener: OnRawGaze)
    fun setOnFps(listener: OnFps) {}
    fun setOnBlinkStats(listener: OnBlinkStats) {}
    fun setOnDiagnostics(listener: OnDiagnostics) {}
    fun clearOnDiagnostics() {}
    fun setOnHybridEyeDiagnostics(listener: OnHybridEyeDiagnostics?) {}
    fun start(owner: LifecycleOwner)
    fun stop()
}
