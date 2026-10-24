package com.newsmead.gaze.reading

/**
 * Opt-in content residence proxy for a translating article, not a physiological fixation model.
 * Origins must come from geometry observed at or before capture, in selected screen pixels.
 * The layout ID remains stable only across explicitly controlled translations.
 */
data class ReadingMotionCompensation(
    val geometryTimeMs: Double,
    val geometrySnapshotId: String,
    val textOriginX: Double,
    val textOriginY: Double,
    val contentX: Double,
    val contentY: Double,
    val modeId: String = MODE_ID,
) {
    companion object {
        const val MODE_ID = "auto_scroll_content_frame_v1"
    }

    fun validFor(sample: ReadingSample): Boolean {
        val capture = sample.captureMs ?: return false
        val x = sample.processingX ?: return false
        val y = sample.processingY ?: return false
        return sample.coordinateStream == ReadingCoordinateStream.ARTICLE_SMOOTHED &&
            modeId == MODE_ID && geometrySnapshotId.isNotBlank() &&
            geometrySnapshotId == sample.geometrySnapshotId &&
            capture.isFinite() && capture >= 0 && geometryTimeMs.isFinite() &&
            geometryTimeMs >= 0 && geometryTimeMs <= capture &&
            x.isFinite() && y.isFinite() && textOriginX.isFinite() && textOriginY.isFinite() &&
            contentX.isFinite() && contentY.isFinite() &&
            contentX == x - textOriginX && contentY == y - textOriginY
    }
}

/**
 * Causal geometry history. A floor snapshot is retained for static geometry, but capture
 * queries outside the retained time window or after the latest record always abstain.
 * At extreme record rates the resource cap can shorten the usable history further.
 */
class ReadingGeometryTimeline(private val historyMs: Double = 2000.0) {
    data class TimedGeometry(val atMs: Double, val geometry: ReadingGeometry)

    private val snapshots = ArrayDeque<TimedGeometry>()

    init {
        require(historyMs.isFinite() && historyMs > 0)
    }

    fun record(atMs: Double, geometry: ReadingGeometry): Boolean {
        if (!atMs.isFinite() || atMs < 0 ||
            snapshots.lastOrNull()?.let { atMs < it.atMs } == true) return false
        // Copy the list so a caller cannot mutate the recorded line geometry later.
        val entry = TimedGeometry(atMs, geometry.copy(lines = geometry.lines.toList()))
        if (snapshots.lastOrNull()?.atMs == atMs) snapshots.removeLast()
        snapshots.addLast(entry)
        val cutoff = atMs - historyMs
        while (snapshots.size > 1 && snapshots.elementAt(1).atMs <= cutoff) {
            snapshots.removeFirst()
        }
        while (snapshots.size > MAX_SNAPSHOTS) snapshots.removeFirst()
        return true
    }

    fun at(captureMs: Double, maxAgeMs: Double? = null): TimedGeometry? {
        if (!captureMs.isFinite() || captureMs < 0 ||
            (maxAgeMs != null && (!maxAgeMs.isFinite() || maxAgeMs < 0))) return null
        val latest = snapshots.lastOrNull() ?: return null
        if (captureMs > latest.atMs || captureMs < latest.atMs - historyMs) return null
        val entry = snapshots.lastOrNull { it.atMs <= captureMs } ?: return null
        if (maxAgeMs != null && captureMs - entry.atMs > maxAgeMs) return null
        return entry
    }

    fun clear() = snapshots.clear()

    private companion object {
        const val MAX_SNAPSHOTS = 4096
    }
}
