package com.newsmead.gaze

import java.security.MessageDigest

data class ComparisonBuildIdentity(
    val estimatorId: String,
    val baseCommit: String,
    val baseApkSha256: String,
) {
    init {
        require(estimatorId in setOf("current", "mgazenet"))
        require(ComparisonProtocol.isGitCommit(baseCommit))
        require(ComparisonProtocol.isSha256(baseApkSha256))
    }
}

data class ComparisonSlot(
    val id: String,
    val deviceModel: String,
    val estimatorId: String,
    val orderVariant: String,
)

data class ComparisonLaunchSpec(
    val protocolId: String,
    val slot: ComparisonSlot,
    val orderVariant: String,
)

/** Pure, fixed comparison contract. It contains no preference or fallback path. */
object ComparisonProtocol {
    const val PROTOCOL_ID = "newsmead_current_vs_mgazenet_reading_v1"
    const val MANIFEST_ASSET =
        "comparison/newsmead-current-vs-mgazenet-reading-v1.json"
    const val MANIFEST_SHA256 =
        "41a3fbccd4df38c2bb13d4695a052fe15d3ef78423b19a4c1737d3de6944f185"
    const val COORDINATE_SPACE = "screen_px_v1"
    const val MONOTONIC_CLOCK = "elapsedRealtimeNanos"
    const val MIN_COORDINATES_PER_SCORED_TARGET = 10

    const val EXTRA_PROTOCOL_ID = "comparison_protocol_id"
    const val EXTRA_SLOT_ID = "comparison_slot_id"
    const val EXTRA_ORDER_VARIANT = "comparison_order_variant"

    private const val CURRENT_COMMIT =
        "ec635fd01905544c8251c37b6891105ebbdee923"
    private const val CURRENT_APK =
        "3c4019e9ae0390e1f9af05f262ba50ab4c71332ac9258aa4c9cc172e67c78e01"
    private const val MGAZENET_COMMIT =
        "bea2dacbfa8d95f6519ccd4fad1cb0dfd6028b22"
    private const val MGAZENET_APK =
        "131f8d96f9bdc3f5a5c16855aa6347e119fd07e96609cba9f6792b161c682fd9"

    val slots = listOf(
        ComparisonSlot("a56_current_A", "SM-A566B", "current", "A"),
        ComparisonSlot("a56_mgazenet_A", "SM-A566B", "mgazenet", "A"),
        ComparisonSlot("g991b_mgazenet_B", "SM-G991B", "mgazenet", "B"),
        ComparisonSlot("g991b_current_B", "SM-G991B", "current", "B"),
    )

    /** All three values are required and independently checked against the slot. */
    fun validateLaunch(
        protocolId: String?,
        slotId: String?,
        orderVariant: String?,
        build: ComparisonBuildIdentity,
        deviceModel: String,
    ): ComparisonLaunchSpec {
        require(protocolId == PROTOCOL_ID) { "Missing or unknown comparison protocol ID." }
        require(!slotId.isNullOrBlank()) { "Missing comparison slot ID." }
        require(orderVariant == "A" || orderVariant == "B") {
            "Missing or unknown comparison order variant."
        }
        validateBuild(build)
        val slot = slots.singleOrNull { it.id == slotId }
            ?: throw IllegalArgumentException("Unknown comparison slot ID.")
        require(slot.estimatorId == build.estimatorId) {
            "The comparison slot does not belong to this estimator build."
        }
        require(slot.orderVariant == orderVariant) {
            "The explicit order variant does not match the frozen slot."
        }
        require(slot.deviceModel == deviceModel) {
            "The comparison slot does not belong to this device model."
        }
        return ComparisonLaunchSpec(protocolId, slot, orderVariant)
    }

    fun validateBuild(build: ComparisonBuildIdentity) {
        val expected = when (build.estimatorId) {
            "current" -> CURRENT_COMMIT to CURRENT_APK
            "mgazenet" -> MGAZENET_COMMIT to MGAZENET_APK
            else -> throw IllegalArgumentException("Unknown estimator build.")
        }
        require(build.baseCommit == expected.first && build.baseApkSha256 == expected.second) {
            "The estimator baseline identity does not match the frozen protocol."
        }
    }

    fun passageSha256(passage: String): String = sha256(passage.toByteArray(Charsets.UTF_8))

    /**
     * Binds the rendered text topology and the exact ordered target plan. Float
     * bit patterns avoid locale- or formatter-dependent hashes.
     */
    fun readingLayoutSha256(
        passage: String,
        lines: List<ReadingValidationLine>,
        wordTargets: List<ReadingValidationCheckpoint>,
        lineTargets: List<ReadingValidationCheckpoint>,
        lineHeightPx: Float,
        textSizePx: Float,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
    ): String {
        val canonical = buildString {
            append("protocol=").append(PROTOCOL_ID).append('\n')
            append("version=").append(ReadingValidationProtocol.VERSION).append('\n')
            append("passage=").append(passageSha256(passage)).append('\n')
            append("line_height_bits=").append(lineHeightPx.toRawBits()).append('\n')
            append("text_size_bits=").append(textSizePx.toRawBits()).append('\n')
            append("viewport=").append(viewportWidthPx).append('x').append(viewportHeightPx).append('\n')
            lines.forEach { line ->
                append("line|").append(line.lineIndex)
                line.words.forEach { word ->
                    append('|').append(word.start).append(':').append(word.end).append(':').append(word.text)
                }
                append('\n')
            }
            (wordTargets + lineTargets).forEach { target ->
                append("target|").append(target.id).append('|').append(target.region)
                    .append('|').append(target.lineIndex).append('|').append(target.targetKind.name)
                    .append('|').append(target.targetStart).append('|').append(target.targetEnd)
                    .append('|').append(target.viewportFraction.toRawBits()).append('|')
                    .append(target.targetText).append('\n')
            }
        }
        return sha256(canonical.toByteArray(Charsets.UTF_8))
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun isSha256(value: String?): Boolean =
        value?.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

    fun isGitCommit(value: String?): Boolean =
        value?.length == 40 && value.all { it in '0'..'9' || it in 'a'..'f' }
}
