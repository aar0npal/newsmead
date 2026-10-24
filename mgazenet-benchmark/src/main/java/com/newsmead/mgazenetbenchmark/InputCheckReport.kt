package com.newsmead.mgazenetbenchmark

/** Aggregate numeric input evidence. It contains no images, landmarks, features or gaze coordinates. */
object InputCheckReport {
    fun payload(session: InputCheckSession, id: String, device: String,
                pipeline: Map<String,Any?>, cameraCounters: Map<String,Any?>): Map<String,Any?> {
        val pipelineJson = AccuracyReport.encode(pipeline)
        val closeFailed = (cameraCounters["resource_close_errors"] as? Iterable<*>)?.any() == true
        val complete = session.phase == InputCheckSession.Phase.COMPLETE && !closeFailed
        return linkedMapOf(
            "schema" to if (complete)
                "mgazenet_input_check_v1" else "mgazenet_input_check_partial_v1",
            "outcome" to if (closeFailed) "failed" else session.phase.name.lowercase(),
            "failure" to (session.failure ?: if (closeFailed) "resource_close_failed" else null),
            "protocol_id" to InputCheckSession.VERSION,
            "session_id" to id,
            "run_label" to session.runLabel,
            "device_id" to device,
            "pipeline_id" to AccuracyReport.sha256(pipelineJson.toByteArray(Charsets.UTF_8)),
            "pipeline" to pipeline,
            "pipeline_json" to pipelineJson,
            "requested_ms" to session.requestedMs,
            "camera_initialization_deadline_ms" to session.requestedMs + InputCheckSession.INITIALIZATION_TIMEOUT_MS,
            "camera_ready_ms" to session.readyMs,
            "planned_start_ms" to session.plannedStartMs,
            "planned_end_ms" to session.plannedEndMs,
            "planned_duration_ms" to InputCheckSession.OBSERVATION_MS,
            "observed_duration_ms" to session.observedDurationMs(),
            "unobserved_planned_duration_ms" to InputCheckSession.OBSERVATION_MS-session.observedDurationMs(),
            "finished_ms" to session.finishedMs,
            "emitted_results" to session.emissions.size,
            "result_counts" to session.categories,
            "eye_area_summary" to session.areaSummaries(),
            "crop_size_summary" to session.cropSummaries(),
            "emitted_result_age_ms" to session.ageSummary(),
            "callback_timing" to session.timingSummary(),
            "ignored_before_window" to session.ignoredBeforeWindow,
            "ignored_after_window" to session.ignoredAfterWindow,
            "timestamp_consistency" to mapOf(
                "declared_source_clock" to pipeline["camera_clock"],
                "emitted_frame_checks_passed" to session.clockConsistent,
                "checked_results" to session.emissions.size + session.ignoredBeforeWindow + session.ignoredAfterWindow
            ),
            "camera_counters" to cameraCounters,
            "area_rule" to "both_pixel_polygon_areas_strictly_above_10",
            "human_blinks_verified" to false,
            "target_compliance_verified" to false,
            "svr_fit" to false,
            "calibration_grid_presented" to false,
            "gaze_coordinates_emitted" to false,
            "active_tracker_access" to false,
            "calibration_store_access" to false,
            "camera_frames_retained" to false,
            "crop_images_retained" to false,
            "landmarks_retained" to false,
            "features_retained" to false,
            "personal_model_retained" to false,
            "gaze_accuracy_established" to false
        )
    }
}
