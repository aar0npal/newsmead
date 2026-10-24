package com.newsmead.mgazenetbenchmark

import android.content.Context
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object SyntheticRunner {
    /** Synthetic fixtures only. Finite/repeated output is NOT an accuracy/parity pass. */
    fun run(context: Context, cancelled: () -> Boolean): File {
        val report = BenchmarkReport(context, "synthetic")
        val fixtures = JSONArray()
        try {
            Preprocessor().use { preprocess -> MnnEstimator(context).use { model ->
                for (fixture in SyntheticFixtures.images()) {
                    check(!cancelled()) { "Cancelled" }
                    val input = preprocess.prepare(fixture.frame, fixture.crops)
                    val output = model.infer(input)
                    val again = model.infer(input)
                    check(output.indices.all { kotlin.math.abs(output[it] - again[it]) <= 1e-5f }) { "Repeated inference differs" }
                    fixtures.put(JSONObject().put("name", fixture.name)
                        .put("face", report.tensor(fixture.name + "_face", input.face))
                        .put("left", report.tensor(fixture.name + "_left", input.left))
                        .put("right", report.tensor(fixture.name + "_right", input.right))
                        .put("rect", report.tensor(fixture.name + "_rect", input.rect))
                        .put("output", BenchmarkReport.array(output)))
                }
                val fixture = SyntheticFixtures.images().first()
                val input = preprocess.prepare(fixture.frame, fixture.crops)
                repeat(20) { check(!cancelled()); model.infer(input) }
                val inference = ArrayList<Double>()
                val total = ArrayList<Double>()
                repeat(100) {
                    check(!cancelled()) { "Cancelled" }
                    val start = SystemClock.elapsedRealtimeNanos()
                    val prepared = preprocess.prepare(fixture.frame, fixture.crops)
                    val modelStart = SystemClock.elapsedRealtimeNanos()
                    model.infer(prepared)
                    val end = SystemClock.elapsedRealtimeNanos()
                    inference.add((end - modelStart) / 1e6)
                    total.add((end - start) / 1e6)
                }
                report.root.put("model_with_io_ms", BenchmarkReport.stats(inference))
                    .put("synthetic_preprocess_and_model_ms", BenchmarkReport.stats(total))
                    .put("warmup_iterations", 20)
            } }
            SvrCalibration().use { calibration ->
                val start = SystemClock.elapsedRealtimeNanos()
                calibration.fit(SyntheticFixtures.calibrationFeatures(), SyntheticFixtures.calibrationLabels())
                report.root.put("synthetic_svr_training_ms", (SystemClock.elapsedRealtimeNanos() - start) / 1e6)
                    .put("synthetic_support_vector_counts", JSONArray(calibration.supportVectorCounts))
                val queries = SyntheticFixtures.queries()
                report.root.put("synthetic_svr_predictions", JSONArray(queries.map { BenchmarkReport.array(calibration.predict(it)) }))
                val times = ArrayList<Double>()
                repeat(100) {
                    check(!cancelled()) { "Cancelled" }
                    val t = SystemClock.elapsedRealtimeNanos()
                    calibration.predict(queries[it % queries.size])
                    times.add((SystemClock.elapsedRealtimeNanos() - t) / 1e6)
                }
                report.root.put("synthetic_svr_predict_ms", BenchmarkReport.stats(times))
            }
            report.root.put("outcome", "completed_pending_external_parity")
        } catch (error: Throwable) {
            report.root.put("outcome", "failed_or_cancelled").put("error", error.toString())
        }
        report.root.put("fixtures", fixtures)
        return report.save()
    }
}
