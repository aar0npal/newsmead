package com.newsmead.gaze

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** Pure geometry used by the non-authoritative hybrid-eye shadow backend. */
internal object HybridEyeGeometry {
    data class Point(val x: Float, val y: Float)

    data class CropSpec(
        val center: Point,
        val sidePx: Float,
        val rotationDegrees: Float,
        val flipHorizontally: Boolean,
    )

    data class EyeSample(
        val feature: EyeLocalGazeGeometry.Feature,
        val openness: Float,
        val cornerA: Point,
        val cornerB: Point,
    )

    data class CropPair(
        val rightEye: CropSpec,
        val leftEye: CropSpec,
    )

    data class CropRefinement(
        val crop: CropSpec,
        val centerShiftPx: Float,
        val sideRatio: Float,
    )

    /**
     * Builds provisional square eye crops from BlazeFace's anatomical right/left eye centres.
     * Eye width is approximated from both inter-eye distance and face-box width; the iris model's
     * own contour output supplies the actual corners used by the gaze feature.
     */
    fun cropPair(
        rightEye: Point,
        leftEye: Point,
        faceWidthPx: Float,
    ): CropPair? {
        if (!rightEye.isFinite() || !leftEye.isFinite() || !faceWidthPx.isFinite()) return null
        val dx = leftEye.x - rightEye.x
        val dy = leftEye.y - rightEye.y
        val interEye = hypot(dx, dy)
        if (interEye < MIN_INTER_EYE_PX || faceWidthPx <= 0f) return null

        val (screenLeft, screenRight) = if (rightEye.x <= leftEye.x) {
            rightEye to leftEye
        } else {
            leftEye to rightEye
        }
        val rotation = Math.toDegrees(
            atan2(
                (screenRight.y - screenLeft.y).toDouble(),
                (screenRight.x - screenLeft.x).toDouble(),
            ),
        ).toFloat()
        val side = max(interEye * INTER_EYE_TO_CROP, faceWidthPx * FACE_WIDTH_TO_CROP)
            .coerceIn(interEye * MIN_CROP_TO_INTER_EYE, interEye * MAX_CROP_TO_INTER_EYE)

        return CropPair(
            rightEye = CropSpec(rightEye, side, rotation, flipHorizontally = true),
            leftEye = CropSpec(leftEye, side, rotation, flipHorizontally = false),
        )
    }

    /** Decodes the 71-point eye contour plus five-point iris output into the active raw geometry. */
    fun decodeEye(
        eyeLandmarks: FloatArray,
        irisLandmarks: FloatArray,
        wasFlipped: Boolean,
    ): EyeSample? {
        if (eyeLandmarks.size < EYE_LANDMARK_FLOATS || irisLandmarks.size < IRIS_LANDMARK_FLOATS) {
            return null
        }
        val cornerA = point(eyeLandmarks, CORNER_A_INDEX, wasFlipped)
        val cornerB = point(eyeLandmarks, CORNER_B_INDEX, wasFlipped)
        val lidTop = point(eyeLandmarks, LID_TOP_INDEX, wasFlipped)
        val lidBottom = point(eyeLandmarks, LID_BOTTOM_INDEX, wasFlipped)
        val iris = averagePoints(irisLandmarks, IRIS_LANDMARK_COUNT, wasFlipped)
        if (
            !cornerA.isFinite() || !cornerB.isFinite() || !lidTop.isFinite() ||
            !lidBottom.isFinite() || !iris.isFinite()
        ) {
            return null
        }
        val eyeWidth = hypot(cornerB.x - cornerA.x, cornerB.y - cornerA.y)
        if (eyeWidth <= MIN_EYE_WIDTH) return null
        return EyeSample(
            feature = EyeLocalGazeGeometry.feature(
                iris = EyeLocalGazeGeometry.Point(iris.x, iris.y),
                cornerA = EyeLocalGazeGeometry.Point(cornerA.x, cornerA.y),
                cornerB = EyeLocalGazeGeometry.Point(cornerB.x, cornerB.y),
            ),
            openness = hypot(lidBottom.x - lidTop.x, lidBottom.y - lidTop.y) / eyeWidth,
            cornerA = cornerA,
            cornerB = cornerB,
        )
    }

