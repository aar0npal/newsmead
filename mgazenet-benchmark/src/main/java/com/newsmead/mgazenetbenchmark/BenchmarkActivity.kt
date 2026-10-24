package com.newsmead.mgazenetbenchmark

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.opencv.android.OpenCVLoader
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Nothing starts on launch. Camera permission is requested only on explicit Start. */
class BenchmarkActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var synthetic: Button
    private lateinit var camera: Button
    private lateinit var accuracy: Button
    private lateinit var calibrationAudit: Button
    private lateinit var confirmation: Button
    private lateinit var inputCheck: Button
    private var cameraRun: CameraBenchmark? = null
    private val worker = Executors.newSingleThreadExecutor()
    private val cancelled = AtomicBoolean(false)
    private var syntheticRunning = false
    private var visible = false
    private var runtimeReady = false
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && visible) startCamera() else status.text = "Camera not started."
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32,64,32,32) }
        root.addView(TextView(this).apply {
            textSize = 22f; text = "MGazeNet research benchmark"
        })
        status = TextView(this).apply {
            textSize = 16f
            text = "Separate research app. Nothing starts automatically. Camera frames are never saved. Synthetic checks do not establish accuracy."
        }
        root.addView(status)
        synthetic = Button(this).apply { text = "Run synthetic checks and model timing"; setOnClickListener { runSynthetic() } }
        camera = Button(this).apply {
            text = "Start camera timing (125 seconds)"
            setOnClickListener {
                if (ContextCompat.checkSelfPermission(this@BenchmarkActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
                else permission.launch(Manifest.permission.CAMERA)
            }
        }
        root.addView(synthetic); root.addView(camera)
        inputCheck = Button(this).apply {
            text = "Open 20-second input-only setup (camera stays off)"
            setOnClickListener { startActivity(android.content.Intent(this@BenchmarkActivity,InputCheckActivity::class.java)) }
        }
        root.addView(inputCheck)
        accuracy = Button(this).apply {
            text = "Open accuracy research setup (camera stays off)"
            setOnClickListener { startActivity(android.content.Intent(this@BenchmarkActivity,AccuracyActivity::class.java)) }
        }
        root.addView(accuracy)
        calibrationAudit = Button(this).apply {
            text = "Open 16-point calibration audit setup (camera stays off)"
            setOnClickListener { startActivity(android.content.Intent(this@BenchmarkActivity,CalibrationAuditActivity::class.java)) }
        }
        root.addView(calibrationAudit)
        confirmation = Button(this).apply {
            text = "Open direct-screen confirmation setup (camera stays off)"
            setOnClickListener { startActivity(android.content.Intent(this@BenchmarkActivity,ConfirmationActivity::class.java)) }
        }
        root.addView(confirmation)
        root.addView(Button(this).apply { text = "Stop"; setOnClickListener {
            cancelled.set(true); cameraRun?.stop("user_stopped"); status.text = "Stopping…"
        } })
        setContentView(root)
        try { check(OpenCVLoader.initLocal()) { "OpenCV initialization failed" }; runtimeReady = true }
        catch (e: Throwable) { status.text = e.toString(); enable(false) }
    }
    private fun enable(enabled: Boolean) {
        synthetic.isEnabled = enabled; camera.isEnabled = enabled
        inputCheck.isEnabled = enabled; accuracy.isEnabled = enabled; calibrationAudit.isEnabled = enabled
        confirmation.isEnabled = enabled
    }
    private fun runSynthetic() {
        if (syntheticRunning || cameraRun != null) return
        cancelled.set(false); syntheticRunning = true; enable(false)
        status.text = "Running synthetic fixtures; camera remains closed."
        worker.execute {
            val message = try {
                val file = SyntheticRunner.run(applicationContext) { cancelled.get() }
                val outcome = org.json.JSONObject(file.readText()).getString("outcome")
                "Synthetic result: $outcome\nSaved: $file\nExternal parity comparison is still required."
            }
            catch (e: Throwable) { "Synthetic check failed: $e" }
            runOnUiThread { syntheticRunning = false; status.text = message; if (visible) enable(true) }
        }
    }
    private fun startCamera() {
        if (!visible || syntheticRunning || cameraRun != null) return
        enable(false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        cameraRun = CameraBenchmark(applicationContext, this, { status.text = it }, { file ->
            val outcome = org.json.JSONObject(file.readText()).getString("outcome")
            status.text = "Camera result: $outcome\nNumeric timing report: $file\nThis is camera-to-feature timing, not calibrated gaze accuracy."
            cameraRun = null; window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (visible) enable(true)
        }).also { it.start() }
    }
    override fun onStart() { super.onStart(); visible = true; enable(runtimeReady && !syntheticRunning && cameraRun == null) }
    override fun onStop() {
        visible = false; cancelled.set(true); cameraRun?.stop("activity_backgrounded")
        super.onStop()
    }
    override fun onDestroy() { worker.shutdown(); super.onDestroy() }
}
