package com.newsmead.mgazenetbenchmark

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.opencv.android.OpenCVLoader

class InferenceProfileTest {
    @Test fun exportCameraFreeThreadAndStageProfile() {
        check(OpenCVLoader.initLocal())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = InferenceProfileRunner.run(instrumentation.targetContext)
        instrumentation.sendStatus(0,android.os.Bundle().apply { putString("synthetic_profile_report",file.absolutePath) })
        val result = org.json.JSONObject(file.readText())
        assertEquals(result.optString("error"),"completed_pending_external_parity",result.getString("outcome"))
    }
}
