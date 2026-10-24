package com.newsmead.gaze.mahalanobis

enum class TemporalStatus { CLEAR, PENDING, ACTIVE, RECOVERING, UNAVAILABLE, DISCONTINUITY }
data class HighEpisode(val startMs: Long, val endMs: Long, val durationMs: Long, val peakMd2: Double, val meanMd2: Double, val sampleCount: Int, val thresholdMd2: Double)
data class TemporalResult(val timestampMs: Long, val status: TemporalStatus, val active: Boolean, val high: Boolean?, val completedEpisode: HighEpisode?)

/** Raw contiguous high runs are episodes, independently of the persistence decision.
 * Invalid input, threshold changes, backwards/duplicate time and gaps end continuity.
 * An offending out-of-order sample is rejected and cannot seed new support.
 */
class MahalanobisTemporalController(private val config: MahalanobisConfig = MahalanobisConfig()) {
    private var lastTimestamp: Long? = null
    private var lastThreshold: Double? = null
    private var highStarted: Long? = null
    private var recoveryStarted: Long? = null
    private var active = false
    private var episode: EpisodeAccumulator? = null

    fun accept(score: MahalanobisScore): TemporalResult {
        val t = score.vector.timestampMs
        val previous = lastTimestamp
        val threshold = score.thresholdMd2
        val md2 = score.md2
        val usable = score.status == ScoreStatus.AVAILABLE && score.vector.isUsable(config) &&
            md2 != null && md2.isFinite() && md2 >= 0 && threshold != null && threshold.isFinite() && threshold >= 0
        val outOfOrder = previous != null && t <= previous
        val broken = previous != null && (outOfOrder || t - previous > config.maxGapMs || lastThreshold != threshold)
        var completed: HighEpisode? = null
        if (!usable || broken) {
            completed = clearEvidence()
            if (!outOfOrder) { lastTimestamp = t; lastThreshold = threshold }
            if (!usable || outOfOrder) return TemporalResult(t, if (outOfOrder) TemporalStatus.DISCONTINUITY else TemporalStatus.UNAVAILABLE, false, null, completed)
        }
        lastTimestamp = t
        lastThreshold = threshold
        val value = md2!!
        val high = value >= threshold!!
        if (high) {
            recoveryStarted = null
            if (highStarted == null) highStarted = t
            val e = episode ?: EpisodeAccumulator(t, threshold).also { episode = it }
            e.add(t, value)
            if (t - highStarted!! >= config.persistenceMs) active = true
        } else {
            completed = completed ?: episode?.finish()
            episode = null
            highStarted = null
            if (active && value < threshold * config.recoveryThresholdRatio) {
                if (recoveryStarted == null) recoveryStarted = t
                if (t - recoveryStarted!! >= config.recoveryMs) { active = false; recoveryStarted = null }
            } else recoveryStarted = null
        }
        val status = when { active && recoveryStarted != null -> TemporalStatus.RECOVERING; active -> TemporalStatus.ACTIVE; high -> TemporalStatus.PENDING; else -> TemporalStatus.CLEAR }
        return TemporalResult(t, status, active, high, completed)
    }

    /** Terminal close uses the last observed high time, never extrapolates through silence. */
    fun finish(): HighEpisode? {
        val result = clearEvidence()
        lastTimestamp = null
        lastThreshold = null
        return result
    }

    private fun clearEvidence(): HighEpisode? {
        val result = episode?.finish()
        episode = null
        highStarted = null
        recoveryStarted = null
        active = false
        return result
    }

    private class EpisodeAccumulator(private val start: Long, private val threshold: Double) {
        private var end = start
        private var peak = 0.0
        private var mean = 0.0
        private var count = 0
        fun add(timestamp: Long, md2: Double) { end = timestamp; peak = maxOf(peak, md2); count++; mean += (md2 - mean) / count }
        fun finish() = HighEpisode(start, end, end - start, peak, mean, count, threshold)
    }
}

data class ReplayResult(val scores: List<MahalanobisScore>, val decisions: List<TemporalResult>, val episodes: List<HighEpisode>)
/** Replay and online calls use exactly the same scorer and temporal implementation. */
object MahalanobisReplay {
    fun run(profile: FrozenMahalanobisProfile, vectors: Iterable<RdfFeatureVector>): ReplayResult {
        val scorer = MahalanobisScorer(profile)
        val controller = MahalanobisTemporalController(profile.config)
        val scores = mutableListOf<MahalanobisScore>()
        val decisions = mutableListOf<TemporalResult>()
        val episodes = mutableListOf<HighEpisode>()
        vectors.forEach { vector ->
            val score = scorer.score(vector)
            scores.add(score)
            val decision = controller.accept(score)
            decisions.add(decision)
            decision.completedEpisode?.let { episodes.add(it) }
        }
        controller.finish()?.let { episodes.add(it) }
        return ReplayResult(scores.toList(), decisions.toList(), episodes.toList())
    }
}
