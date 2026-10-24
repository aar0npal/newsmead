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
 * Incremental per-session calibration log (docs/calibration-design.md §6).
 * Flushed to disk after every mutation, so a crash or abort mid-session still
 * leaves analyzable data. Written alongside (never instead of) the
 * calibration_16point.csv that the mapper consumes.
 */
class CalibrationSessionLog(
    context: Context,
    runLabel: String,
    telemetryMode: DetailedTelemetryMode,
    screenWidthPx: Int,
    screenHeightPx: Int,
    densityDpi: Int,
    orderSeed: Long,
    orderMode: String,
    rawFeatureMode: String,
    targetViewFrame: GazeCoordinateFrame,
) {

    private val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    private val file = File(context.filesDir, "calibration_session_$stamp.json")
    private val root = JSONObject()
    private val points = JSONArray()
    private var validation = JSONArray()
    private val flags = JSONArray()
    private val detailedTelemetryEnabled = telemetryMode.enabled

    init {
        root.put("session_id", "calibration_session_$stamp")
        root.put("coordinate_space", GazeCoordinateContract.SPACE)
        root.put("coordinate_schema_version", 1)
        root.put("target_view_frame", targetViewFrame.toJson())
        root.put("run_label", runLabel)
        root.put("telemetry_mode", telemetryMode.logLabel)
        root.put("detailed_telemetry_enabled", detailedTelemetryEnabled)
        root.put(
            "timestamp_start",
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date()),
        )
        root.put("device_model", Build.MODEL)
        root.put("screen_px", JSONArray().put(screenWidthPx).put(screenHeightPx))
        root.put("density_dpi", densityDpi)
        root.put("order_seed", orderSeed)
        root.put("order_mode", orderMode)
        root.put("practice_target_count", 1)
        root.put("upper_left_practice_before_first_fit", false)
        root.put("requested_mapping_mode", MAPPING_MODE)
        root.put("raw_feature_mode", rawFeatureMode)
        root.put("camera_frames_retained", false)
        root.put(
            "processing_path",
            "$rawFeatureMode -> eye_average -> fixation_window_aggregate -> quadratic_mapper_for_validation",
        )
        root.put("points", points)
        root.put("validation", validation)
        root.put("session_quality_flags", flags)
        flush()
    }

    fun logPoint(
        pointId: Int,
        kind: String,
        attempt: Int,
        status: String,
        screenX: Float,
        screenY: Float,
        targetViewFrame: GazeCoordinateFrame,
        localTargetX: Float,
        localTargetY: Float,
        presentationIndex: Int,
        practice: Boolean,
        driftRepeat: Boolean,
        recaptured: Boolean,
        excluded: Boolean,
        rawSampleCount: Int,
        retainedSampleCount: Int,
        blinkDropped: Long,
        noFace: Long,
        featureX: Float,
        featureY: Float,
        eye1X: Float,
        eye1Y: Float,
        eye2X: Float,
        eye2Y: Float,
        faceCenterX: Float,
        faceCenterY: Float,
        faceScale: Float,
        headRollDeg: Float,
        dispersionX: Float,
        dispersionY: Float,
        fpsSummary: FpsSummary?,
        sourceEvents: List<LocalRawGazeSource.Diagnostics>,
    ) {
        points.put(
            JSONObject().apply {
                put("point_id", pointId)
                put("kind", kind)
                put("attempt", attempt)
                put("status", status)
                putNum("screen_x", screenX)
                putNum("screen_y", screenY)
                put("target_view_frame", targetViewFrame.toJson())
                putNum("target_x_local_px", localTargetX)
                putNum("target_y_local_px", localTargetY)
                put("presentation_index", presentationIndex)
                put("practice", practice)
                put("drift_repeat", driftRepeat)
                put("recaptured", recaptured)
                put("excluded", excluded)
                put("raw_sample_count", rawSampleCount)
                put("retained_sample_count", retainedSampleCount)
                put("blink_dropped", blinkDropped)
                put("no_face", noFace)
                put("aggregated_feature", featureArray(featureX, featureY))
                put("aggregated_per_eye_feature", featureArray(eye1X, eye1Y, eye2X, eye2Y))
                put(
                    "aggregated_posture_feature",
                    JSONObject().apply {
                        putNum("face_center_x", faceCenterX)
                        putNum("face_center_y", faceCenterY)
                        putNum("face_scale", faceScale)
                        putNum("head_roll_deg", headRollDeg)
                    },
                )
                put("dispersion_feature", featureArray(dispersionX, dispersionY))
                put("fps_summary", fpsSummary?.toJson() ?: JSONObject.NULL)
                if (detailedTelemetryEnabled) {
                    put("source_events", JSONArray().apply {
                        sourceEvents.forEach { put(it.toJson()) }
                    })
                }
            },
        )
        flush()
    }

    fun logSavedCalibration(fingerprint: String?) {
        root.put("saved_calibration_sha256", fingerprint ?: JSONObject.NULL)
        flush()
    }

    fun logDrift(
        first: FloatArray?,
        second: FloatArray?,
        dxPxPostFit: Float,
        dyPxPostFit: Float,
        deltaPxPostFit: Float,
        flagged: Boolean,
    ) {
        root.put(
            "drift_check",
            JSONObject().apply {
                put("first_pass_feature", first?.let { featureArray(it[0], it[1]) } ?: JSONObject.NULL)
                put("second_pass_feature", second?.let { featureArray(it[0], it[1]) } ?: JSONObject.NULL)
                putNum("dx_px_postfit", dxPxPostFit)
                putNum("dy_px_postfit", dyPxPostFit)
                putNum("delta_px_postfit", deltaPxPostFit)
                put("flagged_high_drift", flagged)
            },
        )
        flush()
    }

    fun logPostureProfile(profile: PostureProfile?) {
        root.put("posture_shadow_mode", true)
        root.put("posture_profile", profile?.toJson() ?: JSONObject.NULL)
        flush()
    }

    fun logFit(
        pointsUsed: Int,
        summary: ReadingSpatialMetrics.Summary,
        lineHeightPx: Float,
        worstPointIds: List<Int>,
    ) {
        root.put(
            "fit",
            JSONObject().apply {
                put("points_used", pointsUsed)
                put("active_mapping_mode", MAPPING_MODE)
                putNum("loo_median_px", summary.medianErrorPx)
                putNum("loo_p95_px", summary.p95ErrorPx)
                putNum("loo_max_px", summary.maxErrorPx)
                putNum("loo_vertical_median_lines", summary.medianVerticalPx / lineHeightPx)
                putNum("loo_vertical_p95_lines", summary.p95VerticalPx / lineHeightPx)
                putNum("loo_vertical_max_lines", summary.maxVerticalPx / lineHeightPx)
                put("within_0_5_line", summary.withinHalfLine)
                put("within_1_0_line", summary.withinOneLine)
                put("within_1_2_line_provisional", summary.withinReference)
                put("meets_provisional_1_2_line_reference", summary.meetsProvisionalReference)
                put("worst_point_ids", JSONArray(worstPointIds))
                put("leave_one_out_points", JSONArray().apply {
                    summary.points.forEach { point ->
                        put(JSONObject().apply {
                            put("point_id", point.id)
                            put("region", point.label)
                            putNum("dx_px", point.dxPx)
                            putNum("dy_px", point.dyPx)
                            putNum("error_px", point.errorPx)
                            putNum("vertical_error_lines", point.verticalLines)
                        })
                    }
                })
            },
        )
        flush()
    }

    fun addValidationPoint(
        screenX: Float,
        screenY: Float,
        mappedX: Float,
        mappedY: Float,
        errorPx: Float,
        errorLines: Float,
        verticalErrorLines: Float,
    ) {
        validation.put(
            JSONObject().apply {
                putNum("screen_x", screenX)
                putNum("screen_y", screenY)
                putNum("mapped_x", mappedX)
                putNum("mapped_y", mappedY)
                putNum("dx_px", mappedX - screenX)
                putNum("dy_px", mappedY - screenY)
                putNum("error_px", errorPx)
                putNum("error_lines", errorLines)
                putNum("vertical_error_lines", verticalErrorLines)
            },
        )
        flush()
    }

    fun logValidationSummary(summary: ReadingSpatialMetrics.Summary, lineHeightPx: Float) {
        root.put(
            "validation_summary",
            JSONObject().apply {
                put("accepted_point_count", summary.points.size)
                putNum("median_px", summary.medianErrorPx)
                putNum("p95_px", summary.p95ErrorPx)
                putNum("max_px", summary.maxErrorPx)
                putNum("vertical_median_lines", summary.medianVerticalPx / lineHeightPx)
                putNum("vertical_p95_lines", summary.p95VerticalPx / lineHeightPx)
                putNum("vertical_max_lines", summary.maxVerticalPx / lineHeightPx)
                put("within_0_5_line", summary.withinHalfLine)
                put("within_1_0_line", summary.withinOneLine)
                put("within_1_2_line_provisional", summary.withinReference)
                put("meets_provisional_1_2_line_reference", summary.meetsProvisionalReference)
                put("worst_vertical_point_index", summary.worstVertical.id)
            },
        )
        flush()
    }

    /** Clears validation entries for a fresh round after a redo. */
    fun resetValidation() {
        validation = JSONArray()
        root.put("validation", validation)
        flush()
    }

    fun addFlag(flag: String) {
        flags.put(flag)
        flush()
    }

    fun finish(outcome: String, fpsSummary: FpsSummary? = null) {
        root.put("outcome", outcome)
        root.put("fps_summary", fpsSummary?.toJson() ?: JSONObject.NULL)
        root.put(
            "timestamp_end",
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date()),
        )
        flush()
    }

    private fun featureArray(x: Float, y: Float): JSONArray =
        JSONArray().apply {
            put(if (x.isFinite()) x.toDouble() else JSONObject.NULL)
            put(if (y.isFinite()) y.toDouble() else JSONObject.NULL)
        }

    private fun featureArray(a: Float, b: Float, c: Float, d: Float): JSONArray =
        JSONArray().apply {
            listOf(a, b, c, d).forEach { value ->
                put(if (value.isFinite()) value.toDouble() else JSONObject.NULL)
            }
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

    private fun FpsSummary.toJson(): JSONObject = JSONObject().apply {
        put("sample_count", sampleCount)
        putNum("mean", mean)
        putNum("min", min)
        putNum("max", max)
    }

    /** JSONObject.put(double) throws on NaN/Infinity; store null instead. */
    private fun JSONObject.putNum(key: String, value: Float) {
        put(key, if (value.isFinite()) value.toDouble() else JSONObject.NULL)
    }

    private fun flush() {
        try {
            file.writeText(root.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write session log ${file.name}", e)
        }
    }

    companion object {
        private const val TAG = "GazeCalib"
        private const val MAPPING_MODE = "quadratic_average"
    }
}
