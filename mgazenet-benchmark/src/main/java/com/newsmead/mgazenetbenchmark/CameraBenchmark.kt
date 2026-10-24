package com.newsmead.mgazenetbenchmark

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Range
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Dedicated camera-to-feature path. It has no gaze provider or calibration store. */
class CameraBenchmark(private val context: Context, private val owner: LifecycleOwner,
                      private val update: (String) -> Unit, private val finished: (File) -> Unit) {
    private val worker = Executors.newSingleThreadExecutor()
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val main = ContextCompat.getMainExecutor(context)
    private val timer = Handler(Looper.getMainLooper())
    private val deadline = Runnable { stop("completed") }
    private val busy = AtomicBoolean(false)
    private val stopped = AtomicBoolean(false)
    private val drops = AtomicLong()
    private val arrivals = AtomicLong()
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var landmarker: FaceLandmarker? = null
    private var model: MnnEstimator? = null
    private var preprocess: Preprocessor? = null
    private val frames = CameraFrames()
    private val report = BenchmarkReport(context, "camera_to_features")
    private val records = JSONArray()
    private val thermal = JSONArray()
    @Volatile private var startedNs = 0L
    @Volatile private var endedNs = Long.MAX_VALUE
    private var lastUpdateNs = 0L
    private var lastVideoMs = 0L
    private var completed = 0
    @Volatile private var realtimeTimestamp = false
    private val latency = ArrayList<Double>()
    private val analysisLatency = ArrayList<Double>()
    private val localization = ArrayList<Double>()
    private val cropTime = ArrayList<Double>()
    private val modelTime = ArrayList<Double>()

    fun start() {
        worker.execute {
            try {
                model = MnnEstimator(context); preprocess = Preprocessor()
                fun create(delegate: Delegate): FaceLandmarker = FaceLandmarker.createFromOptions(context,
                    FaceLandmarker.FaceLandmarkerOptions.builder()
                        .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").setDelegate(delegate).build())
                        .setRunningMode(RunningMode.VIDEO).setNumFaces(1)
                        .setMinFaceDetectionConfidence(.1f).setMinTrackingConfidence(.1f)
                        .setMinFacePresenceConfidence(.5f).build())
                var delegate = "GPU"
                landmarker = try { create(Delegate.GPU) } catch (e: Exception) {
                    delegate = "CPU_fallback"
                    report.root.put("gpu_initialization_error", e.toString())
                    create(Delegate.CPU)
                }
                report.root.put("localizer", "MediaPipe Tasks 0.10.29 VIDEO 478; legacy FaceMesh parity unverified")
                    .put("localizer_delegate", delegate).put("mirrored", false).put("warmup_seconds", 5)
                    .put("planned_measurement_seconds", 120).put("calibration_and_filter_included", false)
                    .put("cameraX_undelivered_frames", "not_observable; busy drops count analyzer arrivals only")
                main.execute { if (!stopped.get()) bind() }
            } catch (e: Throwable) { fail(e) }
        }
    }

    @OptIn(markerClass = [ExperimentalCamera2Interop::class])
    private fun bind() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (stopped.get()) return@addListener
            try {
                provider = future.get()
                val builder = ImageAnalysis.Builder().setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                        ResolutionStrategy(Size(640,480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
                Camera2Interop.Extender(builder).setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(30,30))
                analysis = builder.build().also { it.setAnalyzer(cameraExecutor, ::analyze) }
                val camera = provider!!.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis!!)
                realtimeTimestamp = Camera2CameraInfo.from(camera.cameraInfo).getCameraCharacteristic(
                    CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE) == CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME
                startedNs = SystemClock.elapsedRealtimeNanos()
                timer.postDelayed(deadline, 125_000L)
                update("Camera running: 5-second warm-up, then 120 seconds. No targets or calibration.")
            } catch (e: Throwable) { fail(e) }
        }, main)
    }

    private fun analyze(image: ImageProxy) {
        val arrival = SystemClock.elapsedRealtimeNanos()
        if (stopped.get() || startedNs == 0L) { image.close(); return }
        arrivals.incrementAndGet()
        if (!busy.compareAndSet(false, true)) { drops.incrementAndGet(); image.close(); return }
        try {
            val capture = image.imageInfo.timestamp
            val rotation = image.imageInfo.rotationDegrees
            val sourceW = image.width; val sourceH = image.height
            val bitmap = frames.copy(image)
            val copied = SystemClock.elapsedRealtimeNanos()
            worker.execute {
                try {
                    if (stopped.get()) return@execute
                    val mpImage = BitmapImageBuilder(bitmap).build()
                    val result = try {
                        lastVideoMs = maxOf(lastVideoMs + 1, SystemClock.elapsedRealtime())
                        landmarker!!.detectForVideo(mpImage, lastVideoMs)
                    } finally { mpImage.close() }
                    val localized = SystemClock.elapsedRealtimeNanos()
                    val points = result.faceLandmarks().firstOrNull()
                    val crops = points?.map { GazeGeometry.Landmark(it.x().toDouble(), it.y().toDouble()) }
                        ?.let { GazeGeometry.crops(it, bitmap.width, bitmap.height) }
                    var outcome = if (points == null) "no_face" else "invalid_crops"
                    var prepared = localized; var inferred = localized
                    if (crops != null) {
                        val input = preprocess!!.prepare(frames.rgb(bitmap), crops)
                        prepared = SystemClock.elapsedRealtimeNanos()
                        model!!.infer(input) // Features are used only in memory and then discarded.
                        inferred = SystemClock.elapsedRealtimeNanos()
                        outcome = "features_complete"
                    }
                    val elapsed = (inferred - startedNs) / 1e9
                    val measuring = arrival >= startedNs + 5_000_000_000L && inferred <= minOf(endedNs, startedNs + 125_000_000_000L)
                    if (measuring && crops != null) {
                        completed++
                        analysisLatency.add((inferred - arrival) / 1e6)
                        if (realtimeTimestamp && capture <= inferred) latency.add((inferred - capture) / 1e6)
                        localization.add((localized - copied) / 1e6)
                        cropTime.add((prepared - localized) / 1e6)
                        modelTime.add((inferred - prepared) / 1e6)
                    }
                    records.put(JSONObject().put("elapsed_s", elapsed).put("included_in_measurement", measuring).put("outcome", outcome)
                        .put("source_width", sourceW).put("source_height", sourceH).put("rotation", rotation)
                        .put("upright_width", bitmap.width).put("upright_height", bitmap.height)
                        .put("capture_ns", capture).put("analysis_arrival_ns", arrival).put("output_ns", inferred)
                        .put("copy_rotate_ms", (copied-arrival)/1e6).put("localization_ms", (localized-copied)/1e6)
                        .put("crop_rgb_tensor_ms", (prepared-localized)/1e6).put("model_io_ms", (inferred-prepared)/1e6)
                        .put("observed_busy_drops", drops.get()).put("analyzer_arrivals", arrivals.get()))
                    if (inferred - lastUpdateNs > 1_000_000_000L) {
                        lastUpdateNs = inferred
                        val status = if (Build.VERSION.SDK_INT >= 29) context.getSystemService(PowerManager::class.java).currentThermalStatus else -1
                        thermal.put(JSONObject().put("elapsed_s", elapsed).put("android_thermal_status", status))
                        main.execute { update("${elapsed.toInt()} / 125 seconds; ${completed} measured feature results. Thermal status $status.") }
                    }
                    if (elapsed >= 125.0) main.execute { stop("completed") }
                } catch (e: Throwable) { fail(e) }
                finally { busy.set(false) }
            }
        } catch (e: Throwable) { busy.set(false); fail(e) }
        finally { image.close() }
    }

    private fun fail(error: Throwable) {
        main.execute { stop("failed", error.toString()) }
    }

    /** Main thread. Close native resources on the same worker that created them. */
    fun stop(outcome: String = "stopped", error: String? = null) {
        if (!stopped.compareAndSet(false, true)) return
        val stoppedNs = SystemClock.elapsedRealtimeNanos()
        endedNs = stoppedNs
        timer.removeCallbacks(deadline)
        analysis?.clearAnalyzer(); analysis?.let { provider?.unbind(it) }
        cameraExecutor.shutdown()
        // Barrier after any analyzer copy/submission, then close on inference worker.
        Thread {
            cameraExecutor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)
            worker.execute {
                try {
                    val duration = if (startedNs == 0L) 0.0 else ((stoppedNs - startedNs) / 1e9 - 5).coerceIn(0.0, 120.0)
                    val measuredRecords = (0 until records.length()).map { records.getJSONObject(it) }
                        .filter { it.getBoolean("included_in_measurement") && it.getLong("output_ns") <= stoppedNs }
                    val gaps = measuredRecords.filter { it.getString("outcome") == "features_complete" }
                        .map { it.getLong("output_ns") }.zipWithNext { a, b -> (b-a)/1e6 }
                    report.root.put("outcome", outcome).put("error", error ?: JSONObject.NULL)
                        .put("measurement_wall_seconds", duration).put("completed_feature_results", completed)
                        .put("effective_completion_fps", if (duration > 0) completed / duration else JSONObject.NULL)
                        .put("sensor_timestamp_realtime", realtimeTimestamp).put("observed_busy_drops", drops.get())
                        .put("analyzer_arrivals", arrivals.get()).put("capture_to_feature_ms", BenchmarkReport.stats(latency))
                        .put("measured_analyzed_frames", measuredRecords.size)
                        .put("measured_no_face_frames", measuredRecords.count { it.getString("outcome") == "no_face" })
                        .put("measured_invalid_crop_frames", measuredRecords.count { it.getString("outcome") == "invalid_crops" })
                        .put("feature_interarrival_ms", BenchmarkReport.stats(gaps))
                        .put("analysis_to_feature_ms", BenchmarkReport.stats(analysisLatency))
                        .put("localization_ms", BenchmarkReport.stats(localization))
                        .put("crop_rgb_tensor_ms", BenchmarkReport.stats(cropTime)).put("model_io_ms", BenchmarkReport.stats(modelTime))
                        .put("records", records).put("thermal", thermal)
                    val file = report.save()
                    main.execute { finished(file) }
                } finally {
                    landmarker?.close(); model?.close(); preprocess?.close(); frames.close()
                    worker.shutdown()
                }
            }
        }.start()
    }
}
