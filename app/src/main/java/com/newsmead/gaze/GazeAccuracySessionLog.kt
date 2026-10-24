package com.newsmead.gaze

import android.content.Context
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent, numeric-only record of one nine-point diagnostic run. Camera
 * frames are never stored. The log keeps each observable pipeline stage so an
 * error can be localized without changing the gaze estimate delivered to the UI.
 */
class GazeAccuracySessionLog(
    context: Context,
    runLabel: String,
    telemetryMode: DetailedTelemetryMode,
    screenWidthPx: Int,
    screenHeightPx: Int,
    targetViewFrame: GazeCoordinateFrame,
    calibrationFingerprint: String?,
    densityDpi: Int,
    lineHeightPx: Float,
    calibrationPointCount: Int,
    activeCorrection: DriftCorrection?,
    postureProfile: PostureProfile?,
    rawFeatureMode: String,
) {

    private val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
    private val file = File(context.filesDir, "gaze_accuracy_session_$stamp.json")
    private val root = JSONObject()
    private val points = JSONArray()
    private var finished = false
    private val detailedTelemetryEnabled = telemetryMode.enabled

    init {
        root.put("session_id", "gaze_accuracy_session_$stamp")
        root.put("run_label", runLabel)
        root.put("coordinate_space", GazeCoordinateContract.SPACE)
        root.put("coordinate_schema_version", 1)
        root.put("target_view_frame", targetViewFrame.toJson())
        root.put("calibration_sha256", calibrationFingerprint ?: JSONObject.NULL)
        root.put("telemetry_mode", telemetryMode.logLabel)
        root.put("detailed_telemetry_enabled", detailedTelemetryEnabled)
        root.put("timestamp_start", isoTimestamp())
        root.put("device_model", Build.MODEL)
        root.put("device_manufacturer", Build.MANUFACTURER)
        root.put("android_version", Build.VERSION.RELEASE)
        root.put("screen_px", JSONArray().put(screenWidthPx).put(screenHeightPx))
        root.put("density_dpi", densityDpi)
        root.putNum("line_height_px", lineHeightPx)
        root.put("calibration_point_count", calibrationPointCount)
        root.put("active_mapping_mode", MAPPING_MODE)
        root.put("raw_feature_mode", rawFeatureMode)
        root.put("drift_correction_active", activeCorrection != null)
        root.put("drift_correction", activeCorrection?.toJson() ?: JSONObject.NULL)
        root.put("posture_shadow_mode", true)
        root.put("posture_affects_gaze_output", false)
        root.put("hybrid_eye_shadow_enabled", com.newsmead.data.StudyConfig.GAZE_HYBRID_SHADOW_ENABLED)
        root.put("hybrid_eye_shadow_affects_gaze_output", false)
        root.put("hybrid_eye_shadow_reference", "face_landmarker_478_gpu")
        root.put(
            "hybrid_eye_shadow_candidate",
            "blazeface_short_range_plus_iris_64_cpu_detector_anchored_reextract_v1",
        )
        root.put("hybrid_eye_shadow_crop_mode", "detector_anchored_one_step_center_size")
        root.put("posture_profile", postureProfile?.toJson() ?: JSONObject.NULL)
        root.put("camera_frames_retained", false)
        root.put(
            "processing_path",
            "$rawFeatureMode -> eye_average -> median_7 -> one_euro -> quadratic_mapper -> optional_affine_correction",
        )
        root.put("points", points)
        flush()
        Log.i(TAG, "Started gaze accuracy diagnostic log ${file.absolutePath}")
    }

    fun logPoint(
        pointIndex: Int,
        attempt: Int,
        targetX: Float,
        targetY: Float,
        targetViewFrame: GazeCoordinateFrame,
        localTargetX: Float,
        localTargetY: Float,
        result: FixationWindowFilter.Result,
        fpsSummary: FpsSummary?,
        pipelineSamples: List<LocalCalibratedGazeProvider.PipelineDiagnostics>,
        sourceEvents: List<LocalRawGazeSource.Diagnostics>,
        hybridEyeSamples: List<HybridEyeShadowSample>,
        postureSummary: PostureSummary,
    ) {
        val accepted = result.status == FixationWindowFilter.Status.ACCEPTED
        val dx = if (accepted) result.medianX - targetX else Float.NaN
        val dy = if (accepted) result.medianY - targetY else Float.NaN
        val error = if (accepted) kotlin.math.hypot(dx, dy) else Float.NaN
        points.put(
            JSONObject().apply {
                put("point_index", pointIndex)
                put("attempt", attempt)
                put("status", result.status.name)
                putNum("target_x", targetX)
                putNum("target_y", targetY)
                put("target_view_frame", targetViewFrame.toJson())
                putNum("target_x_local_px", localTargetX)
                putNum("target_y_local_px", localTargetY)
                putNum("estimate_x", result.medianX)
                putNum("estimate_y", result.medianY)
                putNum("dx_px", dx)
                putNum("dy_px", dy)
                putNum("error_px", error)
                putNum("output_dispersion_x", result.dispersionX)
                putNum("output_dispersion_y", result.dispersionY)
                put("collector_raw_count", result.rawCount)
                put("collector_retained_count", result.retainedCount)
                put("fps_summary", fpsSummary?.toJson() ?: JSONObject.NULL)
                put("posture_shadow_summary", postureSummary.toJson())
                put("hybrid_eye_shadow_sample_count", hybridEyeSamples.size)
                put("hybrid_eye_shadow_samples", JSONArray().apply {
                    hybridEyeSamples.forEach { put(it.toJson()) }
                })
                if (detailedTelemetryEnabled) {
                    put("pipeline_samples", JSONArray().apply {
                        pipelineSamples.forEach { put(it.toJson()) }
                    })
                    put("source_events", JSONArray().apply {
                        sourceEvents.forEach { put(it.toJson()) }
                    })
                }
            },
        )
        flush()
    }

    fun logSummary(
        summary: ReadingSpatialMetrics.Summary,
        lineHeightPx: Float,
        estimatedCorrectedLooPx: Float?,
    ) {
        root.put(
            "summary",
            JSONObject().apply {
                put("accepted_point_count", summary.points.size)
                putNum("median_px", summary.medianErrorPx)
                putNum("p95_px", summary.p95ErrorPx)
                putNum("max_px", summary.maxErrorPx)
                putNum("vertical_median_px", summary.medianVerticalPx)
                putNum("vertical_p95_px", summary.p95VerticalPx)
                putNum("vertical_max_px", summary.maxVerticalPx)
                putNum("vertical_median_lines", summary.medianVerticalPx / lineHeightPx)
                putNum("vertical_p95_lines", summary.p95VerticalPx / lineHeightPx)
                putNum("vertical_max_lines", summary.maxVerticalPx / lineHeightPx)
                put("within_0_5_line", summary.withinHalfLine)
                put("within_1_0_line", summary.withinOneLine)
                put("within_1_2_line_provisional", summary.withinReference)
                put("meets_provisional_1_2_line_reference", summary.meetsProvisionalReference)
                put("worst_vertical_point_index", summary.worstVertical.id)
                put("worst_euclidean_point_index", summary.worstEuclidean.id)
                put("point_metrics", JSONArray().apply {
                    summary.points.forEach { point ->
                        put(JSONObject().apply {
                            put("point_index", point.id)
                            put("region", point.label)
                            putNum("dx_px", point.dxPx)
                            putNum("dy_px", point.dyPx)
                            putNum("error_px", point.errorPx)
                            putNum("vertical_error_lines", point.verticalLines)
                        })
                    }
                })
                putNum("estimated_corrected_loo_px", estimatedCorrectedLooPx ?: Float.NaN)
            },
        )
        flush()
    }

    fun finish(
        outcome: String,
        fpsSummary: FpsSummary? = null,
        postureSummary: PostureSummary? = null,
    ) {
        if (finished) return
        finished = true
        root.put("outcome", outcome)
        root.put("fps_summary", fpsSummary?.toJson() ?: JSONObject.NULL)
        root.put("posture_shadow_summary", postureSummary?.toJson() ?: JSONObject.NULL)
        root.put("timestamp_end", isoTimestamp())
        flush()
        Log.i(TAG, "Finished gaze accuracy diagnostic log ${file.absolutePath}: $outcome")
    }

    fun fileName(): String = file.name

    private fun LocalCalibratedGazeProvider.PipelineDiagnostics.toJson(): JSONObject =
        JSONObject().apply {
            put("source_sequence", source?.sequence ?: JSONObject.NULL)
            put("output_timestamp_ms", outputTimestampMs)
            putNum("raw_x", rawX)
            putNum("raw_y", rawY)
            putNum("median_x", medianX)
            putNum("median_y", medianY)
            putNum("filtered_x", filteredX)
            putNum("filtered_y", filteredY)
            putNum("mapped_x", mappedX)
            putNum("mapped_y", mappedY)
            putNum("output_x", outputX)
            putNum("output_y", outputY)
            put("correction_active", correctionActive)
            put("posture_status", postureAssessment.status.name)
            put("posture_out_of_range_axes", JSONArray(postureAssessment.outOfRangeAxes))
            putNum("posture_max_normalized_excess", postureAssessment.maxNormalizedExcess)
        }

    private fun LocalRawGazeSource.Diagnostics.toJson(): JSONObject =
        JSONObject().apply {
            put("sequence", sequence)
            put("outcome", outcome.name)
            put("capture_timestamp_ns", captureTimestampNs)
            put("submitted_elapsed_ns", submittedElapsedNs)
            put("result_elapsed_ns", resultElapsedNs)
            putNum(
                "submit_to_result_ms",
                if (submittedElapsedNs > 0L && resultElapsedNs >= submittedElapsedNs) {
                    (resultElapsedNs - submittedElapsedNs) / 1_000_000f
                } else {
                    Float.NaN
                },
            )
            put("frame_width", frameWidth)
            put("frame_height", frameHeight)
            put("rotation_degrees", rotationDegrees)
            putNum("eye_1_x", eye1X)
            putNum("eye_1_y", eye1Y)
            putNum("eye_2_x", eye2X)
            putNum("eye_2_y", eye2Y)
            putNum("gaze_x", gazeX)
            putNum("gaze_y", gazeY)
            putNum("eye_1_openness", eye1Openness)
            putNum("eye_2_openness", eye2Openness)
            putNum("face_center_x", faceCenterX)
            putNum("face_center_y", faceCenterY)
            putNum("face_scale", faceScale)
            putNum("head_roll_deg", headRollDeg)
            put("busy_dropped_frames_cumulative", busyDroppedFrames)
        }

    private fun HybridEyeShadowSample.toJson(): JSONObject = JSONObject().apply {
        put("capture_timestamp_ns", captureTimestampNs)
        put("result_elapsed_ns", resultElapsedNs)
        putNum("reference_horizontal", referenceHorizontal)
        putNum("reference_vertical", referenceVertical)
        putNum("candidate_horizontal", candidateHorizontal)
        putNum("candidate_vertical", candidateVertical)
        putNum("right_eye_openness", rightEyeOpenness)
        putNum("left_eye_openness", leftEyeOpenness)
        put("face_detector_ran", faceDetectorRan)
        put("detector_reextract_applied", detectorReextractApplied)
        putNum("right_crop_center_shift_px", rightCropCenterShiftPx)
        putNum("left_crop_center_shift_px", leftCropCenterShiftPx)
        putNum("right_crop_side_ratio", rightCropSideRatio)
        putNum("left_crop_side_ratio", leftCropSideRatio)
        putNum("total_ms", totalMs)
        putNum("detector_ms", detectorMs)
        putNum("eye_ms", eyeMs)
        putNum("reextract_ms", reextractMs)
    }

    private fun DriftCorrection.toJson(): JSONObject = JSONObject().apply {
        put("coeff_x", JSONArray().apply { coeffX.forEach { put(it) } })
        put("coeff_y", JSONArray().apply { coeffY.forEach { put(it) } })
    }

    private fun PostureProfile.toJson(): JSONObject = JSONObject().apply {
        put("calibration_point_count", calibrationPointCount)
        put("face_center_x", faceCenterX.toJson())
        put("face_center_y", faceCenterY.toJson())
        put("face_scale", faceScale.toJson())
        put("head_roll_deg", headRollDeg.toJson())
    }

    private fun PostureAxisRange.toJson(): JSONObject = JSONObject().apply {
        putNum("median", median)
        putNum("observed_min", minimum)
        putNum("observed_max", maximum)
        putNum("margin", margin)
        putNum("lower_bound", lowerBound)
        putNum("upper_bound", upperBound)
    }

    private fun PostureSummary.toJson(): JSONObject = JSONObject().apply {
        put("sample_count", sampleCount)
        put("assessed_count", assessedCount)
        put("in_range_count", inRangeCount)
        put("out_of_range_count", outOfRangeCount)
        put("unavailable_count", unavailableCount)
        putNum("out_of_range_fraction", outOfRangeFraction)
        putNum("max_normalized_excess", maxNormalizedExcess)
        put("out_of_range_axis_counts", JSONObject(outOfRangeAxisCounts))
    }

    private fun FpsSummary.toJson(): JSONObject = JSONObject().apply {
        put("sample_count", sampleCount)
        putNum("mean", mean)
        putNum("min", min)
        putNum("max", max)
    }

    private fun JSONObject.putNum(key: String, value: Float) {
        put(key, if (value.isFinite()) value.toDouble() else JSONObject.NULL)
    }

    private fun isoTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())

    private fun flush() {
        try {
            file.writeText(root.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write ${file.name}", e)
        }
    }

    companion object {
        private const val TAG = "GazeDiagnostics"
        private const val MAPPING_MODE = "quadratic_average"
    }
}
