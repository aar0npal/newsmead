package com.newsmead.mgazenetbenchmark

import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

/** The exact confirmation measurement surface, shared by production and camera-free layout checks. */
object ConfirmationMeasurementLayout {
    data class Views(
        val root: LinearLayout,
        val status: TextView,
        val target: AccuracyTargetView
    )

    fun create(activity: ComponentActivity, onStop: () -> Unit): Views {
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val status = TextView(activity).apply {
            textSize = 18f
            setPadding(20,12,20,0)
            text = "Preparing camera. Follow the red target when it appears."
        }
        root.addView(
            status,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (112*activity.resources.displayMetrics.density).toInt()
            )
        )
        val target = AccuracyTargetView(activity)
        root.addView(
            target,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f)
        )
        root.addView(Button(activity).apply {
            text = "Stop and return"
            setOnClickListener { onStop() }
        })
        return Views(root,status,target)
    }
}
