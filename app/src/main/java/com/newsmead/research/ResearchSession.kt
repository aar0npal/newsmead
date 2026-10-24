package com.newsmead.research

import android.content.Context
import com.newsmead.logging.AppLog

/**
 * The one research log the app is currently writing, plus the vocabulary of
 * events that go into it.
 *
 * Deliberately keyed on a participant code rather than a Firebase uid. The
 * `bypass-login` branch turns the auth gate off for study use, at which point
 * `FirebaseHelper.getUid()` returns the literal string `"null"` for everyone —
 * so any uid-keyed record would collapse into a single indistinguishable
 * bucket. The participant code is assigned on paper and mapped to a person
 * offline, which also keeps the file free of account identifiers.
 *
 * Every method is a no-op when no session is running, so call sites never need
 * to check first.
 */
object ResearchSession {

    private const val TAG = "ResearchSession"

    @Volatile
    private var log: SessionLog? = null

    /** Null when no session is running. */
    val sessionId: String? get() = log?.sessionId

    val isRunning: Boolean get() = log != null

    /**
     * Opens a new session log. A session already running is closed first, so
     * a double start cannot leave an orphaned writer thread.
     *
     * @param participantCode Study code for this run, or null outside a study.
     * @param condition Study arm or configuration label, or null.
     */
    @Synchronized
    fun start(context: Context, participantCode: String? = null, condition: String? = null) {
        end("restarted")
        val started = SessionLog(
            context = context.applicationContext,
            kind = "reading",
            onClosed = { file, complete ->
                AppLog.i(TAG, "session closed file=${file.name} complete=$complete")
            },
        ) {
            put("participant_code", participantCode ?: "unassigned")
            put("condition", condition ?: "none")
        }
        log = started
        AppLog.setResearchSession(started.sessionId)
        AppLog.i(TAG, "session started ${started.sessionId}")
    }

    @Synchronized
    fun end(reason: String) {
        log?.finish(reason)
        log = null
        AppLog.setResearchSession(null)
    }

    /**
     * Last screen MainActivity's nav graph reported, used as the origin for
     * [articleOpen]. Tracked here rather than passed as a fragment argument
     * because every article launch goes through a safeargs activity
     * destination, which cannot carry an extra without editing every nav
     * action and call site.
     */
    @Volatile
    private var lastScreen: String? = null

    /** Screen the user was on before opening an article, or null. */
    fun lastScreen(): String? = lastScreen

    fun screenView(screen: String) {
        lastScreen = screen
        log?.event("screen_view") { put("screen", screen) }
    }

    /**
     * @param kind One of "sign_up", "log_in", "log_out", "verification_blocked".
     * @param errorCode Firebase error code on failure, else null. Never an
     *                  email address.
     */
    fun authEvent(kind: String, success: Boolean, errorCode: String? = null) {
        log?.event("auth_event") {
            put("kind", kind)
            put("success", success)
            put("error_code", errorCode ?: "none")
        }
    }

    fun onboardingComplete(categoryCount: Int) {
        log?.event("onboarding_complete") { put("category_count", categoryCount) }
    }

    /** @param from One of "feed", "search", "saved", "history", "recommended". */
    fun articleOpen(
        articleId: String,
        source: String,
        category: String,
        language: String,
        from: String,
    ) {
        log?.event("article_open") {
            put("article_id", articleId)
            put("source", source)
            put("category", category)
            put("language", language)
            put("from", from)
        }
    }

    fun articleClose(articleId: String, dwellMs: Long, scrollDepthPct: Int) {
        log?.event("article_close") {
            put("article_id", articleId)
            put("dwell_ms", dwellMs)
            put("scroll_depth_pct", scrollDepthPct)
        }
    }

    /**
     * Throttle at the call site; this is the highest-volume record type and
     * the queue is bounded.
     */
    fun articleScroll(articleId: String, scrollYPx: Int, viewportHeightPx: Int, contentHeightPx: Int,
        origin: String = "manual_or_layout") {
        log?.event("article_scroll") {
            put("article_id", articleId)
            put("scroll_y_px", scrollYPx)
            put("viewport_height_px", viewportHeightPx)
            put("content_height_px", contentHeightPx)
            put("scroll_origin", origin)
        }
    }

    fun readAloud(articleId: String, started: Boolean) {
        log?.event("read_aloud") {
            put("article_id", articleId)
            put("started", started)
        }
    }

    fun translate(articleId: String, toFilipino: Boolean, useGoogle: Boolean) {
        log?.event("translate") {
            put("article_id", articleId)
            put("to_filipino", toFilipino)
            put("use_google", useGoogle)
        }
    }

    fun saveToList(articleId: String, listId: String) {
        log?.event("save_to_list") {
            put("article_id", articleId)
            put("list_id", listId)
        }
    }

    fun removeFromList(articleId: String, listId: String) {
        log?.event("remove_from_list") {
            put("article_id", articleId)
            put("list_id", listId)
        }
    }

    /**
     * @param queryLength Character count only. The text the participant typed
     *                    stays on the device and out of the record, which is
     *                    what lets these files be shared without re-consent.
     */
    fun search(queryLength: Int, language: String, resultCount: Int) {
        log?.event("search") {
            put("query_length", queryLength)
            put("language", language)
            put("result_count", resultCount)
        }
    }
}
