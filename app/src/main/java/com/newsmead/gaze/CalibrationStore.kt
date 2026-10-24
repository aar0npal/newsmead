package com.newsmead.gaze

import android.content.Context
import android.util.Log
import java.io.File

/** One calibration sample: known screen point (px) to measured local gaze feature. */
data class CalibrationSample(
    val screenX: Float,
    val screenY: Float,
    val gazeX: Float,
    val gazeY: Float,
    val eye1X: Float = Float.NaN,
    val eye1Y: Float = Float.NaN,
    val eye2X: Float = Float.NaN,
    val eye2Y: Float = Float.NaN,
    val faceCenterX: Float = Float.NaN,
    val faceCenterY: Float = Float.NaN,
    val faceScale: Float = Float.NaN,
    val headRollDeg: Float = Float.NaN,
) {
    val hasPerEye: Boolean
        get() = eye1X.isFinite() && eye1Y.isFinite() && eye2X.isFinite() && eye2Y.isFinite()

    val posture: PostureFeatures
        get() = PostureFeatures(faceCenterX, faceCenterY, faceScale, headRollDeg)
}

internal object CalibrationCsvCodec {
    const val HEADER = "screen_x,screen_y,gaze_x,gaze_y,eye1_x,eye1_y,eye2_x,eye2_y," +
        "face_center_x,face_center_y,face_scale,head_roll_deg"

    fun encode(sample: CalibrationSample): String =
        "${sample.screenX},${sample.screenY},${sample.gazeX},${sample.gazeY}," +
            "${sample.eye1X},${sample.eye1Y},${sample.eye2X},${sample.eye2Y}," +
            "${sample.faceCenterX},${sample.faceCenterY},${sample.faceScale},${sample.headRollDeg}"

    /** Accept legacy four/eight-column and posture-aware twelve-column calibrations. */
    fun decode(line: String): CalibrationSample {
        val values = line.split(',')
        require(values.size == 4 || values.size == 8 || values.size == 12) {
            "Expected 4, 8, or 12 calibration columns"
        }
        return CalibrationSample(
            screenX = values[0].toFloat(),
            screenY = values[1].toFloat(),
            gazeX = values[2].toFloat(),
            gazeY = values[3].toFloat(),
            eye1X = values.getOrNull(4)?.toFloat() ?: Float.NaN,
            eye1Y = values.getOrNull(5)?.toFloat() ?: Float.NaN,
            eye2X = values.getOrNull(6)?.toFloat() ?: Float.NaN,
            eye2Y = values.getOrNull(7)?.toFloat() ?: Float.NaN,
            faceCenterX = values.getOrNull(8)?.toFloat() ?: Float.NaN,
            faceCenterY = values.getOrNull(9)?.toFloat() ?: Float.NaN,
            faceScale = values.getOrNull(10)?.toFloat() ?: Float.NaN,
            headRollDeg = values.getOrNull(11)?.toFloat() ?: Float.NaN,
        )
    }
}

/** Reads/writes the calibration pairs as a CSV in app-private storage. */
object CalibrationStore {

    private const val TAG = "GazeCalib"
    private const val CSV_NAME = "calibration_16point.csv"
    private const val FEATURE_MODE_NAME = "calibration_feature_mode.txt"
    private const val COORDINATE_METADATA_NAME = "calibration_coordinates.txt"
    private const val DRIFT_COORDINATE_METADATA_NAME = "drift_correction_coordinates.txt"
    private const val LEGACY_WIFI_CSV_NAME = "calibration_wifi.csv"
    private const val DRIFT_CSV_NAME = "drift_correction.csv"
    private const val DRIFT_HEADER = "cx0,cx1,cx2,cy0,cy1,cy2,timestamp_ms,pre_median_px,post_median_px"
    private const val RECAL_LOG_NAME = "recalibration_log.csv"
    private const val RECAL_LOG_HEADER = "timestamp_ms,outcome,pre_median_px,post_median_px"

    /** Accept only targets already converted from their drawing view to screen pixels. */
    fun saveScreenCalibration(
        context: Context,
        samples: List<CalibrationSample>,
        featureMode: RawGazeFeatureMode = LocalGazeSources.ACTIVE_FEATURE_MODE,
    ) {
        val file = File(context.filesDir, CSV_NAME)
        val text = buildString {
            append(CalibrationCsvCodec.HEADER).append('\n')
            samples.forEach { append(CalibrationCsvCodec.encode(it)).append('\n') }
        }
        file.writeText(text)
        File(context.filesDir, FEATURE_MODE_NAME).writeText(featureMode.logLabel + '\n')
        Log.i(TAG, "Wrote ${samples.size} calibration pairs to ${file.absolutePath}")
        // A fresh full calibration supersedes any drift correction fitted on top
        // of the previous one.
        clearDriftCorrection(context)
        // Publish last: an interrupted save must not certify incompatible data.
        File(context.filesDir, COORDINATE_METADATA_NAME).writeText(
            GazeCoordinateContract.encode(calibrationPayload(text, featureMode.logLabel)),
        )
    }

    /** Returns only samples verified for the active feature and screen-coordinate contracts. */
    fun load(context: Context): List<CalibrationSample>? {
        if (compatibilityIssue(context) != null) return null
        val file = File(context.filesDir, CSV_NAME)
        if (!file.exists()) return null
        return read(file)
    }

