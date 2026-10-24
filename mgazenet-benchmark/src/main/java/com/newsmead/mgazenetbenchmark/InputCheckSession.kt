package com.newsmead.mgazenetbenchmark

import kotlin.math.ceil
import kotlin.math.max

/** Pure state and aggregate-only diagnostics for the explicit input check. */
class InputCheckSession(val runLabel: String, val requestedMs: Double) {
    enum class Phase { INITIALIZING, OBSERVING, COMPLETE, STOPPED, FAILED }

    data class Emission(val captureOffsetMs: Double, val outputOffsetMs: Double,
                        val ageMs: Double, val category: String)

    var phase = Phase.INITIALIZING; private set
    var failure: String? = null; private set
    var readyMs: Double? = null; private set
    var plannedStartMs: Double? = null; private set
    var plannedEndMs: Double? = null; private set
    var finishedMs: Double? = null; private set
    var ignoredBeforeWindow = 0; private set
    var ignoredAfterWindow = 0; private set
    var clockConsistent = true; private set
    val emissions = mutableListOf<Emission>()
    val categories = linkedMapOf(
        "eligible" to 0,
        "no_face" to 0,
        "invalid_crops" to 0,
        "eye_area_rejected" to 0,
        "invalid_features" to 0
    )
    private val leftAreas = mutableListOf<Double>()
    private val rightAreas = mutableListOf<Double>()
    private val cropMeasurements = List(6) { mutableListOf<Double>() }
    private var previousCapture = -1.0
    private var previousOutput = -1.0

    init {
        require(runLabel.isNotBlank() && runLabel.length <= 120)
        require(requestedMs.isFinite() && requestedMs >= 0)
    }

    fun ready(now: Double) {
        requireTime(now)
        if (phase != Phase.INITIALIZING) return
        if (now > requestedMs + INITIALIZATION_TIMEOUT_MS) {
            fail("camera_initialization_timeout",now)
            return
        }
        readyMs = now
        plannedStartMs = now
        plannedEndMs = now + OBSERVATION_MS
        phase = Phase.OBSERVING
    }

    fun frame(frame: AccuracySession.Frame) {
        if (phase != Phase.OBSERVING) return
        if (!frame.captureMs.isFinite() || !frame.outputMs.isFinite() || frame.captureMs < 0 ||
            frame.outputMs < frame.captureMs || frame.captureMs <= previousCapture || frame.outputMs <= previousOutput) {
            clockConsistent = false
            fail("invalid_or_nonmonotonic_frame_clock",frame.outputMs.takeIf { it.isFinite() } ?: requestedMs)
            return
        }
        previousCapture = frame.captureMs
        previousOutput = frame.outputMs
        val start = plannedStartMs!!
        val end = plannedEndMs!!
        if (frame.outputMs < start) { ignoredBeforeWindow++; return }
        if (frame.outputMs >= end) { ignoredAfterWindow++; return }

        val sizes = frame.cropSizes
        if (sizes != null && (sizes.size != 3 || sizes.any { it.size != 2 || it.any { value -> value <= 0 } })) {
            fail("invalid_crop_diagnostics",frame.outputMs)
            return
        }
        if (sizes != null && frame.leftArea.isFinite() && frame.rightArea.isFinite()) {
            leftAreas.add(frame.leftArea)
            rightAreas.add(frame.rightArea)
            sizes.flatten().forEachIndexed { index, value -> cropMeasurements[index].add(value.toDouble()) }
        }
        val category = when {
            frame.reason == "no_face" -> "no_face"
            frame.reason == "invalid_crops" || sizes == null -> "invalid_crops"
            frame.features?.let { it.size == 258 && it.all(Float::isFinite) } != true -> "invalid_features"
            !frame.leftArea.isFinite() || !frame.rightArea.isFinite() || frame.leftArea <= 10 || frame.rightArea <= 10 ->
                "eye_area_rejected"
            else -> "eligible"
        }
        categories[category] = categories.getValue(category) + 1
        emissions.add(Emission(frame.captureMs-start,frame.outputMs-start,frame.outputMs-frame.captureMs,category))
    }

