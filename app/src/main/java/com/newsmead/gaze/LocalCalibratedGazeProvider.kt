package com.newsmead.gaze

import android.util.Log
import androidx.lifecycle.LifecycleOwner
import java.util.Locale

/**
 * Local Stage 3 provider: maps phone-side raw gaze features through the saved
 * 16-point calibration and emits calibrated full-screen phone pixels. An
 * optional affine [correction] (fitted from the gaze accuracy test) is applied
 * after the mapper to compensate mid-session drift without re-calibrating.
 */
class LocalCalibratedGazeProvider(
    private val mapper: GazeMapper,
    private val rawSource: LocalRawGazeSource,
    private val correction: DriftCorrection? = null,
    private val postureProfile: PostureProfile? = null,
) : GazeProvider {

    data class PipelineDiagnostics(
        val source: LocalRawGazeSource.Diagnostics?,
        val rawX: Float,
        val rawY: Float,
        val medianX: Float,
        val medianY: Float,
        val filteredX: Float,
        val filteredY: Float,
        val mappedX: Float,
        val mappedY: Float,
        val outputX: Float,
        val outputY: Float,
        val correctionActive: Boolean,
        val outputTimestampMs: Long,
        val postureAssessment: PostureAssessment,
    )

    fun interface OnDiagnostics {
        fun onDiagnostics(diagnostics: PipelineDiagnostics)
    }

    fun interface OnPostureAssessment {
        fun onPostureAssessment(assessment: PostureAssessment)
    }

    private val medianX = MedianFilter()
    private val medianY = MedianFilter()
    private val smoothX = OneEuroFilter(minCutoff = 0.7, beta = 0.005)
    private val smoothY = OneEuroFilter(minCutoff = 0.7, beta = 0.005)
    private var onGaze: GazeProvider.OnGaze? = null
    private var onDiagnostics: OnDiagnostics? = null
    private var onPostureAssessment: OnPostureAssessment? = null
    private var onSourceDiagnostics: LocalRawGazeSource.OnDiagnostics? = null
    @Volatile private var latestSourceDiagnostics: LocalRawGazeSource.Diagnostics? = null
    private var started = false
    private var logCounter = 0

    override fun setOnGaze(listener: GazeProvider.OnGaze) {
        onGaze = listener
    }

    fun setOnDiagnostics(listener: OnDiagnostics?) {
        onDiagnostics = listener
        refreshSourceDiagnostics()
    }

    fun setOnSourceDiagnostics(listener: LocalRawGazeSource.OnDiagnostics?) {
        onSourceDiagnostics = listener
        refreshSourceDiagnostics()
    }

    fun setOnPostureAssessment(listener: OnPostureAssessment?) {
        onPostureAssessment = listener
    }

    override fun start(owner: LifecycleOwner) {
        started = true
        refreshSourceDiagnostics()
        rawSource.setOnRawGaze { sample ->
            val postureAssessment = postureProfile?.assess(sample.posture)
                ?: PostureAssessment.unavailable()
            val mx = medianX.filter(sample.gazeX)
            val my = medianY.filter(sample.gazeY)
            val fx = smoothX.filter(mx, sample.timestampMs)
            val fy = smoothY.filter(my, sample.timestampMs)
            val screen = mapper.map(fx, fy)
            val corrected = correction?.apply(screen[0], screen[1]) ?: screen
            // Diagnostic: pair the raw feature with the mapped px so a persistent
            // vertical "wall" can be attributed. If raw fy keeps changing while
            // mapped y stops, it is the mapping; if raw fy itself flatlines when
            // looking further up/down, the feature is saturating at the source
            // (appearance-based vertical limit) and no mapper change will help.
            if (++logCounter % LOG_INTERVAL == 0) {
                Log.d(
                    TAG,
                    String.format(
                        Locale.US,
                        "raw=(%.4f, %.4f) filt=(%.4f, %.4f) -> screen=(%.0f, %.0f)%s pose=%s",
                        sample.gazeX, sample.gazeY, fx, fy, corrected[0], corrected[1],
                        if (correction != null) " [drift-corrected]" else "",
                        postureAssessment.status.name,
                    ),
                )
            }
            onPostureAssessment?.onPostureAssessment(postureAssessment)
            onDiagnostics?.onDiagnostics(
                PipelineDiagnostics(
                    source = latestSourceDiagnostics?.takeIf {
                        it.outcome == LocalRawGazeSource.DiagnosticOutcome.EMITTED &&
                            it.gazeX == sample.gazeX && it.gazeY == sample.gazeY
                    },
                    rawX = sample.gazeX,
                    rawY = sample.gazeY,
                    medianX = mx,
                    medianY = my,
                    filteredX = fx,
                    filteredY = fy,
                    mappedX = screen[0],
                    mappedY = screen[1],
                    outputX = corrected[0],
                    outputY = corrected[1],
                    correctionActive = correction != null,
                    outputTimestampMs = sample.timestampMs,
                    postureAssessment = postureAssessment,
                ),
            )
            onGaze?.onGaze(corrected[0], corrected[1])
        }
        rawSource.start(owner)
    }

    override fun stop() {
        started = false
        rawSource.clearOnDiagnostics()
        rawSource.stop()
    }

    private fun refreshSourceDiagnostics() {
        if (!started) return
        if (onDiagnostics == null && onSourceDiagnostics == null) {
            latestSourceDiagnostics = null
            rawSource.clearOnDiagnostics()
            return
        }
        rawSource.setOnDiagnostics { diagnostics ->
            latestSourceDiagnostics = diagnostics
            onSourceDiagnostics?.onDiagnostics(diagnostics)
        }
    }

    companion object {
        private const val TAG = "GazeMap"
        private const val LOG_INTERVAL = 15
    }
}
