package com.newsmead.gaze

import android.content.Context
import android.util.Log
import java.io.File

/** One calibration sample: known screen point (px) to measured local gaze feature. */
data class CalibrationSample(
    val screenX: Float,
    val screenY: Float,
    val gazeX: Float,
    val gazeY: Float,
)

/** Reads/writes the calibration pairs as a CSV in app-private storage. */
object CalibrationStore {

    private const val TAG = "GazeCalib"
    private const val HEADER = "screen_x,screen_y,gaze_x,gaze_y"
    private const val CSV_NAME = "calibration_16point.csv"
    private const val LEGACY_WIFI_CSV_NAME = "calibration_wifi.csv"

    fun save(context: Context, samples: List<CalibrationSample>) {
        val file = File(context.filesDir, CSV_NAME)
        val text = buildString {
            append(HEADER).append('\n')
            samples.forEach { append("${it.screenX},${it.screenY},${it.gazeX},${it.gazeY}\n") }
        }
        file.writeText(text)
        Log.i(TAG, "Wrote ${samples.size} calibration pairs to ${file.absolutePath}")
    }

    /** Returns the saved samples, or null if the file is missing/unparseable. */
    fun load(context: Context): List<CalibrationSample>? {
        val file = File(context.filesDir, CSV_NAME)
        if (!file.exists()) return null
        return read(file)
    }

    private fun read(file: File): List<CalibrationSample>? {
        if (!file.exists()) return null
        return try {
            file.readLines()
                .drop(1)
                .filter { it.isNotBlank() }
                .map { line ->
                    val (sx, sy, gx, gy) = line.split(",")
                    CalibrationSample(sx.toFloat(), sy.toFloat(), gx.toFloat(), gy.toFloat())
                }
                .takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read ${file.name}", e)
            null
        }
    }
}
