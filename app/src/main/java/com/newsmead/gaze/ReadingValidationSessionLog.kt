package com.newsmead.gaze

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Structured reading trace. Comparison records use one slot-bound append-only
 * file and a shared elapsedRealtimeNanos envelope; ordinary diagnostic runs
 * keep their existing independent files.
 */
class ReadingValidationSessionLog(
    context: Context,
    runLabel: String,
    orderVariant: String,
    screenWidthPx: Int,
    screenHeightPx: Int,
    densityDpi: Int,
    rawFeatureMode: String,
    calibrationPointCount: Int,
    calibrationFingerprint: String?,
    driftCorrectionActive: Boolean,
    verticalAlignmentMode: ReadingVerticalAlignmentMode,
    private val feedbackMode: ReadingValidationFeedbackMode,
    private val comparison: ComparisonReadingBinding? = null,
) {
    private val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
    private val sessionId = comparison?.let { "comparison_${it.record.runtime.spec.slot.id}" }
        ?: "reading_validation_session_$stamp"
    private val normalFile: File? = if (comparison == null) {
        File(context.filesDir, "$sessionId.jsonl")
    } else {
        null
    }
    private val normalWriter: BufferedWriter? = normalFile?.bufferedWriter()
    private val comparisonWriter = comparison?.let { ComparisonSlotStore(context).openReading(it) }
    private val startedAtMs = System.currentTimeMillis()
    private val startedElapsedNs = SystemClock.elapsedRealtimeNanos()
    private var finished = false
    private var receipt: ComparisonSlotReceipt? = null

    init {
        require(orderVariant == "A" || orderVariant == "B")
        feedbackMode.requireCompatibleWithComparison(comparison != null)
        if (comparison != null) {
            require(calibrationFingerprint == comparison.record.calibrationSha256)
            require(orderVariant == comparison.record.runtime.spec.orderVariant)
            require(screenWidthPx == comparison.record.runtime.screenWidthPx)
            require(screenHeightPx == comparison.record.runtime.screenHeightPx)
            require(densityDpi == comparison.record.runtime.densityDpi)
            require(!driftCorrectionActive)
            require(verticalAlignmentMode == ReadingVerticalAlignmentMode.OFF)
        }
        event("session_start", flush = true) {
            put("schema_version", feedbackMode.schemaVersion)
            put("calibration_sha256", calibrationFingerprint ?: JSONObject.NULL)
            put("protocol_version", feedbackMode.protocolVersion)
            put("run_label", runLabel)
            put("order_variant", orderVariant)
            put("timestamp_iso", isoTimestamp(startedAtMs))
            put("device_model", Build.MODEL)
            put("device_manufacturer", Build.MANUFACTURER)
            put("android_version", Build.VERSION.RELEASE)
            put("screen_width_px", screenWidthPx)
            put("screen_height_px", screenHeightPx)
            put("density_dpi", densityDpi)
            put("raw_feature_mode", rawFeatureMode)
            put("calibration_point_count", calibrationPointCount)
            put("drift_correction_active", driftCorrectionActive)
            put("vertical_alignment_mode", verticalAlignmentMode.name.lowercase(Locale.ROOT))
            put("vertical_alignment_scope", "session_only_reading_surface_y_gain_bias")
            put("detailed_source_telemetry_enabled", false)
            put("camera_frames_retained", false)
            if (feedbackMode.visibleDuringScoredTrials) {
                put("gaze_feedback_mode", feedbackMode.logValue)
                put("gaze_feedback_visible_during_scored_trials", true)
                put(
                    "gaze_feedback_coordinate",
                    if (feedbackMode.smoothForReading) {
                        "effective_article_smoothed_gaze_screen_px"
                    } else {
                        "effective_gaze_screen_px"
                    },
                )
                put("gaze_feedback_presentation", JSONObject().apply {
                    put("shape", "circle")
                    put("radius_px", GazeOverlayView.GAZE_DOT_RADIUS_PX)
                    put("alpha", GazeOverlayView.GAZE_DOT_ALPHA)
                    put("red", GazeOverlayView.GAZE_DOT_RED)
                    put("green", GazeOverlayView.GAZE_DOT_GREEN)
                    put("blue", GazeOverlayView.GAZE_DOT_BLUE)
                })
                put("validation_scope", "known_target_visible_feedback_performance")
                if (feedbackMode.smoothForReading) {
                    put("output_coordinate_filter", "mgazenet_article_one_euro_v1")
                    put("output_coordinate_filter_applied_to", "dot_mapping_scoring")
                    put("scoring_coordinate", "effective_article_smoothed_gaze_screen_px")
                    put("raw_source_coordinate", "mgazenet_source.raw_screen_x_y")
                }
            } else {
                put("validation_scope", "known_target_spatial_accuracy_only")
            }
            put("result_visibility", if (comparison == null) "terminal_ui" else "offline_after_four_hashes")
            put(
                "processing_path",
                if (feedbackMode.smoothForReading) {
                    "calibrated_gaze -> article_one_euro_screen_smoothing -> optional_session_vertical_alignment -> line_aoi -> existing_target_stabilizer"
                } else {
                    "calibrated_gaze -> optional_session_vertical_alignment -> line_aoi -> existing_target_stabilizer"
                },
            )
        }
    }

    fun logProtocolPlan(
        passageSha256: String,
        wordTargets: List<ReadingValidationCheckpoint>,
        lineTargets: List<ReadingValidationCheckpoint>,
    ) = event("protocol_plan", flush = true) {
        put("passage_sha256", passageSha256)
        put("unscored_dot_preview_count", 1)
        put("vertical_reference_count", 3)
        put("word_target_count", wordTargets.size)
        put("line_target_count", lineTargets.size)
        put("countdown_ms", ReadingValidationProtocol.COUNTDOWN_MS)
        put("vertical_reference_acquire_ms", ReadingValidationProtocol.ALIGNMENT_ACQUIRE_MS)
        put("vertical_reference_measure_ms", ReadingValidationProtocol.ALIGNMENT_MEASURE_MS)
        put("word_acquire_ms", ReadingValidationProtocol.LOCALIZATION_ACQUIRE_MS)
        put("word_measure_ms", ReadingValidationProtocol.LOCALIZATION_MEASURE_MS)
        put("line_acquire_ms", ReadingValidationProtocol.LINE_READING_ACQUIRE_MS)
        put("line_measure_ms", ReadingValidationProtocol.LINE_READING_MEASURE_MS)
        put("minimum_coordinates_per_scored_target", ComparisonProtocol.MIN_COORDINATES_PER_SCORED_TARGET)
        if (feedbackMode.visibleDuringScoredTrials) {
            put("gaze_feedback_mode", feedbackMode.logValue)
            put("gaze_feedback_visible_during_preview", true)
            put("gaze_feedback_visible_during_vertical_references", true)
            put("gaze_feedback_visible_during_word_trials", true)
            put("gaze_feedback_visible_during_line_trials", true)
            if (feedbackMode.smoothForReading) {
                put("output_coordinate_filter", "mgazenet_article_one_euro_v1")
                put("output_coordinate_filter_applied_to", "dot_mapping_scoring")
            }
        }
        put("targets", JSONArray().apply {
            (wordTargets + lineTargets).forEach { target ->
                put(JSONObject().apply { putExpected(target) })
            }
        })
    }

    fun logCoordinateFrames(
        phase: ReadingValidationPhase,
        stepId: String,
        rootFrame: GazeCoordinateFrame,
        viewportFrame: GazeCoordinateFrame,
        textFrame: GazeCoordinateFrame,
    ) = event("coordinate_frames") {
        put("phase", phase.name)
        put("step_id", stepId)
        put("root_view_frame", rootFrame.toJson())
        put("viewport_frame", viewportFrame.toJson())
        put("text_view_frame", textFrame.toJson())
    }

    fun logLayout(
        textLength: Int,
        lineCount: Int,
        lineHeightPx: Float,
        textSizePx: Float,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
    ) = event("layout") {
        put("text_length", textLength)
        put("line_count", lineCount)
        putNum("line_height_px", lineHeightPx)
        putNum("text_size_px", textSizePx)
        put("viewport_width_px", viewportWidthPx)
        put("viewport_height_px", viewportHeightPx)
    }

    fun logProtocolState(
        phase: ReadingValidationPhase,
        stepId: String,
        state: ReadingValidationTrialState,
        instruction: String,
        expected: ReadingValidationCheckpoint? = null,
    ) = event("protocol_state", flush = true) {
        putContext(phase, stepId, state, expected)
        put(
            "gaze_feedback_visible",
            feedbackMode.visibleDuringScoredTrials || stepId == "unscored_dot_preview",
        )
        put("instruction", instruction)
    }

    fun logScroll(
        phase: ReadingValidationPhase,
        stepId: String,
        state: ReadingValidationTrialState,
        scrollY: Int,
        oldScrollY: Int,
        source: String,
    ) = event("scroll") {
        putContext(phase, stepId, state, null)
        put("scroll_y", scrollY)
        put("old_scroll_y", oldScrollY)
        put("delta_y", scrollY - oldScrollY)
        put("source", source)
    }

    fun logFps(
        fps: Float,
        phase: ReadingValidationPhase,
        stepId: String,
        state: ReadingValidationTrialState,
    ) = event("fps") {
        putContext(phase, stepId, state, null)
        putNum("fps", fps)
    }

    fun logVerticalReference(aggregate: ReadingVerticalReferenceAggregate) =
        event("vertical_alignment_reference", flush = true) {
            put("reference_id", aggregate.reference.id)
            putNum("target_x_screen_px", aggregate.reference.targetX)
            putNum("target_y_screen_px", aggregate.reference.targetY)
            put("sample_count", aggregate.sampleCount)
            putNum("observed_median_y_screen_px", aggregate.observedMedianY)
            putNum("signed_error_before_px", aggregate.observedMedianY - aggregate.reference.targetY)
        }

    fun logVerticalAlignmentFit(
        mode: ReadingVerticalAlignmentMode,
        fit: ReadingVerticalAlignmentFit,
        applied: Boolean,
    ) = event("vertical_alignment_fit", flush = true) {
        put("mode", mode.name.lowercase(Locale.ROOT))
        put("accepted", fit.accepted)
        put("applied", applied)
        put("reason", fit.reason)
        put("reference_count", fit.referenceCount)
        putNum("gain", fit.gain)
        putNum("intercept_px", fit.interceptPx)
        putNum("max_residual_px", fit.maxResidualPx)
        putNum("leave_one_out_max_px", fit.leaveOneOutMaxPx)
        putNum("median_before_px", fit.medianBeforePx)
        putNum("median_after_px", fit.medianAfterPx)
    }

    fun logGaze(
        timestampMs: Long,
        deliveryElapsedNs: Long,
        deliveryId: Long,
        phase: ReadingValidationPhase,
        stepId: String,
        state: ReadingValidationTrialState,
        expected: ReadingValidationCheckpoint?,
        gazeX: Float,
        baseGazeY: Float,
        effectiveGazeY: Float,
        baseTarget: TextTarget,
        rawTarget: TextTarget,
        stableTarget: TextTarget,
        scrollY: Int,
        textTopOnScreen: Int,
    ) = event("gaze_sample", timestampMs, deliveryElapsedNs) {
        putContext(phase, stepId, state, expected)
        put("delivery_id", deliveryId)
        put("delivery_elapsed_ns", deliveryElapsedNs)
        putNum("gaze_x_screen_px", gazeX)
        putNum("gaze_y_screen_px", baseGazeY)
        putNum("effective_gaze_y_screen_px", effectiveGazeY)
        putNum("vertical_alignment_delta_px", effectiveGazeY - baseGazeY)
        putTarget("base_target", baseTarget)
        putTarget("raw_target", rawTarget)
        putTarget("stable_target", stableTarget)
        put("scroll_y", scrollY)
        put("text_top_screen_px", textTopOnScreen)
        put("line_count", stableTarget.lineCount.takeIf { it > 0 } ?: rawTarget.lineCount)
    }

    /** MGazeNet-only source events. Current-arm records must not invent capture time. */
    fun logMgazeNetObservation(
        captureMs: Double?,
        deliveryElapsedNs: Long,
        deliveryId: Long,
        reason: String,
        rawX: Float?,
        rawY: Float?,
        arrivals: Long?,
        busyDrops: Long?,
        phase: ReadingValidationPhase,
        stepId: String,
        state: ReadingValidationTrialState,
    ) = event("mgazenet_source", elapsedNs = deliveryElapsedNs) {
        putContext(phase, stepId, state, null)
        put("delivery_id", deliveryId)
        putNum("capture_elapsed_ms", captureMs)
        put("delivery_elapsed_ns", deliveryElapsedNs)
        putNum("output_age_ms", captureMs?.let { deliveryElapsedNs / 1e6 - it })
        put("reason", reason)
        putNum("raw_screen_x", rawX)
        putNum("raw_screen_y", rawY)
        putNum("analyzer_arrivals", arrivals)
        putNum("observed_busy_drops", busyDrops)
        put("operational_expiry_ms", 500)
        put("filter", "none")
        if (feedbackMode.smoothForReading) {
            put("coordinate_role", "prefilter_raw_source")
            put("delivered_output_filter", "mgazenet_article_one_euro_v1")
        }
    }

    fun finish(outcome: String, summary: ReadingValidationMetrics.Summary) {
        if (finished) return
        event("session_end", flush = true) {
            put("outcome", outcome)
            put("timestamp_iso", isoTimestamp(System.currentTimeMillis()))
            put("spatial_result_exposed", comparison == null)
            if (comparison == null) {
                put("measured_sample_count", summary.measuredSamples)
                put("valid_sample_count", summary.validSamples)
                put("completed_checkpoint_count", summary.checkpointCount)
                putNum("valid_fraction", summary.validFraction)
                putNum("exact_line_accuracy", summary.exactLineAccuracy)
                putNum("within_one_line_accuracy", summary.withinOneLineAccuracy)
                putNum("exact_word_accuracy", summary.exactWordAccuracy)
                put("word_measurement_sample_count", summary.wordMeasuredSamples)
                putNum("word_trial_exact_line_accuracy", summary.wordExactLineAccuracy)
                putNum("word_trial_within_one_line_accuracy", summary.wordWithinOneLineAccuracy)
                put("guided_line_measurement_sample_count", summary.lineMeasuredSamples)
                putNum("guided_line_exact_accuracy", summary.guidedLineExactAccuracy)
                putNum("guided_line_within_one_accuracy", summary.guidedLineWithinOneAccuracy)
                putNum("median_absolute_line_error", summary.medianAbsoluteLineError)
                putNum("p95_absolute_line_error", summary.p95AbsoluteLineError)
            }
        }
        finished = true
        if (comparisonWriter != null) {
            receipt = comparisonWriter.closeAndSeal()
        } else {
            normalWriter?.close()
        }
        Log.i(TAG, "Finished structured reading log ${fileName()}: $outcome")
    }

    fun flush() {
        if (!finished) comparisonWriter?.flush() ?: normalWriter?.flush()
    }

    fun fileName(): String = comparisonWriter?.fileName() ?: normalFile?.name.orEmpty()

    fun sealedReceipt(): ComparisonSlotReceipt? = receipt

    private fun event(
        recordType: String,
        timestampMs: Long = System.currentTimeMillis(),
        elapsedNs: Long = SystemClock.elapsedRealtimeNanos(),
        flush: Boolean = false,
        block: JSONObject.() -> Unit,
    ) {
        if (finished) return
        val value = JSONObject().apply {
            put("record_type", recordType)
            put("session_id", sessionId)
            put("timestamp_ms", timestampMs)
            put("event_elapsed_ns", elapsedNs)
            put("elapsed_ms", ((elapsedNs - startedElapsedNs).coerceAtLeast(0L)) / 1e6)
            block()
        }
        write(value, elapsedNs, flush)
    }

    private fun JSONObject.putContext(
        phase: ReadingValidationPhase,
        stepId: String,
        state: ReadingValidationTrialState,
        expectedCheckpoint: ReadingValidationCheckpoint?,
    ) {
        put("phase", phase.name)
        put("step_id", stepId)
        put("trial_state", state.name)
        put("expected_checkpoint", expectedCheckpoint?.id ?: JSONObject.NULL)
        put("expected_region", expectedCheckpoint?.region ?: JSONObject.NULL)
        put("expected_line", expectedCheckpoint?.lineIndex ?: JSONObject.NULL)
        put("expected_target_kind", expectedCheckpoint?.targetKind?.name ?: JSONObject.NULL)
        put("expected_target_text", expectedCheckpoint?.targetText ?: JSONObject.NULL)
        put("expected_target_start", expectedCheckpoint?.targetStart ?: JSONObject.NULL)
        put("expected_target_end", expectedCheckpoint?.targetEnd ?: JSONObject.NULL)
    }

    private fun JSONObject.putExpected(target: ReadingValidationCheckpoint) {
        put("id", target.id)
        put("region", target.region)
        put("line_index", target.lineIndex)
        put("target_kind", target.targetKind.name)
        put("target_text", target.targetText)
        put("target_start", target.targetStart)
        put("target_end", target.targetEnd)
        putNum("viewport_fraction", target.viewportFraction)
    }

    private fun JSONObject.putTarget(prefix: String, target: TextTarget) {
        put("${prefix}_valid", target.isValid)
        put("${prefix}_line", target.lineIndex)
        put("${prefix}_line_count", target.lineCount)
        put("${prefix}_word_start", target.wordStart)
        put("${prefix}_word_end", target.wordEnd)
    }

    private fun JSONObject.putNum(key: String, value: Number?) {
        val number = value?.toDouble()
        put(key, if (number != null && number.isFinite()) number else JSONObject.NULL)
    }

    private fun write(value: JSONObject, elapsedNs: Long, flush: Boolean) {
        if (comparisonWriter != null) {
            comparisonWriter.append(value, elapsedNs, flush)
            return
        }
        try {
            normalWriter?.append(value.toString())
            normalWriter?.newLine()
            if (flush) normalWriter?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write ${normalFile?.name}", e)
        }
    }

    private fun GazeCoordinateFrame.toJson() = JSONObject().apply {
        put("origin_x", originX)
        put("origin_y", originY)
        put("width", width)
        put("height", height)
    }

    private fun isoTimestamp(timestampMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date(timestampMs))

    companion object {
        const val SCHEMA_VERSION = 7
        const val RAW_VISIBLE_FEEDBACK_SCHEMA_VERSION = 8
        const val ARTICLE_SMOOTHED_VISIBLE_FEEDBACK_SCHEMA_VERSION = 9
        private const val TAG = "ReadingValidation"
    }
}
