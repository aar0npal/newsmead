package com.newsmead.research

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.newsmead.gaze.AdaptiveScaffoldConfig
import com.newsmead.gaze.ScaffoldState
import com.newsmead.gaze.mahalanobis.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Private feature-only journal. No images, raw gaze, uploads or invented labels. */
class MahalanobisSessionLog(context: Context, kind: String) {
    companion object {
        /** Explicit researcher action; include prior completed sessions as well as this one. */
        fun shareAll(context: Context, current: MahalanobisSessionLog? = null): Intent {
            current?.let {
                if (!it.closed) it.queue.submit {}.get(5, TimeUnit.SECONDS)
                else check(it.queue.awaitTermination(5, TimeUnit.SECONDS)) { "Log is still closing" }
            }
            current?.failure?.let { throw it }
            val files = context.noBackupFilesDir.listFiles { candidate ->
                candidate.isFile && candidate.name.startsWith("mahalanobis_") && candidate.name.endsWith(".jsonl")
            }.orEmpty().sortedByDescending(File::lastModified)
            require(files.isNotEmpty()) { "No detector logs have been recorded" }
            val staging = File(context.cacheDir, "research-export").apply { mkdirs() }
            val uris = ArrayList<android.net.Uri>()
            for (file in files) {
                val copy = File(staging, file.name)
                file.copyTo(copy, overwrite = true)
                uris.add(FileProvider.getUriForFile(context, context.packageName + ".research.fileprovider", copy))
            }
            return Intent.createChooser(Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType("application/x-ndjson").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Export detector logs")
        }
    }
    val file = File(context.noBackupFilesDir, "mahalanobis_${kind}_${UUID.randomUUID()}.jsonl")
    private val writer = file.bufferedWriter()
    private val queue = Executors.newSingleThreadExecutor()
    @Volatile private var failure: Exception? = null
    private var closed = false
    init { write(JSONObject().put("type", "session_start").put("schemaVersion", 2)
        .put("timeBase", "elapsed_realtime_ms").put("researchSessionId", ResearchSession.sessionId)) }
    fun write(record: JSONObject) {
        if (closed) return
        val line = record.toString()
        queue.execute { try { writer.appendLine(line); writer.flush() } catch (e: Exception) { failure = e } }
    }
    fun close() {
        if (closed) return
        closed = true
        queue.execute { writer.close() }
        queue.shutdown()
    }
    fun share(context: Context): Intent {
        if (!closed) queue.submit {}.get(5, TimeUnit.SECONDS)
        else check(queue.awaitTermination(5, TimeUnit.SECONDS)) { "Log is still closing" }
        failure?.let { throw it }
        val copy = File(context.cacheDir, "research-export/${file.name}")
        copy.parentFile!!.mkdirs(); file.copyTo(copy, overwrite = true)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".research.fileprovider", copy)
        return Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/x-ndjson")
            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Export detector log")
    }
}

