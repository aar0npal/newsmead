package com.newsmead.gaze.mgazenet

import android.content.Context
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.newsmead.gaze.GazeProvider
import com.newsmead.gaze.physicalDisplaySize

internal enum class MgazeNetStopAction { START_CLOSE, WAIT_FOR_CLOSE, ALREADY_CLOSED }

/** Keeps every terminal callback behind the one asynchronous source close. */
internal class MgazeNetStopCompletionQueue {
    private enum class State { OPEN, CLOSING, CLOSED }

    private var state = State.OPEN
    private val callbacks = ArrayList<() -> Unit>()

    @Synchronized
    fun request(callback: () -> Unit): MgazeNetStopAction = when (state) {
        State.OPEN -> {
            callbacks += callback
            state = State.CLOSING
            MgazeNetStopAction.START_CLOSE
        }
        State.CLOSING -> {
            callbacks += callback
            MgazeNetStopAction.WAIT_FOR_CLOSE
        }
        State.CLOSED -> MgazeNetStopAction.ALREADY_CLOSED
    }

    @Synchronized
    fun complete(): List<() -> Unit> {
        check(state == State.CLOSING) { "MGazeNet source close was not pending." }
        state = State.CLOSED
        return callbacks.toList().also { callbacks.clear() }
    }
}

