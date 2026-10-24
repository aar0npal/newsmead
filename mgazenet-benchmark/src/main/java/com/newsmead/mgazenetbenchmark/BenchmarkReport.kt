package com.newsmead.mgazenetbenchmark

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class BenchmarkReport(context: Context, val mode: String) {
    val directory = File(context.filesDir, "benchmarks/${System.currentTimeMillis()}_${UUID.randomUUID()}").apply { mkdirs() }
    val root = JSONObject().put("schema_version", 1).put("mode", mode)
        .put("device", Build.MODEL).put("android_sdk", Build.VERSION.SDK_INT)
        .put("runtime", MnnEstimator.RUNTIME).put("model_sha256", MnnEstimator.MODEL_SHA)
        .put("opencv", org.opencv.core.Core.VERSION).put("mnn_version", "3.6.1")
        .put("mnn_backend", "CPU").put("mnn_precision", "normal_default").put("mnn_threads", 4)
        .put("active_tracker_access", false)
        .put("calibration_store_access", false).put("camera_frames_retained", false)
        .put("gaze_output_emitted", false).put("accuracy_measured", false)
        .put("external_numerical_parity", "not_yet_compared")
        .put("vendor", JSONObject(context.assets.open("vendor-manifest.json").bufferedReader().use { it.readText() }))

    fun tensor(name: String, values: FloatArray): JSONObject {
        check(mode == "synthetic") { "Only synthetic tensors may be retained" }
        require(name.matches(Regex("[a-z_]+")))
        val bytes = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            values.forEach { putFloat(it) }
        }.array()
        val file = File(directory, "$name.f32")
        file.writeBytes(bytes)
        return JSONObject().put("file", file.name).put("count", values.size).put("sha256", MnnEstimator.sha256(bytes))
    }
    fun save(): File = File(directory, "report.json").also { it.writeText(root.toString(2)) }

    companion object {
        fun stats(values: List<Double>): JSONObject {
            val sorted = values.filter { it.isFinite() }.sorted()
            if (sorted.isEmpty()) return JSONObject().put("n", 0)
            fun q(p: Double) = sorted[(kotlin.math.ceil(p * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)]
            return JSONObject().put("n", sorted.size).put("mean", sorted.average())
                .put("median", q(.5)).put("p95", q(.95)).put("max", sorted.last())
        }
        fun array(values: FloatArray) = JSONArray(values.map { it.toDouble() })
    }
}