    /**
     * Derives one tight eye crop from an iris result obtained inside the current detector crop.
     *
     * The decoded corners are already returned in the unflipped crop coordinate system. Their
     * midpoint is projected back into the source frame using the detector crop's scale and roll.
     * MediaPipe's iris graph makes this two-corner ROI square and expands it by 2.3. The result is
     * always anchored to this detector crop; callers must not feed cached-frame outputs back here.
     */
    fun detectorAnchoredRefinement(
        detectorCrop: CropSpec,
        eye: EyeSample,
    ): CropRefinement? {
        if (!eye.cornerA.isFinite() || !eye.cornerB.isFinite()) return null
        if (!detectorCrop.center.isFinite() || !detectorCrop.sidePx.isFinite()) return null
        if (!detectorCrop.rotationDegrees.isFinite() || detectorCrop.sidePx <= 0f) return null

        val midpointX = (eye.cornerA.x + eye.cornerB.x) / 2f
        val midpointY = (eye.cornerA.y + eye.cornerB.y) / 2f
        val modelOffsetX = midpointX - MODEL_SIZE / 2f
        val modelOffsetY = midpointY - MODEL_SIZE / 2f
        val radians = Math.toRadians(detectorCrop.rotationDegrees.toDouble())
        val sourceScale = detectorCrop.sidePx / MODEL_SIZE
        val centerOffsetX = (
            cos(radians) * modelOffsetX - sin(radians) * modelOffsetY
            ).toFloat() * sourceScale
        val centerOffsetY = (
            sin(radians) * modelOffsetX + cos(radians) * modelOffsetY
            ).toFloat() * sourceScale
        val centerShift = hypot(centerOffsetX, centerOffsetY)
        if (centerShift > detectorCrop.sidePx * MAX_CENTER_SHIFT_RATIO) return null

        val modelEyeWidth = hypot(
            eye.cornerB.x - eye.cornerA.x,
            eye.cornerB.y - eye.cornerA.y,
        )
        val eyeWidth = modelEyeWidth * detectorCrop.sidePx / MODEL_SIZE
        if (eyeWidth < MIN_SOURCE_EYE_WIDTH_PX) return null
        val measuredSide = (eyeWidth * OFFICIAL_EYE_ROI_SCALE)
            .coerceIn(
                detectorCrop.sidePx * MIN_SIDE_RATIO,
                detectorCrop.sidePx * MAX_SIDE_RATIO,
            )
        return CropRefinement(
            crop = detectorCrop.copy(
                center = Point(
                    detectorCrop.center.x + centerOffsetX,
                    detectorCrop.center.y + centerOffsetY,
                ),
                sidePx = measuredSide,
            ),
            centerShiftPx = centerShift,
            sideRatio = measuredSide / detectorCrop.sidePx,
        )
    }

    private fun point(values: FloatArray, index: Int, flip: Boolean): Point {
        val offset = index * LANDMARK_DIMS
        val x = values[offset]
        return Point(if (flip) MODEL_SIZE - x else x, values[offset + 1])
    }

    private fun averagePoints(values: FloatArray, count: Int, flip: Boolean): Point {
        var x = 0f
        var y = 0f
        for (index in 0 until count) {
            val point = point(values, index, flip)
            x += point.x
            y += point.y
        }
        return Point(x / count, y / count)
    }

    private fun Point.isFinite(): Boolean = x.isFinite() && y.isFinite()

    const val MODEL_SIZE = 64f
    const val EYE_LANDMARK_COUNT = 71
    const val IRIS_LANDMARK_COUNT = 5
    const val LANDMARK_DIMS = 3
    const val EYE_LANDMARK_FLOATS = EYE_LANDMARK_COUNT * LANDMARK_DIMS
    const val IRIS_LANDMARK_FLOATS = IRIS_LANDMARK_COUNT * LANDMARK_DIMS

    // The first 16 model outputs are the visible eye contour. Their correspondence is
    // 0=corner A, 4=lower lid, 8=corner B, 12=upper lid.
    private const val CORNER_A_INDEX = 0
    private const val LID_BOTTOM_INDEX = 4
    private const val CORNER_B_INDEX = 8
    private const val LID_TOP_INDEX = 12
    private const val MIN_INTER_EYE_PX = 8f
    private const val MIN_EYE_WIDTH = 1e-3f
    private const val MIN_SOURCE_EYE_WIDTH_PX = 2f
    private const val INTER_EYE_TO_CROP = 1.05f
    private const val FACE_WIDTH_TO_CROP = 0.40f
    private const val MIN_CROP_TO_INTER_EYE = 0.90f
    private const val MAX_CROP_TO_INTER_EYE = 1.40f
    private const val OFFICIAL_EYE_ROI_SCALE = 2.3f
    private const val MAX_CENTER_SHIFT_RATIO = 0.35f
    private const val MIN_SIDE_RATIO = 0.70f
    private const val MAX_SIDE_RATIO = 1.30f
}