    /**
     * Calibrations created before feature-mode metadata used the original
     * eyelid-fraction geometry. This prevents a candidate build or rollback
     * from silently mapping incompatible raw features through the saved fit.
     */
    fun loadFeatureMode(context: Context): String {
        val file = File(context.filesDir, FEATURE_MODE_NAME)
        return if (file.exists()) {
            file.readText().trim().ifEmpty { RawGazeFeatureMode.EYELID_FRACTION_AVERAGE.logLabel }
        } else {
            RawGazeFeatureMode.EYELID_FRACTION_AVERAGE.logLabel
        }
    }

    fun isCompatibleWithActiveFeatureMode(context: Context): Boolean =
        loadFeatureMode(context) == LocalGazeSources.ACTIVE_FEATURE_MODE.logLabel

    /** Read-only: never migrate, relabel or delete an old calibration. */
    fun compatibilityIssue(context: Context): String? {
        val file = File(context.filesDir, CSV_NAME)
        if (!file.exists()) return "Run a full 16-point calibration before using gaze."
        return try {
            if (!isCompatibleWithActiveFeatureMode(context)) {
                "The eye-measurement format changed. Run a fresh 16-point calibration."
            } else if (!GazeCoordinateContract.matches(
                    File(context.filesDir, COORDINATE_METADATA_NAME).takeIf { it.exists() }?.readText(),
                    calibrationPayload(file.readText(), loadFeatureMode(context)),
                )) {
                "The screen-coordinate format changed or the saved calibration is incompatible. Run a fresh 16-point calibration. Existing diagnostic logs are preserved."
            } else null
        } catch (e: Exception) {
            "The saved calibration cannot be verified. Run a fresh 16-point calibration."
        }
    }

    fun fingerprint(context: Context): String? =
        File(context.filesDir, CSV_NAME).takeIf { it.exists() }?.let { GazeCoordinateContract.fingerprint(it.readText()) }

    private fun calibrationPayload(csv: String, featureMode: String) = "$featureMode\n$csv"

    private fun driftPayload(context: Context, driftCsv: String): String =
        "${fingerprint(context)}\n$driftCsv"

    // --- Drift correction (fitted from the gaze accuracy test) --------------

    fun saveDriftCorrection(
        context: Context,
        correction: DriftCorrection,
        preMedianPx: Float,
        postMedianPx: Float,
    ) {
        require(compatibilityIssue(context) == null) { "A screen-coordinate calibration is required" }
        val file = File(context.filesDir, DRIFT_CSV_NAME)
        val cx = correction.coeffX
        val cy = correction.coeffY
        file.writeText(
            DRIFT_HEADER + '\n' +
                "${cx[0]},${cx[1]},${cx[2]},${cy[0]},${cy[1]},${cy[2]}," +
                "${System.currentTimeMillis()},$preMedianPx,$postMedianPx\n",
        )
        File(context.filesDir, DRIFT_COORDINATE_METADATA_NAME).writeText(
            GazeCoordinateContract.encode(driftPayload(context, file.readText())),
        )
        Log.i(
            TAG,
            "Saved drift correction (median $preMedianPx px -> est. $postMedianPx px) to ${file.absolutePath}",
        )
    }

    /** Returns the saved drift correction, or null if absent/unparseable. */
    fun loadDriftCorrection(context: Context): DriftCorrection? {
        if (compatibilityIssue(context) != null) return null
        val file = File(context.filesDir, DRIFT_CSV_NAME)
        if (!file.exists()) return null
        return try {
            if (!GazeCoordinateContract.matches(
                    File(context.filesDir, DRIFT_COORDINATE_METADATA_NAME).takeIf { it.exists() }?.readText(),
                    driftPayload(context, file.readText()),
                )) return null
            val parts = file.readLines()[1].split(",")
            DriftCorrection(
                doubleArrayOf(parts[0].toDouble(), parts[1].toDouble(), parts[2].toDouble()),
                doubleArrayOf(parts[3].toDouble(), parts[4].toDouble(), parts[5].toDouble()),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read ${file.name}", e)
            null
        }
    }

    fun clearDriftCorrection(context: Context) {
        val file = File(context.filesDir, DRIFT_CSV_NAME)
        if (file.exists() && file.delete()) {
            Log.i(TAG, "Cleared drift correction (superseded)")
        }
        File(context.filesDir, DRIFT_COORDINATE_METADATA_NAME).delete()
    }

    /**
     * Append-only audit trail of re-calibration actions (apply/revert), one line
     * per action, for thesis data-quality reporting. Separate from the single
     * active drift_correction.csv so history is never overwritten.
     */
    fun appendRecalibrationHistory(
        context: Context,
        outcome: String,
        preMedianPx: Float,
        postMedianPx: Float,
    ) {
        val file = File(context.filesDir, RECAL_LOG_NAME)
        try {
            if (!file.exists()) file.appendText(RECAL_LOG_HEADER + '\n')
            file.appendText("${System.currentTimeMillis()},$outcome,$preMedianPx,$postMedianPx\n")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to append $RECAL_LOG_NAME", e)
        }
    }

    private fun read(file: File): List<CalibrationSample>? {
        if (!file.exists()) return null
        return try {
            file.readLines()
                .drop(1)
                .filter { it.isNotBlank() }
                .map(CalibrationCsvCodec::decode)
                .takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read ${file.name}", e)
            null
        }
    }
}
