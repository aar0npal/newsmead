package com.newsmead.gaze

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** Pure crop/projection geometry for the compact 468-landmark + iris research candidate. */
internal object CompactFaceIrisGeometry {
    data class FaceCrop(
        val center: HybridEyeGeometry.Point,
        val sidePx: Float,
        val rotationDegrees: Float,
    )

    /** Mirrors MediaPipe's detection-to-face-ROI intent: aligned, square, and 1.5x expanded. */
    fun faceCrop(
        rightEye: HybridEyeGeometry.Point,
        leftEye: HybridEyeGeometry.Point,
        boxLeft: Float,
        boxTop: Float,
        boxRight: Float,
        boxBottom: Float,
    ): FaceCrop? {
        val width = boxRight - boxLeft
        val height = boxBottom - boxTop
        if (
            !rightEye.isFinite() || !leftEye.isFinite() ||
            !width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f
        ) return null
        val dx = leftEye.x - rightEye.x
        val dy = leftEye.y - rightEye.y
        if (hypot(dx, dy) < MIN_INTER_EYE_PX) return null
        val (screenLeft, screenRight) = if (rightEye.x <= leftEye.x) {
            rightEye to leftEye
        } else {
            leftEye to rightEye
        }
        return FaceCrop(
            center = HybridEyeGeometry.Point(
                (boxLeft + boxRight) / 2f,
                (boxTop + boxBottom) / 2f,
            ),
            sidePx = max(width, height) * FACE_ROI_SCALE,
            rotationDegrees = Math.toDegrees(
                atan2(
                    (screenRight.y - screenLeft.y).toDouble(),
                    (screenRight.x - screenLeft.x).toDouble(),
                ),
            ).toFloat(),
        )
    }

    /**
     * Projects the compact model's 468 points out of its 192x192 face tensor and creates the
     * official 2.3x two-corner eye ROIs used by MediaPipe's iris pipeline.
     */
    fun eyeCrops(faceLandmarks: FloatArray, faceCrop: FaceCrop): HybridEyeGeometry.CropPair? {
        if (faceLandmarks.size < FACE_LANDMARK_FLOATS) return null
        val rightA = project(faceLandmarks, RIGHT_EYE_CORNER_A, faceCrop) ?: return null
        val rightB = project(faceLandmarks, RIGHT_EYE_CORNER_B, faceCrop) ?: return null
        val leftA = project(faceLandmarks, LEFT_EYE_CORNER_A, faceCrop) ?: return null
        val leftB = project(faceLandmarks, LEFT_EYE_CORNER_B, faceCrop) ?: return null
        val rightCrop = eyeCrop(rightA, rightB, flipHorizontally = true) ?: return null
        val leftCrop = eyeCrop(leftA, leftB, flipHorizontally = false) ?: return null
        return HybridEyeGeometry.CropPair(rightCrop, leftCrop)
    }

    internal fun project(
        faceLandmarks: FloatArray,
        index: Int,
        crop: FaceCrop,
    ): HybridEyeGeometry.Point? {
        val offset = index * LANDMARK_DIMS
        if (offset + 1 >= faceLandmarks.size) return null
        val modelX = faceLandmarks[offset]
        val modelY = faceLandmarks[offset + 1]
        if (!modelX.isFinite() || !modelY.isFinite() || crop.sidePx <= 0f) return null
        val localX = modelX - FACE_MODEL_EDGE / 2f
        val localY = modelY - FACE_MODEL_EDGE / 2f
        val radians = Math.toRadians(crop.rotationDegrees.toDouble())
        val scale = crop.sidePx / FACE_MODEL_EDGE
        return HybridEyeGeometry.Point(
            crop.center.x + (cos(radians) * localX - sin(radians) * localY).toFloat() * scale,
            crop.center.y + (sin(radians) * localX + cos(radians) * localY).toFloat() * scale,
        )
    }

    private fun eyeCrop(
        cornerA: HybridEyeGeometry.Point,
        cornerB: HybridEyeGeometry.Point,
        flipHorizontally: Boolean,
    ): HybridEyeGeometry.CropSpec? {
        val eyeWidth = hypot(cornerB.x - cornerA.x, cornerB.y - cornerA.y)
        if (!eyeWidth.isFinite() || eyeWidth < MIN_SOURCE_EYE_WIDTH_PX) return null
        val (screenLeft, screenRight) = if (cornerA.x <= cornerB.x) {
            cornerA to cornerB
        } else {
            cornerB to cornerA
        }
        val rotation = Math.toDegrees(
            atan2(
                (screenRight.y - screenLeft.y).toDouble(),
                (screenRight.x - screenLeft.x).toDouble(),
            ),
        ).toFloat()
        return HybridEyeGeometry.CropSpec(
            center = HybridEyeGeometry.Point(
                (cornerA.x + cornerB.x) / 2f,
                (cornerA.y + cornerB.y) / 2f,
            ),
            sidePx = eyeWidth * OFFICIAL_EYE_ROI_SCALE,
            rotationDegrees = rotation,
            flipHorizontally = flipHorizontally,
        )
    }

    private fun HybridEyeGeometry.Point.isFinite(): Boolean = x.isFinite() && y.isFinite()

    const val FACE_MODEL_EDGE = 192f
    const val FACE_LANDMARK_COUNT = 468
    const val LANDMARK_DIMS = 3
    const val FACE_LANDMARK_FLOATS = FACE_LANDMARK_COUNT * LANDMARK_DIMS

    // Canonical MediaPipe face-mesh eye-corner indices.
    private const val RIGHT_EYE_CORNER_A = 33
    private const val RIGHT_EYE_CORNER_B = 133
    private const val LEFT_EYE_CORNER_A = 362
    private const val LEFT_EYE_CORNER_B = 263
    private const val FACE_ROI_SCALE = 1.5f
    private const val OFFICIAL_EYE_ROI_SCALE = 2.3f
    private const val MIN_INTER_EYE_PX = 8f
    private const val MIN_SOURCE_EYE_WIDTH_PX = 2f
}
