package com.newsmead.gaze.mgazenet

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

/**
 * Low-dimensional, image-free posture guard for calibration capture.
 *
 * Values describe only face/eye crop geometry. No image, landmark array, or MGazeNet feature is
 * retained. The practice target establishes the reference used for every fitted and post-fit row.
 */
object MgazeNetPostureGate {
    data class Sample(
        val faceCenterX: Double,
        val faceCenterY: Double,
        val faceWidth: Double,
        val faceHeight: Double,
        val eyeMidpointInFaceX: Double,
        val eyeMidpointInFaceY: Double,
        val eyeSeparationInFaceWidths: Double,
        val eyeLineRollDegrees: Double,
    ) {
        val valid get() = faceCenterX.isFinite() && faceCenterY.isFinite() &&
            faceWidth.isFinite() && faceHeight.isFinite() && eyeMidpointInFaceX.isFinite() &&
            eyeMidpointInFaceY.isFinite() && eyeSeparationInFaceWidths.isFinite() &&
            eyeLineRollDegrees.isFinite() &&
            faceCenterX in 0.0..1.0 && faceCenterY in 0.0..1.0 &&
            faceWidth in 0.0..1.0 && faceHeight in 0.0..1.0 &&
            faceWidth > 0.0 && faceHeight > 0.0 && eyeSeparationInFaceWidths > 0.0
    }

    data class Reference(val median: Sample, val sourceCount: Int, val inlierCount: Int)

    fun from(crops: GazeGeometry.Crops, frameWidth: Int, frameHeight: Int): Sample? {
        if (frameWidth <= 0 || frameHeight <= 0 || crops.face.width <= 0 || crops.face.height <= 0) return null
        val leftX = crops.left.x + crops.left.width/2.0
        val leftY = crops.left.y + crops.left.height/2.0
        val rightX = crops.right.x + crops.right.width/2.0
        val rightY = crops.right.y + crops.right.height/2.0
        val midpointX = (leftX+rightX)/2.0
        val midpointY = (leftY+rightY)/2.0
        return Sample(
            faceCenterX = (crops.face.x+crops.face.width/2.0)/frameWidth,
            faceCenterY = (crops.face.y+crops.face.height/2.0)/frameHeight,
            faceWidth = crops.face.width.toDouble()/frameWidth,
            faceHeight = crops.face.height.toDouble()/frameHeight,
            eyeMidpointInFaceX = (midpointX-crops.face.x)/crops.face.width,
            eyeMidpointInFaceY = (midpointY-crops.face.y)/crops.face.height,
            eyeSeparationInFaceWidths = hypot(rightX-leftX,rightY-leftY)/crops.face.width,
            eyeLineRollDegrees = Math.toDegrees(atan2(rightY-leftY,rightX-leftX)),
        ).takeIf(Sample::valid)
    }

    fun reference(samples: List<Sample>): Reference? {
        if (samples.size < REFERENCE_SAMPLE_COUNT || samples.any { !it.valid }) return null
        val center = Sample(
            faceCenterX = median(samples.map(Sample::faceCenterX)),
            faceCenterY = median(samples.map(Sample::faceCenterY)),
            faceWidth = median(samples.map(Sample::faceWidth)),
            faceHeight = median(samples.map(Sample::faceHeight)),
            eyeMidpointInFaceX = median(samples.map(Sample::eyeMidpointInFaceX)),
            eyeMidpointInFaceY = median(samples.map(Sample::eyeMidpointInFaceY)),
            eyeSeparationInFaceWidths = median(samples.map(Sample::eyeSeparationInFaceWidths)),
            eyeLineRollDegrees = circularMedianDegrees(samples.map(Sample::eyeLineRollDegrees)),
        )
        val inliers = samples.count { accepts(center,it) }
        val required = max(MIN_REFERENCE_INLIERS,ceil(samples.size*MIN_REFERENCE_INLIER_FRACTION).toInt())
        return Reference(center,samples.size,inliers).takeIf { inliers >= required }
    }

    fun accepts(reference: Reference, sample: Sample?): Boolean =
        sample?.let { accepts(reference.median,it) } == true

    private fun accepts(reference: Sample, sample: Sample): Boolean {
        if (!reference.valid || !sample.valid) return false
        val widthRatio = sample.faceWidth/reference.faceWidth
        val heightRatio = sample.faceHeight/reference.faceHeight
        val eyeRatio = sample.eyeSeparationInFaceWidths/reference.eyeSeparationInFaceWidths
        return abs(sample.faceCenterX-reference.faceCenterX) <= reference.faceWidth*MAX_CENTER_SHIFT_FACE_FRACTION &&
            abs(sample.faceCenterY-reference.faceCenterY) <= reference.faceHeight*MAX_CENTER_SHIFT_FACE_FRACTION &&
            widthRatio in MIN_SCALE_RATIO..MAX_SCALE_RATIO &&
            heightRatio in MIN_SCALE_RATIO..MAX_SCALE_RATIO &&
            abs(sample.eyeMidpointInFaceX-reference.eyeMidpointInFaceX) <= MAX_EYE_MIDPOINT_SHIFT &&
            abs(sample.eyeMidpointInFaceY-reference.eyeMidpointInFaceY) <= MAX_EYE_MIDPOINT_SHIFT &&
            eyeRatio in MIN_EYE_SEPARATION_RATIO..MAX_EYE_SEPARATION_RATIO &&
            angleDistance(reference.eyeLineRollDegrees,sample.eyeLineRollDegrees) <= MAX_ROLL_CHANGE_DEGREES
    }

    private fun median(values: List<Double>) = values.sorted().let {
        (it[(it.size-1)/2]+it[it.size/2])/2.0
    }

    private fun circularMedianDegrees(values: List<Double>): Double = values.minBy { candidate ->
        values.sumOf { angleDistance(candidate,it) }
    }

    private fun angleDistance(a: Double, b: Double): Double {
        val difference = abs(a-b)%360.0
        return minOf(difference,360.0-difference)
    }

    const val REFERENCE_SAMPLE_COUNT = CalibrationIdentity.SAMPLES_PER_TARGET
    const val MIN_REFERENCE_INLIERS = 36
    const val MIN_REFERENCE_INLIER_FRACTION = .80
    const val MAX_CENTER_SHIFT_FACE_FRACTION = .20
    const val MIN_SCALE_RATIO = .80
    const val MAX_SCALE_RATIO = 1.25
    const val MAX_EYE_MIDPOINT_SHIFT = .15
    const val MIN_EYE_SEPARATION_RATIO = .80
    const val MAX_EYE_SEPARATION_RATIO = 1.25
    const val MAX_ROLL_CHANGE_DEGREES = 12.0
}
