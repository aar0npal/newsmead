package com.newsmead.gaze

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

data class ComparisonCalibrationEvidence(
    val fitTargetCount: Int,
    val acceptedRowsPerTarget: Int?,
    val featureCount: Int?,
    val postFitCheckCount: Int,
    val excludedFitTargetCount: Int,
    val detailedTelemetryEnabled: Boolean,
    val correctionApplied: Boolean,
)

data class ComparisonCalibrationReservation(
    val runtime: ComparisonRuntimeIdentity,
    val startedElapsedNs: Long,
    val startedWallTimeMs: Long,
)

data class ComparisonSlotReceipt(
    val fileName: String,
    val sha256: String,
)

/** Append-only per-slot storage shared across sequential in-place arm installs. */
class ComparisonSlotStore(context: Context) {
    private val context = context.applicationContext
    private val root = File(context.noBackupFilesDir, DIRECTORY)

    fun calibrationStartIssue(spec: ComparisonLaunchSpec): String? {
        val files = paths(spec.slot.id)
        return when {
            files.marker.exists() -> "This comparison slot already started and is retained as interrupted."
            files.log.exists() || files.hash.exists() -> "This comparison slot already exists and cannot be replaced."
            else -> null
        }
    }

    /** The first mutation for a slot. Call only after the explicit Start action. */
    fun reserveCalibration(runtime: ComparisonRuntimeIdentity): ComparisonCalibrationReservation =
        synchronized(LOCK) {
            calibrationStartIssue(runtime.spec)?.let { throw IllegalStateException(it) }
            check(root.isDirectory || root.mkdirs()) { "Cannot create comparison slot storage." }
            val reservation = ComparisonCalibrationReservation(
                runtime,
                ComparisonRuntime.nowNs(),
                System.currentTimeMillis(),
            )
            val marker = JSONObject().apply {
                put("comparison_schema", SCHEMA)
                put("record_type", "slot_reservation")
                putRuntime(runtime)
                put("started_elapsed_ns", reservation.startedElapsedNs)
                put("started_wall_time_ms", reservation.startedWallTimeMs)
            }
            writeAtomic(paths(runtime.spec.slot.id).marker, marker.toString().toByteArray(Charsets.UTF_8))
            reservation
        }

    fun completeCalibration(
        reservation: ComparisonCalibrationReservation,
        calibrationSha256: String,
        evidence: ComparisonCalibrationEvidence,
    ) = synchronized(LOCK) {
        require(ComparisonProtocol.isSha256(calibrationSha256)) { "Invalid calibration fingerprint." }
        require(evidence.fitTargetCount == 16 && evidence.excludedFitTargetCount == 0)
        require(!evidence.detailedTelemetryEnabled && !evidence.correctionApplied)
        when (reservation.runtime.build.estimatorId) {
            "current" -> {
                require(evidence.acceptedRowsPerTarget == null)
                require(evidence.featureCount == 2)
                require(evidence.postFitCheckCount == 6)
            }
            "mgazenet" -> {
                require(evidence.acceptedRowsPerTarget == 45)
                require(evidence.featureCount == 258)
                require(evidence.postFitCheckCount == 6)
            }
            else -> error("Unknown comparison estimator.")
        }
        val paths = paths(reservation.runtime.spec.slot.id)
        check(paths.marker.isFile && !paths.log.exists() && !paths.hash.exists()) {
            "Comparison slot reservation changed before calibration completion."
        }
        verifyMarker(paths.marker, reservation)
        val binding = ComparisonRecordBinding(reservation.runtime, calibrationSha256)
        val completedNs = ComparisonRuntime.nowNs()
        val records = listOf(
            JSONObject().apply {
                put("record_type", "slot_start")
                put("stage", "calibration")
                put("started_wall_time_ms", reservation.startedWallTimeMs)
            } to reservation.startedElapsedNs,
            JSONObject().apply {
                put("record_type", "calibration_start")
                put("explicit_start", true)
                put("fresh_calibration_required", true)
            } to reservation.startedElapsedNs,
            JSONObject().apply {
                put("record_type", "calibration_end")
                put("outcome", "completed")
                put("calibration_technically_complete", true)
                put("fit_target_count", evidence.fitTargetCount)
                putNum("accepted_rows_per_target", evidence.acceptedRowsPerTarget)
                putNum("feature_count", evidence.featureCount)
                put("post_fit_check_count", evidence.postFitCheckCount)
                put("excluded_fit_target_count", evidence.excludedFitTargetCount)
                put("detailed_telemetry_enabled", evidence.detailedTelemetryEnabled)
                put("correction_applied", evidence.correctionApplied)
                put("spatial_result_exposed", false)
            } to completedNs,
        )
        val bytes = buildString {
            records.forEachIndexed { index, (record, elapsedNs) ->
                stamp(record, binding, index, elapsedNs, null)
                append(record).append('\n')
            }
        }.toByteArray(Charsets.UTF_8)
        writeAtomic(paths.log, bytes)
        check(paths.marker.delete()) { "Cannot retire completed comparison reservation." }
    }

