package com.newsmead.gaze

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Calibration target renderer (docs/calibration-design.md §2): a concentric
 * reticle sized in dp for 45-65-year-old acuity, with a fixed four-arm
 * hit-marker and a red filled disc that contracts once to a small fixed centre
 * plus to guide fixation. The collector completes the contraction during target
 * acquisition, then holds the center cue stationary through settle and sampling.
 * Also draws a 16-dot progress mini-map.
 *
 * Colors assume the light (reading-surface) background set in the layout.
 */
class CalibrationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    enum class MiniDotState { PENDING, CURRENT, DONE, EXCLUDED }

    private val density = resources.displayMetrics.density

    private var targetX: Float? = null
    private var targetY: Float? = null
    private var appearProgress = 1f
    private var confirmProgress = 0f
    private var focusRadiusDp = CONTRACT_END_RADIUS_DP
    private var appearAnimator: ValueAnimator? = null
    private var confirmAnimator: ValueAnimator? = null
    private var focusAnimator: ValueAnimator? = null
    private var miniStates: List<MiniDotState> = emptyList()
    private var pendingTargetDraw: (() -> Unit)? = null

    /** Acknowledge actual drawing; callers still account separately for attention/settle time. */
    fun onNextTargetDraw(callback: () -> Unit) { pendingTargetDraw = callback }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RING_COLOR
        style = Paint.Style.STROKE
        strokeWidth = RING_STROKE_DP * density
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DOT_COLOR
        style = Paint.Style.FILL
    }
    private val focusOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RING_COLOR
        style = Paint.Style.STROKE
        strokeWidth = FOCUS_OUTLINE_STROKE_DP * density
    }
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RING_COLOR
        style = Paint.Style.STROKE
        strokeWidth = CROSSHAIR_STROKE_DP * density
        strokeCap = Paint.Cap.ROUND
    }
    private val centerCrossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RING_COLOR
        style = Paint.Style.STROKE
        strokeWidth = CENTER_CROSS_STROKE_DP * density
        strokeCap = Paint.Cap.ROUND
    }
    private val confirmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CONFIRM_COLOR
        style = Paint.Style.FILL
    }
    private val miniPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /** Show the target at [x],[y] with the APPEAR fade/scale-in animation. */
    fun showTarget(x: Float, y: Float, contractionDurationMs: Long) {
        targetX = x
        targetY = y
        confirmProgress = 0f
        appearProgress = 0f
        appearAnimator?.cancel()
        appearAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = APPEAR_MS
            addUpdateListener { animation ->
                appearProgress = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
        startContraction(contractionDurationMs)
    }

    /** One inward contraction completed before the collector begins sampling. */
    private fun startContraction(durationMs: Long) {
        focusAnimator?.cancel()
        focusAnimator = ValueAnimator.ofFloat(CONTRACT_START_RADIUS_DP, CONTRACT_END_RADIUS_DP).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                focusRadiusDp = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun hideTarget() {
        pendingTargetDraw = null
        appearAnimator?.cancel()
        confirmAnimator?.cancel()
        focusAnimator?.cancel()
        focusRadiusDp = CONTRACT_END_RADIUS_DP
        targetX = null
        targetY = null
        invalidate()
    }

    /** Brief green flash confirming the point was captured. */
    fun flashConfirm() {
        confirmAnimator?.cancel()
        confirmAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = CONFIRM_FLASH_MS
            addUpdateListener { animation ->
                confirmProgress = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /** One state per calibration grid point, in grid order. */
    fun setMiniMap(states: List<MiniDotState>) {
        miniStates = states
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawMiniMap(canvas)
        val x = targetX ?: return
        val y = targetY ?: return
        val scale = APPEAR_START_SCALE - (APPEAR_START_SCALE - 1f) * appearProgress
        val alpha = (255 * appearProgress).toInt()
        ringPaint.alpha = alpha
        dotPaint.alpha = alpha
        focusOutlinePaint.alpha = alpha
        crosshairPaint.alpha = alpha
        centerCrossPaint.alpha = alpha
        // The saturated red guidance disc starts at the arm tips. Draw the
        // black reticle over it so every alignment mark remains high-contrast.
        canvas.drawCircle(x, y, focusRadiusDp * density, dotPaint)
        canvas.drawCircle(x, y, focusRadiusDp * density, focusOutlinePaint)
        canvas.drawCircle(x, y, RING_RADIUS_DP * density * scale, ringPaint)
        drawCrosshair(canvas, x, y, scale)
        drawCenterCross(canvas, x, y)
        pendingTargetDraw?.let { callback -> pendingTargetDraw = null; post { callback() } }
        if (confirmProgress > 0f) {
            confirmPaint.alpha = (200 * confirmProgress).toInt()
            canvas.drawCircle(x, y, RING_RADIUS_DP * density, confirmPaint)
        }
    }

    /** Four separated arms keep the exact centre unobstructed, like a hit-marker. */
    private fun drawCrosshair(canvas: Canvas, x: Float, y: Float, scale: Float) {
        val inner = CROSSHAIR_INNER_GAP_DP * density * scale
        val outer = CROSSHAIR_OUTER_DP * density * scale
        canvas.drawLine(x - outer, y, x - inner, y, crosshairPaint)
        canvas.drawLine(x + inner, y, x + outer, y, crosshairPaint)
        canvas.drawLine(x, y - outer, x, y - inner, crosshairPaint)
        canvas.drawLine(x, y + inner, x, y + outer, crosshairPaint)
    }

    /** Small permanent plus marking the exact fixation coordinate. */
    private fun drawCenterCross(canvas: Canvas, x: Float, y: Float) {
        val half = CENTER_CROSS_HALF_DP * density
        canvas.drawLine(x - half, y, x + half, y, centerCrossPaint)
        canvas.drawLine(x, y - half, x, y + half, centerCrossPaint)
    }

    private fun drawMiniMap(canvas: Canvas) {
        if (miniStates.isEmpty()) return
        val r = MINI_RADIUS_DP * density
        val gap = MINI_GAP_DP * density
        val totalWidth = miniStates.size * 2 * r + (miniStates.size - 1) * gap
        var cx = (width - totalWidth) / 2f + r
        val cy = MINI_TOP_DP * density
        for (state in miniStates) {
            miniPaint.color = when (state) {
                MiniDotState.PENDING -> MINI_PENDING_COLOR
                MiniDotState.CURRENT -> MINI_CURRENT_COLOR
                MiniDotState.DONE -> MINI_DONE_COLOR
                MiniDotState.EXCLUDED -> MINI_EXCLUDED_COLOR
            }
            val radius = if (state == MiniDotState.CURRENT) r * 1.35f else r
            canvas.drawCircle(cx, cy, radius, miniPaint)
            cx += 2 * r + gap
        }
    }

    companion object {
        /** Appear animation length; the activity's APPEAR state waits this long. */
        const val APPEAR_MS = 200L

        private const val CONFIRM_FLASH_MS = 250L
        private const val APPEAR_START_SCALE = 1.3f

        // Starts at the four outer arm tips and ends at the center plus bounds.
        private const val CONTRACT_START_RADIUS_DP = 24f
        private const val CONTRACT_END_RADIUS_DP = 6f

        // Bullseye sized in dp (44dp ring / 9dp dot diameters), per design §2.2.
        private const val RING_RADIUS_DP = 22f
        private const val RING_STROKE_DP = 3f
        private const val FOCUS_OUTLINE_STROKE_DP = 1.5f
        private const val CROSSHAIR_STROKE_DP = 2.5f
        private const val CROSSHAIR_INNER_GAP_DP = 19f
        private const val CROSSHAIR_OUTER_DP = 24f
        private const val CENTER_CROSS_STROKE_DP = 2f
        private const val CENTER_CROSS_HALF_DP = 6f

        private const val MINI_RADIUS_DP = 3.5f
        private const val MINI_GAP_DP = 6f
        private const val MINI_TOP_DP = 64f

        // Contrast-first colors for the light reading-surface background.
        private val RING_COLOR = Color.parseColor("#37474F")
        private val DOT_COLOR = Color.parseColor("#FF0000")
        private val CONFIRM_COLOR = Color.parseColor("#2E7D32")
        private val MINI_PENDING_COLOR = Color.parseColor("#BDBDBD")
        private val MINI_CURRENT_COLOR = Color.parseColor("#C62828")
        private val MINI_DONE_COLOR = Color.parseColor("#2E7D32")
        private val MINI_EXCLUDED_COLOR = Color.parseColor("#F9A825")
    }
}
