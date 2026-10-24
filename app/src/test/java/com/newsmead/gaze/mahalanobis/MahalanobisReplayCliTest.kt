package com.newsmead.gaze.mahalanobis

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MahalanobisReplayCliTest {
    @get:Rule val temporary = TemporaryFolder()
    private val config = MahalanobisConfig(minimumBaselineDurationMs = 0, minimumRetainedWindows = 6, lambda = 0.0, persistenceMs = 500, recoveryMs = 500)
    private fun profile(c: MahalanobisConfig = config) = JSONObject().put("type", "baseline_profile").put("config", MahalanobisReplayCli.configJson(c))
    private fun vector(type: String, t: Long, r: Double = 10.0, d: Double = .5, f: Double = 100.0): JSONObject = JSONObject()
        .put("type", type).put("timestamp", t).put("windowStart", t - 10_000).put("windowEnd", t)
        .put("R", r).put("D", d).put("F", f).put("qualityValid", true).put("validFraction", 1.0)
    private fun log(name: String, rows: List<JSONObject>): File = temporary.newFile(name).also { it.writeText(rows.joinToString("\n") { row -> row.toString() } + "\n") }
    private fun baselineRows() = listOf(profile(), vector("baseline_vector", 10_000, r = 9.0), vector("baseline_vector", 10_500, r = 11.0),
        vector("baseline_vector", 11_000, d = .4), vector("baseline_vector", 11_500, d = .6),
        vector("baseline_vector", 12_000, f = 90.0), vector("baseline_vector", 12_500, f = 110.0))

    @Test fun exportsThreeThresholdsUsingCoreAndIgnoresPreviouslyRecordedScores() {
        val baseline = log("baseline.jsonl", baselineRows())
        val vectors = listOf(vector("scoring_update", 20_000, r = 15.0).put("MD2", 0).put("scoreStatus", "BASELINE_UNAVAILABLE"),
            vector("feature_vector", 20_500, r = 15.0), vector("scoring_update", 21_000),
            vector("scoring_update", 21_500).put("qualityValid", false).put("R", JSONObject.NULL))
        val input = log("input.jsonl", vectors)
        val output = temporary.newFolder("replay")
        val summary = MahalanobisReplayCli.run(baseline, input, output)
        assertFalse(summary.getBoolean("labelsUsed"))
        for (name in listOf("p90", "p95", "p99")) {
            val rows = File(output, "${name}_updates.csv").readLines().drop(1).map { it.split(',') }
            assertEquals(4, rows.size)
            assertEquals(62.5, rows[0][1].toDouble(), 1e-8)
            assertEquals("NOT_DETECTED", rows[0][2])
            assertEquals("true", rows[0][3])
            assertEquals("DETECTED", rows[1][2])
            assertEquals("UNAVAILABLE", rows[3][2])
            assertEquals("", rows[3][1])
            val episode = File(output, "${name}_episodes.csv").readLines().drop(1).single().split(',')
            assertEquals(listOf("20000", "20500", "500"), episode.take(3))
            assertEquals("2", episode[5])
        }
        assertTrue(File(output, "replay_summary.json").isFile)
    }

    @Test fun missingOrIncompleteConfigIsRejectedBeforeExport() {
        val input = log("input.jsonl", listOf(vector("scoring_update", 20_000)))
        val noProfile = log("missing.jsonl", baselineRows().drop(1))
        val incomplete = profile().also { it.getJSONObject("config").remove("lambda") }
        val incompleteLog = log("incomplete.jsonl", listOf(incomplete) + baselineRows().drop(1))
        for (baseline in listOf(noProfile, incompleteLog)) {
            try { MahalanobisReplayCli.run(baseline, input, File(temporary.root, baseline.name + "_out")); fail("Missing explicit config accepted") }
            catch (expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("config", ignoreCase = true)) }
        }
    }

    @Test fun incompatibleConfigMalformedTimestampAndMalformedQualityAreRejected() {
        val baseline = log("baseline.jsonl", baselineRows())
        val badInputs = listOf(
            listOf(profile(config.copy(lambda = 1.0)), vector("scoring_update", 20_000)),
            listOf(vector("scoring_update", 20_000).put("timestamp", "20000")),
            listOf(vector("scoring_update", 20_000).put("qualityValid", "true")))
        badInputs.forEachIndexed { index, rows ->
            val input = log("bad$index.jsonl", rows)
            try { MahalanobisReplayCli.run(baseline, input, File(temporary.root, "out$index")); fail("Malformed input accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun invalidBaselineExportsUnavailableRatherThanFabricatingDistances() {
        val baseline = log("baseline.jsonl", baselineRows().take(3))
        val input = log("input.jsonl", listOf(vector("scoring_update", 20_000)))
        val output = temporary.newFolder("replay")
        val summary = MahalanobisReplayCli.run(baseline, input, output)
        assertEquals("INSUFFICIENT_WINDOWS", summary.getJSONObject("thresholds").getJSONObject("P95").getString("baselineStatus"))
        assertEquals("UNAVAILABLE", File(output, "p95_updates.csv").readLines()[1].split(',')[2])
        assertEquals(1, File(output, "p95_episodes.csv").readLines().size)
    }

    @Test fun exactConfigRoundTripAndUnknownFieldsAreRejected() {
        assertEquals(config, MahalanobisReplayCli.parseConfig(MahalanobisReplayCli.configJson(config)))
        try { MahalanobisReplayCli.parseConfig(MahalanobisReplayCli.configJson(config).put("unknown", 1)); fail("Unknown config field accepted") }
        catch (_: IllegalArgumentException) { }
    }
}
