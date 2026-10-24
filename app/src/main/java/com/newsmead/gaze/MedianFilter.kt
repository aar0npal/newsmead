package com.newsmead.gaze

/**
 * Sliding-window median for one scalar signal. Rejects the occasional spike
 * frame (bad landmark detection, blink) that an ill-conditioned mapping would
 * otherwise amplify into a large dot jump.
 *
 * Ported verbatim (except package) from gaze-thesis-prototype.
 */
class MedianFilter(private val window: Int = 7) {

    private val buffer = ArrayDeque<Float>()

    fun filter(value: Float): Float {
        buffer.addLast(value)
        if (buffer.size > window) buffer.removeFirst()
        return buffer.sorted()[buffer.size / 2]
    }

    fun reset() = buffer.clear()
}
