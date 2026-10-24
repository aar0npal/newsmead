package com.newsmead.gaze.scroll

import android.graphics.Rect
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import com.newsmead.custom.LockableNestedScrollView
import com.newsmead.gaze.getVisibleRectOnScreen
import com.newsmead.gaze.physicalDisplaySize
import com.newsmead.gaze.mgazenet.MgazeNetGazeProvider
import com.newsmead.gaze.reading.ArticleReadingSession
import com.newsmead.gaze.reading.ReadingCoordinateStream
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Article navigation only; supplied gaze controls a continuous forward velocity. */
internal class ArticleAutoScrollController(
    private val body: TextView,
    private val scroller: LockableNestedScrollView,
    private val footer: View,
    private val tail: View,
    private val blocked: () -> String?,
    private val onBoundary: (Boolean, Double, JSONObject) -> Unit,
    private val onJournal: (String, Double, JSONObject) -> Unit,
    private val onState: (Boolean, Boolean) -> Unit,
    private val clock: () -> Double = { SystemClock.elapsedRealtimeNanos() / 1e6 },
    private val screenHeight: () -> Int = { if (body.display != null) body.physicalDisplaySize().y else 0 },
) {
    private val policy = ArticleAutoScrollPolicy()
    private data class ViewGeometry(
        val contentOriginX: Int, val contentOriginY: Int,
        val bodyWidth: Int, val bodyHeight: Int, val visible: Rect, val viewport: Rect,
        val textSize: Float, val padding: List<Int>,
    )
    private var snapshot: ViewGeometry? = null
    private var expectedScrollY: Int? = null
    private var textDirty = true
    private var contentText = ""
    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { textDirty = true }
        override fun afterTextChanged(s: Editable?) = Unit
    }
    private var geometryKey = 0L
    private var layoutReadyAfterMs = Double.POSITIVE_INFINITY
    private var captureFloorMs = Double.NEGATIVE_INFINITY
    private var inputAfterMs = Double.NEGATIVE_INFINITY
    private var fractionalPx = 0.0
    private var frameScheduled = false
    private var motionEpoch = 0L
    private var motionDetail: JSONObject? = null
    private var closed = false
    var enabled = false; private set
    var pausedByReader = false; private set
    var isMoving = false; private set
    var statusReason = "disabled"; private set
    private val frame = Runnable { frameScheduled = false; advanceFrame() }

    init { body.addTextChangedListener(textWatcher) }

    private fun status(reason: String) {
        if (reason == statusReason) return
        statusReason = reason
        onJournal("status", clock(), JSONObject().put("reason", reason)
            .put("speed_px_per_second", policy.status.speedPxPerSecond)
            .put("target_speed_px_per_second", policy.status.targetSpeedPxPerSecond))
    }

    fun setEnabled(value: Boolean) {
        if (closed || value == enabled) return
        halt("setting_changed")
        enabled = value
        pausedByReader = false
        policy.reset()
        snapshot = null
        expectedScrollY = null
        layoutReadyAfterMs = Double.POSITIVE_INFINITY
        inputAfterMs = clock()
        updateTail()
        status(if (enabled) "waiting_lower" else "disabled")
        onJournal("setting", clock(), configuration().put("enabled", enabled))
        onState(enabled, pausedByReader)
    }

    /** Finger navigation takes over until the reader explicitly resumes. */
    fun pauseByReader(reason: String = "reader_pause") {
        if (closed || !enabled || pausedByReader) return
        pausedByReader = true
        halt(reason)
        status("manual_pause")
        onJournal("paused", clock(), JSONObject().put("reason", reason))
        onState(enabled, pausedByReader)
    }

    fun resumeByReader() {
        if (closed || !enabled || !pausedByReader) return
        pausedByReader = false
        halt("waiting_lower")
        onJournal("resumed", clock(), JSONObject())
        onState(enabled, pausedByReader)
    }

    fun interrupt(reason: String) {
        if (!closed && enabled) halt(reason)
    }

    fun onPreDraw() {
        if (closed || !enabled) return
        updateTail()
        if (pausedByReader) { status("manual_pause"); return }
        blocked()?.let { halt(it); return }
        if (refreshGeometry() == null) halt("geometry_unavailable")
    }

    fun onObservation(value: MgazeNetGazeProvider.Observation) {
        if (closed || !enabled) return
        if (pausedByReader) { status("manual_pause"); return }
        blocked()?.let { halt(it); return }
        val capture = value.captureMs
        if (value.reason != "coordinate" || capture == null || value.x == null || value.y == null) {
            halt("gaze_unavailable")
            return
        }
        val current = refreshGeometry() ?: run { halt("geometry_unavailable"); return }
        // This floor is for controls/reflow only. Navigation must keep receiving
        // fresh gaze while its own motion is excluded from reading evidence.
        if (capture <= inputAfterMs || capture < layoutReadyAfterMs) {
            // Discard delayed captures without moving the floor forward again.
            policy.interrupt("settling")
            stopMotion("settling")
            status("settling")
            return
        }
        val accepted = policy.onSample(ArticleAutoScrollPolicy.Sample(capture, value.outputMs,
            value.x.toDouble(), value.y.toDouble()), geometry(current), clock())
        status(policy.status.reason)
        if (!accepted) { stopMotion(policy.status.reason); return }
        if (policy.status.targetSpeedPxPerSecond > 0 || policy.status.speedPxPerSecond > 0) scheduleFrame()
    }

    private fun scheduleFrame() {
        if (frameScheduled || closed || !enabled || pausedByReader) return
        frameScheduled = true
        body.postOnAnimation(frame)
    }

    private fun advanceFrame() {
        if (closed || !enabled || pausedByReader) return
        blocked()?.let { halt(it); return }
        val current = refreshGeometry() ?: run { halt("geometry_unavailable"); return }
        if (clock() < layoutReadyAfterMs) { halt("settling"); return }
        val tick = policy.advance(clock(), geometry(current))
        status(tick.reason)
        val epoch = motionEpoch
        fractionalPx += tick.deltaPx
        val delta = floor(fractionalPx).toInt()
        fractionalPx -= delta
        if (delta > 0) {
            if (!isMoving) {
                isMoving = true
                val detail = JSONObject().put("policy", ArticleAutoScrollPolicy.VERSION)
                    .put("coordinate_stream", ReadingCoordinateStream.ARTICLE_SMOOTHED.id)
                    .put("from_scroll_y_px", scroller.scrollY)
                    .put("source_capture_ms", tick.sourceCaptureTimeMs)
                    .put("source_delivery_ms", tick.sourceDeliveryTimeMs)
                    .put("source_gaze_y_screen_px", tick.sourceYPx)
                    .put("target_speed_px_per_second", tick.targetSpeedPxPerSecond)
                    .put("geometry_key", geometryKey)
                motionDetail = detail
                onBoundary(true, clock(), JSONObject(detail.toString()))
                // A consultation or lifecycle callback may interrupt synchronously.
                if (epoch != motionEpoch || !isMoving || closed || pausedByReader || !enabled) return
            }
            scroller.scrollTo(scroller.scrollX, scroller.scrollY + delta)
            expectedScrollY = scroller.scrollY
            if (epoch != motionEpoch) return
        }
        if (tick.active) scheduleFrame()
        else stopMotion(tick.reason, if (tick.reason in listOf("waiting_lower", "no_scroll_room"))
            "completed" else "cancelled")
    }

    /** Motion plus late captured frames remain ineligible for stationary-text metrics. */
    fun exclusion(captureMs: Double?): String? = when {
        isMoving -> "auto_scroll_motion"
        captureMs != null && captureMs.isFinite() && captureMs <= captureFloorMs -> "auto_scroll_settling"
        else -> null
    }

    private fun halt(reason: String) {
        policy.interrupt(reason)
        stopMotion(reason)
        inputAfterMs = clock()
        status(reason)
    }

    private fun stopMotion(reason: String, result: String = "cancelled") {
        motionEpoch++
        body.removeCallbacks(frame)
        frameScheduled = false
        fractionalPx = 0.0
        if (!isMoving) return
        isMoving = false
        val now = clock()
        captureFloorMs = max(captureFloorMs, now + ArticleReadingSession.GEOMETRY_SETTLE_MS)
        val detail = JSONObject(motionDetail?.toString() ?: "{}")
            .put("result", result).put("reason", reason)
            .put("actual_to_scroll_y_px", scroller.scrollY)
            .put("exclude_captures_through_ms", captureFloorMs)
        motionDetail = null
        onBoundary(false, now, detail)
    }

    private fun updateTail() {
        val height = if (enabled) ceil(screenHeight() * .70).toInt() else 0
        if (tail.layoutParams.height != height)
            tail.layoutParams = tail.layoutParams.apply { this.height = height }
        tail.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    private fun refreshGeometry(): ViewGeometry? {
        val viewport = Rect()
        if (!scroller.getVisibleRectOnScreen(viewport)) return null
        val footerRect = Rect()
        if (footer.visibility == View.VISIBLE && footer.getVisibleRectOnScreen(footerRect))
            viewport.bottom = min(viewport.bottom, footerRect.top)
        if (viewport.isEmpty) return null
        val origin = IntArray(2)
        body.getLocationOnScreen(origin)
        val visibleBody = Rect(origin[0] + body.totalPaddingLeft, origin[1] + body.totalPaddingTop,
            origin[0] + body.width - body.totalPaddingRight, origin[1] + body.height - body.totalPaddingBottom)
        if (body.visibility != View.VISIBLE || body.text.isBlank() || !visibleBody.intersect(viewport)) return null
        val next = ViewGeometry(origin[0], origin[1] + scroller.scrollY,
            body.width, body.height, visibleBody, viewport, body.textSize,
            listOf(body.totalPaddingLeft, body.totalPaddingTop, body.totalPaddingRight, body.totalPaddingBottom))
        val old = snapshot
        val contentChanged = textDirty && body.text.toString() != contentText
        if (textDirty) { contentText = body.text.toString(); textDirty = false }
        val layoutChanged = old == null || contentChanged || next.bodyWidth != old.bodyWidth ||
            next.bodyHeight != old.bodyHeight || next.textSize != old.textSize || next.padding != old.padding ||
            next.viewport != old.viewport || next.contentOriginX != old.contentOriginX ||
            next.contentOriginY != old.contentOriginY
        val externalScroll = expectedScrollY != null && expectedScrollY != scroller.scrollY
        if (layoutChanged || externalScroll) {
            halt(if (layoutChanged) "reading_layout_changed" else "external_scroll")
            layoutReadyAfterMs = clock() + ArticleReadingSession.GEOMETRY_SETTLE_MS
        }
        if (next != old || contentChanged) geometryKey++
        snapshot = next
        expectedScrollY = scroller.scrollY
        return next
    }

    private fun geometry(current: ViewGeometry): ArticleAutoScrollPolicy.Geometry {
        val child = scroller.getChildAt(0)
        val maxScroll = max(0, (child?.height ?: 0) + scroller.paddingTop + scroller.paddingBottom - scroller.height)
        return ArticleAutoScrollPolicy.Geometry(screenHeight().toDouble(),
            current.visible.left.toDouble(), current.visible.top.toDouble(),
            current.visible.right.toDouble(), current.visible.bottom.toDouble(),
            current.viewport.top.toDouble(), current.viewport.bottom.toDouble(),
            scroller.scrollY.toDouble(), maxScroll.toDouble(), geometryKey)
    }

    fun configuration() = JSONObject().put("version", ArticleAutoScrollPolicy.VERSION)
        .put("trigger_basis", "continuous_gaze_height_speed_without_line_or_word_assignment")
        .put("start_screen_fraction", policy.config.startScreenFraction)
        .put("full_speed_screen_fraction", policy.config.fullSpeedScreenFraction)
        .put("max_speed_screen_fraction_per_second", policy.config.maxSpeedScreenFractionPerSecond)
        .put("response_ms", policy.config.responseMs)
        .put("max_sample_gap_ms", policy.config.maxSampleGapMs)
        .put("max_output_age_ms", policy.config.maxOutputAgeMs)
        .put("max_frame_delta_ms", policy.config.maxFrameDeltaMs)
        .put("settle_ms", ArticleReadingSession.GEOMETRY_SETTLE_MS).put("trial_settings", true)

    fun close() {
        if (closed) return
        halt("view_closed")
        closed = true
        body.removeTextChangedListener(textWatcher)
        policy.reset()
    }
}