/** Finite physical-screen measurements reach GazeProvider with no correction or replay. */
class MgazeNetGazeProvider(
    private val context: Context,
    private val view: View,
    private val smoothForReading: Boolean = false,
    private val rejectBlinks: Boolean = false,
    private val maxOutputAgeMs: Double = MgazeNetOutputGate.EXPIRY_MS,
) : GazeProvider, DefaultLifecycleObserver {
    data class Observation(val captureMs: Double?, val deliveryElapsedNs: Long, val deliveryId: Long, val reason: String,
        val x: Float? = null, val y: Float? = null, val rawX: Float? = null, val rawY: Float? = null,
        val arrivals: Long? = null, val busyDrops: Long? = null,
        val leftEyeAspect: Double? = null, val rightEyeAspect: Double? = null,
        val blinkFilterApplied: Boolean = false,
        val timeoutWatchdog: Boolean = false) {
        val outputMs: Double get() = deliveryElapsedNs / 1e6
    }
    private var listener: GazeProvider.OnGaze? = null
    private var source: MgazeNetCameraSource? = null
    private var owner: LifecycleOwner? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastCapture = -1.0
    private var lastOutput = -1.0
    private var fresh = false
    var onObservation: ((Observation) -> Unit)? = null
    var onFailure: ((String) -> Unit)? = null
    var onFps: ((Float) -> Unit)? = null
    private var fpsStart = 0.0
    private var fpsCount = 0
    private var deliverySequence = 0L
    private val displaySize = Point()
    private var liveOutput: MgazeNetLiveOutput? = null
    private val stopCompletions = MgazeNetStopCompletionQueue()
    override fun setOnGaze(listener: GazeProvider.OnGaze) { this.listener = listener }
    override fun start(owner: LifecycleOwner) {
        check(source == null)
        val store = MgazeNetCalibrationStore(context)
        val saved = try { store.snapshot(view) } catch (_: Exception) {
            onFailure?.invoke("A fresh MGazeNet calibration is required."); return
        }
        this.owner = owner; owner.lifecycle.addObserver(this)
        lastCapture = -1.0; lastOutput = -1.0; fpsStart = MgazeNetCameraSource.now(); fpsCount = 0
        deliverySequence = 0L
        val identity = saved.identity
        val output = MgazeNetLiveOutput(identity.screenWidth, identity.screenHeight,
            smoothForReading, rejectBlinks, maxOutputAgeMs)
        liveOutput = output
        source = MgazeNetCameraSource(context,owner,ready = {}, result = { frame ->
            val size = view.physicalDisplaySize(displaySize)
            if (size.x != identity.screenWidth || size.y != identity.screenHeight || view.display.rotation != identity.rotation) {
                fail("Screen geometry changed; recalibrate.")
            } else {
                val deliveryElapsedNs = SystemClock.elapsedRealtimeNanos()
                val now = deliveryElapsedNs / 1e6
                val deliveryId = ++deliverySequence
                val result = output.evaluate(frame.captureMs,now,frame.eligible,frame.prediction,
                    if (frame.features == null) frame.reason else "eye_area", frame.eyelids)
                val reason = result.reason
                lastCapture = frame.captureMs; lastOutput = frame.outputMs
                fresh = reason == "coordinate"
                onObservation?.invoke(Observation(frame.captureMs,deliveryElapsedNs,deliveryId,reason,result.x,result.y,
                    result.rawX,result.rawY,frame.arrivals,frame.busyDrops,
                    frame.eyelids?.leftAspect,frame.eyelids?.rightAspect,rejectBlinks))
                if (fresh) listener?.onGaze(result.x!!,result.y!!)
                fpsCount++
                if (now-fpsStart >= 1000) { onFps?.invoke((fpsCount*1000/(now-fpsStart)).toFloat()); fpsStart=now; fpsCount=0 }
            }
        }, failed = ::fail, store = store, saved = saved, eyelidTelemetryEnabled = rejectBlinks).also { it.start() }
        handler.post(watchdog)
    }
    private val watchdog = object : Runnable {
        override fun run() {
            if (source == null) return
            val now = MgazeNetCameraSource.now()
            if (lastOutput < 0 && now - fpsStart > INITIALIZATION_TIMEOUT_MS) {
                fail("MGazeNet initialization timed out after ${INITIALIZATION_TIMEOUT_MS / 1000} seconds.")
                return
            }
            if (fresh && now-lastCapture > maxOutputAgeMs) {
                fresh = false
                liveOutput?.expire()
                onObservation?.invoke(Observation(lastCapture,SystemClock.elapsedRealtimeNanos(),++deliverySequence,"stale",
                    blinkFilterApplied = rejectBlinks, timeoutWatchdog = true))
            }
            handler.postDelayed(this,50)
        }
    }
    private fun fail(reason: String) { stopAndThen { onFailure?.invoke(reason) } }
    override fun onStop(owner: LifecycleOwner) { stop() }
    override fun stop() { stopAndThen {} }

    /** Runs [complete] only after the exact terminal source counters were observed. */
    fun stopAndThen(complete: () -> Unit) {
        when (stopCompletions.request(complete)) {
            MgazeNetStopAction.WAIT_FOR_CLOSE -> return
            MgazeNetStopAction.ALREADY_CLOSED -> {
                complete()
                return
            }
            MgazeNetStopAction.START_CLOSE -> Unit
        }
        handler.removeCallbacksAndMessages(null)
        owner?.lifecycle?.removeObserver(this); owner = null
        val closing = source; source = null; fresh = false
        liveOutput?.invalidate(); liveOutput = null
        if (closing == null) {
            stopCompletions.complete().forEach { it() }
            return
        }
        closing.close { counts ->
            try {
                onObservation?.invoke(Observation(
                    null,SystemClock.elapsedRealtimeNanos(),++deliverySequence,"stopped",
                    arrivals = counts["analyzer_arrivals"] as? Long,
                    busyDrops = counts["observed_busy_drops"] as? Long,
                    blinkFilterApplied = rejectBlinks,
                ))
            } finally {
                stopCompletions.complete().forEach { it() }
            }
        }
    }
    companion object {
        // Explicit operational expiry, NOT an accuracy threshold. No interpolation.
        const val MAX_OUTPUT_AGE_MS = MgazeNetOutputGate.EXPIRY_MS
        // The A02's first delivered output has been observed near 20 seconds.
        // This bounds startup only; it does not admit older gaze samples.
        const val INITIALIZATION_TIMEOUT_MS = 40_000.0
    }
}
