package com.newsmead.logging

import android.os.Bundle
import androidx.core.os.bundleOf
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

/**
 * Firebase Analytics events, declared in one place.
 *
 * `firebase-analytics-ktx` was already a dependency with no `logEvent` call
 * anywhere in the app, so only Google's automatically collected events were
 * ever recorded. Everything the product actually cares about — which articles
 * get opened, whether sign-up completes, whether Read Aloud gets used — was
 * invisible.
 *
 * Same boundary rule as [AppLog]: these events leave the device, so they carry
 * identifiers and counts, never free text. Search logs the query *length*, not
 * the query. Auth logs an error code, not an email address.
 */
object Analytics {

    private val analytics: FirebaseAnalytics?
        get() = try {
            Firebase.analytics
        } catch (exception: IllegalStateException) {
            null
        }

    fun screenView(screenName: String, screenClass: String) {
        log(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            bundleOf(
                FirebaseAnalytics.Param.SCREEN_NAME to screenName,
                FirebaseAnalytics.Param.SCREEN_CLASS to screenClass,
            ),
        )
    }

    /** @param errorCode Firebase error code, or null on success. */
    fun signUp(success: Boolean, errorCode: String? = null) {
        log(
            FirebaseAnalytics.Event.SIGN_UP,
            bundleOf(
                FirebaseAnalytics.Param.METHOD to METHOD_PASSWORD,
                PARAM_SUCCESS to success.toString(),
                PARAM_ERROR_CODE to (errorCode ?: NONE),
            ),
        )
    }

    /** @param errorCode Firebase error code, or null on success. */
    fun login(success: Boolean, errorCode: String? = null) {
        log(
            FirebaseAnalytics.Event.LOGIN,
            bundleOf(
                FirebaseAnalytics.Param.METHOD to METHOD_PASSWORD,
                PARAM_SUCCESS to success.toString(),
                PARAM_ERROR_CODE to (errorCode ?: NONE),
            ),
        )
    }

    fun logout() = log(EVENT_LOGOUT, Bundle.EMPTY)

    /** Fired when a log-in attempt is refused for an unverified address. */
    fun verificationBlocked() = log(EVENT_VERIFICATION_BLOCKED, Bundle.EMPTY)

    fun onboardingComplete(categoryCount: Int) {
        log(EVENT_ONBOARDING_COMPLETE, bundleOf(PARAM_CATEGORY_COUNT to categoryCount.toLong()))
    }

    /** @param from One of "feed", "search", "saved", "history", "recommended". */
    fun articleOpen(
        articleId: String,
        source: String,
        category: String,
        language: String,
        from: String,
    ) {
        log(
            FirebaseAnalytics.Event.SELECT_CONTENT,
            bundleOf(
                FirebaseAnalytics.Param.CONTENT_TYPE to CONTENT_TYPE_ARTICLE,
                FirebaseAnalytics.Param.ITEM_ID to articleId.take(MAX_VALUE_LENGTH),
                PARAM_SOURCE to source.take(MAX_VALUE_LENGTH),
                PARAM_CATEGORY to category.take(MAX_VALUE_LENGTH),
                PARAM_LANGUAGE to language.take(MAX_VALUE_LENGTH),
                PARAM_FROM to from,
            ),
        )
    }

    fun articleClose(articleId: String, dwellMs: Long, scrollDepthPct: Int) {
        log(
            EVENT_ARTICLE_CLOSE,
            bundleOf(
                FirebaseAnalytics.Param.ITEM_ID to articleId.take(MAX_VALUE_LENGTH),
                PARAM_DWELL_MS to dwellMs,
                PARAM_SCROLL_DEPTH_PCT to scrollDepthPct.toLong(),
            ),
        )
    }

    /**
     * @param queryLength Character count only. The query itself is free text
     *                    the user typed and does not leave the device.
     */
    fun search(queryLength: Int, language: String, resultCount: Int) {
        log(
            FirebaseAnalytics.Event.SEARCH,
            bundleOf(
                PARAM_QUERY_LENGTH to queryLength.toLong(),
                PARAM_LANGUAGE to language.take(MAX_VALUE_LENGTH),
                PARAM_RESULT_COUNT to resultCount.toLong(),
            ),
        )
    }

    fun saveToList(articleId: String, listId: String) {
        log(
            EVENT_SAVE_TO_LIST,
            bundleOf(
                FirebaseAnalytics.Param.ITEM_ID to articleId.take(MAX_VALUE_LENGTH),
                PARAM_LIST_ID to listId.take(MAX_VALUE_LENGTH),
            ),
        )
    }

    fun removeFromList(articleId: String, listId: String) {
        log(
            EVENT_REMOVE_FROM_LIST,
            bundleOf(
                FirebaseAnalytics.Param.ITEM_ID to articleId.take(MAX_VALUE_LENGTH),
                PARAM_LIST_ID to listId.take(MAX_VALUE_LENGTH),
            ),
        )
    }

    fun readAloud(started: Boolean) {
        log(EVENT_READ_ALOUD, bundleOf(PARAM_STARTED to started.toString()))
    }

    fun translate(toFilipino: Boolean, useGoogle: Boolean) {
        log(
            EVENT_TRANSLATE,
            bundleOf(
                PARAM_TO_FILIPINO to toFilipino.toString(),
                PARAM_USE_GOOGLE to useGoogle.toString(),
            ),
        )
    }

    private fun log(event: String, params: Bundle) {
        analytics?.logEvent(event, params)
    }

    // Firebase caps string parameter values at 100 characters and silently
    // drops anything longer, so truncate rather than lose the event.
    private const val MAX_VALUE_LENGTH = 100

    private const val NONE = "none"
    private const val METHOD_PASSWORD = "password"
    private const val CONTENT_TYPE_ARTICLE = "article"

    private const val EVENT_LOGOUT = "logout"
    private const val EVENT_VERIFICATION_BLOCKED = "verification_blocked"
    private const val EVENT_ONBOARDING_COMPLETE = "onboarding_complete"
    private const val EVENT_ARTICLE_CLOSE = "article_close"
    private const val EVENT_SAVE_TO_LIST = "save_to_list"
    private const val EVENT_REMOVE_FROM_LIST = "remove_from_list"
    private const val EVENT_READ_ALOUD = "read_aloud"
    private const val EVENT_TRANSLATE = "translate"

    private const val PARAM_SUCCESS = "success"
    private const val PARAM_ERROR_CODE = "error_code"
    private const val PARAM_CATEGORY_COUNT = "category_count"
    private const val PARAM_SOURCE = "article_source"
    private const val PARAM_CATEGORY = "article_category"
    private const val PARAM_LANGUAGE = "language"
    private const val PARAM_FROM = "opened_from"
    private const val PARAM_DWELL_MS = "dwell_ms"
    private const val PARAM_SCROLL_DEPTH_PCT = "scroll_depth_pct"
    private const val PARAM_QUERY_LENGTH = "query_length"
    private const val PARAM_RESULT_COUNT = "result_count"
    private const val PARAM_LIST_ID = "list_id"
    private const val PARAM_STARTED = "started"
    private const val PARAM_TO_FILIPINO = "to_filipino"
    private const val PARAM_USE_GOOGLE = "use_google"
}
