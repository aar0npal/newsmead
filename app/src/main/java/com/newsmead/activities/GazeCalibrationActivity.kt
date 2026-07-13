package com.newsmead.activities

import android.content.pm.PackageManager
import android.graphics.PointF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.newsmead.databinding.ActivityGazeCalibrationBinding
import com.newsmead.gaze.CalibrationSample
import com.newsmead.gaze.CalibrationStore
import com.newsmead.gaze.LocalGazeSources
import com.newsmead.gaze.LocalRawGazeSource
import java.util.Locale

/**
 * Full-screen 16-dot gaze calibration. Gaze arrives from the local phone-side raw
 * gaze source. For each dot it settles, collects ~1 s of samples, and stores
 * their robust median paired with the dot's phone-screen position. The pairs are
 * written to calibration_16point.csv, from which GazeMapper learns the local
 * raw-feature -> phone-screen mapping used by LocalCalibratedGazeProvider.
 *
 * Run this once per participant before a reading session.
 */
class GazeCalibrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGazeCalibrationBinding
    private var rawGazeSource: LocalRawGazeSource? = null
    private val handler = Handler(Looper.getMainLooper())

    private var points: List<PointF> = emptyList()
    private var currentIndex = 0
    private var collecting = false
    private var started = false
    private val samplesX = ArrayList<Float>()
    private val samplesY = ArrayList<Float>()
    private val pairs = ArrayList<CalibrationSample>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGazeCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableImmersiveMode()
        rawGazeSource = LocalGazeSources.create(this).also { source ->
            source.setOnRawGaze { x, y, _ -> runOnUiThread { onSample(x, y) } }
            source.setOnFps { fps -> runOnUiThread { showFps(fps) } }
            source.start(this)
        }

        // Start the dot sequence only when the researcher taps Start.
        binding.progressText.setText(com.newsmead.R.string.calib_ready_instruction)
        binding.startButton.setOnClickListener {
            binding.startButton.visibility = View.GONE
            beginSequenceWhenLaidOut()
        }
    }


    private fun startRawGazeSource() {
        rawGazeSource?.stop()
        rawGazeSource = LocalGazeSources.create(this).also { source ->
            source.setOnRawGaze { x, y, _ -> runOnUiThread { onSample(x, y) } }
            source.setOnFps { fps -> runOnUiThread { showFps(fps) } }
            source.start(this)
        }
    }

    private fun showFps(fps: Float) {
        binding.fpsText.text = String.format(Locale.US, "%.0f fps", fps)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startRawGazeSource()
        }
    }

    /** Start the dot sequence once the view has a size. */
    private fun beginSequenceWhenLaidOut() {
        if (started) return
        val view = binding.calibrationView
        if (view.width == 0 || view.height == 0) {
            view.post { beginSequenceWhenLaidOut() }
            return
        }
        started = true
        points = computePoints(view.width, view.height)
        showDot(0)
    }

    private fun computePoints(w: Int, h: Int): List<PointF> {
        // 4x4 grid evenly spaced across the inset region (16 points).
        val fractions = floatArrayOf(MARGIN_FRAC, 0.3667f, 0.6333f, 1f - MARGIN_FRAC)
        val xs = fractions.map { w * it }
        val ys = fractions.map { h * it }
        return buildList { for (y in ys) for (x in xs) add(PointF(x, y)) }
    }

    private fun showDot(index: Int) {
        currentIndex = index
        collecting = false
        samplesX.clear()
        samplesY.clear()
        val p = points[index]
        binding.calibrationView.setTarget(p.x, p.y)
        binding.progressText.text = getString(com.newsmead.R.string.calib_progress, index + 1, points.size)
        binding.statusText.text = ""
        handler.postDelayed({ startCollecting() }, SETTLE_MS)
    }

    private fun startCollecting() {
        samplesX.clear()
        samplesY.clear()
        collecting = true
        handler.postDelayed({ finishCollecting() }, COLLECT_MS)
    }

    private fun finishCollecting() {
        collecting = false
        if (samplesX.size < MIN_SAMPLES) {
            Log.w(TAG, "Dot ${currentIndex + 1}: only ${samplesX.size} samples, re-collecting")
            binding.statusText.text = getString(com.newsmead.R.string.calib_retry)
            handler.postDelayed({ showDot(currentIndex) }, RETRY_PAUSE_MS)
            return
        }

        val gazeX = median(samplesX)
        val gazeY = median(samplesY)
        val p = points[currentIndex]
        pairs.add(CalibrationSample(p.x, p.y, gazeX, gazeY))
        Log.i(
            TAG,
            String.format(
                Locale.US,
                "pair %d/%d: screen=(%.0f, %.0f) gaze=(%.4f, %.4f) n=%d",
                currentIndex + 1, points.size, p.x, p.y, gazeX, gazeY, samplesX.size,
            ),
        )

        val next = currentIndex + 1
        if (next < points.size) showDot(next) else finishCalibration()
    }

    private fun finishCalibration() {
        binding.calibrationView.setTarget(null, null)
        binding.progressText.text = getString(com.newsmead.R.string.calib_done, pairs.size, points.size)
        binding.statusText.text = getString(com.newsmead.R.string.calib_saved)
        pairs.forEach { Log.i(TAG, "final ${it.screenX},${it.screenY},${it.gazeX},${it.gazeY}") }
        CalibrationStore.save(this, pairs)
    }

    private fun onSample(fx: Float, fy: Float) {
        if (collecting) {
            samplesX.add(fx)
            samplesY.add(fy)
        }
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2f
    }

    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        rawGazeSource?.stop()
    }

    companion object {
        private const val TAG = "GazeCalib"
        private const val SETTLE_MS = 800L
        private const val COLLECT_MS = 1000L
        private const val RETRY_PAUSE_MS = 400L
        private const val MIN_SAMPLES = 10
        private const val MARGIN_FRAC = 0.1f
        private const val CAMERA_PERMISSION_REQUEST = 4104
    }
}
