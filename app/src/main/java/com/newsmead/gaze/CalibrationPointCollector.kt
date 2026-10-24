package com.newsmead.gaze

import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Shared per-point capture engine for both full 16-point calibration and the
 * re-calibration / accuracy screen (docs/calibration-design.md §7.2).
 *
 * Drives one target through APPEAR -> HOLD -> SETTLE -> SAMPLE (adaptive) ->
 * CONFIRM on the supplied [CalibrationView], filtering the collected samples with
 * [FixationWindowFilter], and reports the [FixationWindowFilter.Result]. The host
 * owns sequencing, retry/exclusion policy, quality gating and logging; this owns
 * only the capture mechanics, so both screens share identical sampling instead of
 * duplicating (and silently diverging in) it.
 */
class CalibrationPointCollector(
    private val view: CalibrationView,
    private val tone: ToneGenerator?,
    private val filter: FixationWindowFilter = FixationWindowFilter(),
) {

    private val handler = Handler(Looper.getMainLooper())
    private val buffer = ArrayList<FixationWindowFilter.Sample>()
    private var collecting = false
    private var sampleWindowListener: ((Boolean) -> Unit)? = null
    private var sampleStartMs = 0L
    private var onDone: ((FixationWindowFilter.Result) -> Unit)? = null

    /** Forward every raw gaze sample here; buffered only during the SAMPLE window. */
    fun onRawSample(sample: LocalRawGazeSource.Sample) {
        if (collecting) {
            buffer.add(
                FixationWindowFilter.Sample(
                    timestampMs = sample.timestampMs,
                    x = sample.gazeX,
                    y = sample.gazeY,
                    eye1X = sample.eye1X,
                    eye1Y = sample.eye1Y,
                    eye2X = sample.eye2X,
                    eye2Y = sample.eye2Y,
                    faceCenterX = sample.faceCenterX,
                    faceCenterY = sample.faceCenterY,
                    faceScale = sample.faceScale,
                    headRollDeg = sample.headRollDeg,
                ),
            )
        }
    }

    /** Screen-space compatibility path used by the nine-point accuracy collector. */
    fun onRawSample(x: Float, y: Float, timestampMs: Long) =
        onRawSample(LocalRawGazeSource.Sample(x, y, timestampMs))

    /** Passive notification used to align diagnostic telemetry with SAMPLE only. */
    fun setOnSampleWindowChanged(listener: (Boolean) -> Unit) {
        sampleWindowListener = listener
    }

    /**
     * Capture the target at [x],[y]. [onDone] receives the filtered result: on
     * ACCEPTED a confirm flash + tone have already fired; on a failure status the
     * host decides whether to re-capture. One attempt per call.
     */
    fun capture(x: Float, y: Float, onDone: (FixationWindowFilter.Result) -> Unit) {
        this.onDone = onDone
        setCollecting(false)
        buffer.clear()
        // Finish the visual contraction before the unsampled settle phase. The
        // target then stays completely stationary before and during SAMPLE, so
        // target acquisition or a row-wrap saccade cannot be mistaken for a
        // measured fixation merely because the cue was still moving/shrinking.
        view.showTarget(x, y, ACQUISITION_CONTRACTION_MS)
        // APPEAR (animation) + HOLD_ATTENTION (older-adult saccadic latency).
        handler.postDelayed({ settle() }, CalibrationView.APPEAR_MS + HOLD_MS)
    }

    /** Cancel any in-flight capture (on stop / redo / activity teardown). */
    fun cancel() {
        handler.removeCallbacksAndMessages(null)
        setCollecting(false)
        onDone = null
    }

    /** Static, unsampled: overshoot/correction saccades land here. */
    private fun settle() {
        handler.postDelayed({ startSample() }, SETTLE_MS)
    }

    private fun startSample() {
        buffer.clear()
        setCollecting(true)
        sampleStartMs = SystemClock.uptimeMillis()
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP, TICK_TONE_MS)
        handler.postDelayed({ check() }, BASE_SAMPLE_MS)
    }

    /** Accept as soon as the filter is satisfied; else extend to the timeout. */
    private fun check() {
        val result = filter.filter(buffer.toList())
        if (result.status == FixationWindowFilter.Status.ACCEPTED) {
            setCollecting(false)
            view.flashConfirm()
            tone?.startTone(ToneGenerator.TONE_PROP_ACK, ACK_TONE_MS)
            onDone?.invoke(result)
            return
        }
        if (SystemClock.uptimeMillis() - sampleStartMs < SAMPLE_TIMEOUT_MS) {
            handler.postDelayed({ check() }, SAMPLE_RECHECK_MS)
        } else {
            setCollecting(false)
            onDone?.invoke(result)
        }
    }

    private fun setCollecting(value: Boolean) {
        if (collecting == value) return
        collecting = value
        sampleWindowListener?.invoke(value)
    }

    companion object {
        // Per-point state timings (docs/calibration-design.md §2.3).
        // Give participants 1.8 s total before recording: 1.0 s to acquire the
        // contracting cue, then 0.8 s on the stationary center. This applies to
        // every target rather than special-casing the first/left column.
        private const val HOLD_MS = 800L
        private const val SETTLE_MS = 800L
        private const val ACQUISITION_CONTRACTION_MS = CalibrationView.APPEAR_MS + HOLD_MS
        private const val BASE_SAMPLE_MS = 700L
        private const val SAMPLE_RECHECK_MS = 250L
        private const val SAMPLE_TIMEOUT_MS = 1500L
        private const val TICK_TONE_MS = 50
        private const val ACK_TONE_MS = 100
    }
}
