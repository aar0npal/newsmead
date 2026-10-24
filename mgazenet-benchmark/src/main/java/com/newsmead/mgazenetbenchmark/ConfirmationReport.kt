package com.newsmead.mgazenetbenchmark

import java.io.File

/** Numeric-only screen-confirmation evidence. Never encodes images, features, or a personal model. */
object ConfirmationReport {
    fun payload(session: ConfirmationSession, id: String, device: String,
                pipeline: Map<String,Any?>, evidenceKind: String = "recorded"): Map<String,Any?> {
        require(evidenceKind in listOf("recorded","synthetic_contract"))
        val fitTargets = session.fitPoints.mapIndexed { index, point -> linkedMapOf<String,Any?>(
            "id" to point.id,"practice" to point.practice,
            "fraction" to if (point.practice) listOf(.5,.5) else listOf(
                CalibrationAuditSession.GRID_FRACTIONS[(index-1)%4],
                CalibrationAuditSession.GRID_FRACTIONS[(index-1)/4]),
            "point_px" to point.point.values()) }
        val screenTargets = session.screenBlocks.mapIndexed { index, block ->
            val fraction = CalibrationAuditSession.VALIDATION_FRACTIONS[index]
            linkedMapOf<String,Any?>("id" to block.id,"role" to "candidate_screen",
                "fraction" to listOf(fraction.first,fraction.second),"point_px" to block.point.values())
        }
        val confirmationTargets = session.confirmationBlocks.map { block -> linkedMapOf<String,Any?>(
            "id" to block.id,"location_id" to block.locationId,"grid_index" to block.gridIndex,
            "sweep" to block.sweep,"sweep_direction" to block.sweepDirection,
            "order_in_sweep" to block.orderInSweep,"point_px" to block.point.values(),
            "target_line_index" to block.line) }
        val manifest = linkedMapOf<String,Any?>(
            "protocol" to ConfirmationSession.VERSION,"pipeline" to pipeline,"device" to device,
            "screen_px" to listOf(session.layout.screenWidth,session.layout.screenHeight),
            "viewport_screen_px" to session.layout.viewport.values(),"line_height_px" to session.layout.lineHeight,
            "coordinate_space" to "physical_screen_px","label_space" to "physical_screen_fractions",
            "fit_geometry_source" to "NewsMead 16-point calibration structure",
            "fit_grid_fractions" to CalibrationAuditSession.GRID_FRACTIONS,
            "fit_grid_order" to "row_major_top_to_bottom_left_to_right",
            "fit_settle_ms" to ConfirmationSession.FIT_SETTLE_MS,
            "fit_samples_per_target" to ConfirmationSession.FIT_SAMPLES,
            "fit_wait_ms" to ConfirmationSession.FIT_WAIT_MS,
            "fit_timeout_ms" to ConfirmationSession.FIT_TIMEOUT_MS,
            "screen_settle_ms" to ConfirmationSession.SETTLE_MS,
            "screen_measure_ms" to ConfirmationSession.MEASURE_MS,
            "screen_drain_ms" to ConfirmationSession.DRAIN_MS,
            "confirmation_settle_ms" to ConfirmationSession.SETTLE_MS,
            "confirmation_measure_ms" to ConfirmationSession.MEASURE_MS,
            "confirmation_drain_ms" to ConfirmationSession.DRAIN_MS,
            "eye_area_rule" to "both_pixel_polygon_areas_strictly_above_10",
            "filter" to "none","correction" to "none",
            "audit_method" to CalibrationAuditEngine.METHOD,
            "audit_training_groups_per_fold" to 15,
            "audit_rows_per_group" to ConfirmationSession.FIT_SAMPLES,
            "training_rows" to session.trainingRows,"training_digest" to session.fitDigest,
            "svr" to "OpenCV EPS-SVR/RBF C=1 gamma=.005 P=.001 MAX_ITER=10000 epsilon_argument=.0001",
            "screen_method" to ConfirmationSession.SCREEN_METHOD,
            "screen_minimum_coordinates_per_target" to ConfirmationSession.MIN_COORDINATES_PER_TARGET,
            "screen_maximum_mean_target_median_absolute_vertical_lines" to
                ConfirmationSession.MAX_MEAN_TARGET_MEDIAN_LINES,
            "screen_maximum_worst_target_median_absolute_vertical_lines" to
                ConfirmationSession.MAX_WORST_TARGET_MEDIAN_LINES,
            "screen_result_visibility" to "sealed_and_hidden_until_terminal_record",
            "continue_after_screen_result" to true,
            "confirmation_locations" to AccuracySession.TEST_LOCATIONS,
            "validation_order" to session.validationOrder.code,
            "sweep_directions" to session.validationOrder.directions.map { it.code },
            "fit_targets" to fitTargets,"screen_targets" to screenTargets,
            "confirmation_targets" to confirmationTargets)
        val pipelineJson = AccuracyReport.encode(pipeline)
        val manifestJson = AccuracyReport.encode(manifest)
        val manifestHash = AccuracyReport.sha256(manifestJson.toByteArray(Charsets.UTF_8))
        val screen = session.screenResult
        return linkedMapOf(
            "schema" to if (session.phase == ConfirmationSession.Phase.COMPLETE)
                "mgazenet_direct_validation_confirmation_v1" else
                "mgazenet_direct_validation_confirmation_partial_v1",
            "outcome" to session.phase.name.lowercase(),"failure" to session.failure,
            "evidence_kind" to evidenceKind,"protocol_id" to ConfirmationSession.VERSION,
            "session_id" to id,"run_label" to session.runLabel,"device_id" to device,
            "pipeline_id" to AccuracyReport.sha256(pipelineJson.toByteArray(Charsets.UTF_8)),
            "pipeline_json" to pipelineJson,"calibration_id" to "$id:$manifestHash",
            "calibration_manifest_sha256" to manifestHash,"calibration_manifest" to manifest,
            "calibration_manifest_json" to manifestJson,"coordinate_space" to "physical_screen_px",
            "clock" to "shared_monotonic_ms","audit_method" to CalibrationAuditEngine.METHOD,
            "validation_order" to session.validationOrder.code,
            "fit_block_ids" to session.fitPoints.filterNot { it.practice }.map { it.id },
            "fit_points" to session.fitPoints.map { point -> linkedMapOf<String,Any?>(
                "id" to point.id,"practice" to point.practice,"shown_ms" to point.shownMs,
                "accepted" to point.accepted,"rejected" to point.rejected,
                "first_accepted_capture_ms" to point.firstAcceptedCaptureMs,
                "completed_output_ms" to point.completedOutputMs,
                "collection_elapsed_ms_from_window_open" to point.completedOutputMs?.let { completed ->
                    point.shownMs?.let { completed-(it+ConfirmationSession.FIT_SETTLE_MS) } },
                "output_age_ms" to distribution(point.acceptedOutputAgesMs),
                "within_target_feature_dispersion_rms" to point.featureDispersionRms) },
            "loo_folds" to (session.auditResult?.folds?.map { fold -> linkedMapOf<String,Any?>(
                "held_out_id" to fold.heldOutId,
                "target_px" to session.layout.pixels(fold.normalizedTarget).values(),
                "samples" to fold.predictions.map { prediction -> linkedMapOf<String,Any?>(
                    "capture_ms" to prediction.captureMs,"output_ms" to prediction.sourceOutputMs,
                    "point_px" to session.layout.pixels(prediction.normalizedPoint).values()) }) }
                ?: emptyList<Any>()),
            "screen_blocks" to session.screenBlocks.map { block -> linkedMapOf<String,Any?>(
                "id" to block.id,"role" to "candidate_screen","shown_ms" to block.shownMs,
                "start_ms" to block.startMs,"end_ms" to block.endMs,"target_px" to block.point.values(),
                "line_height_px" to session.layout.lineHeight,
                "samples" to block.samples.map { sample -> linkedMapOf<String,Any?>(
                    "capture_ms" to sample.captureMs,"output_ms" to sample.outputMs,
                    "point_px" to sample.point?.values(),"reason" to sample.reason) }) },
            "sealed_screen_result" to screen?.let { result -> linkedMapOf<String,Any?>(
                "method" to result.method,"sealed_ms" to result.sealedMs,
                "targets" to result.targets.map { target -> linkedMapOf<String,Any?>(
                    "id" to target.id,"coordinate_samples" to target.coordinateSamples,
                    "median_absolute_vertical_lines" to target.medianAbsoluteVerticalLines,
                    "p95_absolute_vertical_lines" to target.p95AbsoluteVerticalLines,
                    "max_absolute_vertical_lines" to target.maxAbsoluteVerticalLines) },
                "minimum_coordinate_samples" to result.minimumCoordinateSamples,
                "mean_target_median_absolute_vertical_lines" to
                    result.meanTargetMedianAbsoluteVerticalLines,
                "mean_target_p95_absolute_vertical_lines" to result.meanTargetP95AbsoluteVerticalLines,
                "worst_target_median_absolute_vertical_lines" to
                    result.worstTargetMedianAbsoluteVerticalLines,
                "checks" to linkedMapOf(
                    "all_five_targets_contribute" to result.allFiveTargetsContribute,
                    "minimum_coordinates" to result.minimumCoordinatesPass,
                    "aggregate_vertical" to result.aggregateVerticalPass,
                    "regional_vertical" to result.regionalVerticalPass),
                "screen_candidate_pass" to result.candidatePass) },
            "screen_candidate_pass" to screen?.candidatePass,
            "confirmation_blocks" to session.confirmationBlocks.map { block -> linkedMapOf<String,Any?>(
                "id" to block.id,"region" to block.region,"role" to "independent_confirmation",
                "task" to "stationary_point","location_id" to block.locationId,
                "grid_index" to block.gridIndex,"sweep" to block.sweep,
                "sweep_direction" to block.sweepDirection,"order_in_sweep" to block.orderInSweep,
                "shown_ms" to block.shownMs,"start_ms" to block.startMs,"end_ms" to block.endMs,
                "target_px" to block.point.values(),"target_line_index" to block.line,
                "line_height_px" to session.layout.lineHeight,
                "viewport_screen_px" to session.layout.viewport.values(),
                "lines" to session.layout.lines.mapIndexed { index, rect ->
                    mapOf("index" to index,"rect_px" to rect.values()) },
                "samples" to block.samples.map { sample -> linkedMapOf<String,Any?>(
                    "capture_ms" to sample.captureMs,"output_ms" to sample.outputMs,
                    "point_px" to sample.point?.values(),"reason" to sample.reason) }) },
            "started_ms" to session.startedMs,"finished_ms" to session.finishedMs,
            "discarded_outside_sampling_windows" to session.discardedFrames,
            "active_tracker_access" to false,"calibration_store_access" to false,
            "camera_frames_retained" to false,"features_retained" to false,
            "personal_model_retained" to false,"model_correction_applied" to false,
            "screen_result_hidden_until_terminal" to true,
            "confirmation_accuracy_pass" to null,"accuracy_gate_pass" to null,
            "promotion_decision" to "not_evaluated",
            "scope" to "vertical calibration screening and independent stationary confirmation only")
    }

    fun writeNew(directory: File, payload: Map<String,Any?>) = AccuracyReport.writeNew(directory,payload)

    private fun AccuracySession.Point.values() = listOf(x,y)

    private fun distribution(values: List<Double>): Map<String,Any?> {
        val sorted = values.sorted()
        fun percentile(fraction: Double): Double? = if (sorted.isEmpty()) null else {
            val position = (sorted.size-1)*fraction
            val lower = position.toInt()
            val upper = kotlin.math.ceil(position).toInt()
            if (lower == upper) sorted[lower]
            else sorted[lower]+(sorted[upper]-sorted[lower])*(position-lower)
        }
        return linkedMapOf("count" to sorted.size,"median" to percentile(.5),
            "p95" to percentile(.95),"max" to sorted.lastOrNull())
    }
}
