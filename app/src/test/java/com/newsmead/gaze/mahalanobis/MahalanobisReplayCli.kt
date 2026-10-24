package com.newsmead.gaze.mahalanobis

import org.json.JSONObject
import java.io.File

/** Offline exploratory replay. No labels or outcome validity are inferred from these exports.
 *
 * Requires the original baseline vectors and an explicit baseline_profile.config record.
 * Recorded scores are ignored: all three thresholds use the production core afresh.
 */
object MahalanobisReplayCli {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 6 && args.toList().chunked(2).map { it[0] }.toSet() == setOf("--baseline", "--input", "--output-dir")) {
            "Usage: --baseline baseline.jsonl --input session.jsonl --output-dir directory"
        }
        val options = args.toList().chunked(2).associate { it[0] to it[1] }
        run(File(options.getValue("--baseline")), File(options.getValue("--input")), File(options.getValue("--output-dir")))
    }

    fun run(baselineFile: File, inputFile: File, outputDirectory: File): JSONObject {
        val baselineLog = readLog(baselineFile, setOf("baseline_vector"), requireConfig = true)
        val inputLog = readLog(inputFile, setOf("scoring_update", "feature_vector"), requireConfig = false)
        val config = baselineLog.config!!
        require(inputLog.config == null || inputLog.config == config) { "Input profile config differs from baseline profile config" }
        require(baselineLog.vectors.isNotEmpty()) { "No baseline_vector records" }
        require(inputLog.vectors.isNotEmpty()) { "No scoring_update or feature_vector records" }
        require(outputDirectory.isDirectory || outputDirectory.mkdirs()) { "Cannot create output directory: $outputDirectory" }
        val summary = JSONObject().put("analysis", "exploratory_mahalanobis_replay_v1")
            .put("baselineFile", baselineFile.canonicalPath).put("inputFile", inputFile.canonicalPath)
            .put("config", configJson(config)).put("labelsUsed", false)
            .put("interpretation", "In-sample empirical baseline thresholds; repeated windows are not independent observations. Independent labels and held-out evaluation are separate work.")
        val thresholds = JSONObject()
        for (percentile in listOf(ThresholdPercentile.P90, ThresholdPercentile.P95, ThresholdPercentile.P99)) {
            val replayConfig = config.copy(percentile = percentile)
            val collector = MahalanobisBaselineCollector(replayConfig)
            baselineLog.vectors.forEach { collector.add(it) }
            val fit = collector.freeze()
            val scorer = MahalanobisScorer(fit.profile)
            val controller = MahalanobisTemporalController(replayConfig)
            val episodes = mutableListOf<HighEpisode>()
            var confirmedCount = 0
            var unavailableCount = 0
            val prefix = percentile.name.lowercase()
            File(outputDirectory, "${prefix}_updates.csv").bufferedWriter().use { out ->
                out.appendLine("timestamp,MD2,predicted_state,candidate,confirmed,quality,windowStart,windowEnd,threshold,score_status,temporal_status,validFraction,validSampleCount,totalSampleCount")
                inputLog.vectors.forEach { vector ->
                    val score = scorer.score(vector)
                    val decision = controller.accept(score)
                    decision.completedEpisode?.let { episodes.add(it) }
                    val unavailable = decision.status == TemporalStatus.UNAVAILABLE || decision.status == TemporalStatus.DISCONTINUITY
                    val state = if (unavailable) "UNAVAILABLE" else if (decision.active) "DETECTED" else "NOT_DETECTED"
                    if (decision.active) confirmedCount++
                    if (unavailable) unavailableCount++
                    out.appendLine(listOf(vector.timestampMs, score.md2, state, decision.high, decision.active,
                        vector.quality.name, vector.windowStartMs, vector.windowEndMs, score.thresholdMd2,
                        score.status.name, decision.status.name, vector.validFraction.takeIf { it.isFinite() },
                        vector.validSampleCount, vector.totalSampleCount).joinToString(",") { it?.toString() ?: "" })
                }
            }
            controller.finish()?.let { episodes.add(it) }
            File(outputDirectory, "${prefix}_episodes.csv").bufferedWriter().use { out ->
                out.appendLine("start,end,duration,peak_MD2,mean_MD2,update_count,threshold")
                episodes.forEach { e -> out.appendLine(listOf(e.startMs, e.endMs, e.durationMs, e.peakMd2, e.meanMd2, e.sampleCount, e.thresholdMd2).joinToString(",")) }
            }
            thresholds.put(percentile.name, JSONObject().put("baselineStatus", fit.status.name)
                .put("thresholdMd2", fit.profile?.thresholdMd2 ?: JSONObject.NULL)
                .put("receivedBaselineWindows", fit.sampling.receivedCount).put("validBaselineWindows", fit.sampling.validCount)
                .put("invalidBaselineWindows", fit.sampling.invalidCount).put("baselineCoverageMs", fit.sampling.coverageMs)
                .put("retainedBaselineWindows", fit.sampling.retainedCount).put("samplingPolicy", fit.sampling.policy.name)
                .put("scoringUpdates", inputLog.vectors.size).put("confirmedUpdates", confirmedCount)
                .put("unavailableUpdates", unavailableCount).put("highEpisodes", episodes.size))
        }
        summary.put("thresholds", thresholds)
        File(outputDirectory, "replay_summary.json").writeText(summary.toString(2) + "\n")
        return summary
    }

    private data class LogData(val config: MahalanobisConfig?, val vectors: List<RdfFeatureVector>)
    private fun readLog(file: File, vectorTypes: Set<String>, requireConfig: Boolean): LogData {
        require(file.isFile) { "Missing log: $file" }
        var config: MahalanobisConfig? = null
        val vectors = mutableListOf<RdfFeatureVector>()
        file.useLines { lines -> lines.forEachIndexed { index, line ->
            if (line.isNotBlank()) try {
                val record = JSONObject(line.removePrefix("\uFEFF"))
                val type = record.getString("type")
                if (type == "baseline_profile") {
                    val parsed = parseConfig(record.getJSONObject("config"))
                    require(config == null || config == parsed) { "Multiple different baseline profile configs" }
                    config = parsed
                }
                if (type in vectorTypes) vectors.add(parseVector(record))
            } catch (e: Exception) { throw IllegalArgumentException("${file.name}:${index + 1}: ${e.message}", e) }
        } }
        require(!requireConfig || config != null) { "Missing baseline_profile.config in $file; implicit defaults are forbidden" }
        return LogData(config, vectors)
    }

    private fun parseVector(o: JSONObject): RdfFeatureVector {
        val valid = o.get("qualityValid") as? Boolean ?: error("qualityValid must be boolean")
        fun number(key: String): Double = if (!o.has(key) || o.isNull(key)) Double.NaN else strictDouble(o, key)
        fun optionalCount(key: String): Int? = if (!o.has(key) || o.isNull(key)) null else strictInt(o, key)
        return RdfFeatureVector(strictLong(o, "timestamp"), strictLong(o, "windowStart"), strictLong(o, "windowEnd"),
            RdfValues(number("R"), number("D"), number("F")), if (valid) FeatureQuality.VALID else FeatureQuality.MISSING,
            number("validFraction"), optionalCount("validSampleCount"), optionalCount("totalSampleCount"))
    }

    internal fun parseConfig(o: JSONObject): MahalanobisConfig {
        val expected = configJson(MahalanobisConfig()).keySet()
        require(o.keySet() == expected) { "Config keys must exactly match MahalanobisConfig: missing=${expected - o.keySet()}, unknown=${o.keySet() - expected}" }
        return MahalanobisConfig(
            windowMs = strictLong(o, "windowMs"), updateIntervalMs = strictLong(o, "updateIntervalMs"),
            maxWindowOverhangMs = strictLong(o, "maxWindowOverhangMs"), minimumBaselineDurationMs = strictLong(o, "minimumBaselineDurationMs"),
            minimumRetainedWindows = strictInt(o, "minimumRetainedWindows"), lambda = strictDouble(o, "lambda"),
            sampling = BaselineSampling.valueOf(o.getString("sampling")), thinningIntervalMs = strictLong(o, "thinningIntervalMs"),
            percentile = ThresholdPercentile.valueOf(o.getString("percentile")),
            customThresholdMd2 = if (o.isNull("customThresholdMd2")) null else strictDouble(o, "customThresholdMd2"),
            persistenceMs = strictLong(o, "persistenceMs"), recoveryMs = strictLong(o, "recoveryMs"),
            recoveryThresholdRatio = strictDouble(o, "recoveryThresholdRatio"), maxGapMs = strictLong(o, "maxGapMs"),
            nearTolerance = strictDouble(o, "nearTolerance"), minimumValidFraction = strictDouble(o, "minimumValidFraction"))
    }

    internal fun configJson(c: MahalanobisConfig) = JSONObject()
        .put("windowMs", c.windowMs).put("updateIntervalMs", c.updateIntervalMs).put("maxWindowOverhangMs", c.maxWindowOverhangMs)
        .put("minimumBaselineDurationMs", c.minimumBaselineDurationMs).put("minimumRetainedWindows", c.minimumRetainedWindows)
        .put("lambda", c.lambda).put("sampling", c.sampling.name).put("thinningIntervalMs", c.thinningIntervalMs)
        .put("percentile", c.percentile.name).put("customThresholdMd2", c.customThresholdMd2 ?: JSONObject.NULL)
        .put("persistenceMs", c.persistenceMs).put("recoveryMs", c.recoveryMs).put("recoveryThresholdRatio", c.recoveryThresholdRatio)
        .put("maxGapMs", c.maxGapMs).put("nearTolerance", c.nearTolerance).put("minimumValidFraction", c.minimumValidFraction)

    private fun strictDouble(o: JSONObject, key: String): Double {
        val value = o.get(key)
        require(value is Number && value.toDouble().isFinite()) { "$key must be a finite JSON number" }
        return value.toDouble()
    }
    private fun strictLong(o: JSONObject, key: String): Long {
        val value = o.get(key)
        require(value is Byte || value is Short || value is Int || value is Long || value is java.math.BigInteger) { "$key must be an integer JSON number" }
        return java.math.BigInteger(value.toString()).longValueExact()
    }
    private fun strictInt(o: JSONObject, key: String): Int = Math.toIntExact(strictLong(o, key))
}
