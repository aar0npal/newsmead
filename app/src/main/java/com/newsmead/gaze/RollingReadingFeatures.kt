package com.newsmead.gaze

import com.newsmead.gaze.mahalanobis.*
import java.util.ArrayDeque

/** Existing cumulative-counter window definitions, without a detector or baseline. */
class RollingReadingFeatures(private val config: MahalanobisConfig = MahalanobisConfig()) {
    private data class Counters(val at: Long, val regressions: Int, val dwellMs: Long, val stays: Int)
    private data class Quality(val at: Long, val valid: Boolean)
    private val counters = ArrayDeque<Counters>()
    private val quality = ArrayDeque<Quality>()
    private var latest = Counters(0, 0, 0, 0)
    private var lastTick: Long? = null
    private var lastInput: Long? = null
    private var discontinuityAt: Long? = null

    fun onScore(score: ReadingStateInferencer.RsiScore) {
        if (score.regressionCount < latest.regressions || score.totalDwellMs < latest.dwellMs ||
            score.fixationCount < latest.stays) reset()
        latest = Counters(score.timestampMs, score.regressionCount, score.totalDwellMs, score.fixationCount)
    }

    fun recordQuality(atMs: Long, valid: Boolean) {
        val previous = lastInput
        if (previous != null && atMs <= previous) return
        if (previous != null && atMs - previous > config.maxGapMs) discontinuityAt = atMs
        lastInput = atMs
        quality.addLast(Quality(atMs, valid))
        trimQuality(atMs)
    }

    /** Called on the elapsed-real-time schedule even when no usable gaze is delivered. */
    fun tick(atMs: Long): RdfFeatureVector? {
        val previous = lastTick
        if (previous != null && atMs - previous < config.updateIntervalMs) return null
        if (previous != null && atMs - previous > config.maxGapMs) discontinuityAt = atMs
        lastTick = atMs
        counters.addLast(latest.copy(at = atMs))
        while (counters.size > 2) {
            val iterator = counters.iterator()
            iterator.next()
            if (iterator.next().at > atMs - config.windowMs) break
            counters.removeFirst()
        }
        trimQuality(atMs)
        val first = counters.first
        val span = atMs - first.at
        val validCount = quality.count { it.valid }
        val fraction = if (quality.isEmpty()) 0.0 else validCount.toDouble() / quality.size
        val fresh = lastInput?.let { atMs - it in 0..config.maxGapMs } == true
        val state = when {
            span < config.windowMs -> FeatureQuality.INSUFFICIENT
            span > config.windowMs + config.updateIntervalMs -> FeatureQuality.DISCONTINUITY
            discontinuityAt?.let { it > first.at } == true -> FeatureQuality.DISCONTINUITY
            !fresh || quality.peekLast()?.valid != true || fraction < config.minimumValidFraction -> FeatureQuality.MISSING
            else -> FeatureQuality.VALID
        }
        val values = if (span > 0) RdfValues(
            (latest.regressions - first.regressions) * 60_000.0 / span,
            ((latest.dwellMs - first.dwellMs).toDouble() / span).coerceIn(0.0, 1.0),
            (latest.stays - first.stays) * 60_000.0 / span,
        ) else RdfValues(0.0, 0.0, 0.0)
        return RdfFeatureVector(atMs, first.at, atMs, values, state, fraction,
            validCount, quality.size)
    }

    fun reset() {
        counters.clear(); quality.clear(); latest = Counters(0, 0, 0, 0)
        lastTick = null; lastInput = null; discontinuityAt = null
    }

    private fun trimQuality(atMs: Long) {
        while (quality.isNotEmpty() && quality.first.at < atMs - config.windowMs) quality.removeFirst()
    }
}
