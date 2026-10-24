package com.newsmead.mgazenetbenchmark

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.opencv.android.OpenCVLoader
import java.io.File
import java.util.UUID

/** Isolated confirmation setup. Opening it performs no camera, fit, or storage action. */
class ConfirmationActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var startButton: Button
    private var targetView: AccuracyTargetView? = null
    private var source: AccuracyCameraSource? = null
    private var session: ConfirmationSession? = null
    private var pipeline: Map<String,Any?> = emptyMap()
    private var visible = false
    private var closing = false
    private var fitting = false
    private var pendingStart = false
    private var label = ""
    private var validationOrder: AccuracySession.ValidationOrder? = null
    private var openedAt = 0.0
    private var runId = ""
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingStart = false
        if (granted && visible) begin() else {
            status.text = "Camera not started."
            startButton.isEnabled = true
        }
    }
    private val ticker = object : Runnable {
        override fun run() {
            if (closing || source == null) return
            val now = AccuracyCameraSource.now()
            if ((session == null && now-openedAt > 20_000) || now-openedAt > 600_000) {
                finishRun("session_deadline",true)
                return
            }
            session?.tick(now)
            updateState()
            if (!closing) handler.postDelayed(this,50)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24,48,24,24)
        }
        status = TextView(this).apply {
            textSize = 18f
            text = "MGazeNet direct-screen confirmation setup\n\nOpening this page keeps the camera off. A separate 16-point calibration is followed by five hidden-result screen targets and two full sweeps over ten independent text locations. Every run continues through the independent locations regardless of the screen result. No images, features, personal model, active tracker, or reading assistance are saved or changed.\n\nSelect the preassigned order. Start only under a separately authorized collection protocol."
        }
        val runLabel = EditText(this).apply { hint = "Research run label"; setSingleLine() }
        val forwardFirstId = View.generateViewId()
        val reverseFirstId = View.generateViewId()
        val orderGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            addView(RadioButton(this@ConfirmationActivity).apply {
                id = forwardFirstId
                text = "Forward, then reverse"
            })
            addView(RadioButton(this@ConfirmationActivity).apply {
                id = reverseFirstId
                text = "Reverse, then forward"
            })
        }
        startButton = Button(this).apply {
            text = "Start separate confirmation session"
            setOnClickListener {
                if (source != null || pendingStart) return@setOnClickListener
                label = runLabel.text.toString().trim()
                validationOrder = when (orderGroup.checkedRadioButtonId) {
                    forwardFirstId -> AccuracySession.ValidationOrder.FORWARD_THEN_REVERSE
                    reverseFirstId -> AccuracySession.ValidationOrder.REVERSE_THEN_FORWARD
                    else -> null
                }
                if (label.isBlank() || label.length > 120 || validationOrder == null) {
                    status.text = "Enter a run label and select the preassigned order. No order is selected by default."
                    return@setOnClickListener
                }
                isEnabled = false
                if (ContextCompat.checkSelfPermission(this@ConfirmationActivity,Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED) begin()
                else {
                    pendingStart = true
                    permission.launch(Manifest.permission.CAMERA)
                }
            }
        }
        root.addView(status)
        root.addView(runLabel)
        root.addView(orderGroup)
        root.addView(startButton)
        root.addView(Button(this).apply { text = "Back"; setOnClickListener { finish() } })
        setContentView(root)
    }

    private fun begin() {
        if (!visible || source != null) return
        try {
            check(OpenCVLoader.initLocal())
        } catch (e: Throwable) {
            status.text = "Cannot start: $e"
            startButton.isEnabled = true
            return
        }
        closing = false
        openedAt = AccuracyCameraSource.now()
        runId = "${System.currentTimeMillis()}_${UUID.randomUUID()}"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val measurement = ConfirmationMeasurementLayout.create(this) {
            if (closing) finish() else finishRun("user_stopped",false)
        }
        status = measurement.status
        targetView = measurement.target.also { view ->
            view.onPresented = { token,time -> session?.presented(token,time) }
            view.onGeometry = { geometry ->
                if (!closing && session != null && session!!.layout != geometry)
                    finishRun("screen_layout_changed",true)
            }
        }
        setContentView(measurement.root)
        source = AccuracyCameraSource(applicationContext,this,{ metadata ->
            if (!closing) {
                try {
                    pipeline = metadata
                    session = ConfirmationSession(targetView!!.snapshot(),label,validationOrder!!)
                    updateState()
                } catch (e: Throwable) {
                    finishRun("invalid_target_layout: $e",true)
                }
            }
        },{ frame ->
            session?.frame(frame)
            frame.features?.fill(0f)
            if (!closing) updateState()
        },{ error -> finishRun(error,true) }).also { it.start() }
        handler.post(ticker)
    }

    private fun updateState() {
        val current = session ?: return
        if (closing) return
        targetView?.target = current.target
        when (current.phase) {
            ConfirmationSession.Phase.CALIBRATION -> {
                val point = current.fitPoints.first { it.id == current.target!!.id }
                status.text = if (point.practice) {
                    "Practice: look at the red target.\n${point.accepted} / 45 samples"
                } else {
                    "16-point calibration: look at the red target.\nPoint ${current.fitPoints.indexOf(point)} of 16 · ${point.accepted} / 45 samples"
                }
            }
            ConfirmationSession.Phase.FITTING -> if (!fitting) {
                fitting = true
                status.text = "Checking target-group isolation, then fitting the separate calibration…"
                source!!.fitAudited(current.training()) { audit,success ->
                    current.fitted(audit,success,AccuracyCameraSource.now())
                    if (!closing) updateState()
                }
            }
            ConfirmationSession.Phase.SCREEN -> {
                val index = current.screenBlocks.indexOfFirst { it.id == current.target!!.id }
                status.text = "Calibration screen: keep looking at the red target.\nTarget ${index+1} of 5"
            }
            ConfirmationSession.Phase.CONFIRMATION -> {
                val block = current.confirmationBlocks.first { it.id == current.target!!.id }
                status.text = "Independent location check: keep looking at the red target.\nSweep ${block.sweep} of 2 · location ${block.orderInSweep} of 10"
            }
            ConfirmationSession.Phase.COMPLETE -> finishRun("complete",false)
            else -> finishRun(current.failure ?: "session_failed",true)
        }
    }

    private fun finishRun(reason: String, failed: Boolean) {
        if (closing || source == null) return
        closing = true
        handler.removeCallbacks(ticker)
        if (failed) session?.fail(reason,AccuracyCameraSource.now())
        else session?.stop(reason,AccuracyCameraSource.now())
        targetView?.target = null
        status.text = "Closing camera and saving the sealed numeric confirmation record…"
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        source!!.close { counters ->
            val current = session
            val payload = current?.let {
                ConfirmationReport.payload(it,runId,
                    "${android.os.Build.MANUFACTURER}/${android.os.Build.MODEL}",pipeline).toMutableMap()
            } ?: linkedMapOf<String,Any?>(
                "schema" to "mgazenet_direct_validation_confirmation_partial_v1",
                "outcome" to "failed","protocol_id" to ConfirmationSession.VERSION,
                "session_id" to runId,"failure" to reason,"screen_blocks" to emptyList<Any>(),
                "confirmation_blocks" to emptyList<Any>(),"active_tracker_access" to false,
                "calibration_store_access" to false,"camera_frames_retained" to false,
                "features_retained" to false,"personal_model_retained" to false,
                "model_correction_applied" to false,"screen_result_hidden_until_terminal" to true,
                "confirmation_accuracy_pass" to null,"accuracy_gate_pass" to null,
                "promotion_decision" to "not_evaluated")
            payload["camera_counters"] = counters
            Thread {
                val message = try {
                    val file = ConfirmationReport.writeNew(File(filesDir,"confirmation/$runId"),payload)
                    "${if (current?.phase == ConfirmationSession.Phase.COMPLETE) "Confirmation measurement complete" else "Confirmation session incomplete"}.\nNumeric record saved: ${file.name}\nNo result is shown or applied. You may return."
                } catch (e: Throwable) {
                    "Could not save the numeric confirmation record: $e"
                }
                runOnUiThread { status.text = message }
            }.start()
        }
    }

    override fun onStart() {
        super.onStart()
        visible = true
    }

    override fun onStop() {
        visible = false
        if (!pendingStart) finishRun("activity_backgrounded",false)
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }
}
