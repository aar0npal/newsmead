package com.newsmead.gaze

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.TextView
import com.newsmead.gaze.mgazenet.MgazeNetGazeProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.BreakIterator
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Raw, numeric reading observations. No index, attention label, or camera images. Main-thread API. */
class ReadingMeasurementLog(
    context: Context,
    private val textView: TextView,
    calibrationSha256: String,
    private val onClosed: (File, Boolean) -> Unit,
) {
    private val file = File(context.noBackupFilesDir, "reading_measurement_${UUID.randomUUID()}.jsonl")
    private val writer = file.bufferedWriter()
    private val queue = ArrayBlockingQueue<JSONObject>(2048)
    private val dropped = AtomicInteger()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closing = false
    @Volatile private var closeReason = "finished"
    private val body = textView.text.toString()
    private val bodySource = textView.text
    private val mapper = LineAoiMapper(textView)
    private val location = IntArray(2)
    private val visible = Rect()
    private var geometryKey = ""
    private var geometryId = 0
    private var geometryChangedMs = elapsedMs()
    private var samples = 0

    init {
        enqueue(JSONObject().apply {
            put("type", "session_start")
            put("schema_version", 1)
            put("session_id", file.nameWithoutExtension)
            put("started_utc_ms", System.currentTimeMillis())
            put("elapsed_ms", elapsedMs())
            put("device_model", Build.MODEL)
            val screen = textView.physicalDisplaySize()
            put("screen_width_px", screen.x)
            put("screen_height_px", screen.y)
            put("rotation", textView.display.rotation)
            put("calibration_sha256", calibrationSha256)
            put("text_sha256", MessageDigest.getInstance("SHA-256").digest(body.toByteArray())
                .joinToString("") { "%02x".format(it) })
            put("text_length", body.length)
            put("locale", textView.textLocale.toLanguageTag())
            put("density", textView.resources.displayMetrics.density)
            put("word_ranges", wordRanges(body, textView))
            put("mode", "measurement_with_diagnostic_dot")
            put("diagnostic_dot_visible", true)
            put("diagnostic_dot_stream", "display_coordinates_after_one_euro")
            put("scaffolds_enabled", false)
            put("coordinate_stream", "raw_calibrated_screen_pixels_before_one_euro")
            put("clock", "elapsedRealtimeNanos / 1e6")
            put("geometry_settle_ms", GEOMETRY_SETTLE_MS)
            put("camera_frames_retained", false)
        })
        checkGeometry()
        Thread({
            var ok = true
            try {
                writer.use { output ->
                    var lastFlush = elapsedMs()
                    while (!closing || queue.isNotEmpty()) {
                        queue.poll(100, TimeUnit.MILLISECONDS)?.let {
                            output.append(it.toString()).append('\n')
                        }
                        // Bound crash loss without per-frame disk flushes.
                        if (elapsedMs() - lastFlush >= 1000) {
                            output.flush()
                            lastFlush = elapsedMs()
                        }
                    }
                    output.append(JSONObject().apply {
                        put("type", "session_end")
                        put("elapsed_ms", elapsedMs())
                        put("reason", closeReason)
                        put("dropped_records", dropped.get())
                        put("sample_records", samples)
                        put("complete", dropped.get() == 0)
                    }.toString()).append('\n')
                }
            } catch (_: Exception) {
                ok = false
                closing = true
            }
            val complete = ok && dropped.get() == 0
            main.post { onClosed(file, complete) }
        }, "reading-measurement-writer").start()
    }

    /** Called on pre-draw as well as observations so scrolling ends fixation continuity. */
    fun checkGeometry() {
        if (closing) return
        if (textView.text !== bodySource && textView.text.toString() != body) {
            finish("text_changed"); return
        }
        val layout = textView.layout ?: return
        if (!textView.getVisibleRectOnScreen(visible, location)) visible.setEmpty()
        textView.getLocationOnScreen(location)
        val key = "${location[0]},${location[1]},${textView.width},${textView.height}," +
            "${textView.textSize},${visible.flattenToString()},${layout.lineCount},${textView.currentTextColor}"
        if (key == geometryKey) return
        geometryKey = key
        geometryChangedMs = elapsedMs()
        geometryId++
        enqueue(JSONObject().apply {
            put("type", "geometry")
            put("elapsed_ms", geometryChangedMs)
            put("geometry_id", geometryId)
            put("text_origin_x", location[0] + textView.totalPaddingLeft)
            put("text_origin_y", location[1] + textView.totalPaddingTop)
            put("text_size_px", textView.textSize)
            put("text_color", textView.currentTextColor)
            put("line_height_px", textView.lineHeight)
            put("visible_rect_screen", JSONArray(listOf(visible.left, visible.top, visible.right, visible.bottom)))
            put("lines", JSONArray().apply {
                for (line in 0 until layout.lineCount) put(JSONArray(listOf(
                    layout.getLineStart(line), layout.getLineEnd(line),
                    layout.getLineLeft(line), layout.getLineRight(line),
                    layout.getLineTop(line), layout.getLineBottom(line),
                )))
            })
        })
    }

    fun record(observation: MgazeNetGazeProvider.Observation) {
        if (closing) return
        checkGeometry()
        if (closing) return
        val x = observation.rawX
        val y = observation.rawY
        val capture = observation.captureMs
        val settled = capture != null && capture >= geometryChangedMs + GEOMETRY_SETTLE_MS
        val validCoordinate = observation.reason == "coordinate" &&
            x != null && y != null && x.isFinite() && y.isFinite()
        val target = if (validCoordinate && settled) mapper.targetAt(x!!, y!!) else TextTarget.INVALID
        // The legacy line mapper accepts the horizontal margins. Measurement does not.
        val inside = validCoordinate && textView.getVisibleRectOnScreen(visible, location) &&
            visible.contains(x!!.toInt(), y!!.toInt()) && target.isValid
        enqueue(JSONObject().apply {
            put("type", "sample")
            put("capture_ms", capture ?: JSONObject.NULL)
            put("delivery_ms", observation.outputMs)
            put("delivery_id", observation.deliveryId)
            put("reason", observation.reason)
            put("blink_filter_applied", observation.blinkFilterApplied)
            put("left_eye_aspect_ratio", observation.leftEyeAspect?.takeIf { it.isFinite() } ?: JSONObject.NULL)
            put("right_eye_aspect_ratio", observation.rightEyeAspect?.takeIf { it.isFinite() } ?: JSONObject.NULL)
            put("raw_x", finite(x))
            put("raw_y", finite(y))
            put("display_x", finite(observation.x))
            put("display_y", finite(observation.y))
            put("geometry_id", geometryId)
            put("measurement_valid", inside && settled)
            put("measurement_exclusion", when {
                !validCoordinate -> observation.reason
                !settled -> "geometry_unsettled"
                !inside -> "outside_visible_text"
                else -> JSONObject.NULL
            })
            put("line", if (inside) target.lineIndex else -1)
            put("word_start", if (inside) target.wordStart else -1)
            put("word_end", if (inside) target.wordEnd else -1)
        })
        samples++
    }

    fun finish(reason: String) {
        if (closing) return
        closeReason = reason
        closing = true
    }

    private fun enqueue(record: JSONObject) {
        if (!closing && !queue.offer(record)) dropped.incrementAndGet()
    }

    companion object {
        const val GEOMETRY_SETTLE_MS = 150.0 // Engineering exclusion after movement, not a reading threshold.
        private fun elapsedMs() = SystemClock.elapsedRealtimeNanos() / 1e6
        private fun finite(value: Float?): Any = value?.takeIf { it.isFinite() } ?: JSONObject.NULL
        private fun wordRanges(text: String, view: TextView): JSONArray {
            val iterator = BreakIterator.getWordInstance(view.textLocale).apply { setText(text) }
            val ranges = JSONArray()
            var start = iterator.first()
            var end = iterator.next()
            while (end != BreakIterator.DONE) {
                if ((start until end).any { text[it].isLetterOrDigit() }) {
                    ranges.put(JSONArray(listOf(start, end)))
                }
                start = end
                end = iterator.next()
            }
            return ranges
        }
    }
}
