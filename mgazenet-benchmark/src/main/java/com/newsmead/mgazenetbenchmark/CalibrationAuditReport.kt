package com.newsmead.mgazenetbenchmark

import java.io.File

/** Numeric-only calibration diagnostics. No images, landmarks, features or model are encoded. */
object CalibrationAuditReport {
    fun payload(session: CalibrationAuditSession, id: String, device: String,
                pipeline: Map<String,Any?>, evidenceKind: String = "recorded"): Map<String,Any?> {
        require(evidenceKind in listOf("recorded","synthetic_contract"))
        val fitTargets = session.fitPoints.mapIndexed { index, point -> linkedMapOf<String,Any?>(
            "id" to point.id,"practice" to point.practice,
            "fraction" to if (point.practice) listOf(.5,.5) else listOf(
                CalibrationAuditSession.GRID_FRACTIONS[(index-1)%4],
                CalibrationAuditSession.GRID_FRACTIONS[(index-1)/4]),
            "point_px" to point.point.values()) }
        val verificationTargets = session.verificationBlocks.mapIndexed { index, block -> linkedMapOf<String,Any?>(
            "id" to block.id,"role" to block.role,
            "fraction" to if (index == 0) fitTargets.first { it["id"] == session.driftFitId }["fraction"]
                else CalibrationAuditSession.VALIDATION_FRACTIONS[index-1].let { listOf(it.first,it.second) },
            "point_px" to block.point.values()) }
        val manifest = linkedMapOf<String,Any?>(
            "protocol" to CalibrationAuditSession.VERSION,"pipeline" to pipeline,"device" to device,
            "screen_px" to listOf(session.layout.screenWidth,session.layout.screenHeight),
            "viewport_screen_px" to session.layout.viewport.values(),"line_height_px" to session.layout.lineHeight,
            "coordinate_space" to "physical_screen_px","label_space" to "physical_screen_fractions",
            "fit_geometry_source" to "NewsMead 16-point calibration structure",
            "fit_grid_fractions" to CalibrationAuditSession.GRID_FRACTIONS,
            "fit_grid_order" to "row_major_top_to_bottom_left_to_right",
            "fit_settle_ms" to CalibrationAuditSession.FIT_SETTLE_MS,
            "fit_samples_per_target" to CalibrationAuditSession.FIT_SAMPLES,
            "fit_wait_ms" to CalibrationAuditSession.FIT_WAIT_MS,
            "fit_timeout_ms" to CalibrationAuditSession.FIT_TIMEOUT_MS,
            "verification_settle_ms" to CalibrationAuditSession.VERIFY_SETTLE_MS,
            "verification_measure_ms" to CalibrationAuditSession.VERIFY_MEASURE_MS,
            "verification_drain_ms" to CalibrationAuditSession.VERIFY_DRAIN_MS,
            "eye_area_rule" to "both_pixel_polygon_areas_strictly_above_10",
            "filter" to "none","correction" to "none",
            "audit_method" to CalibrationAuditEngine.METHOD,
            "audit_training_groups_per_fold" to 15,"audit_rows_per_group" to CalibrationAuditSession.FIT_SAMPLES,
            "training_rows" to session.trainingRows,"training_digest" to session.fitDigest,
            "svr" to "OpenCV EPS-SVR/RBF C=1 gamma=.005 P=.001 MAX_ITER=10000 epsilon_argument=.0001",
            "fit_targets" to fitTargets,"drift_repeat_fit_id" to session.driftFitId,
            "verification_targets" to verificationTargets)
        val pipelineJson = AccuracyReport.encode(pipeline)
        val manifestJson = AccuracyReport.encode(manifest)
        val manifestHash = AccuracyReport.sha256(manifestJson.toByteArray(Charsets.UTF_8))
        return linkedMapOf(
            "schema" to if (session.phase == CalibrationAuditSession.Phase.COMPLETE)
                "mgazenet_calibration_audit_v3" else "mgazenet_calibration_audit_partial_v3",
            "outcome" to session.phase.name.lowercase(),"failure" to session.failure,
            "evidence_kind" to evidenceKind,"protocol_id" to CalibrationAuditSession.VERSION,
            "session_id" to id,"run_label" to session.runLabel,"device_id" to device,
            "pipeline_id" to AccuracyReport.sha256(pipelineJson.toByteArray(Charsets.UTF_8)),
            "pipeline_json" to pipelineJson,"calibration_id" to "$id:$manifestHash",
            "calibration_manifest_sha256" to manifestHash,"calibration_manifest" to manifest,
            "calibration_manifest_json" to manifestJson,"coordinate_space" to "physical_screen_px",
            "clock" to "shared_monotonic_ms","audit_method" to CalibrationAuditEngine.METHOD,
            "fit_block_ids" to session.fitPoints.filterNot { it.practice }.map { it.id },
            "fit_points" to session.fitPoints.map { point -> linkedMapOf<String,Any?>(
                "id" to point.id,"practice" to point.practice,"shown_ms" to point.shownMs,
                "accepted" to point.accepted,"rejected" to point.rejected,
                "first_accepted_capture_ms" to point.firstAcceptedCaptureMs,
                "completed_output_ms" to point.completedOutputMs,
                "collection_elapsed_ms_from_window_open" to point.completedOutputMs?.let { completed ->
                    point.shownMs?.let { completed-(it+CalibrationAuditSession.FIT_SETTLE_MS) } },
                "output_age_ms" to distribution(point.acceptedOutputAgesMs),
                "within_target_feature_dispersion_rms" to point.featureDispersionRms) },
            "loo_folds" to (session.auditResult?.folds?.map { fold -> linkedMapOf<String,Any?>(
                "held_out_id" to fold.heldOutId,
                "target_px" to session.layout.pixels(fold.normalizedTarget).values(),
                "samples" to fold.predictions.map { prediction -> linkedMapOf<String,Any?>(
                    "capture_ms" to prediction.captureMs,"output_ms" to prediction.sourceOutputMs,
                    "point_px" to session.layout.pixels(prediction.normalizedPoint).values()) }) } ?: emptyList<Any>()),
            "verification_blocks" to session.verificationBlocks.map { block -> linkedMapOf<String,Any?>(
                "id" to block.id,"role" to block.role,"shown_ms" to block.shownMs,
                "start_ms" to block.startMs,"end_ms" to block.endMs,"target_px" to block.point.values(),
                "samples" to block.samples.map { sample -> linkedMapOf<String,Any?>(
                    "capture_ms" to sample.captureMs,"output_ms" to sample.outputMs,
                    "point_px" to sample.point?.values(),"reason" to sample.reason) }) },
            "started_ms" to session.startedMs,"finished_ms" to session.finishedMs,
            "discarded_outside_sampling_windows" to session.discardedFrames,
            "active_tracker_access" to false,"calibration_store_access" to false,
            "camera_frames_retained" to false,"features_retained" to false,
            "personal_model_retained" to false,"model_correction_applied" to false,
            "accuracy_gate_pass" to null,"promotion_decision" to "not_evaluated",
            "scope" to "calibration observability and instructed stationary verification only")
    }

    fun writeNew(directory: File, payload: Map<String,Any?>) = AccuracyReport.writeNew(directory,payload)

    private fun AccuracySession.Point.values() = listOf(x,y)

    private fun distribution(values: List<Double>): Map<String,Any?> {
        val sorted = values.sorted()
        fun percentile(fraction: Double): Double? = if (sorted.isEmpty()) null else {
            val position = (sorted.size-1)*fraction
            val lower = position.toInt()
            val upper = kotlin.math.ceil(position).toInt()
            if (lower == upper) sorted[lower] else sorted[lower]+(sorted[upper]-sorted[lower])*(position-lower)
        }
        return linkedMapOf("count" to sorted.size,"median" to percentile(.5),
            "p95" to percentile(.95),"max" to sorted.lastOrNull())
    }
}
