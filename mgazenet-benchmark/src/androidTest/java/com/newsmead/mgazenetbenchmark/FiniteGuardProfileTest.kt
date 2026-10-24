package com.newsmead.mgazenetbenchmark

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.opencv.android.OpenCVLoader

class FiniteGuardProfileTest {
    @Test fun compareEquivalentGuardsAndExportProfile() {
        check(OpenCVLoader.initLocal())
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        // Bad dimensions and all non-finite classes still stop before unchecked JNI.
        Preprocessor().use { prep -> MnnEstimator(context).use { model ->
            val f=SyntheticFixtures.images().first(); val input=prep.prepare(f.frame,f.crops)
            for (bad in listOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY)) {
                val rect=input.rect.copyOf(); rect[5]=bad
                try { model.profileInference(input.copy(rect=rect),true); fail("Non-finite value reached JNI") }
                catch (_: IllegalArgumentException) { }
            }
            try { model.profileInference(input.copy(face=FloatArray(3)),true); fail("Bad length reached JNI") }
            catch (_: IllegalArgumentException) { }
        } }
        val file=InferenceProfileRunner.run(context,true)
        instrumentation.sendStatus(0,android.os.Bundle().apply { putString("synthetic_profile_report",file.absolutePath) })
        val result=org.json.JSONObject(file.readText())
        assertEquals(result.optString("error"),"completed_pending_external_parity",result.getString("outcome"))
    }
}
