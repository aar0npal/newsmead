package com.newsmead.gaze

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Draws a single fixation target (outer ring + inner dot) at a settable point,
 * for the gaze calibration sequence. Set the target to null to hide it.
 *
 * Ported verbatim (except package) from gaze-thesis-prototype.
 */
class CalibrationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private var targetX: Float? = null
    private var targetY: Float? = null

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    /** Position the target at [x],[y] in view pixels, or hide it if either is null. */
    fun setTarget(x: Float?, y: Float?) {
        targetX = x
        targetY = y
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val x = targetX ?: return
        val y = targetY ?: return
        canvas.drawCircle(x, y, OUTER_RADIUS, ringPaint)
        canvas.drawCircle(x, y, INNER_RADIUS, dotPaint)
    }

    companion object {
        private const val OUTER_RADIUS = 34f
        private const val INNER_RADIUS = 10f
    }
}
