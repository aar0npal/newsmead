package com.newsmead.mgazenetbenchmark

import android.view.WindowManager
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.opencv.android.OpenCVLoader

/** Foreground scheduling control only; no camera permission or camera button invocation. */
class ForegroundProfileTest {
    @Test fun compareGuardsAndThreadsWithVisibleActivity() {
        check(OpenCVLoader.initLocal())
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(BenchmarkActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity.setContentView(TextView(activity).apply {
                    textSize=22f; setPadding(32,80,32,32)
                    text="Measuring on-device model speed.\n\nCamera is closed. No calibration is needed."
                })
            }
            instrumentation.waitForIdleSync()
            val file=InferenceProfileRunner.run(instrumentation.targetContext,true,
                "debug instrumentation with foreground benchmark Activity; screen kept on; camera closed")
            instrumentation.sendStatus(0,android.os.Bundle().apply { putString("synthetic_profile_report",file.absolutePath) })
            val result=org.json.JSONObject(file.readText())
            assertEquals(result.optString("error"),"completed_pending_external_parity",result.getString("outcome"))
        }
    }
}
