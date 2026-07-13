package com.newsmead.data

/**
 * Central switches for running NewsMead as a thesis-study research instrument
 * (no real users, no backend). Flip these back to false to restore the original
 * production behaviour.
 *
 * See PROGRESS_NOTES.md for the full context and the study_articles.json schema.
 */
object StudyConfig {

    /**
     * Bypass the Firebase login gate in SplashActivity and treat every session
     * as already authenticated, so launch goes straight to the home screen.
     */
    const val BYPASS_LOGIN = true

    /**
     * Serve article content from a bundled local asset instead of the (now
     * decommissioned) newsmead-api. When true, DataHelper.loadArticleData()
     * reads from [ARTICLES_ASSET] and the app runs fully offline.
     */
    const val OFFLINE_MODE = true

    /** Name of the bundled article dataset in app/src/main/assets/. */
    const val ARTICLES_ASSET = "study_articles.json"

    /**
     * Lock the article body font size for the study and remove the in-article
     * text-size (+/-) controls. Line bounding boxes for gaze AOI mapping (Stage 4)
     * are only stable if the font size cannot change during a session.
     */
    const val LOCK_ARTICLE_FONT_SIZE = true

    /**
     * Fixed article body size. Applied in **dp** (not sp) so it ignores the OS
     * accessibility font-scale setting - the on-screen pixel size, and therefore
     * the line boxes, stay fixed on a given device. 22dp == 22sp at 100% system
     * font. (App's original default was 20sp; ts_body_large.)
     */
    const val ARTICLE_FONT_SIZE_DP = 22f

    // ---- Stage 4: gaze AOI mapping (article reading screen)  ----

    /**
     * Attach the debug gaze layer to the article screen: the line-AOI mapper
     * (gaze-y -> text line index) plus the gaze-dot overlay.
     */
    const val GAZE_ENABLED = true

    /**
     * Validation mode: a finger touch on the article substitutes for the gaze
     * coordinate, so line mapping can be verified on-device without the tracker
     * (build-spec "validate the plumbing before trusting the gaze"). Set this
     * to false only after the local MediaPipe/iris raw source is wired.
     */
    const val GAZE_TOUCH_VALIDATION = false

    /**
     * Blink filtering thresholds for the local MediaPipe gaze source. Openness is
     * the eye-aspect ratio: lower values mean the eyelids are closer together.
     * Hysteresis avoids flicker: enter blink below close, leave above open.
     */
    const val GAZE_BLINK_CLOSE_THRESHOLD = 0.04f
    const val GAZE_BLINK_OPEN_THRESHOLD = 0.055f
}
