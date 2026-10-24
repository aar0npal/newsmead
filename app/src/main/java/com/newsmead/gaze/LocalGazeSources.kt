package com.newsmead.gaze

import android.content.Context

/** Central creation point for the phone-side raw gaze tracker. */
object LocalGazeSources {
    // One-line rollback boundary for the prospective geometry intervention.
    val ACTIVE_FEATURE_MODE = RawGazeFeatureMode.EYE_LOCAL_WIDTH_AVERAGE_V1

    fun create(context: Context): LocalRawGazeSource = MediaPipeRawGazeSource(
        context = context,
        featureMode = ACTIVE_FEATURE_MODE,
    )
}
