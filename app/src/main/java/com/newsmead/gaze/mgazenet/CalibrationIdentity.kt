package com.newsmead.gaze.mgazenet

import com.newsmead.gaze.GazeCoordinateFrame
import java.security.MessageDigest

/** Immutable acquisition/label contract. Changes require a fresh calibration. */
data class CalibrationIdentity(
    val device: String, val screenWidth: Int, val screenHeight: Int,
    val rotation: Int, val viewport: GazeCoordinateFrame, val acquisition: String = "unbound",
) {
    init {
        require(device.matches(Regex("[a-f0-9]{64}")))
        require(screenWidth > 0 && screenHeight > 0 && rotation in 0..3)
        require(viewport.width > 0 && viewport.height > 0 && viewport.originX >= 0 && viewport.originY >= 0)
        require(viewport.originX.toLong() + viewport.width <= screenWidth &&
            viewport.originY.toLong() + viewport.height <= screenHeight)
        require(acquisition == "unbound" || acquisition.matches(Regex("[1-9][0-9]*x[1-9][0-9]*:(0|90|180|270):(GPU|CPU_initialization_fallback)")))
    }
    val targets get() = TARGET_FRACTIONS.map { (x, y) ->
        viewport.toScreen(x * viewport.width, y * viewport.height)
    }
    fun canonical(): String = listOf(VERSION, MODEL, LOCALIZER, PIPELINE, SVR, COORDINATES,
        device, screenWidth, screenHeight, rotation, viewport.originX, viewport.originY,
        viewport.width, viewport.height,
        targets.joinToString(";") { "${it.x},${it.y}" },acquisition).joinToString("\n")

    companion object {
        const val VERSION = "newsmead_mgazenet_calibration_v2"
        const val MODEL = "2f96b95275fe6d7b79e98df3237ebb96e15ef5522c96968f08167da7e1954a96"
        const val LOCALIZER = "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff"
        const val PIPELINE = "Tasks0.10.29_VIDEO_1face_.1_.1_.5_GPU_initCPU;640x480_requested_RGBA_upright_unmirrored;GF1.0.2_crops_RGB_linear_uint8_NCHW_rightflip;MNN3.6.1_CPU4_normal;GF1.0.2_13point_9x5order_phone10to90_cue1200_onsetsettle1500_45rows_post500_area>10;no_filter_no_correction_v2"
        const val SVR = "OpenCV4.11.0_EPS_SVR_RBF_C1_gamma.005_P.001_MAX_ITER10000_epsilon.0001"
        const val COORDINATES = "screen_px_v1;labels=physical_px/physical_display;full_calibration_viewport"
        const val FIT_TARGET_COUNT = 13
        const val SAMPLES_PER_TARGET = 45
        const val TARGET_CUE_MS = 1200L
        const val TARGET_INSET_FRACTION = .10f

        /** GazeFollower 1.0.2 order/topology, inset so NewsMead's large phone target is fully visible. */
        val UPSTREAM_GRID_INDICES = listOf(1, 5, 9, 12, 16, 19, 27, 30, 34, 37, 41, 45, 23)
        val TARGET_FRACTIONS = UPSTREAM_GRID_INDICES.map { index ->
            val column = (index - 1) % 9
            val row = (index - 1) / 9
            (10 + column * 10) / 100f to (10 + row * 20) / 100f
        }.also {
            require(it.size == FIT_TARGET_COUNT && it.distinct().size == FIT_TARGET_COUNT &&
                it.all { (x, y) -> x in TARGET_INSET_FRACTION..(1f - TARGET_INSET_FRACTION) &&
                    y in TARGET_INSET_FRACTION..(1f - TARGET_INSET_FRACTION) })
        }
        fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        fun parse(text: String): CalibrationIdentity {
            val p = text.split('\n')
            require(p.size == 16)
            val value = CalibrationIdentity(p[6],p[7].toInt(),p[8].toInt(),p[9].toInt(),
                GazeCoordinateFrame(p[10].toInt(),p[11].toInt(),p[12].toInt(),p[13].toInt()),p[15])
            require(value.canonical() == text) { "Incompatible MGazeNet identity or target geometry" }
            return value
        }
    }
}