    fun tick(now: Double) {
        requireTime(now)
        when (phase) {
            Phase.INITIALIZING -> if (now >= requestedMs + INITIALIZATION_TIMEOUT_MS) {
                fail("camera_initialization_timeout",now)
            }
            Phase.OBSERVING -> if (now >= plannedEndMs!!) {
                phase = Phase.COMPLETE
                finishedMs = now
            }
            else -> Unit
        }
    }

    fun stop(reason: String, now: Double) {
        if (terminal()) return
        failure = reason
        phase = Phase.STOPPED
        finishedMs = validFinish(now)
    }

    fun fail(reason: String, now: Double) {
        if (terminal()) return
        failure = reason
        phase = Phase.FAILED
        finishedMs = validFinish(now)
    }

    fun terminal() = phase in listOf(Phase.COMPLETE,Phase.STOPPED,Phase.FAILED)

    fun observedDurationMs(): Double {
        val start = plannedStartMs ?: return 0.0
        val boundary = minOf(finishedMs ?: start,plannedEndMs ?: start)
        return max(0.0,boundary-start)
    }

    fun areaSummaries() = linkedMapOf<String,Any?>(
        "left_px2" to summary(leftAreas),
        "right_px2" to summary(rightAreas)
    )

    fun cropSummaries(): Map<String,Any?> {
        val names = listOf("face_width_px","face_height_px","left_width_px","left_height_px",
            "right_width_px","right_height_px")
        return names.mapIndexed { index, name -> name to summary(cropMeasurements[index]) }.toMap(linkedMapOf())
    }

    fun ageSummary() = summary(emissions.map { it.ageMs })

    fun timingSummary(): Map<String,Any?> {
        val duration = observedDurationMs()
        val offsets = emissions.map { it.outputOffsetMs.coerceIn(0.0,duration) }
        if (offsets.isEmpty()) return linkedMapOf(
            "output_offsets_ms" to emptyList<Double>(),
            "capture_offsets_ms" to emptyList<Double>(),
            "initial_callback_gap_ms" to duration,
            "inter_callback_gap_ms" to summary(emptyList()),
            "final_callback_gap_ms" to null,
            "longest_callback_gap_ms" to duration
        )
        val gaps = offsets.zipWithNext { a, b -> b-a }
        val initial = offsets.first()
        val final = max(0.0,duration-offsets.last())
        return linkedMapOf(
            "output_offsets_ms" to emissions.map { it.outputOffsetMs },
            "capture_offsets_ms" to emissions.map { it.captureOffsetMs },
            "initial_callback_gap_ms" to initial,
            "inter_callback_gap_ms" to summary(gaps),
            "final_callback_gap_ms" to final,
            "longest_callback_gap_ms" to (listOf(initial,final)+gaps).maxOrNull()
        )
    }

    private fun summary(values: List<Double>): Map<String,Any?> {
        if (values.isEmpty()) return linkedMapOf("count" to 0,"min" to null,"median" to null,
            "p95" to null,"max" to null,"mean" to null)
        require(values.all { it.isFinite() })
        val sorted = values.sorted()
        val middle = sorted.size/2
        val median = if (sorted.size%2 == 1) sorted[middle] else (sorted[middle-1]+sorted[middle])/2
        val p95 = sorted[max(0,ceil(.95*sorted.size).toInt()-1)]
        return linkedMapOf("count" to sorted.size,"min" to sorted.first(),"median" to median,
            "p95" to p95,"max" to sorted.last(),"mean" to sorted.average())
    }

    private fun validFinish(now: Double): Double = if (now.isFinite() && now >= 0) now else requestedMs
    private fun requireTime(now: Double) = require(now.isFinite() && now >= 0)

    companion object {
        const val VERSION = "mgazenet_input_check_v1"
        const val INITIALIZATION_TIMEOUT_MS = 20_000.0
        const val OBSERVATION_MS = 20_000.0
    }
}
