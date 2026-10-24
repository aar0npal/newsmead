package com.newsmead.gaze.mgazenet

import kotlin.math.abs

/** Crop geometry adapted from GazeFollower v1.0.2 MediaPipeFaceAlignment.
 * CC BY-NC-SA 4.0. Modifications: pure Kotlin, explicit invalid result, no camera.
 * Eye names are upstream tensor identities, NOT anatomical labels.
 */
object GazeGeometry {
    data class Landmark(val x: Double, val y: Double)
    data class Box(val x: Int, val y: Int, val width: Int, val height: Int)
    data class Crops(val face: Box, val left: Box, val right: Box, val leftOpenness: Double, val rightOpenness: Double) {
        fun rectangles(width: Int, height: Int): FloatArray = rectangles(width, height, FloatArray(12))

        internal fun rectangles(width: Int, height: Int, out: FloatArray): FloatArray {
            require(width > 0 && height > 0)
            require(out.size == 12)
            fun write(box: Box, offset: Int) {
                out[offset] = box.width.toFloat() / width
                out[offset + 1] = box.height.toFloat() / height
                out[offset + 2] = box.x.toFloat() / width
                out[offset + 3] = box.y.toFloat() / height
            }
            write(face, 0)
            write(left, 4)
            write(right, 8)
            return out
        }
    }

    fun crops(landmarks: List<Landmark>, width: Int, height: Int): Crops? {
        if (landmarks.size != LANDMARK_COUNT) return null
        val x = DoubleArray(LANDMARK_COUNT)
        val y = DoubleArray(LANDMARK_COUNT)
        landmarks.forEachIndexed { index, landmark ->
            x[index] = landmark.x
            y[index] = landmark.y
        }
        return crops(x, y, width, height)
    }

    fun crops(x: DoubleArray, y: DoubleArray, width: Int, height: Int): Crops? {
        if (width <= 0 || height <= 0 || x.size != LANDMARK_COUNT || y.size != LANDMARK_COUNT) return null
        // np.round uses ties-to-even; roundToInt is not equivalent.
        val px = DoubleArray(LANDMARK_COUNT)
        val py = DoubleArray(LANDMARK_COUNT)
        var minX = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        for (index in 0 until LANDMARK_COUNT) {
            val normalizedX = x[index]
            val normalizedY = y[index]
            if (!normalizedX.isFinite() || !normalizedY.isFinite()) return null
            val pixelX = Math.rint(normalizedX * width)
            val pixelY = Math.rint(normalizedY * height)
            if (pixelX !in -32768.0..32767.0 || pixelY !in -32768.0..32767.0) return null
            px[index] = pixelX
            py[index] = pixelY
            minX = minOf(minX, pixelX)
            maxX = maxOf(maxX, pixelX)
            minY = minOf(minY, pixelY)
            maxY = maxOf(maxY, pixelY)
        }
        minX = minX.coerceAtLeast(0.0)
        maxX = maxX.coerceAtMost(width.toDouble())
        minY = minY.coerceAtLeast(0.0)
        maxY = maxY.coerceAtMost(height.toDouble())
        if (MOUTH_INDICES.sumOf { py[it] } / MOUTH_INDICES.size >= height) return null
        val delta = (abs(maxX - minX) - abs(maxY - minY)) / 4.0
        val fx = (minX + delta).coerceAtLeast(0.0).toInt()
        val fy = (minY - delta).coerceAtLeast(0.0).toInt()
        val right = if (maxX - delta > width) width - 1 else (maxX - delta).toInt()
        val bottom = if (maxY + delta > height) height - 1 else (maxY + delta).toInt()
        val face = Box(fx, fy, right - fx, bottom - fy)
        val scale = abs(px[362] - px[133]) / 100.0
        fun eye(a: Int, b: Int): Box {
            val x0 = px[a] - 20 * scale
            val x1 = px[b] + 20 * scale
            val eyeHeight = abs(x1 - x0) * .75
            val cy = (py[a] + py[b]) / 2
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
                val a = indices[i]
                val b = indices[(i + 1) % indices.size]
                sum += px[a] * py[b] - py[a] * px[b]
            }
            return abs(sum) / 2
        }
        return Crops(face, leftEye, rightEye,
            area(LEFT_EYE_CONTOUR), area(RIGHT_EYE_CONTOUR))
    }

    /** NumPy half-open slice behavior of upstream clip_patch; no added padding. */
    fun clip(box: Box, width: Int, height: Int): Box? {
        if (box.x < 0 || box.y < 0 || box.width <= 0 || box.height <= 0 || box.x >= width || box.y >= height) return null
        return box.copy(width = minOf(box.width, width - box.x), height = minOf(box.height, height - box.y))
    }

    private const val LANDMARK_COUNT = 478
    private val MOUTH_INDICES = intArrayOf(61, 91, 14, 178, 402, 324, 95)
    private val LEFT_EYE_CONTOUR = intArrayOf(33,246,161,160,159,158,157,173,133,155,154,153,145,144,163,7,33)
    private val RIGHT_EYE_CONTOUR = intArrayOf(362,388,384,385,386,387,388,466,263,249,380,373,374,380,381,382,362)
}
