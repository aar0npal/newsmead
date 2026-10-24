package com.newsmead.mgazenetbenchmark

import java.io.File
import java.security.MessageDigest

/** Numeric-only evidence. No images, landmarks, feature vectors or saved personal model. */
object AccuracyReport {
    fun payload(session: AccuracySession, id: String, device: String, pipeline: Map<String, Any?>,
                evidenceKind: String = "recorded"): Map<String, Any?> {
        require(evidenceKind in listOf("recorded","synthetic_contract"))
        val manifest = linkedMapOf<String, Any?>(
            "protocol" to AccuracySession.VERSION, "pipeline" to pipeline,"device" to device,
            "screen_px" to listOf(session.layout.screenWidth,session.layout.screenHeight),
            "viewport_screen_px" to session.layout.viewport.values(), "line_height_px" to session.layout.lineHeight,
            "coordinate_space" to "physical_screen_px", "label_space" to "physical_screen_fractions",
            "filter" to "none", "correction" to "none", "eye_area_rule" to "both_pixel_polygon_areas_strictly_above_10",
            "fit_settle_ms" to AccuracySession.FIT_SETTLE_MS, "fit_samples_per_target" to AccuracySession.FIT_SAMPLES,
            "fit_wait_ms" to AccuracySession.FIT_WAIT_MS, "fit_timeout_ms" to AccuracySession.FIT_TIMEOUT_MS,
            "test_settle_ms" to AccuracySession.TEST_SETTLE_MS, "test_measure_ms" to AccuracySession.TEST_MEASURE_MS,
            "test_drain_ms" to AccuracySession.TEST_DRAIN_MS,
            "validation_locations" to AccuracySession.TEST_LOCATIONS,
            "validation_order" to session.validationOrder.code,
            "sweep_directions" to session.validationOrder.directions.map { it.code },
            "analysis_age_limits_ms" to AccuracySession.ANALYSIS_AGE_LIMITS_MS,
            "training_rows" to session.trainingRows, "training_digest" to session.fitDigest,
            "svr" to "OpenCV EPS-SVR/RBF C=1 gamma=.005 P=.001 MAX_ITER=10000 epsilon_argument=.0001",
            "targets" to session.fitPoints.map { mapOf("id" to it.id,"practice" to it.practice,
                "point_px" to listOf(it.point.x,it.point.y)) })
        val manifestHash = sha256(encode(manifest).toByteArray(Charsets.UTF_8))
        return linkedMapOf(
            "schema" to if (session.phase == AccuracySession.Phase.COMPLETE) "mgazenet_accuracy_v2" else "mgazenet_accuracy_partial_v2",
            "outcome" to session.phase.name.lowercase(), "failure" to session.failure,
            "evidence_kind" to evidenceKind, "protocol_id" to AccuracySession.VERSION,
            "session_id" to id, "run_label" to session.runLabel, "device_id" to device,
            "pipeline_id" to sha256(encode(pipeline).toByteArray(Charsets.UTF_8)),
            "pipeline_json" to encode(pipeline),
            "calibration_id" to "$id:$manifestHash", "calibration_manifest_sha256" to manifestHash,
            "calibration_manifest" to manifest, "calibration_manifest_json" to encode(manifest),
            "coordinate_space" to "physical_screen_px",
            "clock" to "shared_monotonic_ms", "max_output_age_ms" to null,
            "analysis_age_limits_ms" to AccuracySession.ANALYSIS_AGE_LIMITS_MS,
            "validation_order" to session.validationOrder.code,
            "fit_block_ids" to session.fitPoints.filterNot { it.practice }.map { it.id },
            "fit_points" to session.fitPoints.map { linkedMapOf("id" to it.id,"practice" to it.practice,
                "shown_ms" to it.shownMs,"accepted" to it.accepted,"rejected" to it.rejected) },
            "blocks" to session.blocks.map { block -> linkedMapOf(
                "id" to block.id,"region" to block.region,"role" to "held_out","task" to "stationary_point",
                "location_id" to block.locationId,"grid_index" to block.gridIndex,"sweep" to block.sweep,
                "sweep_direction" to block.sweepDirection,"order_in_sweep" to block.orderInSweep,
                "shown_ms" to block.shownMs,"start_ms" to block.startMs,"end_ms" to block.endMs,
                "target_px" to listOf(block.point.x,block.point.y),"target_line_index" to block.line,
                "line_height_px" to session.layout.lineHeight,"viewport_screen_px" to session.layout.viewport.values(),
                "lines" to session.layout.lines.mapIndexed { index, rect -> mapOf("index" to index,"rect_px" to rect.values()) },
                "samples" to block.samples.map { sample -> linkedMapOf("capture_ms" to sample.captureMs,
                    "output_ms" to sample.outputMs,"point_px" to sample.point?.let { listOf(it.x,it.y) },"reason" to sample.reason) }) },
            "started_ms" to session.startedMs,"finished_ms" to session.finishedMs,
            "discarded_outside_sampling_windows" to session.discardedFrames,
            "active_tracker_access" to false,"calibration_store_access" to false,
            "camera_frames_retained" to false,"features_retained" to false,"personal_model_retained" to false,
            "spatial_task" to "instructed stationary targets; compliance not independently verified",
            "reading_accuracy_established" to false,"accuracy_gate_pass" to null)
    }

    fun writeNew(directory: File, payload: Map<String, Any?>): File {
        check(directory.mkdirs()) { "Use a new isolated evidence directory" }
        val json = encode(payload)
        val pending = File(directory,"report.pending")
        pending.writeText(json,Charsets.UTF_8)
        val report = File(directory,"report.json")
        check(pending.renameTo(report)) { "Could not finalize numeric report" }
        File(directory,"report.sha256").writeText(sha256(json.toByteArray(Charsets.UTF_8))+"\n")
        return report
    }
    fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    /** Small deterministic JSON encoder, shared by JVM evidence tests and Android writer. */
    fun encode(value: Any?): String = when (value) {
        null -> "null"
        is String -> buildString {
            append('"'); value.forEach { c -> when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c.code < 32 -> append("\\u%04x".format(c.code))
                else -> append(c)
            } }; append('"')
        }
        is Boolean -> value.toString()
        is Number -> { require(value.toDouble().isFinite()); value.toString() }
        is Map<*,*> -> value.entries.joinToString(",","{","}") { require(it.key is String); encode(it.key)+":"+encode(it.value) }
        is Iterable<*> -> value.joinToString(",","[","]") { encode(it) }
        else -> error("Unsupported numeric evidence type: ${value.javaClass}")
    }
}
