package com.newsmead.gaze.mgazenet

/** Target collection only. Never encodes, logs or exports personal features. Main-thread confined. */
class MgazeNetCalibrationSession(val identity: CalibrationIdentity) : AutoCloseable {
    enum class Phase { READY, WAIT_DRAW, COLLECT, POST, FITTING, COMPLETE, FAILED }
    private data class Candidate(val features: FloatArray)

    var phase = Phase.READY; private set
    var index = -1; private set // -1 is practice; non-negative indices are fit targets
    var count = 0; private set
    var rejected = 0; private set
    var postureRejected = 0; private set
    var practiceRestarts = 0; private set
    var trainingCandidates = 0; private set
    private var onset = Double.NaN
    private var lastCapture = -1.0
    private var postUntil = Double.NaN
    private val rows = ArrayList<FloatArray>()
    private val labels = ArrayList<FloatArray>()
    private val practicePostures = ArrayList<MgazeNetPostureGate.Sample>()
    private val targetCandidates = ArrayList<Candidate>()
    private var postureReference: MgazeNetPostureGate.Reference? = null
    private var postureWarningUntil = Double.NEGATIVE_INFINITY

    val target get() = if (index < 0) identity.viewport.toScreen(identity.viewport.width/2f,identity.viewport.height/2f)
        else identity.targets[index]

    fun begin() { check(phase == Phase.READY); phase = Phase.WAIT_DRAW }

    fun drawn(now: Double) {
        if (phase != Phase.WAIT_DRAW) return
        require(now.isFinite())
        check(practicePostures.isEmpty() && targetCandidates.isEmpty())
        onset = now
        count = 0
        phase = Phase.COLLECT
    }

    fun sample(capture: Double, output: Double, features: FloatArray?, left: Double, right: Double,
        posture: MgazeNetPostureGate.Sample?) {
        if (phase != Phase.COLLECT || capture < onset+SETTLE_MS || capture >= onset+TARGET_TIMEOUT_MS) return
        if (!capture.isFinite() || !output.isFinite() || output < capture || capture <= lastCapture ||
            output >= onset+TARGET_TIMEOUT_MS) {
            rejected++
            return
        }
        lastCapture = capture
        if (features == null || features.size != FEATURE_COUNT || !features.all(Float::isFinite) ||
            !left.isFinite() || !right.isFinite() || left <= MIN_EYE_AREA || right <= MIN_EYE_AREA ||
            posture?.valid != true) {
            rejected++
            return
        }
        if (index < 0) {
            practicePostures.add(posture)
            count = minOf(practicePostures.size,SAMPLES_PER_TARGET)
            return
        }
        if (!postureAccepted(posture)) {
            postureRejected++
            rejected++
            postureWarningUntil = output+POSTURE_WARNING_MS
            return
        }
        targetCandidates.add(Candidate(features.copyOf()))
        trainingCandidates++
        count = minOf(targetCandidates.size,SAMPLES_PER_TARGET)
    }

    fun postureAccepted(posture: MgazeNetPostureGate.Sample?): Boolean =
        postureReference?.let { MgazeNetPostureGate.accepts(it,posture) } == true

    fun showPostureWarning(now: Double) = now < postureWarningUntil

    fun fillingTemporalWindow(now: Double) = phase == Phase.COLLECT && count >= SAMPLES_PER_TARGET &&
        now < onset+SETTLE_MS+SAMPLE_WINDOW_MS

    /** Returns true when a different target must be drawn. */
    fun tick(now: Double): Boolean {
        if (phase == Phase.COLLECT && now >= onset+TARGET_TIMEOUT_MS) {
            close()
            return false
        }
        if (phase == Phase.COLLECT && now >= onset+SETTLE_MS+SAMPLE_WINDOW_MS) {
            if (index < 0) finishPractice(now) else finishTarget(now)
        }
        if (phase == Phase.POST && now >= postUntil) {
            if (index == identity.targets.lastIndex) {
                phase = Phase.FITTING
                return false
            }
            index++
            phase = Phase.WAIT_DRAW
            return true
        }
        return false
    }

    private fun finishPractice(now: Double) {
        if (practicePostures.size < SAMPLES_PER_TARGET) return
        val reference = MgazeNetPostureGate.reference(practicePostures)
        if (reference == null) {
            postureRejected += practicePostures.size
            rejected += practicePostures.size
            practiceRestarts++
            practicePostures.clear()
            count = 0
            onset = now
            postureWarningUntil = now+POSTURE_WARNING_MS
            return
        }
        postureReference = reference
        practicePostures.clear()
        count = SAMPLES_PER_TARGET
        postUntil = now+POST_MS
        phase = Phase.POST
    }

    private fun finishTarget(now: Double) {
        if (targetCandidates.size < SAMPLES_PER_TARGET) return
        val selected = MgazeNetTemporalSampler.indices(targetCandidates.size,SAMPLES_PER_TARGET).toSet()
        val label = floatArrayOf(target.x/identity.screenWidth,target.y/identity.screenHeight)
        targetCandidates.forEachIndexed { candidateIndex, candidate ->
            if (candidateIndex in selected) {
                rows.add(candidate.features)
                labels.add(label.copyOf())
            } else candidate.features.fill(0f)
        }
        targetCandidates.clear()
        label.fill(0f)
        count = SAMPLES_PER_TARGET
        postUntil = now+POST_MS
        phase = Phase.POST
    }

    /** Transfers ownership to the worker; cancellation no longer races its training arrays. */
    fun takeTraining(): Pair<Array<FloatArray>,Array<FloatArray>> {
        val expectedRows = identity.targets.size*SAMPLES_PER_TARGET
        check(phase == Phase.FITTING && rows.size == expectedRows && labels.size == expectedRows &&
            practicePostures.isEmpty() && targetCandidates.isEmpty())
        return (rows.toTypedArray() to labels.toTypedArray()).also { rows.clear(); labels.clear() }
    }

    fun fitted(success: Boolean) {
        check(phase == Phase.FITTING)
        phase = if (success) Phase.COMPLETE else Phase.FAILED
    }

    override fun close() {
        rows.forEach { it.fill(0f) }
        labels.forEach { it.fill(0f) }
        targetCandidates.forEach { it.features.fill(0f) }
        rows.clear()
        labels.clear()
        targetCandidates.clear()
        practicePostures.clear()
        postureReference = null
        if (phase != Phase.COMPLETE) phase = Phase.FAILED
    }

    companion object {
        const val SETTLE_MS = 1500
        const val SAMPLE_WINDOW_MS = 3000
        const val POST_MS = 500
        const val TARGET_TIMEOUT_MS = 30000
        const val SAMPLES_PER_TARGET = CalibrationIdentity.SAMPLES_PER_TARGET
        private const val FEATURE_COUNT = 258
        private const val MIN_EYE_AREA = 10.0
        private const val POSTURE_WARNING_MS = 1000.0
    }
}
