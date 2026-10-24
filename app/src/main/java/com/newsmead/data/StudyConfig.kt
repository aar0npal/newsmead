package com.newsmead.data

/**
 * Central switches for running NewsMead as a thesis-study research instrument
 * (no real users, no backend). Flip these back to false to restore the original
 * production behaviour.
 *
 * See docs/progress-notes.md for the full context and the study_articles.json schema.
 */
object StudyConfig {

    /**
     * Bypass the Firebase login gate in SplashActivity and treat every session
     * as already authenticated, so launch goes straight to the home screen.
     */
    const val BYPASS_LOGIN = true

    /**
     * Serve article content from a bundled local asset instead of NewsData.io.
     * When true, DataHelper.loadArticleData()
     * reads from [ARTICLES_ASSET] and the app runs fully offline.
     */
    const val OFFLINE_MODE = false

    /** Name of the bundled article dataset in app/src/main/assets/. */
    const val ARTICLES_ASSET = "study_articles.json"

    /**
     * Optional normal-article lock. Live reading geometry is rebuilt after a
     * reader resizes text; dedicated validation/pilot views set their own size.
     */
    const val LOCK_ARTICLE_FONT_SIZE = false

    /**
     * Controlled-view/reference body size. Applied in **dp** (not sp) so it ignores the OS
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

    /** Exploratory settings; freeze with the participant baseline. */
    val MAHALANOBIS_CONFIG = com.newsmead.gaze.mahalanobis.MahalanobisConfig()
    /** Presentation policy applied after Mahalanobis confirmation. */
    val MAHALANOBIS_SCAFFOLD_CONFIG = com.newsmead.gaze.AdaptiveScaffoldConfig()
    // Reading-only operational freshness limit on the A02 pilot. Capture times
    // remain unchanged; calibration/validation retain the provider's 500 ms default.
    const val READING_MAX_OUTPUT_AGE_MS = 650.0
    const val GAZE_DEBUG_VISUALS = true

    /**
     * Blink filtering thresholds for the local MediaPipe gaze source. Openness is
     * the eye-aspect ratio: lower values mean the eyelids are closer together.
     * Hysteresis avoids flicker: enter blink below close, leave above open.
     */
    const val GAZE_BLINK_CLOSE_THRESHOLD = 0.04f
    const val GAZE_BLINK_OPEN_THRESHOLD = 0.055f

    /**
     * Development-only, non-authoritative BlazeFace + 64x64 iris comparison.
     * The current 478-point source remains the only producer of gaze coordinates.
     */
    const val GAZE_HYBRID_SHADOW_ENABLED = false
}
