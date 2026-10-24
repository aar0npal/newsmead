package com.newsmead.gaze

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
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

    private val gazePaint = fillPaint(
        Color.argb(GAZE_DOT_ALPHA, GAZE_DOT_RED, GAZE_DOT_GREEN, GAZE_DOT_BLUE),
    )
    private val wordPaint = fillPaint(Color.argb(82, 244, 211, 94))
    private val linePaint = fillPaint(Color.argb(58, 244, 211, 94))
    private val focusPaint = fillPaint(Color.argb(112, 0, 0, 0))
    private val reentryPaint = fillPaint(Color.argb(105, 44, 128, 220))
    private val reentryCuePaint = fillPaint(Color.argb(230, 44, 128, 220))
    private val fpsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 0, 229, 255)
        textSize = 36f
        typeface = Typeface.MONOSPACE
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 0, 229, 255)
        textSize = 34f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val radius = GAZE_DOT_RADIUS_PX
    private val cornerRadius = dp(5f)
    private val loc = IntArray(2)
    private val textLoc = IntArray(2)
    private val visibleTextRect = Rect()
    private val drawRect = RectF()
    private val cuePath = Path()

    private var hasPoint = false
    private var screenX = 0f
    private var screenY = 0f
    private var fpsText: String? = null
    private var captionText: String? = null
    private var debugVisualsEnabled = false
    private var scaffoldTextView: TextView? = null
    private var requestedState = ScaffoldState()
    private var renderedState = ScaffoldState()
    private var scaffoldOpacity = 0f
    private var fadeAnimator: ValueAnimator? = null

    init {
        isClickable = false
        isFocusable = false
    }

    /** Set the current gaze point in full-screen pixels. */
    fun setGazeScreen(x: Float, y: Float) {
        screenX = x
        screenY = y
        hasPoint = true
        postInvalidateOnAnimation()
    }

    /** Clear only the debug gaze dot when the source reports unavailable/stale input. */
    fun clearGaze() {
        if (!hasPoint) return
        hasPoint = false
        postInvalidateOnAnimation()
    }

    fun setDebugVisualsEnabled(enabled: Boolean) {
        if (debugVisualsEnabled == enabled) return
        debugVisualsEnabled = enabled
        postInvalidateOnAnimation()
    }

    fun setScaffoldState(textView: TextView, state: ScaffoldState) {
        if (scaffoldTextView === textView && requestedState == state) return
        scaffoldTextView = textView
        val levelChanged = requestedState.level != state.level
        requestedState = state
        if (!levelChanged) {
            if (state.level != ScaffoldLevel.NONE) renderedState = state
            postInvalidateOnAnimation()
            return
        }

        fadeAnimator?.cancel()
        if (state.level == ScaffoldLevel.NONE) {
            val targetBeingReplaced = state.targetStatus == ScaffoldTargetStatus.WAITING_FOR_TARGET ||
                state.targetStatus == ScaffoldTargetStatus.INVALID
            if (targetBeingReplaced) {
                // A fade would leave the prior line visible during target acquisition.
                renderedState = state
                scaffoldOpacity = 0f
                postInvalidateOnAnimation()
            } else {
                fadeAnimator = animateOpacity(scaffoldOpacity, 0f) {
                    if (requestedState.level == ScaffoldLevel.NONE) renderedState = requestedState
                }
            }
        } else {
            renderedState = state
            scaffoldOpacity = 0f
            fadeAnimator = animateOpacity(0f, 1f)
        }
    }

    fun clearScaffold() {
        scaffoldTextView?.let { setScaffoldState(it, ScaffoldState()) }
    }

    /** Label the active level on screen during a demo recording; null hides it. */
    fun setScaffoldCaption(text: String?) {
        if (captionText == text) return
        captionText = text
        postInvalidateOnAnimation()
    }

    /** Show the tracker's current processing frame rate in the corner. */
    fun setFps(fps: Float) {
        val text = String.format(Locale.US, "%.0f fps", fps)
        if (fpsText == text) return
        fpsText = text
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        fadeAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    private var dotOnly = false

    /** Researcher recording view: retain the orange dot, suppress all other overlays. */
    fun setDotOnly(enabled: Boolean) {
        if (dotOnly == enabled) return
        dotOnly = enabled
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!dotOnly) drawScaffold(canvas)
        // Above the article body, next to the FPS readout - never over the text.
        if (!dotOnly) captionText?.let { canvas.drawText(it, 24f, 104f, captionPaint) }
        if (!debugVisualsEnabled && !dotOnly) return
        if (!dotOnly) fpsText?.let { canvas.drawText(it, 24f, 60f, fpsPaint) }
        if (!hasPoint) return
        getLocationOnScreen(loc)
        canvas.drawCircle(screenX - loc[0], screenY - loc[1], radius, gazePaint)
    }

    private fun drawScaffold(canvas: Canvas) {
        if (scaffoldOpacity <= 0f) return
        val textView = scaffoldTextView ?: return
        when (renderedState.level) {
            ScaffoldLevel.NONE -> Unit
            ScaffoldLevel.WORD -> drawWord(canvas, textView, renderedState)
            ScaffoldLevel.LINE -> drawLine(canvas, textView, renderedState.currentLine, linePaint)
            ScaffoldLevel.FOCUS -> drawFocusWindow(canvas, textView, renderedState.currentLine)
            ScaffoldLevel.REENTRY -> drawReentry(canvas, textView, renderedState.reentryTargetLine)
        }
    }

    private fun drawWord(canvas: Canvas, textView: TextView, state: ScaffoldState) {
        val layout = textView.layout ?: return
        if (state.wordStart < 0 || state.wordEnd <= state.wordStart ||
            state.wordStart >= layout.text.length || !prepareGeometry(textView)
        ) return

        val safeEnd = state.wordEnd.coerceAtMost(layout.text.length)
        val firstLine = layout.getLineForOffset(state.wordStart)
        val lastLine = layout.getLineForOffset((safeEnd - 1).coerceAtLeast(state.wordStart))
        setOpacity(wordPaint, WORD_ALPHA)
        for (line in firstLine..lastLine) {
            val start = maxOf(state.wordStart, layout.getLineStart(line))
            val end = minOf(safeEnd, layout.getLineEnd(line))
            val left = layout.getPrimaryHorizontal(start)
            val right = layout.getPrimaryHorizontal(end)
            if (setTextRect(textView, line, minOf(left, right), maxOf(left, right), dp(3f))) {
                canvas.drawRoundRect(drawRect, cornerRadius, cornerRadius, wordPaint)
            }
        }
    }

    private fun drawLine(canvas: Canvas, textView: TextView, line: Int, paint: Paint) {
        val layout = textView.layout ?: return
        if (line !in 0 until layout.lineCount || !prepareGeometry(textView)) return
        setOpacity(paint, if (paint === reentryPaint) REENTRY_ALPHA else LINE_ALPHA)
        val fullRight = (textView.width - textView.totalPaddingLeft - textView.totalPaddingRight).toFloat()
        if (setTextRect(textView, line, 0f, fullRight)) {
            canvas.drawRoundRect(drawRect, cornerRadius, cornerRadius, paint)
        }
    }

    private fun drawFocusWindow(canvas: Canvas, textView: TextView, centerLine: Int) {
        val layout = textView.layout ?: return
        if (centerLine !in 0 until layout.lineCount || !prepareGeometry(textView)) return
        val firstLine = (centerLine - FOCUS_RADIUS_LINES).coerceAtLeast(0)
        val lastLine = (centerLine + FOCUS_RADIUS_LINES).coerceAtMost(layout.lineCount - 1)
        val windowTop = textLoc[1] + textView.totalPaddingTop - textView.scrollY +
            layout.getLineTop(firstLine)
        val windowBottom = textLoc[1] + textView.totalPaddingTop - textView.scrollY +
            layout.getLineBottom(lastLine)
        val left = (visibleTextRect.left - loc[0]).toFloat()
        val right = (visibleTextRect.right - loc[0]).toFloat()
        val visibleTop = (visibleTextRect.top - loc[1]).toFloat()
        val visibleBottom = (visibleTextRect.bottom - loc[1]).toFloat()
        val focusTop = (windowTop.coerceIn(visibleTextRect.top, visibleTextRect.bottom) - loc[1]).toFloat()
        val focusBottom = (windowBottom.coerceIn(visibleTextRect.top, visibleTextRect.bottom) - loc[1]).toFloat()

        setOpacity(focusPaint, FOCUS_ALPHA)
        if (focusTop > visibleTop) canvas.drawRect(left, visibleTop, right, focusTop, focusPaint)
        if (focusBottom < visibleBottom) canvas.drawRect(left, focusBottom, right, visibleBottom, focusPaint)
    }

    private fun drawReentry(canvas: Canvas, textView: TextView, targetLine: Int) {
        val layout = textView.layout ?: return
        if (targetLine !in 0 until layout.lineCount || !prepareGeometry(textView)) return
        val targetTop = textLoc[1] + textView.totalPaddingTop - textView.scrollY +
            layout.getLineTop(targetLine)
        val targetBottom = textLoc[1] + textView.totalPaddingTop - textView.scrollY +
            layout.getLineBottom(targetLine)
        if (targetBottom > visibleTextRect.top && targetTop < visibleTextRect.bottom) {
            drawLine(canvas, textView, targetLine, reentryPaint)
            drawCue(
                canvas,
                pointsUp = targetLine < renderedState.currentLine,
                y = ((targetTop + targetBottom) / 2 - loc[1]).toFloat(),
            )
        } else {
            val pointsUp = targetBottom <= visibleTextRect.top
            val y = if (pointsUp) {
                (visibleTextRect.top - loc[1]).toFloat() + dp(22f)
            } else {
                (visibleTextRect.bottom - loc[1]).toFloat() - dp(22f)
            }
            drawCue(canvas, pointsUp, y)
        }
    }

    private fun drawCue(canvas: Canvas, pointsUp: Boolean, y: Float) {
        // Sits in the margin beside the text column so it never covers the words.
        // Narrow enough to fit the gutter, then made taller to stay readable.
        val columnLeft = (visibleTextRect.left - loc[0]).toFloat()
        val halfWidth = (columnLeft / 2f - dp(1f)).coerceIn(dp(4f), dp(9f))
        val centerX = (columnLeft - columnLeft / 2f).coerceAtLeast(halfWidth + dp(1f))
        val halfHeight = halfWidth * 1.6f
        cuePath.reset()
        if (pointsUp) {
            cuePath.moveTo(centerX, y - halfHeight)
            cuePath.lineTo(centerX - halfWidth, y + halfHeight)
            cuePath.lineTo(centerX + halfWidth, y + halfHeight)
        } else {
            cuePath.moveTo(centerX, y + halfHeight)
            cuePath.lineTo(centerX - halfWidth, y - halfHeight)
            cuePath.lineTo(centerX + halfWidth, y - halfHeight)
        }
        cuePath.close()
        setOpacity(reentryCuePaint, REENTRY_CUE_ALPHA)
        canvas.drawPath(cuePath, reentryCuePaint)
    }

    private fun prepareGeometry(textView: TextView): Boolean {
        if (!textView.getVisibleRectOnScreen(visibleTextRect, loc)) return false
        getLocationOnScreen(loc)
        textView.getLocationOnScreen(textLoc)
        return true
    }

    private fun setTextRect(
        textView: TextView,
        line: Int,
        contentLeft: Float,
        contentRight: Float,
        horizontalPad: Float = 0f,
    ): Boolean {
        val layout = textView.layout ?: return false
        val screenTop = textLoc[1] + textView.totalPaddingTop - textView.scrollY +
            layout.getLineTop(line)
        val screenBottom = textLoc[1] + textView.totalPaddingTop - textView.scrollY +
            layout.getLineBottom(line)
        val clippedTop = maxOf(screenTop, visibleTextRect.top)
        val clippedBottom = minOf(screenBottom, visibleTextRect.bottom)
        if (clippedTop >= clippedBottom) return false

        val screenLeft = textLoc[0] + textView.totalPaddingLeft + contentLeft - horizontalPad
        val screenRight = textLoc[0] + textView.totalPaddingLeft + contentRight + horizontalPad
        drawRect.set(
            maxOf(screenLeft, visibleTextRect.left.toFloat()) - loc[0],
            clippedTop.toFloat() - loc[1],
            minOf(screenRight, visibleTextRect.right.toFloat()) - loc[0],
            clippedBottom.toFloat() - loc[1],
        )
        return drawRect.left < drawRect.right
    }

    private fun animateOpacity(from: Float, to: Float, after: (() -> Unit)? = null): ValueAnimator =
        ValueAnimator.ofFloat(from, to).apply {
            duration = FADE_DURATION_MS
            addUpdateListener {
                scaffoldOpacity = it.animatedValue as Float
                postInvalidateOnAnimation()
            }
            if (after != null) {
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) = after()
                })
            }
            start()
        }

    private fun setOpacity(paint: Paint, baseAlpha: Int) {
        paint.alpha = (baseAlpha * scaffoldOpacity).toInt().coerceIn(0, 255)
    }

    private fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        const val GAZE_DOT_RADIUS_PX = 18f
        const val GAZE_DOT_ALPHA = 180
        const val GAZE_DOT_RED = 255
        const val GAZE_DOT_GREEN = 64
        const val GAZE_DOT_BLUE = 0
        private const val WORD_ALPHA = 82
        private const val LINE_ALPHA = 58
        private const val FOCUS_ALPHA = 112
        private const val REENTRY_ALPHA = 105
        private const val REENTRY_CUE_ALPHA = 230
        private const val FOCUS_RADIUS_LINES = 2
        private const val FADE_DURATION_MS = 400L
    }
}
