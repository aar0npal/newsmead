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
 * Debug overlay for Stage 4: draws the current gaze point (given in full-screen
 * pixels) as a translucent dot on top of the article content. Purely visual —
 * it never consumes touch, so scrolling and the article controls still work.
 *
 * The point is stored in screen space and converted to this view's local space
 * at draw time via [getLocationOnScreen], so it stays correct wherever the
 * overlay sits in the layout.
 */
class GazeOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 64, 0)
        style = Paint.Style.FILL
    }
    private val fpsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 0, 229, 255)
        textSize = 36f
        typeface = Typeface.MONOSPACE
    }
    private val radius = 18f
    private val loc = IntArray(2)

    private var hasPoint = false
    private var screenX = 0f
    private var screenY = 0f
    private var fpsText: String? = null

    init {
        isClickable = false
        isFocusable = false
    }

    /** Set the current gaze point in full-screen pixels. */
    fun setGazeScreen(x: Float, y: Float) {
        screenX = x
        screenY = y
        hasPoint = true
        invalidate()
    }

    /** Show the tracker's current processing frame rate in the corner. */
    fun setFps(fps: Float) {
        fpsText = String.format(Locale.US, "%.0f fps", fps)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        fpsText?.let { canvas.drawText(it, 24f, 60f, fpsPaint) }
        if (!hasPoint) return
        getLocationOnScreen(loc)
        canvas.drawCircle(screenX - loc[0], screenY - loc[1], radius, paint)
    }
}
