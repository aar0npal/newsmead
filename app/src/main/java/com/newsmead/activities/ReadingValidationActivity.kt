package com.newsmead.activities

import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.NestedScrollView
import com.newsmead.data.StudyConfig
import com.newsmead.databinding.ActivityReadingValidationBinding
import com.newsmead.gaze.gazeCoordinateFrame
import com.newsmead.gaze.getVisibleRectOnScreen
import com.newsmead.gaze.physicalDisplaySize
import com.newsmead.gaze.GazeOverlayView
import com.newsmead.gaze.GazeTargetStabilizer
import com.newsmead.gaze.ComparisonLaunchSpec
import com.newsmead.gaze.ComparisonProtocol
import com.newsmead.gaze.ComparisonReadingBinding
import com.newsmead.gaze.ComparisonRecordBinding
import com.newsmead.gaze.ComparisonRuntime
import com.newsmead.gaze.mgazenet.MgazeNetGazeProvider
import com.newsmead.gaze.mgazenet.MgazeNetCalibrationStore
import com.newsmead.gaze.mgazenet.CalibrationIdentity
import com.newsmead.gaze.LineAoiMapper
import com.newsmead.gaze.ReadingValidationCheckpoint
import com.newsmead.gaze.ReadingValidationFeedbackMode
import com.newsmead.gaze.ReadingValidationLine
import com.newsmead.gaze.ReadingValidationMetrics
import com.newsmead.gaze.ReadingValidationPhase
import com.newsmead.gaze.ReadingValidationProtocol
import com.newsmead.gaze.ReadingValidationSessionLog
import com.newsmead.gaze.ReadingValidationTrialState
import com.newsmead.gaze.ReadingValidationWord
import com.newsmead.gaze.ReadingVerticalAlignment
import com.newsmead.gaze.ReadingVerticalAlignmentFit
import com.newsmead.gaze.ReadingVerticalAlignmentMode
import com.newsmead.gaze.ReadingVerticalCorrection
import com.newsmead.gaze.ReadingVerticalReference
import com.newsmead.gaze.ReadingVerticalReferenceAggregate
import com.newsmead.gaze.TextTarget
import java.util.Locale

