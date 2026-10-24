package com.newsmead.mgazenetbenchmark

import kotlin.math.abs

/** Crop geometry adapted from GazeFollower v1.0.2 MediaPipeFaceAlignment.
 * CC BY-NC-SA 4.0. Modifications: pure Kotlin, explicit invalid result, no camera.
 * Eye names are upstream tensor identities, NOT anatomical labels.
 */
object GazeGeometry {
    data class Landmark(val x: Double, val y: Double)
    data class Box(val x: Int, val y: Int, val width: Int, val height: Int)
    data class Crops(val face: Box, val left: Box, val right: Box, val leftOpenness: Double, val rightOpenness: Double) {
        fun rectangles(width: Int, height: Int): FloatArray {
            require(width > 0 && height > 0)
            return listOf(face, left, right).flatMap {
                listOf(it.width.toFloat() / width, it.height.toFloat() / height,
                    it.x.toFloat() / width, it.y.toFloat() / height)
            }.toFloatArray()
        }
    }

    fun crops(landmarks: List<Landmark>, width: Int, height: Int): Crops? {
        if (width <= 0 || height <= 0 || landmarks.size != 478 || landmarks.any { !it.x.isFinite() || !it.y.isFinite() }) return null
        // np.round uses ties-to-even; roundToInt is not equivalent.
        val p = landmarks.map { Landmark(Math.rint(it.x * width), Math.rint(it.y * height)) }
        if (p.any { it.x !in -32768.0..32767.0 || it.y !in -32768.0..32767.0 }) return null
        val minX = p.minOf { it.x }.coerceAtLeast(0.0)
        val maxX = p.maxOf { it.x }.coerceAtMost(width.toDouble())
        val minY = p.minOf { it.y }.coerceAtLeast(0.0)
        val maxY = p.maxOf { it.y }.coerceAtMost(height.toDouble())
        if (listOf(61, 91, 14, 178, 402, 324, 95).map { p[it].y }.average() >= height) return null
        val delta = (abs(maxX - minX) - abs(maxY - minY)) / 4.0
        val fx = (minX + delta).coerceAtLeast(0.0).toInt()
        val fy = (minY - delta).coerceAtLeast(0.0).toInt()
        val right = if (maxX - delta > width) width - 1 else (maxX - delta).toInt()
        val bottom = if (maxY + delta > height) height - 1 else (maxY + delta).toInt()
        val face = Box(fx, fy, right - fx, bottom - fy)
        val scale = abs(p[362].x - p[133].x) / 100.0
        fun eye(a: Int, b: Int): Box {
            val x0 = p[a].x - 20 * scale
            val x1 = p[b].x + 20 * scale
            val eyeHeight = abs(x1 - x0) * .75
            val cy = (p[a].y + p[b].y) / 2
            val y0 = (cy - eyeHeight * .6).toInt()
            val y1 = (cy + eyeHeight * .4).toInt()
            return Box(x0.toInt(), y0, x1.toInt() - x0.toInt(), y1 - y0)
        }
        val leftEye = eye(33, 133)
        val rightEye = eye(362, 263)
        fun inBounds(b: Box) = b.x > 0 && b.y > 0 && b.width > 0 && b.height > 0 && b.x + b.width < width && b.y + b.height < height
        if (!inBounds(leftEye) || !inBounds(rightEye) || clip(face, width, height) == null) return null
        fun area(indices: IntArray): Double {
            var sum = 0.0
            indices.indices.forEach { i ->
                val a = p[indices[i]]; val b = p[indices[(i + 1) % indices.size]]
                sum += a.x * b.y - a.y * b.x
            }
            return abs(sum) / 2
        }
        return Crops(face, leftEye, rightEye,
            area(intArrayOf(33,246,161,160,159,158,157,173,133,155,154,153,145,144,163,7,33)),
            area(intArrayOf(362,388,384,385,386,387,388,466,263,249,380,373,374,380,381,382,362)))
    }

    /** NumPy half-open slice behavior of upstream clip_patch; no added padding. */
    fun clip(box: Box, width: Int, height: Int): Box? {
        if (box.x < 0 || box.y < 0 || box.width <= 0 || box.height <= 0 || box.x >= width || box.y >= height) return null
        return box.copy(width = minOf(box.width, width - box.x), height = minOf(box.height, height - box.y))
    }
}
