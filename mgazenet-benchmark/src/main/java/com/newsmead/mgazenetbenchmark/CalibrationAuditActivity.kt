package com.newsmead.mgazenetbenchmark

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.opencv.android.OpenCVLoader
import java.io.File
import java.util.UUID

/** Isolated setup. Opening it performs no camera, calibration, audit, or storage action. */
class CalibrationAuditActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var startButton: Button
    private var targetView: AccuracyTargetView? = null
    private var source: AccuracyCameraSource? = null
    private var session: CalibrationAuditSession? = null
    private var pipeline: Map<String,Any?> = emptyMap()
    private var visible = false
    private var closing = false
    private var fitting = false
    private var pendingStart = false
    private var label = ""
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
            text = "16-point MGazeNet calibration audit setup\n\nOpening this page keeps the camera off. The isolated check uses NewsMead's fixed 4×4 calibration layout, then repeats one near-centre point and measures five held-out points. It audits MGazeNet's own 258-value calibration with whole-target leave-one-out fits. It does not read or change the active tracker, and it assigns no accuracy pass.\n\nOnly start after a separate device-run authorization."
        }
        val runLabel = EditText(this).apply { hint = "Research run label"; setSingleLine() }
        startButton = Button(this).apply {
            text = "Start separate 16-point calibration audit"
            setOnClickListener {
                if (source != null || pendingStart) return@setOnClickListener
                label = runLabel.text.toString().trim()
                if (label.isBlank() || label.length > 120) {
                    status.text = "Enter a run label of 1–120 characters. The camera remains off."
                    return@setOnClickListener
                }
                isEnabled = false
                if (ContextCompat.checkSelfPermission(this@CalibrationAuditActivity,Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) begin()
                else {
                    pendingStart = true
                    permission.launch(Manifest.permission.CAMERA)
                }
            }
        }
        root.addView(status)
        root.addView(runLabel)
        root.addView(startButton)
        root.addView(Button(this).apply { text = "Back"; setOnClickListener { finish() } })
        setContentView(root)
    }

    private fun begin() {
        if (!visible || source != null) return
        try { check(OpenCVLoader.initLocal()) } catch (e: Throwable) {
            status.text = "Cannot start: $e"
            startButton.isEnabled = true
            return
        }
        closing = false
        openedAt = AccuracyCameraSource.now()
        runId = "${System.currentTimeMillis()}_${UUID.randomUUID()}"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        status = TextView(this).apply {
            textSize = 18f
            setPadding(20,12,20,0)
            text = "Preparing camera. Follow the red target when it appears."
        }
        root.addView(status,LinearLayout.LayoutParams(-1,(112*resources.displayMetrics.density).toInt()))
        targetView = AccuracyTargetView(this).also { view ->
            root.addView(view,LinearLayout.LayoutParams(-1,0,1f))
            view.onPresented = { token, time -> session?.presented(token,time) }
            view.onGeometry = { geometry ->
                if (!closing && session != null && session!!.layout != geometry) finishRun("screen_layout_changed",true)
            }
        }
        root.addView(Button(this).apply {
            text = "Stop and return"
            setOnClickListener { if (closing) finish() else finishRun("user_stopped",false) }
        })
        setContentView(root)
        source = AccuracyCameraSource(applicationContext,this,{ metadata ->
            if (!closing) {
                try {
                    pipeline = metadata
                    session = CalibrationAuditSession(targetView!!.snapshot(),label)
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
            CalibrationAuditSession.Phase.CALIBRATION -> {
                val point = current.fitPoints.first { it.id == current.target!!.id }
                status.text = if (point.practice) {
                    "Practice: look at the red target.\n${point.accepted} / 45 samples"
                } else {
                    "16-point calibration: look at the red target.\nPoint ${current.fitPoints.indexOf(point)} of 16 · ${point.accepted} / 45 samples"
                }
            }
            CalibrationAuditSession.Phase.FITTING -> if (!fitting) {
                fitting = true
                status.text = "Auditing all 16 held-out target groups, then fitting the separate MGazeNet calibration…"
                source!!.fitAudited(current.training()) { audit,success ->
                    current.fitted(audit,success,AccuracyCameraSource.now())
                    if (!closing) updateState()
                }
            }
            CalibrationAuditSession.Phase.VERIFICATION -> {
                val index = current.target!!.testIndex!!
                val block = current.verificationBlocks[index]
                status.text = if (block.role == "drift_repeat") {
                    "Calibration check: repeat the near-centre target.\nVerification 1 of 6"
                } else {
                    "Held-out calibration check: keep looking at the red target.\nVerification ${index+1} of 6"
                }
            }
            CalibrationAuditSession.Phase.COMPLETE -> finishRun("complete",false)
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
        status.text = "Closing camera and saving the numeric calibration-audit record…"
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        source!!.close { counters ->
            val current = session
            val payload = current?.let {
                CalibrationAuditReport.payload(it,runId,"${android.os.Build.MANUFACTURER}/${android.os.Build.MODEL}",pipeline).toMutableMap()
            } ?: linkedMapOf<String,Any?>(
                "schema" to "mgazenet_calibration_audit_partial_v3","outcome" to "failed",
                "protocol_id" to CalibrationAuditSession.VERSION,"session_id" to runId,"failure" to reason,
                "loo_folds" to emptyList<Any>(),"verification_blocks" to emptyList<Any>(),
                "active_tracker_access" to false,"calibration_store_access" to false,
                "camera_frames_retained" to false,"features_retained" to false,"personal_model_retained" to false,
                "accuracy_gate_pass" to null,"promotion_decision" to "not_evaluated")
            payload["camera_counters"] = counters
            Thread {
                val message = try {
                    val file = CalibrationAuditReport.writeNew(File(filesDir,"calibration-audit/$runId"),payload)
                    "${if (current?.phase == CalibrationAuditSession.Phase.COMPLETE) "Calibration audit complete" else "Calibration audit incomplete"}.\nNumeric record saved: ${file.name}\nNo accuracy pass or tracker change has been assigned. You may return."
                } catch (e: Throwable) {
                    "Could not save the numeric calibration-audit record: $e"
                }
                runOnUiThread { status.text = message }
            }.start()
        }
    }

    override fun onStart() { super.onStart(); visible = true }
    override fun onStop() {
        visible = false
        if (!pendingStart) finishRun("activity_backgrounded",false)
        super.onStop()
    }
    override fun onDestroy() { handler.removeCallbacks(ticker); super.onDestroy() }
}
