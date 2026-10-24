package com.newsmead.research

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.newsmead.BuildConfig
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Append-only JSONL record of one run of the app.
 *
 * The mechanics are carried over from the gaze branches' session logs
 * (`ReadingMeasurementLog`, `ReadingValidationSessionLog`), which had already
 * been hardened against the failure modes that matter for participant data:
 *
 *  - one JSON object per line, so a truncated file still parses up to the cut;
 *  - a dedicated writer thread behind a bounded queue, so recording never
 *    blocks the UI thread and a burst drops records instead of stalling;
 *  - dropped records counted and reported in `session_end`, so a file is
 *    explicitly marked incomplete rather than quietly missing rows;
 *  - a periodic flush, so a crash costs about a second of data rather than
 *    the whole buffer;
 *  - monotonic `elapsed_ms` from `elapsedRealtimeNanos()` on every record,
 *    with wall-clock time recorded once at the start, so a clock change
 *    mid-session cannot reorder events.
 *
 * Files live in `noBackupFilesDir`: participant observations must not travel
 * off the device in a cloud backup, and the manifest has `allowBackup="true"`.
 *
 * This is one of several session-log families that write into
 * `noBackupFilesDir` across this repo's branches — the gaze branches'
 * `ReadingMeasurementLog` (com.newsmead.gaze) is the other one sharing this
 * exact directory. Every file this class writes carries [FILE_PREFIX], a
 * string no other log class on any branch uses, so [SessionLogExport] can
 * list and share only what this system wrote without ever having to know
 * what else might be sitting in that directory. Do not drop or loosen the
 * prefix check in [SessionLogExport.sessionFiles] — that check, not the
 * directory choice, is what keeps an export of ordinary app-usage logs from
 * sweeping up someone else's gaze-tracking recordings if this branch is ever
 * merged alongside one that has them.
 *
 * @param kind Identifies the session within this app's own naming, e.g.
 *             "reading". Distinct from [FILE_PREFIX], which identifies the
 *             file as belonging to this logging system at all.
 * @param metadata Extra fields for the `session_start` record.
 */
class SessionLog(
    context: Context,
    kind: String,
    private val onClosed: (File, Boolean) -> Unit = { _, _ -> },
    metadata: JSONObject.() -> Unit = {},
) {

    val sessionId: String = "$FILE_PREFIX${kind}_${stamp()}_${UUID.randomUUID().toString().take(8)}"
    val file: File = File(context.noBackupFilesDir, "$sessionId.jsonl")

    private val writer = file.bufferedWriter()
    private val queue = ArrayBlockingQueue<JSONObject>(QUEUE_CAPACITY)
    private val dropped = AtomicInteger()
    private val written = AtomicInteger()
    private val startedElapsedNs = SystemClock.elapsedRealtimeNanos()

    @Volatile private var closing = false
    @Volatile private var closeReason = "finished"

    init {
        event(TYPE_SESSION_START) {
            put("schema_version", SCHEMA_VERSION)
            put("session_id", sessionId)
            put("started_utc_ms", System.currentTimeMillis())
            put("started_iso", isoTimestamp(System.currentTimeMillis()))
            put("app_version_name", BuildConfig.VERSION_NAME)
            put("app_debug_build", BuildConfig.DEBUG)
            put("device_model", Build.MODEL)
            put("device_manufacturer", Build.MANUFACTURER)
            put("android_release", Build.VERSION.RELEASE)
            put("android_sdk_int", Build.VERSION.SDK_INT)
            val metrics = context.resources.displayMetrics
            put("screen_width_px", metrics.widthPixels)
            put("screen_height_px", metrics.heightPixels)
            put("density", metrics.density)
            put("locale", Locale.getDefault().toLanguageTag())
            put("clock", "elapsedRealtimeNanos / 1e6")
            // Stated explicitly so a reader of the file never has to infer it.
            put("contains_account_identifiers", false)
            metadata()
        }

        Thread({ drain() }, "$kind-session-writer").start()
    }

    /**
     * Records one event. Safe to call from any thread; returns immediately.
     *
     * @param type Record type, e.g. "article_open".
     */
    fun event(type: String, block: JSONObject.() -> Unit = {}) {
        // An event arriving after finish() is a lost observation, so it is
        // counted rather than silently discarded. Returning quietly here used
        // to let a file report complete=true while an event had vanished.
        if (closing) {
            dropped.incrementAndGet()
            return
        }
        val record = JSONObject().apply {
            put("type", type)
            put("elapsed_ms", elapsedMs())
            block()
        }
        if (!queue.offer(record)) dropped.incrementAndGet()
    }

    /**
     * Stops recording and lets the writer thread finish the file.
     *
     * @param reason Why the session ended, e.g. "finished", "process_death".
     */
    fun finish(reason: String) {
        if (closing) return
        closeReason = reason
        closing = true
    }

    private fun drain() {
        var ok = true
        try {
            writer.use { output ->
                var lastFlush = elapsedMs()
                while (!closing || queue.isNotEmpty()) {
                    queue.poll(POLL_MS, TimeUnit.MILLISECONDS)?.let { record ->
                        output.append(record.toString()).append('\n')
                        written.incrementAndGet()
                    }
                    // Bound crash loss without paying a disk flush per record.
                    if (elapsedMs() - lastFlush >= FLUSH_INTERVAL_MS) {
                        output.flush()
                        lastFlush = elapsedMs()
                    }
                }
                // Final non-blocking sweep. The loop above can exit on a
                // poll timeout that races an offer made just before finish(),
                // which lost the closing article_close record in testing.
                while (true) {
                    val remaining = queue.poll() ?: break
                    output.append(remaining.toString())
                    output.newLine()
                    written.incrementAndGet()
                }

                output.append(
                    JSONObject().apply {
                        put("type", TYPE_SESSION_END)
                        put("elapsed_ms", elapsedMs())
                        put("reason", closeReason)
                        put("record_count", written.get())
                        put("dropped_records", dropped.get())
                        put("complete", dropped.get() == 0)
                    }.toString()
                ).append('\n')
            }
        } catch (exception: Exception) {
            // Deliberately not routed through AppLog: a failing research
            // writer must not start feeding Crashlytics on its way down.
            Log.e(TAG, "Failed writing ${file.name}", exception)
            ok = false
            closing = true
        }
        onClosed(file, ok && dropped.get() == 0)
    }

    private fun elapsedMs(): Double =
        (SystemClock.elapsedRealtimeNanos() - startedElapsedNs).coerceAtLeast(0L) / 1e6

    companion object {
        const val SCHEMA_VERSION = 1
        const val TYPE_SESSION_START = "session_start"
        const val TYPE_SESSION_END = "session_end"

        /**
         * Namespaces every file this class writes so [SessionLogExport] can
         * tell this system's own files apart from any other log family that
         * happens to write into the same `noBackupFilesDir` — see the class
         * doc above. Chosen to not start with "reading", "gaze", or
         * "calibration", which is what the other known writers in this repo
         * use.
         */
        const val FILE_PREFIX = "appusage_"

        private const val TAG = "SessionLog"
        private const val QUEUE_CAPACITY = 2048
        private const val POLL_MS = 100L
        private const val FLUSH_INTERVAL_MS = 1000.0

        private fun stamp() =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        private fun isoTimestamp(timestampMs: Long) =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date(timestampMs))
    }
}