    fun failCalibration(
        reservation: ComparisonCalibrationReservation,
        outcome: String,
        reason: String,
    ): ComparisonSlotReceipt? = synchronized(LOCK) {
        require(outcome in setOf("failed", "interrupted"))
        val paths = paths(reservation.runtime.spec.slot.id)
        if (!paths.marker.isFile || paths.log.exists() || paths.hash.exists()) return@synchronized null
        verifyMarker(paths.marker, reservation)
        val binding = ComparisonRecordBinding(reservation.runtime, null)
        val endNs = ComparisonRuntime.nowNs()
        val records = listOf(
            JSONObject().apply {
                put("record_type", "slot_start")
                put("stage", "calibration")
                put("started_wall_time_ms", reservation.startedWallTimeMs)
            } to reservation.startedElapsedNs,
            JSONObject().apply {
                put("record_type", "calibration_start")
                put("explicit_start", true)
                put("fresh_calibration_required", true)
            } to reservation.startedElapsedNs,
            JSONObject().apply {
                put("record_type", "calibration_end")
                put("outcome", outcome)
                put("reason", reason)
                put("calibration_technically_complete", false)
                put("spatial_result_exposed", false)
            } to endNs,
        )
        val bytes = buildString {
            records.forEachIndexed { index, (record, elapsedNs) ->
                stamp(record, binding, index, elapsedNs, null)
                append(record).append('\n')
            }
        }.toByteArray(Charsets.UTF_8)
        writeAtomic(paths.log, bytes)
        check(paths.marker.delete()) { "Cannot retire failed comparison reservation." }
        seal(paths)
    }

    fun openReading(
        binding: ComparisonReadingBinding,
    ): ComparisonSlotWriter = synchronized(LOCK) {
        val slot = binding.record.runtime.spec.slot.id
        val paths = paths(slot)
        check(!paths.marker.exists() && paths.log.isFile && !paths.hash.exists()) {
            "The slot is not ready for one reading validation."
        }
        val lines = paths.log.readLines().filter(String::isNotBlank)
        check(lines.size == CALIBRATION_RECORD_COUNT) { "The slot already has reading data or is incomplete." }
        val records = lines.map(::JSONObject)
        check(records.map { it.getInt("record_sequence") } == listOf(0, 1, 2))
        val end = records.last()
        check(end.getString("record_type") == "calibration_end" && end.getString("outcome") == "completed") {
            "The slot calibration did not complete."
        }
        records.forEach { verifyBinding(it, binding.record) }
        ComparisonSlotWriter(paths, binding, CALIBRATION_RECORD_COUNT)
    }

    private fun verifyMarker(file: File, reservation: ComparisonCalibrationReservation) {
        val marker = JSONObject(file.readText())
        check(marker.getString("comparison_schema") == SCHEMA)
        check(marker.getString("record_type") == "slot_reservation")
        check(marker.getLong("started_elapsed_ns") == reservation.startedElapsedNs)
        verifyRuntime(marker, reservation.runtime)
    }

    private fun paths(slotId: String): SlotPaths {
        require(ComparisonProtocol.slots.any { it.id == slotId })
        return SlotPaths(
            marker = File(root, "$slotId.started.json"),
            log = File(root, "$slotId.jsonl"),
            hash = File(root, "$slotId.jsonl.sha256"),
        )
    }

    internal data class SlotPaths(val marker: File, val log: File, val hash: File)

    class ComparisonSlotWriter internal constructor(
        private val paths: SlotPaths,
        private val binding: ComparisonReadingBinding,
        firstSequence: Int,
    ) {
        private val writer = BufferedWriter(
            OutputStreamWriter(FileOutputStream(paths.log, true), Charsets.UTF_8),
        )
        private var sequence = firstSequence
        private var closed = false

        @Synchronized
        fun append(record: JSONObject, elapsedNs: Long = ComparisonRuntime.nowNs(), flush: Boolean = false) {
            check(!closed) { "Comparison slot writer is closed." }
            stamp(record, binding.record, sequence++, elapsedNs, binding)
            writer.append(record.toString())
            writer.newLine()
            if (flush) writer.flush()
        }

        @Synchronized
        fun flush() {
            if (!closed) writer.flush()
        }

        @Synchronized
        fun closeAndSeal(): ComparisonSlotReceipt {
            if (!closed) {
                writer.flush()
                writer.close()
                closed = true
            }
            return synchronized(LOCK) { seal(paths) }
        }

        fun fileName(): String = paths.log.name
    }