object MahalanobisJson {
    fun config(c: MahalanobisConfig) = JSONObject()
        .put("windowMs", c.windowMs).put("updateIntervalMs", c.updateIntervalMs)
        .put("maxWindowOverhangMs", c.maxWindowOverhangMs)
        .put("minimumBaselineDurationMs", c.minimumBaselineDurationMs).put("minimumRetainedWindows", c.minimumRetainedWindows)
        .put("lambda", c.lambda).put("sampling", c.sampling.name).put("thinningIntervalMs", c.thinningIntervalMs)
        .put("percentile", c.percentile.name).put("customThresholdMd2", c.customThresholdMd2 ?: JSONObject.NULL)
        .put("persistenceMs", c.persistenceMs).put("recoveryMs", c.recoveryMs)
        .put("recoveryThresholdRatio", c.recoveryThresholdRatio).put("maxGapMs", c.maxGapMs)
        .put("nearTolerance", c.nearTolerance).put("minimumValidFraction", c.minimumValidFraction)
    fun scaffoldConfig(c: AdaptiveScaffoldConfig) = JSONObject()
        .put("supportLevel", c.supportLevel.name)
        .put("lossOffTextMs", c.lossOffTextMs)
        .put("minimumLossLineJump", c.minimumLossLineJump)
        .put("reentryEvidenceMs", c.reentryEvidenceMs)
        .put("targetConfirmMs", c.targetConfirmMs)
        .put("targetInvalidGraceMs", c.targetInvalidGraceMs)
    fun matrix(m: Matrix3) = JSONArray((0..2).map { row -> (0..2).map { col -> m[row, col] } })
    fun values(v: RdfValues) = JSONArray((0..2).map { v[it] })
    fun vector(v: RdfFeatureVector, type: String, c: MahalanobisConfig) = JSONObject()
        .put("type", type).put("timestamp", v.timestampMs).put("windowStart", v.windowStartMs).put("windowEnd", v.windowEndMs)
        .put("R", finite(v.values.regressionRatePerMinute)).put("D", finite(v.values.dwellFraction))
        .put("F", finite(v.values.fixationRatePerMinute)).put("qualityValid", v.isUsable(c))
        .put("qualityStatus", v.quality.name).put("validFraction", finite(v.validFraction))
        .put("validSampleCount", v.validSampleCount ?: JSONObject.NULL).put("totalSampleCount", v.totalSampleCount ?: JSONObject.NULL)
    fun profile(p: FrozenMahalanobisProfile) = JSONObject().put("type", "baseline_profile")
        .put("config", config(p.config)).put("baselineValid", true).put("mu", values(p.mean))
        .put("covariance", matrix(p.covariance)).put("regularizedCovariance", matrix(p.regularizedCovariance))
        .put("lambda", p.config.lambda).put("regularizationApplied", p.config.lambda > 0)
        .put("matrixDiagnostic", JSONObject().put("kind", "cholesky_diagonal_ratio").put("value", p.stability.choleskyDiagonalRatio))
        .put("baselineMD2", JSONArray(p.baselineMd2)).put("P90", p.p90Md2).put("P95", p.p95Md2).put("P99", p.p99Md2)
        .put("activeThreshold", p.thresholdMd2).put("thresholdMode", p.config.percentile.name)
        .put("totalBaselineWindows", p.sampling.receivedCount).put("validBaselineWindows", p.sampling.validCount)
        .put("invalidBaselineWindows", p.sampling.invalidCount).put("baselineCoverageMs", p.sampling.coverageMs)
        .put("retainedBaselineWindows", p.sampling.retainedCount).put("baselineSamplingMode", p.sampling.policy.name)
        .put("independentObservations", false).put("referenceDistribution", "in_sample")
    fun update(score: MahalanobisScore, state: TemporalResult, p: FrozenMahalanobisProfile?, scaffold: ScaffoldState,
        c: MahalanobisConfig) = scaffoldFields(vector(score.vector, "scoring_update", c)
        .put("mu_R", p?.mean?.regressionRatePerMinute ?: JSONObject.NULL)
        .put("mu_D", p?.mean?.dwellFraction ?: JSONObject.NULL).put("mu_F", p?.mean?.fixationRatePerMinute ?: JSONObject.NULL)
        .put("delta_R", score.delta?.regressionRatePerMinute ?: JSONObject.NULL)
        .put("delta_D", score.delta?.dwellFraction ?: JSONObject.NULL).put("delta_F", score.delta?.fixationRatePerMinute ?: JSONObject.NULL)
        .put("directions", score.directions?.let { JSONArray(it.map(Direction::name)) } ?: JSONObject.NULL)
        .put("MD2", score.md2 ?: JSONObject.NULL).put("MD", score.md ?: JSONObject.NULL)
        .put("scoreStatus", score.status.name).put("activeThreshold", score.thresholdMd2 ?: JSONObject.NULL)
        .put("P90", p?.p90Md2 ?: JSONObject.NULL).put("P95", p?.p95Md2 ?: JSONObject.NULL)
        .put("P99", p?.p99Md2 ?: JSONObject.NULL)
        .put("thresholdMode", c.percentile.name).put("candidateDeviation", state.high ?: JSONObject.NULL)
        .put("confirmedInstability", state.active).put("baselineValid", p != null)
        .put("persistenceState", state.status.name), scaffold, score.vector.timestampMs)
    fun scaffoldTarget(state: ScaffoldState, recordedAtMs: Long, reason: String) = scaffoldFields(
        JSONObject().put("type", "scaffold_target_update")
            .put("timestamp", recordedAtMs)
            .put("reason", reason),
        state,
        recordedAtMs,
    )
    fun episode(e: HighEpisode) = JSONObject().put("type", "candidate_episode").put("episodeStart", e.startMs)
        .put("episodeEnd", e.endMs).put("duration", e.durationMs).put("peakMD2", e.peakMd2)
        .put("meanMD2", e.meanMd2).put("numberOfUpdates", e.sampleCount).put("thresholdUsed", e.thresholdMd2)
    private fun scaffoldFields(record: JSONObject, state: ScaffoldState, referenceMs: Long): JSONObject {
        val captureMs = state.targetCaptureMs
        val targetAgeMs = captureMs?.let { (referenceMs - it).takeIf { age -> age >= 0 } }
        return record
            .put("scaffoldState", state.level.name)
            .put("scaffoldTargetStatus", state.targetStatus.name)
            .put("scaffoldMappedLine", nullableLine(state.mappedLine))
            .put("scaffoldStabilizedLine", nullableLine(state.stabilizedLine))
            .put("scaffoldCurrentLine", nullableLine(state.currentLine))
            .put("scaffoldRenderedTargetLine", nullableLine(state.renderedTargetLine))
            .put("reentryTargetLine", nullableLine(state.reentryTargetLine))
            .put("scaffoldTargetCaptureTimestamp", captureMs ?: JSONObject.NULL)
            .put("scaffoldConfirmationTimestamp", state.confirmationTimestampMs ?: JSONObject.NULL)
            .put("scaffoldTargetNotBeforeTimestamp", state.targetNotBeforeMs ?: JSONObject.NULL)
            .put("scaffoldTargetAgeMs", targetAgeMs ?: JSONObject.NULL)
    }
    private fun nullableLine(line: Int): Any = if (line >= 0) line else JSONObject.NULL
    private fun finite(value: Double): Any = if (value.isFinite()) value else JSONObject.NULL
}
