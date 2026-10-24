package com.newsmead.mgazenetbenchmark

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Stationary target on measured screen-space text geometry. Never displays estimated gaze. */
class AccuracyTargetView(context: Context) : View(context) {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY; textSize = 20f*resources.displayMetrics.scaledDensity
    }
    private val marker = Paint(Paint.ANTI_ALIAS_FLAG)
    private val margin = 12f*resources.displayMetrics.density
    private var acknowledgedToken = -1
    var target: AccuracySession.Target? = null
        set(value) { field = value; invalidate() }
    var onPresented: (Int,Double) -> Unit = { _,_ -> }
    var onGeometry: (AccuracySession.Layout) -> Unit = {}

    private fun lineText(): String {
        var text = "Reference"
        val words = listOf("text","for","target","positioning.")
        for (word in words) {
            val candidate = "$text $word"
            if (textPaint.measureText(candidate) > width-2*margin) break
            text = candidate
        }
        return text
    }
    fun snapshot(): AccuracySession.Layout {
        val location = IntArray(2); getLocationOnScreen(location)
        val display = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        context.getSystemService(android.view.WindowManager::class.java).defaultDisplay.getRealMetrics(display)
        val metrics = textPaint.fontMetrics
        val pitch = kotlin.math.ceil((metrics.bottom-metrics.top)*1.45).toDouble()
        val count = ((height-2*margin)/pitch).toInt()
        require(count >= 5 && width > 4*margin) { "Not enough drawable space for accuracy targets" }
        val left = location[0]+margin.toDouble()
        val top = location[1]+margin.toDouble()
        val right = location[0]+width-margin.toDouble()
        val textRight = left + textPaint.measureText(lineText())
        val lines = List(count) { i -> AccuracySession.Rect(left,top+i*pitch,textRight,top+(i+1)*pitch) }
        return AccuracySession.Layout(display.widthPixels,display.heightPixels,
            AccuracySession.Rect(left,top,right,top+count*pitch),pitch,lines)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas); canvas.drawColor(Color.WHITE)
        val layout = runCatching { snapshot() }.getOrNull() ?: return
        onGeometry(layout)
        val item = target ?: return
        val location = IntArray(2); getLocationOnScreen(location)
        if (item.testIndex != null) {
            val metrics = textPaint.fontMetrics
            layout.lines.forEach { line ->
                val baseline = ((line.top+line.bottom)/2-(metrics.ascent+metrics.descent)/2).toFloat()-location[1]
                canvas.drawText(lineText(),(line.left-location[0]).toFloat(),baseline,textPaint)
            }
        }
        val x = (item.point.x-location[0]).toFloat(); val y = (item.point.y-location[1]).toFloat()
        marker.color = Color.WHITE; canvas.drawCircle(x,y,11*resources.displayMetrics.density,marker)
        marker.color = Color.BLACK; canvas.drawCircle(x,y,9*resources.displayMetrics.density,marker)
        marker.color = Color.RED; canvas.drawCircle(x,y,6*resources.displayMetrics.density,marker)
        marker.color = Color.BLACK; canvas.drawCircle(x,y,1.5f*resources.displayMetrics.density,marker)
        if (acknowledgedToken != item.token) {
            acknowledgedToken = item.token
            val drawn = AccuracyCameraSource.now()
            post { onPresented(item.token,drawn) }
        }
    }
}