/** Known-target spatial validation for the unchanged calibrated gaze pipeline. */
class ReadingValidationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReadingValidationBinding
    private val handler = Handler(Looper.getMainLooper())
    private val textLocation = IntArray(2)
    private val rootLocation = IntArray(2)
    private val readingVisibleRect = Rect()
    private val metrics = ReadingValidationMetrics()

    private var gazeProvider: MgazeNetGazeProvider? = null
    private var gazeOverlay: GazeOverlayView? = null
    private var mapper: LineAoiMapper? = null
    private val stabilizer = GazeTargetStabilizer()
    private var sessionLog: ReadingValidationSessionLog? = null

    private var phase = ReadingValidationPhase.SETUP
    private var stepId = "setup"
    private var trialState = ReadingValidationTrialState.PREPARE
    private var expectedCheckpoint: ReadingValidationCheckpoint? = null
    private var wordCheckpoints: List<ReadingValidationCheckpoint> = emptyList()
    private var lineCheckpoints: List<ReadingValidationCheckpoint> = emptyList()
    private var orderVariant = "A"
    private var verticalAlignmentMode = ReadingVerticalAlignmentMode.OFF
    private var verticalReferences: List<ReadingVerticalReference> = emptyList()
    private val verticalAggregates = ArrayList<ReadingVerticalReferenceAggregate>()
    private val activeVerticalSamples = ArrayList<Float>()
    private var activeVerticalReference: ReadingVerticalReference? = null
    private var verticalAlignmentFit: ReadingVerticalAlignmentFit? = null
    private var verticalCorrection: ReadingVerticalCorrection? = null
    private var running = false
    private var finished = false
    private var preparingComparison = false
    private var programmaticScrollUntilMs = 0L
    private var comparisonSpec: ComparisonLaunchSpec? = null
    private var pendingDeliveryId: Long? = null
    private var pendingDeliveryElapsedNs: Long? = null
    private var fallbackDeliveryId = 0L
    private var feedbackMode = ReadingValidationFeedbackMode.CONTINUOUS_ORANGE_DOT_ARTICLE_SMOOTHED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReadingValidationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvValidationText.setTextSize(
            TypedValue.COMPLEX_UNIT_DIP,
            StudyConfig.ARTICLE_FONT_SIZE_DP,
        )
        binding.tvValidationText.text = VALIDATION_PASSAGE
        binding.tvValidationProgress.text = "Accuracy validation · about 3 minutes"
        binding.tvValidationInstruction.text =
            "This test measures performance on known words and visibly highlighted physical lines while the orange gaze dot remains visible. It does not score free reading or adaptive behavior."

        val comparisonLaunch = ComparisonRuntime.parseLaunch(intent)
        if (comparisonLaunch.requested) {
            val spec = comparisonLaunch.spec
            if (spec == null) {
                binding.tvValidationProgress.text = "Comparison setup rejected"
                binding.tvValidationInstruction.text = comparisonLaunch.error
                binding.btnValidationAction.isEnabled = false
            } else {
                comparisonSpec = spec
                binding.tvValidationProgress.text = "Matched comparison · ${spec.slot.id}"
                binding.tvValidationInstruction.text =
                    "Order ${spec.orderVariant} is locked. Start verifies the exact installed build and saved slot calibration before opening the camera."
                binding.btnValidationAction.text = "Start reading validation"
            }
        }

        binding.btnValidationAction.setOnClickListener { showRunSetup() }
        binding.btnCloseValidation.setOnClickListener { requestStop() }
        binding.nsvValidationText.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                if (!running || scrollY == oldScrollY) return@OnScrollChangeListener
                sessionLog?.logScroll(
                    phase,
                    stepId,
                    trialState,
                    scrollY,
                    oldScrollY,
                    if (System.currentTimeMillis() <= programmaticScrollUntilMs) "programmatic" else "participant",
                )
            },
        )
    }

    override fun onPause() {
        sessionLog?.flush()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (!finished && sessionLog != null) {
            finishSessionAfterProviderStops("interrupted", metrics.summary())
        } else {
            gazeProvider?.stop()
            gazeProvider = null
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    private fun showRunSetup() {
        val issue = MgazeNetCalibrationStore(this).compatibilityIssue(binding.root)
        if (issue != null) {
            AlertDialog.Builder(this)
                .setTitle("Fresh calibration required")
                .setMessage(issue)
                .setPositiveButton("OK", null)
                .show()
            return
        }
        comparisonSpec?.let { spec ->
            orderVariant = spec.orderVariant
            verticalAlignmentMode = ReadingVerticalAlignmentMode.OFF
            beginSession(spec.slot.id, 16)
            return
        }
        val runNumber = getPreferences(MODE_PRIVATE).getInt(PREF_RUN_COUNT, 0) + 1
        orderVariant = if (runNumber % 2 == 1) "A" else "B"
        verticalAlignmentMode = ReadingVerticalAlignmentMode.OFF
        showRunLabelSetup(runNumber, CalibrationIdentity.FIT_TARGET_COUNT)
    }

    private fun showRunLabelSetup(runNumber: Int, calibrationPointCount: Int) {
        val modeLabel = verticalAlignmentMode.name.lowercase(Locale.ROOT)
        val label = EditText(this).apply {
            hint = "Example: font22_article-smoothed_run1"
            inputType = InputType.TYPE_CLASS_TEXT
            setText("font22_article-smoothed_run$runNumber")
            setSelectAllOnFocus(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Start known-target visible-feedback validation")
            .setMessage(
                "Alignment $modeLabel · order $orderVariant. First you will see an unscored orange-dot preview. " +
                    "The orange gaze dot remains visible and uses the same gaze smoothing as article reading during the reference, word, and line trials.",
            )
            .setView(label)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                getPreferences(MODE_PRIVATE).edit().putInt(PREF_RUN_COUNT, runNumber).apply()
                beginSession(
                    label.text.toString().trim().ifEmpty { "font22_article-smoothed_run$runNumber" },
                    calibrationPointCount,
                )
            }
            .show()
    }

    private fun beginSession(runLabel: String, calibrationPointCount: Int) {
        if (running || preparingComparison) return
        val layout = binding.tvValidationText.layout
        if (layout == null) {
            binding.tvValidationText.post { beginSession(runLabel, calibrationPointCount) }
            return
        }
        val lines = laidOutLines()
        try {
            wordCheckpoints = ReadingValidationProtocol.localizationCheckpoints(lines, orderVariant)
            lineCheckpoints = ReadingValidationProtocol.lineReadingCheckpoints(lines, orderVariant)
        } catch (e: IllegalArgumentException) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            return
        }

        val comparison = comparisonSpec
        if (comparison != null) {
            val calibrationFingerprint = MgazeNetCalibrationStore(this).fingerprint()
            if (!ComparisonProtocol.isSha256(calibrationFingerprint)) {
                Toast.makeText(this, "The slot calibration fingerprint is unavailable.", Toast.LENGTH_LONG).show()
                return
            }
            val layoutSha256 = ComparisonProtocol.readingLayoutSha256(
                passage = VALIDATION_PASSAGE,
                lines = lines,
                wordTargets = wordCheckpoints,
                lineTargets = lineCheckpoints,
                lineHeightPx = binding.tvValidationText.lineHeight.toFloat(),
                textSizePx = binding.tvValidationText.textSize,
                viewportWidthPx = binding.nsvValidationText.width,
                viewportHeightPx = binding.nsvValidationText.height,
            )
            preparingComparison = true
            binding.btnValidationAction.isEnabled = false
            binding.tvValidationInstruction.text = "Verifying the installed comparison build…"
            val comparisonDisplaySize = binding.root.physicalDisplaySize()
            val comparisonDensityDpi = resources.displayMetrics.densityDpi
            val comparisonRotation = binding.root.display.rotation
            Thread {
                val resolved = runCatching {
                    ComparisonRuntime.resolveIdentity(
                        this,
                        comparison,
                        comparisonDisplaySize.x,
                        comparisonDisplaySize.y,
                        comparisonDensityDpi,
                        comparisonRotation,
                    )
                }
                runOnUiThread {
                    preparingComparison = false
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    resolved.fold(
                        onSuccess = { runtime ->
                            val comparisonBinding = ComparisonReadingBinding(
                                ComparisonRecordBinding(runtime, calibrationFingerprint),
                                layoutSha256,
                                ComparisonProtocol.passageSha256(VALIDATION_PASSAGE),
                            )
                            startPreparedSession(
                                runLabel,
                                calibrationPointCount,
                                layout,
                                comparisonBinding,
                            )
                        },
                        onFailure = { failure ->
                            binding.btnValidationAction.isEnabled = true
                            binding.tvValidationInstruction.text =
                                failure.message ?: "The comparison build could not be verified."
                        },
                    )
                }
            }.start()
            return
        }
        startPreparedSession(runLabel, calibrationPointCount, layout, null)
    }

    private fun startPreparedSession(
        runLabel: String,
        calibrationPointCount: Int,
        layout: android.text.Layout,
        comparisonBinding: ComparisonReadingBinding?,
    ) {
        if (running) return

        feedbackMode = if (comparisonBinding == null) {
            ReadingValidationFeedbackMode.CONTINUOUS_ORANGE_DOT_ARTICLE_SMOOTHED
        } else {
            ReadingValidationFeedbackMode.HIDDEN_AFTER_PREVIEW
        }

        val display = resources.displayMetrics
        val displaySize = binding.root.physicalDisplaySize()
        sessionLog = try {
            ReadingValidationSessionLog(
                context = this,
                runLabel = runLabel,
                orderVariant = orderVariant,
                screenWidthPx = displaySize.x,
                screenHeightPx = displaySize.y,
                densityDpi = display.densityDpi,
                rawFeatureMode = "mgazenet_258_svr_unfiltered_v1",
                calibrationPointCount = calibrationPointCount,
                calibrationFingerprint = MgazeNetCalibrationStore(this).fingerprint(),
                driftCorrectionActive = false,
                verticalAlignmentMode = verticalAlignmentMode,
                feedbackMode = feedbackMode,
                comparison = comparisonBinding,
            ).also {
                it.logLayout(
                    textLength = binding.tvValidationText.text.length,
                    lineCount = layout.lineCount,
                    lineHeightPx = binding.tvValidationText.lineHeight.toFloat(),
                    textSizePx = binding.tvValidationText.textSize,
                    viewportWidthPx = binding.nsvValidationText.width,
                    viewportHeightPx = binding.nsvValidationText.height,
                )
                it.logProtocolPlan(
                    ComparisonProtocol.passageSha256(VALIDATION_PASSAGE),
                    wordCheckpoints,
                    lineCheckpoints,
                )
            }
        } catch (failure: Exception) {
            binding.btnValidationAction.isEnabled = true
            binding.tvValidationInstruction.text =
                failure.message ?: "The comparison slot is not ready for reading."
            return
        }

        running = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        showDotPreview()
        attachGazePipeline()
        gazeOverlay?.setDebugVisualsEnabled(true)
    }

    private fun attachGazePipeline() {
        val overlay = GazeOverlayView(this).also {
            it.setDotOnly(feedbackMode.visibleDuringScoredTrials)
            gazeOverlay = it
        }
        (binding.root as ViewGroup).addView(
            overlay,
            ConstraintLayout.LayoutParams(0, 0).apply {
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            },
        )
        overlay.bringToFront()
        binding.verticalAlignmentTarget.bringToFront()
        binding.validationControlPanel.bringToFront()
        binding.tvValidationCountdown.bringToFront()

        mapper = LineAoiMapper(binding.tvValidationText)
        val provider = MgazeNetGazeProvider(
            this,
            binding.root,
            smoothForReading = feedbackMode.smoothForReading,
        )
        provider.onFps = { fps -> sessionLog?.logFps(fps,phase,stepId,trialState) }
        provider.onFailure = { message ->
            Toast.makeText(this,message,Toast.LENGTH_LONG).show()
            handler.removeCallbacksAndMessages(null)
            finishSessionAfterProviderStops("mgazenet_failed", metrics.summary()) { finish() }
        }
        provider.onObservation = { observation ->
            fallbackDeliveryId = maxOf(fallbackDeliveryId, observation.deliveryId)
            sessionLog?.logMgazeNetObservation(
                observation.captureMs,
                observation.deliveryElapsedNs,
                observation.deliveryId,
                observation.reason,
                observation.rawX,
                observation.rawY,
                observation.arrivals,
                observation.busyDrops,
                phase,
                stepId,
                trialState,
            )
            if (observation.reason == "coordinate") {
                pendingDeliveryId = observation.deliveryId
                pendingDeliveryElapsedNs = observation.deliveryElapsedNs
            }
            if (observation.reason != "coordinate") gazeOverlay?.clearGaze()
        }
        provider.setOnGaze { x, y -> runOnUiThread {
            val elapsedNs = pendingDeliveryElapsedNs ?: SystemClock.elapsedRealtimeNanos()
            val deliveryId = pendingDeliveryId ?: ++fallbackDeliveryId
            pendingDeliveryElapsedNs = null
            pendingDeliveryId = null
            onGaze(x, y, elapsedNs, deliveryId)
        } }
        gazeProvider = provider
        provider.start(this)
    }

    private fun showDotPreview() {
        phase = ReadingValidationPhase.SETUP
        stepId = "unscored_dot_preview"
        trialState = ReadingValidationTrialState.PREPARE
        expectedCheckpoint = null
        clearHighlight()
        binding.nsvValidationText.setScrollingEnabled(true)
        gazeOverlay?.setDebugVisualsEnabled(true)
        binding.tvValidationProgress.text = "Unscored preview · not included in accuracy"
        binding.tvValidationInstruction.text = if (feedbackMode.visibleDuringScoredTrials) {
            "Look at several words around the page. The orange dot should generally follow your gaze. It will remain visible throughout this test. When ready, start the measured test."
        } else {
            "Look at several words around the page. The orange dot should generally follow your gaze. It will be hidden during measured trials. When ready, start the measured test."
        }
        binding.btnValidationAction.text = "Start vertical references"
        binding.btnValidationAction.visibility = View.VISIBLE
        binding.btnValidationAction.setOnClickListener {
            showVerticalAlignmentInstructions()
        }
        sessionLog?.logProtocolState(
            phase, stepId, trialState, binding.tvValidationInstruction.text.toString(),
        )
    }

    private fun showVerticalAlignmentInstructions() {
        val mode = verticalAlignmentMode.name
        val feedbackInstruction = if (feedbackMode.visibleDuringScoredTrials) {
            "The orange gaze dot remains visible; "
        } else {
            "The gaze dot will be hidden; "
        }
        AlertDialog.Builder(this)
            .setTitle("Vertical references · $mode")
            .setMessage(
                "Three orange targets will appear at the top, middle, and bottom of the reading surface. " +
                    "Look at the center of each target and keep looking until the next one appears. " +
                    feedbackInstruction +
                    "finding and measurement happen automatically, with no instructions visible while a target is active.",
            )
            .setNegativeButton("Back", null)
            .setPositiveButton("Begin") { _, _ ->
                binding.validationControlPanel.visibility = View.GONE
                gazeOverlay?.setDebugVisualsEnabled(feedbackMode.visibleDuringScoredTrials)
                clearHighlight()
                binding.nsvValidationText.setScrollingEnabled(false)
                programmaticScrollUntilMs = System.currentTimeMillis() + 1_000L
                binding.nsvValidationText.scrollTo(0, 0)
                binding.root.post {
                    if (prepareVerticalReferences()) runCountdown { startVerticalReference(0) }
                }
            }
            .show()
    }

    private fun prepareVerticalReferences(): Boolean {
        if (!binding.nsvValidationText.getVisibleRectOnScreen(readingVisibleRect)) {
            Toast.makeText(this, "Reading surface unavailable. Please reopen the test.", Toast.LENGTH_LONG).show()
            finishSessionAfterProviderStops("reading_surface_not_visible", metrics.summary()) { finish() }
            return false
        }
        logCoordinateFrames()
        val centerX = readingVisibleRect.exactCenterX()
        val top = readingVisibleRect.top.toFloat()
        val height = readingVisibleRect.height().toFloat()
        verticalReferences = listOf(
            ReadingVerticalReference("vertical_top", centerX, top + height * 0.20f),
            ReadingVerticalReference("vertical_middle", centerX, top + height * 0.50f),
            ReadingVerticalReference("vertical_bottom", centerX, top + height * 0.80f),
        )
        verticalAggregates.clear()
        verticalAlignmentFit = null
        verticalCorrection = null
        return true
    }

    private fun startVerticalReference(index: Int) {
        if (index >= verticalReferences.size) {
            finishVerticalReferences()
            return
        }
        val reference = verticalReferences[index]
        phase = ReadingValidationPhase.VERTICAL_ALIGNMENT
        stepId = reference.id
        trialState = ReadingValidationTrialState.ACQUIRE
        expectedCheckpoint = null
        activeVerticalReference = reference
        activeVerticalSamples.clear()
        positionVerticalTarget(reference)
        binding.verticalAlignmentTarget.visibility = View.VISIBLE
        binding.verticalAlignmentTarget.bringToFront()
        gazeOverlay?.bringToFront()
        sessionLog?.logProtocolState(
            phase,
            stepId,
            trialState,
            "Acquire the orange vertical reference; no instruction UI is visible",
        )

        handler.postDelayed({
            logCoordinateFrames()
            trialState = ReadingValidationTrialState.MEASURE
            activeVerticalSamples.clear()
            sessionLog?.logProtocolState(
                phase,
                stepId,
                trialState,
                "Measure fixation on the orange vertical reference; no instruction UI is visible",
            )
            handler.postDelayed(
                { completeVerticalReference(index, reference) },
                ReadingValidationProtocol.ALIGNMENT_MEASURE_MS,
            )
        }, ReadingValidationProtocol.ALIGNMENT_ACQUIRE_MS)
    }

    private fun completeVerticalReference(index: Int, reference: ReadingVerticalReference) {
        val aggregate = ReadingVerticalAlignment.aggregate(reference, activeVerticalSamples)
            ?: ReadingVerticalReferenceAggregate(reference, 0, Float.NaN)
        verticalAggregates += aggregate
        sessionLog?.logVerticalReference(aggregate)
        activeVerticalReference = null
        activeVerticalSamples.clear()
        startVerticalReference(index + 1)
    }

    private fun finishVerticalReferences() {
        binding.verticalAlignmentTarget.visibility = View.GONE
        activeVerticalReference = null
        phase = ReadingValidationPhase.SETUP
        stepId = "vertical_alignment_result"
        trialState = ReadingValidationTrialState.PREPARE
        val fit = ReadingVerticalAlignment.fit(
            verticalAggregates,
            binding.tvValidationText.lineHeight.toFloat(),
        )
        verticalAlignmentFit = fit
        val applied = verticalAlignmentMode == ReadingVerticalAlignmentMode.ON && fit.accepted
        verticalCorrection = fit.correction.takeIf { applied }
        sessionLog?.logVerticalAlignmentFit(verticalAlignmentMode, fit, applied)
        sessionLog?.flush()

        if (verticalAlignmentMode == ReadingVerticalAlignmentMode.ON && !fit.accepted) {
            showAlignmentRejected(fit)
        } else {
            showWordInstructions()
        }
    }

    private fun showAlignmentRejected(fit: ReadingVerticalAlignmentFit) {
        AlertDialog.Builder(this)
            .setTitle("Vertical alignment rejected")
            .setMessage(
                "The safety checks rejected this fit (${fit.reason}). No correction was applied, " +
                    "and the ON accuracy trials will not run under a false label.",
            )
            .setCancelable(false)
            .setPositiveButton("Save and close") { _, _ -> finishAlignmentRejected() }
            .show()
    }

    private fun finishAlignmentRejected() {
        if (finished) return
        binding.verticalAlignmentTarget.visibility = View.GONE
        val fileName = sessionLog?.fileName().orEmpty()
        finishSessionAfterProviderStops("alignment_rejected", metrics.summary()) {
            Toast.makeText(this, "Alignment evidence saved: $fileName", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun showWordInstructions() {
        val alignmentMessage = if (verticalAlignmentMode == ReadingVerticalAlignmentMode.ON) {
            val fit = verticalAlignmentFit
            "The vertical alignment passed and is active for this session " +
                "(gain ${String.format(Locale.US, "%.3f", fit?.gain)}). "
        } else {
            "The vertical references were recorded but not applied because this is the OFF control. "
        }
        val feedbackInstruction = if (feedbackMode.visibleDuringScoredTrials) {
            "The orange gaze dot remains visible, "
        } else {
            "The gaze dot is hidden, "
        }
        AlertDialog.Builder(this)
            .setTitle("Word accuracy test")
            .setMessage(
                alignmentMessage +
                    "Eight words will appear highlighted in yellow. Look only at the yellow word and keep looking at it until the next highlighted word appears. " +
                    "The first 3 seconds are unscored finding time; measurement then happens automatically for 2.5 seconds. " +
                    feedbackInstruction +
                    "and there will be no instructions to read while a target is active.",
            )
            .setCancelable(false)
            .setPositiveButton("Begin") { _, _ ->
                binding.validationControlPanel.visibility = View.GONE
                gazeOverlay?.setDebugVisualsEnabled(feedbackMode.visibleDuringScoredTrials)
                stabilizer.reset()
                runCountdown { startWordCheckpoint(0) }
            }
            .show()
    }

    private fun onGaze(x: Float, y: Float, deliveryElapsedNs: Long, deliveryId: Long) {
        if (!running || finished) return
        val timestampMs = System.currentTimeMillis()
        val monotonicTimestampMs = deliveryElapsedNs / 1_000_000L
        if (phase == ReadingValidationPhase.VERTICAL_ALIGNMENT &&
            trialState == ReadingValidationTrialState.MEASURE &&
            activeVerticalReference != null
        ) activeVerticalSamples += y
        val effectiveY = verticalCorrection?.applyY(y) ?: y
        gazeOverlay?.setGazeScreen(x, effectiveY)
        val baseTarget = mapper?.targetAt(x, y) ?: TextTarget.INVALID
        val rawTarget = mapper?.targetAt(x, effectiveY) ?: TextTarget.INVALID
        val stableTarget = stabilizer.update(rawTarget, monotonicTimestampMs)
        if ((phase == ReadingValidationPhase.LOCALIZATION ||
                phase == ReadingValidationPhase.GUIDED_LINE_READING) &&
            trialState == ReadingValidationTrialState.MEASURE
        ) expectedCheckpoint?.let { metrics.record(it, stableTarget) }

        binding.tvValidationText.getLocationOnScreen(textLocation)
        sessionLog?.logGaze(
            timestampMs = timestampMs,
            deliveryElapsedNs = deliveryElapsedNs,
            deliveryId = deliveryId,
            phase = phase,
            stepId = stepId,
            state = trialState,
            expected = expectedCheckpoint,
            gazeX = x,
            baseGazeY = y,
            effectiveGazeY = effectiveY,
            baseTarget = baseTarget,
            rawTarget = rawTarget,
            stableTarget = stableTarget,
            scrollY = binding.nsvValidationText.scrollY,
            textTopOnScreen = textLocation[1],
        )
    }

    private fun positionVerticalTarget(reference: ReadingVerticalReference) {
        binding.root.getLocationOnScreen(rootLocation)
        val target = binding.verticalAlignmentTarget
        val width = target.measuredWidth.takeIf { it > 0 } ?: target.layoutParams.width
        val height = target.measuredHeight.takeIf { it > 0 } ?: target.layoutParams.height
        target.x = reference.targetX - rootLocation[0] - width / 2f
        target.y = reference.targetY - rootLocation[1] - height / 2f
    }

    private fun logCoordinateFrames() {
        sessionLog?.logCoordinateFrames(
            phase, stepId,
            binding.root.gazeCoordinateFrame(),
            binding.nsvValidationText.gazeCoordinateFrame(),
            binding.tvValidationText.gazeCoordinateFrame(),
        )
    }

    private fun startWordCheckpoint(index: Int) {
        if (index >= wordCheckpoints.size) {
            showLineReadingIntro()
            return
        }
        val checkpoint = wordCheckpoints[index]
        phase = ReadingValidationPhase.LOCALIZATION
        stepId = checkpoint.id
        trialState = ReadingValidationTrialState.ACQUIRE
        expectedCheckpoint = checkpoint
        stabilizer.reset()
        highlight(checkpoint)
        binding.nsvValidationText.setScrollingEnabled(false)
        scrollCheckpointIntoView(checkpoint)
        sessionLog?.logProtocolState(
            phase,
            stepId,
            trialState,
            "Acquire the highlighted word; no instruction UI is visible",
            checkpoint,
        )

        handler.postDelayed({
            logCoordinateFrames()
            trialState = ReadingValidationTrialState.MEASURE
            sessionLog?.logProtocolState(
                phase,
                stepId,
                trialState,
                "Measure continued fixation on the highlighted word; no instruction UI is visible",
                checkpoint,
            )
            handler.postDelayed(
                { startWordCheckpoint(index + 1) },
                ReadingValidationProtocol.LOCALIZATION_MEASURE_MS,
            )
        }, ReadingValidationProtocol.LOCALIZATION_ACQUIRE_MS)
    }

    private fun showLineReadingIntro() {
        expectedCheckpoint = null
        trialState = ReadingValidationTrialState.PREPARE
        clearHighlight()
        binding.nsvValidationText.setScrollingEnabled(true)
        sessionLog?.flush()
        val feedbackInstruction = if (feedbackMode.visibleDuringScoredTrials) {
            "The orange gaze dot remains visible. "
        } else {
            "The gaze dot remains hidden. "
        }
        AlertDialog.Builder(this)
            .setTitle("Guided line accuracy test")
            .setMessage(
                "Six complete physical lines will appear highlighted in yellow. Read only the yellow line from left to right once, then keep your gaze on that same line until the next line appears. " +
                    feedbackInstruction +
                    "Finding time and measurement happen automatically, and no instructions will be visible while a line is active.",
            )
            .setCancelable(false)
            .setPositiveButton("Begin") { _, _ ->
                binding.validationControlPanel.visibility = View.GONE
                stabilizer.reset()
                runCountdown { startLineCheckpoint(0) }
            }
            .show()
    }

    private fun startLineCheckpoint(index: Int) {
        if (index >= lineCheckpoints.size) {
            completeSession()
            return
        }
        val checkpoint = lineCheckpoints[index]
        phase = ReadingValidationPhase.GUIDED_LINE_READING
        stepId = checkpoint.id
        trialState = ReadingValidationTrialState.ACQUIRE
        expectedCheckpoint = checkpoint
        stabilizer.reset()
        highlight(checkpoint)
        binding.nsvValidationText.setScrollingEnabled(false)
        scrollCheckpointIntoView(checkpoint)
        sessionLog?.logProtocolState(
            phase,
            stepId,
            trialState,
            "Acquire the highlighted physical line; no instruction UI is visible",
            checkpoint,
        )

        handler.postDelayed({
            logCoordinateFrames()
            trialState = ReadingValidationTrialState.MEASURE
            sessionLog?.logProtocolState(
                phase,
                stepId,
                trialState,
                "Measure reading of the highlighted physical line; no instruction UI is visible",
                checkpoint,
            )
            handler.postDelayed(
                { startLineCheckpoint(index + 1) },
                ReadingValidationProtocol.LINE_READING_MEASURE_MS,
            )
        }, ReadingValidationProtocol.LINE_READING_ACQUIRE_MS)
    }

    private fun completeSession() {
        if (finished) return
        phase = ReadingValidationPhase.COMPLETE
        stepId = "complete"
        trialState = ReadingValidationTrialState.MEASURE
        expectedCheckpoint = null
        clearHighlight()
        val summary = metrics.summary()
        val fileName = sessionLog?.fileName().orEmpty()
        finishSessionAfterProviderStops("completed", summary) {
            if (comparisonSpec != null) {
                val receipt = sessionLog?.sealedReceipt()
                val receiptText = receipt?.let { "${it.fileName}\nSHA-256 ${it.sha256}" }
                    ?: "The slot log could not be sealed."
                binding.validationControlPanel.visibility = View.VISIBLE
                binding.tvValidationProgress.text = "Comparison slot sealed"
                binding.tvValidationInstruction.text = receiptText
                binding.btnValidationAction.text = "Done"
                binding.btnValidationAction.visibility = View.VISIBLE
                binding.btnValidationAction.setOnClickListener { finish() }
                AlertDialog.Builder(this)
                    .setTitle("Comparison slot saved")
                    .setMessage("Spatial results remain hidden.\n\n$receiptText")
                    .setCancelable(false)
                    .setPositiveButton("Done") { _, _ -> finish() }
                    .show()
            } else {
                val result = String.format(
                    Locale.US,
                    "File: %s\nAlignment: %s%s\nGaze feedback: continuous orange dot · article smoothing\n\nWORD FIXATION\nExact line: %.1f%%\nWithin one line: %.1f%%\nExact word: %.1f%%\n\nGUIDED LINE READING\nExact line: %.1f%%\nWithin one line: %.1f%%",
                    fileName,
                    verticalAlignmentMode.name,
                    verticalAlignmentFit?.gain?.let { String.format(Locale.US, " · gain %.3f", it) }.orEmpty(),
                    100.0 * summary.wordExactLineAccuracy,
                    100.0 * summary.wordWithinOneLineAccuracy,
                    100.0 * summary.exactWordAccuracy,
                    100.0 * summary.guidedLineExactAccuracy,
                    100.0 * summary.guidedLineWithinOneAccuracy,
                )
                binding.validationControlPanel.visibility = View.VISIBLE
                binding.tvValidationProgress.text = "Known-target visible-feedback validation complete"
                binding.tvValidationInstruction.text = "Saved $fileName"
                binding.btnValidationAction.text = "Done"
                binding.btnValidationAction.visibility = View.VISIBLE
                binding.btnValidationAction.setOnClickListener { finish() }
                AlertDialog.Builder(this)
                    .setTitle("Visible-feedback performance data saved")
                    .setMessage(result)
                    .setPositiveButton("Done") { _, _ -> finish() }
                    .setNegativeButton("Stay", null)
                    .show()
            }
        }
    }

    private fun finishSessionAfterProviderStops(
        outcome: String,
        summary: ReadingValidationMetrics.Summary,
        afterSeal: () -> Unit = {},
    ) {
        if (finished) return
        finished = true
        running = false
        handler.removeCallbacksAndMessages(null)
        val stopping = gazeProvider
        gazeProvider = null
        val seal = {
            sessionLog?.finish(outcome, summary)
            afterSeal()
        }
        if (stopping == null) seal() else stopping.stopAndThen(seal)
    }

    private fun highlight(checkpoint: ReadingValidationCheckpoint) {
        val highlighted = SpannableString(VALIDATION_PASSAGE)
        highlighted.setSpan(
            BackgroundColorSpan(HIGHLIGHT_COLOR),
            checkpoint.targetStart.coerceIn(0, highlighted.length),
            checkpoint.targetEnd.coerceIn(0, highlighted.length),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        highlighted.setSpan(
            ForegroundColorSpan(Color.BLACK),
            checkpoint.targetStart.coerceIn(0, highlighted.length),
            checkpoint.targetEnd.coerceIn(0, highlighted.length),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        binding.tvValidationText.text = highlighted
    }

    private fun clearHighlight() {
        binding.tvValidationText.text = VALIDATION_PASSAGE
    }

    private fun laidOutLines(): List<ReadingValidationLine> {
        val layout = binding.tvValidationText.layout ?: return emptyList()
        val text = VALIDATION_PASSAGE
        return (0 until layout.lineCount).map { lineIndex ->
            val start = layout.getLineStart(lineIndex)
            val end = layout.getLineEnd(lineIndex).coerceAtMost(text.length)
            val words = WORD_REGEX.findAll(text.substring(start, end)).map { match ->
                ReadingValidationWord(
                    text = match.value,
                    start = start + match.range.first,
                    end = start + match.range.last + 1,
                )
            }.toList()
            ReadingValidationLine(lineIndex, words)
        }
    }

    private fun scrollCheckpointIntoView(checkpoint: ReadingValidationCheckpoint) {
        val layout = binding.tvValidationText.layout ?: return
        val lineCenter =
            (layout.getLineTop(checkpoint.lineIndex) + layout.getLineBottom(checkpoint.lineIndex)) / 2
        val desiredY = (binding.nsvValidationText.height * checkpoint.viewportFraction).toInt()
        programmaticScrollUntilMs = System.currentTimeMillis() + 1_000L
        binding.nsvValidationText.smoothScrollTo(
            0,
            (binding.tvValidationText.top + lineCenter - desiredY).coerceAtLeast(0),
        )
    }

    private fun runCountdown(onComplete: () -> Unit) {
        phase = ReadingValidationPhase.SETUP
        stepId = "countdown"
        trialState = ReadingValidationTrialState.PREPARE
        binding.tvValidationCountdown.visibility = View.VISIBLE
        var value = 3
        fun tick() {
            if (value == 0) {
                binding.tvValidationCountdown.visibility = View.GONE
                onComplete()
                return
            }
            binding.tvValidationCountdown.text = value.toString()
            value -= 1
            handler.postDelayed({ tick() }, 1_000L)
        }
        tick()
    }

    private fun requestStop() {
        if (!running || finished) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Stop this validation?")
            .setMessage("The partial trace will be saved as interrupted and will not overwrite another run.")
            .setNegativeButton("Continue", null)
            .setPositiveButton("Stop and save") { _, _ -> finish() }
            .show()
    }

    override fun onStop() {
        if (running && !finished) {
            finishSessionAfterProviderStops("interrupted", metrics.summary())
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }

    companion object {
        private const val PREF_RUN_COUNT = "reading_validation_run_count"
        private val WORD_REGEX = Regex("[\\p{L}\\p{N}]+(?:['’.-][\\p{L}\\p{N}]+)*")
        private val HIGHLIGHT_COLOR = Color.rgb(255, 224, 92)
        private val VALIDATION_PASSAGE = """
            On quiet mornings, Lina walked to the community library before the streets became busy. She liked the wide windows, the wooden tables, and the patient rhythm of pages turning nearby. The familiar room made it easier to settle into a story without rushing.

            One week, the librarian displayed a report about gardens built above crowded buildings. These rooftop spaces reduced heat, absorbed rainwater, and gave residents a place to grow herbs. Volunteers recorded which plants survived strong sunlight and which needed shade during the afternoon.

            Interpreting the results required care because the buildings differed in height, wind exposure, and access to water. A plant that struggled on one roof sometimes flourished only two streets away. The researchers therefore compared repeated observations instead of trusting a single measurement.

            Lina reread that explanation and noticed how easily one dramatic result could hide the larger pattern. She wrote a short note in the margin, then continued to a diagram showing the temperature changes across several months. The evidence became clearer when she connected the sentences to the chart.

            By noon, the room had grown brighter and more readers had arrived. Lina closed the report, returned it to the desk, and carried the main lesson home: careful conclusions depend on consistent observations gathered under different conditions.
        """.trimIndent()
    }
}
