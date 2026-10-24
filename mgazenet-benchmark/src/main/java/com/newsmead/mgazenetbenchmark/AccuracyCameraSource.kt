package com.newsmead.mgazenetbenchmark

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.os.SystemClock
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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Explicit-start input for the isolated accuracy Activity. No persistence or NewsMead access. */
class AccuracyCameraSource(private val context: Context, private val owner: LifecycleOwner,
    private val ready: (Map<String, Any?>) -> Unit, private val result: (AccuracySession.Frame) -> Unit,
    private val failed: (String) -> Unit) {
    private val main = ContextCompat.getMainExecutor(context)
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val worker = Executors.newSingleThreadExecutor()
    private val stopped = AtomicBoolean(false)
    private val busy = AtomicBoolean(false)
    private val drops = AtomicLong()
    private val arrivals = AtomicLong()
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var model: MnnEstimator? = null
    private var landmarker: FaceLandmarker? = null
    private var preprocess: Preprocessor? = null
    private var svr: SvrCalibration? = null
    private val frames = CameraFrames()
    @Volatile private var clockReady = false
    private var signature: List<Int>? = null
    private var lastTimestamp = -1L
    private var delegateName = "GPU"
    private var apkHash = ""
    private var vendorHash = ""

    fun start() {
        worker.execute {
            try {
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                java.io.File(context.applicationInfo.sourceDir).inputStream().use { input ->
                    val buffer = ByteArray(65536)
                    var count = input.read(buffer)
                    while (count >= 0) { if (count > 0) digest.update(buffer,0,count); count = input.read(buffer) }
                }
                apkHash = digest.digest().joinToString("") { "%02x".format(it) }
                vendorHash = AccuracyReport.sha256(context.assets.open("vendor-manifest.json").use { it.readBytes() })
                check(AccuracyReport.sha256(context.assets.open("face_landmarker.task").use { it.readBytes() }) ==
                    "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff")
                model = MnnEstimator(context); preprocess = Preprocessor(); svr = SvrCalibration()
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
        arrivals.incrementAndGet()
        if (!busy.compareAndSet(false,true)) { drops.incrementAndGet(); image.close(); return }
        try {
            val captureNs = image.imageInfo.timestamp
            check(captureNs > 0 && captureNs <= SystemClock.elapsedRealtimeNanos()) { "Invalid shared camera timestamp" }
            val shape = listOf(image.width,image.height,image.imageInfo.rotationDegrees)
            val bitmap = frames.copy(image)
            if (signature == null) {
                signature = shape
                val metadata = linkedMapOf<String,Any?>(
                    "model_sha256" to MnnEstimator.MODEL_SHA,"runtime" to MnnEstimator.RUNTIME,
                    "face_landmarker_sha256" to "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff",
                    "localizer" to "Tasks 0.10.29 VIDEO one-face detection=.1 tracking=.1 presence=.5",
                    "delegate" to delegateName,"camera_source_shape_rotation" to shape,
                    "upright_size" to listOf(bitmap.width,bitmap.height),"mirrored" to false,
                    "pixel_contract" to "RGBA unpack; upright RGB; OpenCV uint8 INTER_LINEAR /255 NCHW; only right eye flipped",
                    "camera_clock" to "REALTIME verified","output_clock" to "main_callback_delivery_elapsedRealtimeNanos",
                    "apk_sha256" to apkHash,"vendor_manifest_sha256" to vendorHash,
                    "android_sdk" to android.os.Build.VERSION.SDK_INT,"opencv" to org.opencv.core.Core.VERSION)
                main.execute { if (!stopped.get()) ready(metadata) }
            } else check(signature == shape) { "Camera dimensions or orientation changed; new calibration required" }
            worker.execute {
                try {
                    if (stopped.get()) return@execute
                    val videoMs = captureNs/1_000_000
                    check(videoMs > lastTimestamp) { "Nonmonotonic MediaPipe capture clock" }
                    lastTimestamp = videoMs
                    val mp = BitmapImageBuilder(bitmap).build()
                    try {
                        // BitmapImageBuilder may transfer ownership. Keep the wrapper open until every bitmap read ends.
                        val detected = landmarker!!.detectForVideo(mp,videoMs)
                        val landmarks = detected.faceLandmarks().firstOrNull()
                        val crops = landmarks?.map { GazeGeometry.Landmark(it.x().toDouble(),it.y().toDouble()) }
                            ?.let { GazeGeometry.crops(it,bitmap.width,bitmap.height) }
                        val features = crops?.let { model!!.infer(preprocess!!.prepare(frames.rgb(bitmap),it)) }
                        val prediction = features?.let { if (svr!!.trained) svr!!.predict(it) else null }
                        val reason = when { landmarks == null -> "no_face"; crops == null -> "invalid_crops"; else -> "features" }
                        main.execute {
                            // Delivery time includes main-queue delay. Final queued results precede close completion.
                            result(AccuracySession.Frame(captureNs/1e6,now(),features,crops?.leftOpenness ?: 0.0,
                                crops?.rightOpenness ?: 0.0,prediction,reason,crops?.let {
                                    listOf(listOf(it.face.width,it.face.height),listOf(it.left.width,it.left.height),
                                        listOf(it.right.width,it.right.height))
                                }))
                        }
                    } finally {
                        mp.close()
                    }
                } catch (e: Throwable) { error(e) }
                finally { busy.set(false) }
            }
        } catch (e: Throwable) { busy.set(false); error(e) }
        finally { image.close() }
    }
    fun fit(training: AccuracySession.Training, complete: (Boolean) -> Unit) {
        worker.execute {
            val success = try { if (stopped.get()) false else { svr!!.fit(training.features,training.labels); true } }
                catch (_: Throwable) { false }
            finally { training.features.forEach { it.fill(0f) }; training.labels.forEach { it.fill(0f) } }
            main.execute { complete(success) }
        }
    }
    fun fitAudited(training: CalibrationAuditEngine.Training,
                   complete: (CalibrationAuditEngine.Result?,Boolean) -> Unit) {
        worker.execute {
            var audit: CalibrationAuditEngine.Result? = null
            val success = try {
                if (stopped.get()) false else {
                    audit = CalibrationAuditEngine { SvrCalibration() }.audit(training)
                    if (stopped.get()) return@execute
                    val (features,labels) = CalibrationAuditEngine.flattened(training)
                    svr!!.fit(features,labels)
                    true
                }
            } catch (_: Throwable) { false }
            finally { CalibrationAuditEngine.clear(training) }
            main.execute { complete(audit,success) }
        }
    }
    private fun error(e: Throwable) { main.execute { if (!stopped.get()) failed(e.toString()) } }
    /** Called on main. Drain analyzer submissions before releasing worker-owned resources. */
    fun close(complete: (Map<String,Any?>) -> Unit) {
        if (!stopped.compareAndSet(false,true)) return
        clockReady = false; analysis?.clearAnalyzer(); analysis?.let { provider?.unbind(it) }
        cameraExecutor.shutdown()
        Thread {
            while (!cameraExecutor.awaitTermination(1,TimeUnit.SECONDS)) { /* wait for outstanding copy */ }
            worker.execute {
                val errors = mutableListOf<String>()
                try {
                    listOf<() -> Unit>({ landmarker?.close() },{ model?.close() },{ preprocess?.close() },
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
    companion object { fun now() = SystemClock.elapsedRealtimeNanos()/1e6 }
}
