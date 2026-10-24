package com.newsmead.gaze.reading

/** Offsets are Android UTF-16. Coordinates are pixels in the explicitly selected frame. */
data class WordRange(val startUtf16: Int, val endUtf16: Int)
data class ReadingRect(val left: Double, val top: Double, val right: Double, val bottom: Double)
data class ReadingLine(
    val startUtf16: Int, val endUtf16: Int,
    val left: Double, val right: Double, val top: Double, val bottom: Double
)
enum class ReadingTextDirection { LTR, RTL, UNKNOWN }
data class ReadingGeometry(
    val snapshotId: String,
    val textOriginX: Double, val textOriginY: Double,
    val visibleRectScreen: ReadingRect,
    val lineHeightPx: Double,
    val lines: List<ReadingLine>,
    val textDirection: ReadingTextDirection? = null
)
/** Versioned coordinate source; neither selection performs calibration or filtering. */
enum class ReadingCoordinateStream(val id: String) {
    CALIBRATED_RAW("calibrated_raw_v1"),
    ARTICLE_SMOOTHED("article_smoothed_v1")
}

data class ReadingSample(
    val captureMs: Double?, val deliveryMs: Double?,
    val rawX: Double?, val rawY: Double?,
    val line: Int?, val wordStartUtf16: Int?, val wordEndUtf16: Int?,
    val geometrySnapshotId: String?,
    val eligible: Boolean = true, val exclusion: String? = null,
    val displayX: Double? = null, val displayY: Double? = null,
    val coordinateStream: ReadingCoordinateStream = ReadingCoordinateStream.CALIBRATED_RAW,
    val motionCompensation: ReadingMotionCompensation? = null
) {
    val processingX: Double? get() = when (coordinateStream) {
        ReadingCoordinateStream.CALIBRATED_RAW -> rawX
        ReadingCoordinateStream.ARTICLE_SMOOTHED -> displayX
    }
    val processingY: Double? get() = when (coordinateStream) {
        ReadingCoordinateStream.CALIBRATED_RAW -> rawY
        ReadingCoordinateStream.ARTICLE_SMOOTHED -> displayY
    }
    /** Body-local pixels only for validated opt-in metadata; processing/display stay on screen. */
    val analysisX: Double? get() {
        val motion = motionCompensation ?: return processingX
        return motion.contentX.takeIf { motion.validFor(this) }
    }
    val analysisY: Double? get() {
        val motion = motionCompensation ?: return processingY
        return motion.contentY.takeIf { motion.validFor(this) }
    }
}

