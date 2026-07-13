package com.newsmead.gaze

import android.content.Context

/** Central creation point for the phone-side raw gaze tracker. */
object LocalGazeSources {
    fun create(context: Context): LocalRawGazeSource = MediaPipeRawGazeSource(context)
}
