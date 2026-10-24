package com.newsmead.gaze

/**
 * One same-camera-frame comparison between the authoritative Face Landmarker feature and the
 * development-only lightweight eye path. Neither value in this record can control gaze output.
 */
data class HybridEyeShadowSample(
    val captureTimestampNs: Long,
    val resultElapsedNs: Long,
    val referenceHorizontal: Float,
    val referenceVertical: Float,
    val candidateHorizontal: Float,
    val candidateVertical: Float,
    val rightEyeOpenness: Float,
    val leftEyeOpenness: Float,
    val faceDetectorRan: Boolean,
    val detectorReextractApplied: Boolean,
    val rightCropCenterShiftPx: Float,
    val leftCropCenterShiftPx: Float,
    val rightCropSideRatio: Float,
    val leftCropSideRatio: Float,
    val totalMs: Float,
    val detectorMs: Float,
    val eyeMs: Float,
    val reextractMs: Float,
)
