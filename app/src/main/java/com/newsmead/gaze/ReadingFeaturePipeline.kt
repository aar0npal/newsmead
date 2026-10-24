package com.newsmead.gaze

import com.newsmead.gaze.mahalanobis.MahalanobisConfig
import com.newsmead.gaze.mahalanobis.RdfFeatureVector

/** Shared baseline/live extraction. Times use elapsed real time in milliseconds. */
class ReadingFeaturePipeline(val config: MahalanobisConfig = MahalanobisConfig()) {
    private val rolling = RollingReadingFeatures(config)
    private val stabilizer = GazeTargetStabilizer()
    private var lastValidAt: Long? = null
    private var lastCapture: Long? = null
    var target: TextTarget = TextTarget.INVALID
        private set
    private fun newInferencer() = ReadingStateInferencer(object : ReadingStateInferencer.Listener {
        override fun onLineSample(sample: ReadingStateInferencer.LineSample) = Unit
        override fun onFixation(event: ReadingStateInferencer.FixationEvent) = Unit
        override fun onDwell(event: ReadingStateInferencer.DwellEvent) = Unit
        override fun onRegression(event: ReadingStateInferencer.RegressionEvent) = Unit
        override fun onScore(score: ReadingStateInferencer.RsiScore) = rolling.onScore(score)
    })
    private var inferencer = newInferencer()

    fun sample(rawTarget: TextTarget, valid: Boolean, captureMs: Long) {
        val previous = lastCapture
        if (previous != null && captureMs <= previous) return
        if (previous != null && captureMs - previous > config.maxGapMs) interrupt()
        lastCapture = captureMs
        val usable = valid && rawTarget.isValid
        rolling.recordQuality(captureMs, usable)
        if (!usable) { interrupt(); return }
        target = stabilizer.update(rawTarget, captureMs)
        if (target.isValid) inferencer.onLine(target.lineIndex, target.lineCount, captureMs)
        lastValidAt = captureMs
    }

    fun tick(nowMs: Long): RdfFeatureVector? {
        if (lastCapture?.let { nowMs - it > config.maxGapMs } == true) interrupt()
        return rolling.tick(nowMs)
    }

    private fun interrupt() {
        lastValidAt?.let(inferencer::breakContinuity)
        lastValidAt = null
        target = TextTarget.INVALID
        stabilizer.reset()
    }

    /** A scroll interrupts line events, but keeps the timed feature window intact. */
    fun breakContinuity() = interrupt()

    /** Reflow, passage and navigation boundaries reset features, never the frozen model. */
    fun boundary() {
        interrupt(); rolling.reset(); inferencer = newInferencer(); lastCapture = null
    }
}
