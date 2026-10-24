package com.newsmead.gaze.reading

import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import com.newsmead.gaze.TextTarget
import com.newsmead.gaze.mgazenet.MgazeNetGazeProvider
import java.util.Locale

/**
 * Main-thread, in-memory article adapter. No camera images, files or aid decisions.
 * The declared coordinate stream is assigned against current rendered article geometry.
 */
class ArticleReadingSession(
    private val textView: TextView,
    private val onGeometry: (generation: Int, geometry: ReadingGeometry?) -> Unit = { _, _ -> },
    private val onInput: (generation: Int, sample: ReadingSample) -> Unit = { _, _ -> },
    private val onBoundary: (reason: String) -> Unit = {},
    val coordinateStream: ReadingCoordinateStream = ReadingCoordinateStream.ARTICLE_SMOOTHED,
    /** Capture-aligned body-local metadata; selected screen coordinates remain untouched. */
    val motionCompensatedAids: Boolean = true,
) {
    var generation: Int = 0
        private set
    private var body = textView.text.toString()
    private var bodySource = textView.text
    private var bodyDirty = false
    private var locale: Locale = textView.textLocale
    private var words = ReadingWordMapper(body, textView)
    private val geometryReader = ReadingTextGeometryReader()
    private var latestCaptureMs: Double? = null
    private var geometry: ReadingTextGeometry? = null
    private var snapshotId: String? = null
    private var geometrySequence = 0
    private var geometryChangedMs = Double.POSITIVE_INFINITY
    private val geometryTimeline = ReadingGeometryTimeline()
    private var controlledAutoScroll = false
    private var layoutSignature: List<Any?>? = null
    private var active = true
    private var closed = false
    private val consultation = ConsultationCaptureGate()
    private var callbackDepth = 0
    private val pendingBoundaries = ArrayDeque<String>()
    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            bodyDirty = true
            geometryReader.invalidate()
        }
        override fun afterTextChanged(s: Editable?) = Unit
    }

    init {
        require(!motionCompensatedAids || coordinateStream == ReadingCoordinateStream.ARTICLE_SMOOTHED)
        textView.addTextChangedListener(textWatcher)
    }

    /** Also called before drawing, so a scroll breaks continuity without waiting for gaze. */
    fun checkGeometry() {
        checkMainThread()
        if (closed || !active) return
        val now = elapsedMs()
        val currentBody = if (bodyDirty || textView.text !== bodySource) textView.text.toString() else body
        bodySource = textView.text
        bodyDirty = false
        if (currentBody != body || textView.textLocale != locale) {
            onGeometry(generation, null)
            emitBoundary("text_changed")
            generation++
            body = currentBody
            locale = textView.textLocale
            words = ReadingWordMapper(body, textView)
            geometryReader.invalidate()
            geometry = null
            snapshotId = null
            geometrySequence = 0
            geometryChangedMs = Double.POSITIVE_INFINITY
            geometryTimeline.clear()
        }
        val current = geometryReader.read(textView)
        if (current == null) {
            onGeometry(generation, null)
            if (geometry != null) emitBoundary("layout_unavailable")
            geometry = null
            snapshotId = null
            geometryChangedMs = Double.POSITIVE_INFINITY
            geometryTimeline.clear()
            return
        }
        val signature = listOf(textView.width, textView.height, textView.textSize, textView.textScaleX,
            textView.letterSpacing, textView.typeface, textView.lineSpacingExtra, textView.lineSpacingMultiplier,
            textView.totalPaddingLeft, textView.totalPaddingTop, textView.totalPaddingRight, textView.totalPaddingBottom)
        val sameLayout = !motionCompensatedAids || signature == layoutSignature
        layoutSignature = signature
        if (current == geometry && sameLayout) {
            if (motionCompensatedAids) snapshotId?.let { geometryTimeline.record(now, current.snapshot(it)) }
            return
        }
        val previous = geometry
        val translation = motionCompensatedAids && controlledAutoScroll && sameLayout && previous != null &&
            current.originX == previous.originX && current.lines == previous.lines &&
            current.lineHeightPx == previous.lineHeightPx && current.direction == previous.direction &&
            current.visible.left == previous.visible.left && current.visible.right == previous.visible.right
        val readingScroll = !controlledAutoScroll && previous != null && sameLayout &&
            current.originX == previous.originX && current.lines == previous.lines &&
            current.lineHeightPx == previous.lineHeightPx && current.direction == previous.direction &&
            current.visible.left == previous.visible.left && current.visible.right == previous.visible.right &&
            (current.originY != previous.originY || current.visible.top != previous.visible.top ||
                current.visible.bottom != previous.visible.bottom)
        geometry = current
        if (translation && snapshotId != null) {
            val display = current.snapshot(snapshotId!!)
            geometryTimeline.record(now, display)
            onGeometry(generation, display)
            return
        }
        snapshotId = "geometry:${++geometrySequence}"
        geometryChangedMs = now
        val snapshot = current.snapshot(snapshotId!!)
        geometryTimeline.clear()
        if (motionCompensatedAids) geometryTimeline.record(now, snapshot)
        onGeometry(generation, snapshot)
        emitBoundary(if (readingScroll) "reading_scroll" else "geometry_change")
    }

    /** Only the explicit navigation controller may mark translations as controlled. */
    fun autoScrollMotion(moving: Boolean, reason: String? = null) {
        checkMainThread()
        if (closed || !active || !motionCompensatedAids) return
        if (moving) {
            checkGeometry()
            controlledAutoScroll = true
        } else {
            // An external move/reflow must never be smuggled through the final self-scroll snapshot.
            if (reason in listOf("external_scroll", "reading_layout_changed")) {
                controlledAutoScroll = false
                onGeometry(generation, null)
                emitBoundary("uncontrolled_scroll")
                geometry = null
                snapshotId = null
                geometryTimeline.clear()
                geometryChangedMs = Double.POSITIVE_INFINITY
            }
            checkGeometry()
            controlledAutoScroll = false
        }
    }

    fun record(observation: MgazeNetGazeProvider.Observation) {
        checkMainThread()
        if (closed || !active) return
        checkGeometry()
        val rawX = observation.rawX?.toDouble()
        val rawY = observation.rawY?.toDouble()
        val displayX = observation.x?.toDouble()
        val displayY = observation.y?.toDouble()
        val x = if (coordinateStream == ReadingCoordinateStream.CALIBRATED_RAW) rawX else displayX
        val y = if (coordinateStream == ReadingCoordinateStream.CALIBRATED_RAW) rawY else displayY
        val capture = observation.captureMs
        val current = geometry
        val valid = observation.reason == "coordinate" && x != null && y != null &&
            x.isFinite() && y.isFinite() && capture != null && capture.isFinite()
        val settled = capture != null && capture >= geometryChangedMs + GEOMETRY_SETTLE_MS
        val historical = if (motionCompensatedAids && capture != null)
            geometryTimeline.at(capture, MAX_CAPTURE_GEOMETRY_AGE_MS) else null
        val historicalGeometry = historical?.geometry?.let {
            ReadingTextGeometry(it.textOriginX, it.textOriginY, it.visibleRectScreen,
                it.lineHeightPx, it.lines, it.textDirection ?: ReadingTextDirection.UNKNOWN)
        }
        // A stationary body can use its current geometry after the capture-based
        // settle guard. Controlled translation still needs causal history.
        val captureGeometry = if (motionCompensatedAids && controlledAutoScroll)
            historicalGeometry else historicalGeometry ?: current
        val freshMotionInput = !motionCompensatedAids || !controlledAutoScroll || (capture != null &&
            observation.outputMs >= capture && observation.outputMs <= elapsedMs() &&
            elapsedMs() - capture <= MAX_MOTION_INPUT_AGE_MS)
        val consultationReason = consultation.exclusion(capture)
        val target = if (valid && settled && captureGeometry != null && freshMotionInput && consultationReason == null)
            words.targetAt(textView, captureGeometry, x!!, y!!) else TextTarget.INVALID
        // Keep the capture floor across geometry generations and invalid observations.
        // Consultation frames retain their dedicated exclusion and do not advance it.
        val monotonic = capture != null && (latestCaptureMs == null || capture > latestCaptureMs!!)
        if (consultationReason == null && capture?.isFinite() == true)
            latestCaptureMs = maxOf(latestCaptureMs ?: capture, capture)
        val eligible = valid && monotonic && settled && freshMotionInput && target.isValid
        val sample = ReadingSample(
            capture, observation.outputMs, rawX, rawY,
            target.lineIndex.takeIf { target.isValid },
            target.wordStart.takeIf { target.hasWord }, target.wordEnd.takeIf { target.hasWord },
            snapshotId, eligible,
            when {
                consultationReason != null -> consultationReason
                !valid -> if (observation.reason != "coordinate" ||
                    coordinateStream == ReadingCoordinateStream.CALIBRATED_RAW)
                    observation.reason else "coordinate_unavailable"
                current == null -> "layout_unavailable"
                !settled -> "geometry_unsettled"
                motionCompensatedAids && controlledAutoScroll && historical == null -> "scroll_geometry_unavailable"
                !freshMotionInput -> "scroll_input_stale"
                !target.isValid -> "outside_visible_text"
                !monotonic -> "non_monotonic_capture"
                else -> null
            },
            displayX, displayY, coordinateStream,
            motionCompensation = if (motionCompensatedAids && historical != null && valid)
                ReadingMotionCompensation(historical.atMs, historical.geometry.snapshotId,
                    historical.geometry.textOriginX, historical.geometry.textOriginY,
                    x!! - historical.geometry.textOriginX, y!! - historical.geometry.textOriginY) else null,
        )
        dispatchCallbacks {
            onInput(generation, sample)
            if (consultationReason == null && !sample.eligible)
                emitBoundary(if (sample.exclusion == "non_monotonic_capture") "non_monotonic_capture" else "invalid_sample")
        }
    }

    fun boundary(reason: String) {
        checkMainThread()
        if (!closed && active) emitBoundary(reason)
    }

    fun setConsultation(value: Boolean, atMs: Double, reason: String) {
        checkMainThread()
        if (closed) return
        consultation.change(value, atMs)
        emitBoundary("definition_${if (value) "opened" else "closed"}:$reason")
    }

    private fun emitBoundary(reason: String) {
        pendingBoundaries.addLast(reason)
        if (callbackDepth == 0) dispatchCallbacks { Unit }
    }

    /** Input callbacks may open help; deliver resulting boundaries after input returns. */
    private fun <T> dispatchCallbacks(block: () -> T): T {
        callbackDepth++
        try { return block() }
        finally {
            callbackDepth--
            if (callbackDepth == 0) {
                callbackDepth++
                try {
                    while (pendingBoundaries.isNotEmpty()) onBoundary(pendingBoundaries.removeFirst())
                } finally { callbackDepth-- }
            }
        }
    }

    fun pause(reason: String) {
        boundary(reason)
        controlledAutoScroll = false
        geometryTimeline.clear()
        active = false
    }

    fun resume() {
        checkMainThread()
        if (closed) return
        active = true
        checkGeometry()
    }

    fun close(reason: String) {
        checkMainThread()
        if (closed) return
        boundary(reason)
        active = false
        closed = true
        textView.removeTextChangedListener(textWatcher)
    }

    private fun checkMainThread() = check(Looper.myLooper() == Looper.getMainLooper())

    companion object {
        // Geometry eligibility guard, not a fixation or difficulty threshold.
        const val GEOMETRY_SETTLE_MS = 150.0
        const val MAX_CAPTURE_GEOMETRY_AGE_MS = 100.0
        const val MAX_MOTION_INPUT_AGE_MS = 250.0
        private fun elapsedMs() = SystemClock.elapsedRealtimeNanos() / 1e6
    }
}
