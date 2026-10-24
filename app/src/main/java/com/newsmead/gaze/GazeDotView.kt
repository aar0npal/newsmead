package com.newsmead.gaze

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import java.util.Locale

/**
 * Draws the live estimated gaze point plus, during the accuracy harness, the
 * target ring the user is asked to fixate. Ported from gaze-thesis-prototype's
 * Stage 3 GazeDotView, with an FPS readout added.
 */
class GazeDotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private var x: Float? = null
    private var y: Float? = null
    private var targetX: Float? = null
    private var targetY: Float? = null
    private var estX: Float? = null
    private var estY: Float? = null
    private var fpsText: String? = null
    private val drawingLocation = IntArray(2)

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 0, 200, 255)
        style = Paint.Style.FILL
    }
    private val targetRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val targetDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }
    private val fpsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 0, 229, 255)
        textSize = 36f
        typeface = Typeface.MONOSPACE
    }
    private val estPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 255, 214, 0)
        style = Paint.Style.FILL
    }
    private val missPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 255, 214, 0)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    /** Live pipeline outputs are absolute screen pixels, not canvas coordinates. */
    fun setGazeScreen(px: Float, py: Float) {
        x = px
        y = py
        invalidate()
    }

    /** Show the tracker's current processing frame rate in the corner. */
    fun setFps(fps: Float) {
        fpsText = String.format(Locale.US, "%.0f fps", fps)
        invalidate()
    }

    /** Optional accuracy target in this canvas's local coordinates. */
    fun setTarget(px: Float, py: Float) {
        targetX = px
        targetY = py
        invalidate()
    }

    fun clearTarget() {
        targetX = null
        targetY = null
        invalidate()
    }

    /** Optional captured estimate in this canvas's local coordinates. */
    fun setEstimate(px: Float, py: Float) {
        estX = px
        estY = py
        invalidate()
    }

    fun clearEstimate() {
        estX = null
        estY = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        fpsText?.let { canvas.drawText(it, 24f, 60f, fpsPaint) }
        val tx = targetX
        val ty = targetY
        if (tx != null && ty != null) {
            canvas.drawCircle(tx, ty, TARGET_RADIUS, targetRingPaint)
            canvas.drawCircle(tx, ty, TARGET_INNER, targetDotPaint)
        }
        val ex = estX
        val ey = estY
        if (ex != null && ey != null) {
            // The estimate for the last point + a line to its target: the length
            // of the line is the per-point miss, so the error is visible per dot.
            if (tx != null && ty != null) canvas.drawLine(tx, ty, ex, ey, missPaint)
            canvas.drawCircle(ex, ey, EST_RADIUS, estPaint)
        }
        getLocationOnScreen(drawingLocation)
        val px = ((x ?: return) - drawingLocation[0]).coerceIn(0f, width.toFloat())
        val py = ((y ?: return) - drawingLocation[1]).coerceIn(0f, height.toFloat())
        canvas.drawCircle(px, py, RADIUS, dotPaint)
    }

    companion object {
        private const val RADIUS = 32f
        private const val TARGET_RADIUS = 30f
        private const val TARGET_INNER = 9f
        private const val EST_RADIUS = 14f
    }
}
