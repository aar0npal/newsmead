package com.newsmead.gaze

import android.graphics.Point
import android.graphics.Rect
import android.view.View
import org.json.JSONObject

fun View.gazeCoordinateFrame(): GazeCoordinateFrame {
    val location = IntArray(2)
    getLocationOnScreen(location)
    return GazeCoordinateFrame(location[0], location[1], width, height)
}

/** getGlobalVisibleRect is ROOT-local, not screen-local on an inset window. */
fun View.getVisibleRectOnScreen(out: Rect, rootLocation: IntArray = IntArray(2)): Boolean {
    if (!getGlobalVisibleRect(out)) return false
    rootView.getLocationOnScreen(rootLocation)
    out.offset(rootLocation[0], rootLocation[1])
    return true
}

@Suppress("DEPRECATION")
fun View.physicalDisplaySize(size: Point): Point = size.also {
    requireNotNull(display) { "View must be attached before recording display coordinates" }.getRealSize(size)
}

fun View.physicalDisplaySize(): Point = physicalDisplaySize(Point())

fun GazeCoordinateFrame.toJson(): JSONObject = JSONObject().apply {
    put("origin_x_screen_px", originX)
    put("origin_y_screen_px", originY)
    put("width_px", width)
    put("height_px", height)
}
