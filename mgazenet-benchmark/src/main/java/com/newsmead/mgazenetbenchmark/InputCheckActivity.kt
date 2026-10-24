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
import kotlin.math.ceil

/** Explicit-start, calibration-free input diagnostics. Opening setup keeps the camera closed. */
class InputCheckActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var startButton: Button
    private var source: AccuracyCameraSource? = null
    private var session: InputCheckSession? = null
    private var pipeline: Map<String,Any?> = emptyMap()
    private var visible = false
    private var closing = false
    private var pendingStart = false
    private var label = ""
    private var runId = ""
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingStart = false
        if (granted && visible) begin() else {
            status.text = "Camera not started. No input record was created."
            startButton.isEnabled = true
        }
    }
    private val ticker = object : Runnable {
        override fun run() {
            val current = session ?: return
            if (closing) return
            current.tick(AccuracyCameraSource.now())
            if (current.terminal()) finishRun(current.failure ?: "complete",current.phase == InputCheckSession.Phase.FAILED)
            else {
                updateState()
                handler.postDelayed(this,50)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,48,24,24) }
        status = TextView(this).apply {
            textSize = 18f
            text = "MGazeNet input-only check\n\nThis separate 20-second check records numeric camera, crop, eye-area and timing diagnostics. It shows no targets, fits no calibration and saves no images, landmarks or features. Opening this screen keeps the camera off."
        }
        val runLabel = EditText(this).apply { hint = "Input-check run label"; setSingleLine() }
        startButton = Button(this).apply {
            text = "Start 20-second input-only check"
            setOnClickListener {
                if (source != null || session != null || pendingStart) return@setOnClickListener
                label = runLabel.text.toString().trim()
                if (label.isBlank() || label.length > 120) {
                    status.text = "Enter a run label. The camera remains off."
                    return@setOnClickListener
                }
                isEnabled = false
                if (ContextCompat.checkSelfPermission(this@InputCheckActivity,Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) begin()
                else { pendingStart = true; permission.launch(Manifest.permission.CAMERA) }
            }
        }
        root.addView(status)
        root.addView(runLabel)
        root.addView(startButton)
        root.addView(Button(this).apply { text = "Back"; setOnClickListener { finish() } })
        setContentView(root)
    }

    private fun begin() {
        if (!visible || source != null || session != null) return
        closing = false
        val requested = AccuracyCameraSource.now()
        session = InputCheckSession(label,requested)
        runId = "${System.currentTimeMillis()}_${UUID.randomUUID()}"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,48,24,24) }
        status = TextView(this).apply {
            textSize = 18f
            text = "Initializing the camera for the numeric input check…"
        }
        root.addView(status)
        root.addView(TextView(this).apply {
            text = "Remain naturally positioned. There is no gaze target and blinks are not treated as verified ground truth."
        })
        root.addView(Button(this).apply {
            text = "Stop and save incomplete record"
            setOnClickListener { finishRun("user_stopped",false) }
        })
        setContentView(root)
        try {
            check(OpenCVLoader.initLocal()) { "OpenCV initialization failed" }
            source = AccuracyCameraSource(applicationContext,this,{ metadata ->
                if (!closing) {
                    pipeline = metadata
                    session?.ready(AccuracyCameraSource.now())
                    updateState()
                }
            },{ frame ->
                session?.frame(frame)
                frame.features?.fill(0f)
                if (!closing) updateState()
            },{ error -> finishRun(error,true) }).also { it.start() }
            handler.post(ticker)
        } catch (e: Throwable) {
            finishRun("initialization_failed: $e",true)
        }
    }

    private fun updateState() {
        val current = session ?: return
        if (closing) return
        status.text = when (current.phase) {
            InputCheckSession.Phase.INITIALIZING -> {
                val left = ceil((current.requestedMs + InputCheckSession.INITIALIZATION_TIMEOUT_MS - AccuracyCameraSource.now())/1000)
                    .toInt().coerceAtLeast(0)
                "Initializing the camera… ${left}s before the separate initialization deadline."
            }
            InputCheckSession.Phase.OBSERVING -> {
                val left = ceil((current.plannedEndMs!!-AccuracyCameraSource.now())/1000).toInt().coerceAtLeast(0)
                "Input-only check running: ${left}s remaining.\n${current.emissions.size} results observed; ${current.categories.getValue("eligible")} met the unchanged source area rule."
            }
            else -> "Closing the input check…"
        }
    }

    private fun finishRun(reason: String, failed: Boolean) {
        val current = session ?: return
        if (closing) return
        closing = true
        handler.removeCallbacks(ticker)
        if (!current.terminal()) {
            if (failed) current.fail(reason,AccuracyCameraSource.now()) else current.stop(reason,AccuracyCameraSource.now())
        }
        status.text = "Closing the camera and saving numeric input diagnostics…"
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val finish: (Map<String,Any?>) -> Unit = { counters ->
            val payload = InputCheckReport.payload(current,runId,
                "${android.os.Build.MANUFACTURER}/${android.os.Build.MODEL}",pipeline,counters)
            Thread {
                val message = try {
                    val file = AccuracyReport.writeNew(File(filesDir,"input-check/$runId"),payload)
                    "${if (payload["schema"] == "mgazenet_input_check_v1") "Input check complete" else "Input check incomplete"}.\nNumeric record saved: ${file.name}\nNo calibration or gaze-accuracy claim was created. You may return."
                } catch (e: Throwable) { "Could not save the numeric input record: $e" }
                runOnUiThread { status.text = message }
            }.start()
        }
        source?.close(finish) ?: finish(mapOf("analyzer_arrivals" to 0,"observed_busy_drops" to 0,
            "cameraX_undelivered_frames" to "not_observable","resource_close_errors" to emptyList<String>()))
    }

    override fun onStart() { super.onStart(); visible = true }
    override fun onStop() {
        visible = false
        if (!pendingStart && session != null) finishRun("activity_backgrounded",false)
        super.onStop()
    }
    override fun onDestroy() { handler.removeCallbacks(ticker); super.onDestroy() }
}
