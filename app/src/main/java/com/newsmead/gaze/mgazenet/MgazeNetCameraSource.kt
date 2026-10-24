package com.newsmead.gaze.mgazenet

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
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
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Explicit-start production source. MNN inference and native calibration are confined to one worker. */
class MgazeNetCameraSource(private val context: Context, private val owner: LifecycleOwner,
    private val ready: (Map<String, Any?>) -> Unit, private val result: (Frame) -> Unit,
    private val failed: (String) -> Unit,
    private val store: MgazeNetCalibrationStore,
    private val saved: CalibrationBundle.Artifact? = null,
    private val eyelidTelemetryEnabled: Boolean = false) {
    data class Frame(val captureMs: Double, val outputMs: Double, val features: FloatArray?,
        val leftArea: Double, val rightArea: Double, val prediction: FloatArray?, val reason: String,
        val cropSizes: List<List<Int>>?, val posture: MgazeNetPostureGate.Sample?,
        val arrivals: Long, val busyDrops: Long,
        val eyelids: MgazeNetEyelidGeometry.Sample? = null) {
        val eligible get() = features?.let { it.size == 258 && it.all(Float::isFinite) } == true &&
            leftArea.isFinite() && rightArea.isFinite() && leftArea > 10 && rightArea > 10
    }
    private val main = ContextCompat.getMainExecutor(context)
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val worker = Executors.newSingleThreadExecutor()
    private val stopped = AtomicBoolean(false)
    private val failedOnce = AtomicBoolean(false)
    private val drops = AtomicLong()
    private val arrivals = AtomicLong()
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var model: MnnEstimator? = null
    private var landmarker: FaceLandmarker? = null
    private var preprocessors: List<Preprocessor> = emptyList()
    // A saved calibration identifies live reading. Keep at most one live frame in
    // inference so an older queued frame cannot consume the output-age budget.
    // The calibration capture path retains its existing two slots.
    private val preprocessorSlotCount = if (saved == null) PREPROCESSOR_SLOT_COUNT else 1
    private val availablePreprocessors = ArrayBlockingQueue<Preprocessor>(preprocessorSlotCount)
    private var svr: SvrCalibration? = null
    private val frames = CameraFrames()
    @Volatile private var clockReady = false
    private var signature: List<Int>? = null
    private var lastTimestamp = -1L
    private var delegateName = "GPU"
    private val performanceLog = PerformanceLog()
    private val landmarkX = DoubleArray(478)
    private val landmarkY = DoubleArray(478)

    fun start() {
        worker.execute {
            try {
                if (stopped.get()) return@execute
                check(org.opencv.android.OpenCVLoader.initLocal())
                check(CalibrationIdentity.hash(context.assets.open("face_landmarker.task").use { it.readBytes() }) == CalibrationIdentity.LOCALIZER)
                store.verifyNativePersistence()
                model = MnnEstimator(context)
                preprocessors = List(preprocessorSlotCount) { Preprocessor() }
                preprocessors.forEach { availablePreprocessors.add(it) }
                svr = saved?.let { store.load(it) } ?: SvrCalibration()
                fun localizer(delegate: Delegate) = FaceLandmarker.createFromOptions(context,
                    FaceLandmarker.FaceLandmarkerOptions.builder().setBaseOptions(BaseOptions.builder()
                        .setModelAssetPath("face_landmarker.task").setDelegate(delegate).build())
                        .setRunningMode(RunningMode.VIDEO).setNumFaces(1).setMinFaceDetectionConfidence(.1f)
                        .setMinTrackingConfidence(.1f).setMinFacePresenceConfidence(.5f).build())
                landmarker = try { localizer(Delegate.GPU) } catch (_: Exception) {
                    delegateName = "CPU_initialization_fallback"; localizer(Delegate.CPU)
                }
                main.execute { if (!stopped.get()) bind() }
            } catch (e: Throwable) { error(e) }
        }
    }
    @OptIn(markerClass = [ExperimentalCamera2Interop::class])
    private fun bind() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (stopped.get()) return@addListener
            try {
                provider = future.get()
                analysis = ImageAnalysis.Builder().setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                        ResolutionStrategy(Size(640,480),ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
                    .build().also { it.setAnalyzer(cameraExecutor,::analyze) }
                val camera = provider!!.bindToLifecycle(owner,CameraSelector.DEFAULT_FRONT_CAMERA,analysis!!)
                check(Camera2CameraInfo.from(camera.cameraInfo).getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE) ==
                    CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME) {
                    "Shared camera clock unavailable; accuracy capture is not supported by this build on this configuration."
                }
                clockReady = true
            } catch (e: Throwable) { error(e) }
        },main)
    }
    private fun analyze(image: ImageProxy) {
        if (stopped.get() || !clockReady) { image.close(); return }
        val analyzerStartedNs = SystemClock.elapsedRealtimeNanos()
        arrivals.incrementAndGet()
        try {
            val captureNs = image.imageInfo.timestamp
            check(captureNs > 0 && captureNs <= SystemClock.elapsedRealtimeNanos()) { "Invalid shared camera timestamp" }
            val shape = listOf(image.width,image.height,image.imageInfo.rotationDegrees)
            val acquisition = "${image.width}x${image.height}:${image.imageInfo.rotationDegrees}:$delegateName"
            check(saved == null || saved.identity.acquisition == acquisition) { "Camera/localizer configuration changed" }
            val bitmap = frames.copy(image)
            val copyFinishedNs = SystemClock.elapsedRealtimeNanos()
            if (signature == null) {
                signature = shape
                val metadata = linkedMapOf<String,Any?>(
                    "acquisition" to acquisition,
                    "model_sha256" to MnnEstimator.MODEL_SHA,"runtime" to MnnEstimator.RUNTIME,
                    "face_landmarker_sha256" to "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff",
                    "localizer" to "Tasks 0.10.29 VIDEO one-face detection=.1 tracking=.1 presence=.5",
                    "delegate" to delegateName,"camera_source_shape_rotation" to shape,
                    "upright_size" to listOf(bitmap.width,bitmap.height),"mirrored" to false,
                    "pixel_contract" to "RGBA unpack; upright RGB; OpenCV uint8 INTER_LINEAR /255 NCHW; only right eye flipped",
                    "camera_clock" to "REALTIME verified","output_clock" to "main_callback_delivery_elapsedRealtimeNanos",
                    
                    "android_sdk" to android.os.Build.VERSION.SDK_INT,"opencv" to org.opencv.core.Core.VERSION)
                main.execute { if (!stopped.get()) ready(metadata) }
            } else check(signature == shape) { "Camera dimensions or orientation changed; new calibration required" }
            val videoMs = captureNs/1_000_000
            check(videoMs > lastTimestamp) { "Nonmonotonic MediaPipe capture clock" }
            lastTimestamp = videoMs
            val mp = BitmapImageBuilder(bitmap).build()
            try {
                // Localization stays serial on the analyzer, while CPU inference for
                // the prior frame can run concurrently on the worker.
                val localizerStartedNs = SystemClock.elapsedRealtimeNanos()
                val detected = landmarker!!.detectForVideo(mp,videoMs)
                val localizerFinishedNs = SystemClock.elapsedRealtimeNanos()
                val landmarks = detected.faceLandmarks().firstOrNull()
                val points = landmarks?.takeIf { it.size == landmarkX.size }
                points?.forEachIndexed { index, point ->
                    landmarkX[index] = point.x().toDouble()
                    landmarkY[index] = point.y().toDouble()
                }
                // Immutable scalars are captured per frame before the analyzer arrays are reused.
                // This telemetry does not alter pinned crop arithmetic or calibration eligibility.
                val eyelids = if (eyelidTelemetryEnabled) points?.let { MgazeNetEyelidGeometry.from(
                    landmarkX, landmarkY, bitmap.width, bitmap.height) } else null
                val crops = points?.let { GazeGeometry.crops(landmarkX,landmarkY,bitmap.width,bitmap.height) }
                val reason = when { landmarks == null -> "no_face"; crops == null -> "invalid_crops"; else -> "features" }
                val posture = crops?.let { MgazeNetPostureGate.from(it,bitmap.width,bitmap.height) }
                val preprocessor = if (crops == null) null else availablePreprocessors.poll()
                if (crops != null && preprocessor == null) {
                    drops.incrementAndGet()
                    return
                }
                val preprocessStartedNs = SystemClock.elapsedRealtimeNanos()
                val inputs = if (crops == null) null else preprocessor!!.prepare(bitmap,crops)
                val preprocessFinishedNs = SystemClock.elapsedRealtimeNanos()
                val queuedNs = SystemClock.elapsedRealtimeNanos()
                worker.execute {
                    try {
                        if (stopped.get()) return@execute
                        val workerStartedNs = SystemClock.elapsedRealtimeNanos()
                        val inferenceStartedNs = SystemClock.elapsedRealtimeNanos()
                        val features = inputs?.let { model!!.infer(it) }
                        val inferenceFinishedNs = SystemClock.elapsedRealtimeNanos()
                        val prediction = try { features?.let { if (svr!!.trained) svr!!.predict(it) else null } }
                            catch (e: Throwable) { features?.fill(0f); throw e }
                        val finishedNs = SystemClock.elapsedRealtimeNanos()
                        performanceLog.record(
                            face = crops != null,
                            copyMs = (copyFinishedNs - analyzerStartedNs) / 1e6,
                            queueMs = (workerStartedNs - queuedNs) / 1e6,
                            localizerMs = (localizerFinishedNs - localizerStartedNs) / 1e6,
                            preprocessMs = (preprocessFinishedNs - preprocessStartedNs) / 1e6,
                            inferenceMs = (inferenceFinishedNs - inferenceStartedNs) / 1e6,
                            predictionMs = (finishedNs - inferenceFinishedNs) / 1e6,
                            totalMs = (finishedNs - analyzerStartedNs) / 1e6,
                            arrivals = arrivals.get(),
                            drops = drops.get(),
                            nowNs = finishedNs,
                        )
                        deliver(captureNs,features,crops,posture,prediction,reason,eyelids)
                    } catch (e: Throwable) { error(e) }
                    finally { preprocessor?.let { availablePreprocessors.offer(it) } }
                }
            } finally { mp.close() }
        } catch (e: Throwable) { error(e) }
        finally { image.close() }
    }

    private fun deliver(captureNs: Long, features: FloatArray?, crops: GazeGeometry.Crops?,
        posture: MgazeNetPostureGate.Sample?, prediction: FloatArray?, reason: String,
        eyelids: MgazeNetEyelidGeometry.Sample?) {
        main.execute callback@{
            // Delivery time includes main-queue delay. Final queued results precede close completion.
            if (stopped.get()) { features?.fill(0f); return@callback }
            try { result(Frame(captureNs/1e6,now(),features,crops?.leftOpenness ?: 0.0,
                crops?.rightOpenness ?: 0.0,prediction,reason,crops?.let {
                    listOf(listOf(it.face.width,it.face.height),listOf(it.left.width,it.left.height),
                        listOf(it.right.width,it.right.height))
                },posture,arrivals.get(),drops.get(),eyelids)) } finally { features?.fill(0f) }
        }
    }
    fun fit(features: Array<FloatArray>, labels: Array<FloatArray>, complete: (Boolean) -> Unit) {
        worker.execute {
            val success = try { if (stopped.get()) false else { svr!!.fit(features,labels); true } }
                catch (_: Throwable) { false }
                finally { features.forEach { it.fill(0f) }; labels.forEach { it.fill(0f) } }
            main.execute { if (!stopped.get()) complete(success) }
        }
    }
    fun save(identity: CalibrationIdentity, complete: (Boolean) -> Unit) {
        worker.execute {
            val success = try { if (stopped.get()) false else { store.save(identity,svr!!) { !stopped.get() }; true } }
                catch (_: Throwable) { false }
            main.execute { if (!stopped.get()) complete(success) }
        }
    }
    private fun error(e: Throwable) {
        if (!failedOnce.compareAndSet(false,true)) return
        clockReady = false
        main.execute {
            if (!stopped.get()) {
                // Error class is safe; exception messages may contain native model content.
                failed("MGazeNet stopped (${e.javaClass.simpleName}). Resolve the error before restarting.")
                close {}
            }
        }
    }
    /** Stop capture while retaining the worker-owned fitted models for the explicit Save decision. */
    fun pauseCapture() {
        clockReady = false; analysis?.clearAnalyzer(); analysis?.let { provider?.unbind(it) }
    }
    /** Called on main. Drain analyzer submissions before releasing worker-owned resources. */
    fun close(complete: (Map<String,Any?>) -> Unit) {
        if (!stopped.compareAndSet(false,true)) return
        pauseCapture()
        cameraExecutor.shutdown()
        Thread {
            while (!cameraExecutor.awaitTermination(1,TimeUnit.SECONDS)) { /* wait for outstanding copy */ }
            worker.execute {
                val errors = mutableListOf<String>()
                try {
                    listOf<() -> Unit>({ landmarker?.close() },{ model?.close() },
                        { preprocessors.forEach { it.close() }; preprocessors = emptyList(); availablePreprocessors.clear() },
                        { svr?.close() },{ frames.close() }).forEach { close ->
                        runCatching { close() }.exceptionOrNull()?.let { errors.add(it.toString()) }
                    }
                }
                finally {
                    worker.shutdown()
                    main.execute { complete(mapOf("analyzer_arrivals" to arrivals.get(),"observed_busy_drops" to drops.get(),
                        "cameraX_undelivered_frames" to "not_observable","resource_close_errors" to errors)) }
                }
            }
        }.start()
    }
    private class PerformanceLog {
        private var startedNs = 0L
        private var samples = 0
        private var faceSamples = 0
        private var copyTotal = 0.0
        private var queueTotal = 0.0
        private var localizerTotal = 0.0
        private var preprocessTotal = 0.0
        private var inferenceTotal = 0.0
        private var predictionTotal = 0.0
        private var total = 0.0

        fun record(face: Boolean, copyMs: Double, queueMs: Double, localizerMs: Double,
            preprocessMs: Double, inferenceMs: Double, predictionMs: Double, totalMs: Double,
            arrivals: Long, drops: Long, nowNs: Long) {
            if (startedNs == 0L) startedNs = nowNs
            samples++
            if (face) faceSamples++
            copyTotal += copyMs
            queueTotal += queueMs
            localizerTotal += localizerMs
            preprocessTotal += preprocessMs
            inferenceTotal += inferenceMs
            predictionTotal += predictionMs
            total += totalMs
            if (nowNs - startedNs < PERFORMANCE_LOG_WINDOW_NS) return
            Log.i(PERFORMANCE_TAG, String.format(Locale.US,
                "samples=%d face=%d arrivals=%d drops=%d avg_ms copy=%.1f queue=%.1f localizer=%.1f preprocess=%.1f inference=%.1f svr=%.1f total=%.1f",
                samples,faceSamples,arrivals,drops,copyTotal/samples,queueTotal/samples,localizerTotal/samples,
                preprocessTotal/samples,inferenceTotal/samples,predictionTotal/samples,total/samples))
            startedNs = nowNs
            samples = 0
            faceSamples = 0
            copyTotal = 0.0
            queueTotal = 0.0
            localizerTotal = 0.0
            preprocessTotal = 0.0
            inferenceTotal = 0.0
            predictionTotal = 0.0
            total = 0.0
        }
    }

    companion object {
        private const val PREPROCESSOR_SLOT_COUNT = 2
        private const val PERFORMANCE_LOG_WINDOW_NS = 2_000_000_000L
        private const val PERFORMANCE_TAG = "MGazeNetPerf"
        fun now() = SystemClock.elapsedRealtimeNanos()/1e6
    }
}