    companion object {
        const val SCHEMA = "newsmead_comparison_slot_v1"
        const val DIRECTORY = "comparison-v1"
        private const val CALIBRATION_RECORD_COUNT = 3
        private val LOCK = Any()

        private fun JSONObject.putRuntime(runtime: ComparisonRuntimeIdentity) {
            put("protocol_id", runtime.spec.protocolId)
            put("protocol_manifest_sha256", runtime.protocolManifestSha256)
            put("slot_id", runtime.spec.slot.id)
            put("order_variant", runtime.spec.orderVariant)
            put("estimator_id", runtime.build.estimatorId)
            put("estimator_base_commit", runtime.build.baseCommit)
            put("estimator_base_apk_sha256", runtime.build.baseApkSha256)
            put("collection_apk_sha256", runtime.collectionApkSha256)
            put("device_instance_sha256", runtime.deviceInstanceSha256)
            put("device_model", runtime.deviceModel)
            put("screen_width_px", runtime.screenWidthPx)
            put("screen_height_px", runtime.screenHeightPx)
            put("density_dpi", runtime.densityDpi)
            put("display_rotation", runtime.rotation)
            put("coordinate_space", ComparisonProtocol.COORDINATE_SPACE)
            put("monotonic_clock", ComparisonProtocol.MONOTONIC_CLOCK)
        }

        private fun stamp(
            value: JSONObject,
            binding: ComparisonRecordBinding,
            sequence: Int,
            elapsedNs: Long,
            reading: ComparisonReadingBinding?,
        ) {
            require(elapsedNs >= 0L)
            value.put("comparison_schema", SCHEMA)
            value.putRuntime(binding.runtime)
            value.put(
                "calibration_sha256",
                binding.calibrationSha256 ?: JSONObject.NULL,
            )
            value.put("comparison_binding_sha256", binding.digest)
            value.put("record_sequence", sequence)
            value.put("event_elapsed_ns", elapsedNs)
            value.put("reading_layout_sha256", reading?.readingLayoutSha256 ?: JSONObject.NULL)
            value.put("passage_sha256", reading?.passageSha256 ?: JSONObject.NULL)
            value.put("reading_binding_sha256", reading?.digest ?: JSONObject.NULL)
        }

        private fun verifyRuntime(value: JSONObject, expected: ComparisonRuntimeIdentity) {
            check(value.getString("protocol_id") == expected.spec.protocolId)
            check(value.getString("protocol_manifest_sha256") == expected.protocolManifestSha256)
            check(value.getString("slot_id") == expected.spec.slot.id)
            check(value.getString("order_variant") == expected.spec.orderVariant)
            check(value.getString("estimator_id") == expected.build.estimatorId)
            check(value.getString("estimator_base_commit") == expected.build.baseCommit)
            check(value.getString("estimator_base_apk_sha256") == expected.build.baseApkSha256)
            check(value.getString("collection_apk_sha256") == expected.collectionApkSha256)
            check(value.getString("device_instance_sha256") == expected.deviceInstanceSha256)
            check(value.getString("device_model") == expected.deviceModel)
            check(value.getInt("screen_width_px") == expected.screenWidthPx)
            check(value.getInt("screen_height_px") == expected.screenHeightPx)
            check(value.getInt("density_dpi") == expected.densityDpi)
            check(value.getInt("display_rotation") == expected.rotation)
            check(value.getString("coordinate_space") == ComparisonProtocol.COORDINATE_SPACE)
            check(value.getString("monotonic_clock") == ComparisonProtocol.MONOTONIC_CLOCK)
        }

        private fun verifyBinding(value: JSONObject, expected: ComparisonRecordBinding) {
            verifyRuntime(value, expected.runtime)
            check(value.getString("calibration_sha256") == expected.calibrationSha256)
            check(value.getString("comparison_binding_sha256") == expected.digest)
        }

        private fun seal(paths: SlotPaths): ComparisonSlotReceipt {
            check(paths.log.isFile && !paths.hash.exists()) { "Comparison slot cannot be sealed." }
            val hash = ComparisonProtocol.sha256(paths.log.readBytes())
            writeAtomic(
                paths.hash,
                "$hash  ${paths.log.name}\n".toByteArray(Charsets.UTF_8),
            )
            return ComparisonSlotReceipt(paths.log.name, hash)
        }

        private fun writeAtomic(file: File, bytes: ByteArray) {
            check(file.parentFile?.isDirectory == true || file.parentFile?.mkdirs() == true)
            val atomic = AtomicFile(file)
            val output: FileOutputStream = atomic.startWrite()
            try {
                output.write(bytes)
                atomic.finishWrite(output)
            } catch (failure: Throwable) {
                atomic.failWrite(output)
                throw failure
            }
        }

        private fun JSONObject.putNum(key: String, value: Number?) {
            put(key, value ?: JSONObject.NULL)
        }
    }
}
