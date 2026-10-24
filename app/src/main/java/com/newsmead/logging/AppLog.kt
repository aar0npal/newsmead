package com.newsmead.logging

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.newsmead.BuildConfig

/**
 * Single entry point for diagnostic logging.
 *
 * Replaces the scattered `android.util.Log` calls, which used fourteen
 * different hand-written tag literals (one of them `ContentValues`, inherited
 * from a copy-pasted Firebase sample) and shipped verbatim into release
 * builds. Crashlytics was a declared dependency with no call sites at all, so
 * crashes arrived with no breadcrumbs and handled failures were never
 * reported.
 *
 * Boundary rule: this layer is developer-facing and leaves the device through
 * Crashlytics. Nothing participant-identifying may pass through it — no email
 * address, no uid, no article text, no search query. Research observations go
 * to [com.newsmead.research.ResearchSession], which stays on the device.
 */
object AppLog {

    /**
     * Debug-level detail. Compiled out of release builds by the
     * `-assumenosideeffects` rule in proguard-rules.pro, and gated here as
     * well so it costs nothing when the rule is not applied.
     */
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.i(tag, message)
    }

    /** Recoverable problem. Kept in release builds and left as a breadcrumb. */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        breadcrumb("W/$tag $message")
    }

    /**
     * Failure worth investigating. Also reported to Crashlytics as a
     * non-fatal, so a problem that the app handled gracefully still shows up
     * instead of vanishing into logcat on someone else's device.
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        breadcrumb("E/$tag $message")
        crashlytics?.recordException(throwable ?: RuntimeException("$tag: $message"))
    }

    /**
     * Adds a line to the rolling log attached to the next crash report.
     * Cheap; safe to call on navigation and other frequent transitions.
     */
    fun breadcrumb(message: String) {
        crashlytics?.log(message)
    }

    /** Names the screen the user is on, so a crash says where it happened. */
    fun setScreen(screen: String) {
        crashlytics?.setCustomKey(KEY_SCREEN, screen)
        breadcrumb("screen=$screen")
    }

    /**
     * @param state One of "signed_out", "unverified", "verified" — never the
     *              uid or the email address.
     */
    fun setAuthState(state: String) {
        crashlytics?.setCustomKey(KEY_AUTH_STATE, state)
    }

    /**
     * Ties a crash report to a research session rather than to a person. The
     * session id is a random UUID with no account behind it, which is also
     * what makes it usable while [com.newsmead.research.ResearchSession] is
     * running with no signed-in user at all.
     */
    fun setResearchSession(sessionId: String?) {
        crashlytics?.setCustomKey(KEY_RESEARCH_SESSION, sessionId ?: "none")
    }

    /**
     * Null when Crashlytics is unavailable, e.g. in a JVM unit test where no
     * FirebaseApp has been initialised. Logging must never be the reason a
     * test or a screen falls over.
     */
    private val crashlytics: FirebaseCrashlytics?
        get() = try {
            FirebaseCrashlytics.getInstance()
        } catch (exception: IllegalStateException) {
            null
        }

    private const val KEY_SCREEN = "screen"
    private const val KEY_AUTH_STATE = "auth_state"
    private const val KEY_RESEARCH_SESSION = "research_session"
}
