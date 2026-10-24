package com.newsmead.gaze

/** Raw iris geometry used before the unchanged calibration mapper and filters. */
enum class RawGazeFeatureMode(val logLabel: String) {
    EYELID_FRACTION_AVERAGE("eyelid_fraction_average"),
    EYE_LOCAL_WIDTH_AVERAGE_V1("eye_local_width_average_v1"),
}

/** Pure eye-local projection so the candidate geometry can be JVM-tested. */
internal object EyeLocalGazeGeometry {
    data class Point(val x: Float, val y: Float)
    data class Feature(val horizontal: Float, val vertical: Float)

    fun feature(iris: Point, cornerA: Point, cornerB: Point): Feature {
        val (left, right) = if (
            cornerA.x < cornerB.x || (cornerA.x == cornerB.x && cornerA.y <= cornerB.y)
        ) {
            cornerA to cornerB
        } else {
            cornerB to cornerA
        }
        val dx = right.x - left.x
        val dy = right.y - left.y
        val width = kotlin.math.hypot(dx, dy)
        if (width <= MIN_EYE_WIDTH_PX) return Feature(0.5f, 0.5f)

        val ux = dx / width
        val uy = dy / width
        // In image coordinates y increases downward. For the normally
        // left-to-right eye axis, (-uy, ux) is therefore the downward normal.
        val vx = -uy
        val vy = ux
        val irisFromLeftX = iris.x - left.x
        val irisFromLeftY = iris.y - left.y
        val centreX = (left.x + right.x) / 2f
        val centreY = (left.y + right.y) / 2f
        return Feature(
            horizontal = (irisFromLeftX * ux + irisFromLeftY * uy) / width,
            vertical = 0.5f + ((iris.x - centreX) * vx + (iris.y - centreY) * vy) / width,
        )
    }

    private const val MIN_EYE_WIDTH_PX = 1e-3f
}
