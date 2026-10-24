package com.newsmead.gaze

import java.security.MessageDigest

data class GazeScreenPoint(val x: Float, val y: Float)

/** Translation only: target layout, scale and mapper coefficients are not changed. */
data class GazeCoordinateFrame(val originX: Int, val originY: Int, val width: Int, val height: Int) {
    fun toScreen(localX: Float, localY: Float) = GazeScreenPoint(localX + originX, localY + originY)
    fun toLocal(screenX: Float, screenY: Float) = GazeScreenPoint(screenX - originX, screenY - originY)
}

/** A commit marker bound to the exact saved payload, including its feature mode.
 * A rollback/old writer cannot leave stale metadata that certifies a new legacy CSV.
 */
internal object GazeCoordinateContract {
    const val SPACE = "screen_px_v1"
    fun fingerprint(payload: String): String = MessageDigest.getInstance("SHA-256")
        .digest(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun encode(payload: String): String = "$SPACE\n${fingerprint(payload)}\n"

    fun matches(metadata: String?, payload: String): Boolean {
        val lines = metadata?.trim()?.lines() ?: return false
        return lines.size == 2 && lines[0] == SPACE && lines[1] == fingerprint(payload)
    }
}
