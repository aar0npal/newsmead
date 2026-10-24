package com.newsmead.gaze.mgazenet

import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.activities.GazeCalibrationActivity
import com.newsmead.activities.GazeTestActivity
import com.newsmead.activities.ReadingValidationActivity
import com.newsmead.gaze.ComparisonProtocol
import com.newsmead.gaze.GazeCoordinateFrame
import org.junit.Assert.*
import org.junit.Test
import org.opencv.android.OpenCVLoader
import java.io.File
import java.util.UUID

/** Camera-free tests. Host assembly does not execute Android/native checks. Never press Start. */
class MgazeNetIntegrationTest {
    @Test fun openingSetupsIsCameraOffAndMutationFree() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun snapshot(): Map<String,String> = listOf(context.filesDir,context.noBackupFilesDir).flatMap { root ->
            root.walkTopDown().filter { it.isFile && (it.path.contains("mgazenet") || it.name.startsWith("calibration")) }
                .map { it.path to CalibrationIdentity.hash(it.readBytes()) }.toList()
        }.toMap()
        val before = snapshot()
        ActivityScenario.launch(GazeCalibrationActivity::class.java).use { scenario -> scenario.onActivity {
            assertEquals(0,it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            assertFalse(it.findViewById<android.view.View>(com.newsmead.R.id.root).keepScreenOn)
            assertTrue(it.findViewById<android.view.View>(com.newsmead.R.id.startButton).isShown)
        } }
        ActivityScenario.launch(GazeTestActivity::class.java).use { scenario -> scenario.onActivity {
            assertEquals(0,it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            assertFalse(it.findViewById<android.view.View>(com.newsmead.R.id.root).keepScreenOn)
        } }
        assertEquals(before,snapshot())
    }
    @Test fun comparisonSetupRequiresExactInputsAndStaysCameraOffAndMutationFree() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun snapshot(): Map<String,String> = listOf(context.filesDir,context.noBackupFilesDir).flatMap { root ->
            root.walkTopDown().filter { it.isFile &&
                (it.path.contains("mgazenet") || it.path.contains("comparison-v1") || it.name.startsWith("calibration"))
            }.map { it.path to CalibrationIdentity.hash(it.readBytes()) }.toList()
        }.toMap()
        val slot: Pair<String,String> = when (Build.MODEL) {
            "SM-A566B" -> "a56_mgazenet_A" to "A"
            "SM-G991B" -> "g991b_mgazenet_B" to "B"
            else -> throw AssertionError("Comparison setup test requires one of the two frozen phone models")
        }
        fun comparisonIntent(activity: Class<*>): Intent = Intent(context,activity).apply {
            putExtra(ComparisonProtocol.EXTRA_PROTOCOL_ID,ComparisonProtocol.PROTOCOL_ID)
            putExtra(ComparisonProtocol.EXTRA_SLOT_ID,slot.first)
            putExtra(ComparisonProtocol.EXTRA_ORDER_VARIANT,slot.second)
        }
        val before = snapshot()
        ActivityScenario.launch<GazeCalibrationActivity>(comparisonIntent(GazeCalibrationActivity::class.java)).use { scenario -> scenario.onActivity {
            assertEquals(0,it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            assertFalse(it.findViewById<android.view.View>(com.newsmead.R.id.root).keepScreenOn)
            assertTrue(it.findViewById<android.view.View>(com.newsmead.R.id.startButton).isEnabled)
        } }
        ActivityScenario.launch<ReadingValidationActivity>(comparisonIntent(ReadingValidationActivity::class.java)).use { scenario -> scenario.onActivity {
            assertEquals(0,it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            assertTrue(it.findViewById<android.view.View>(com.newsmead.R.id.btnValidationAction).isEnabled)
        } }
        val incomplete = Intent(context,GazeCalibrationActivity::class.java).apply {
            putExtra(ComparisonProtocol.EXTRA_PROTOCOL_ID,ComparisonProtocol.PROTOCOL_ID)
        }
        ActivityScenario.launch<GazeCalibrationActivity>(incomplete).use { scenario -> scenario.onActivity {
            assertFalse(it.findViewById<android.view.View>(com.newsmead.R.id.startButton).isEnabled)
        } }
        assertEquals(before,snapshot())
    }
    @Test fun nativeSyntheticPersistenceAndStoreReload() {
        check(OpenCVLoader.initLocal())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val temporary = File(context.cacheDir,"synthetic-mgazenet-${UUID.randomUUID()}")
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext() = this
            override fun getNoBackupFilesDir() = temporary
        }
        try {
            val store = MgazeNetCalibrationStore(isolated)
            store.verifyNativePersistence()
            val features = SyntheticFixtures.calibrationFeatures(); val labels = SyntheticFixtures.calibrationLabels()
            try { SvrCalibration().use { original ->
                original.fit(features,labels)
                val identity = CalibrationIdentity("a".repeat(64),1080,2340,0,GazeCoordinateFrame(0,101,1080,2239),"640x480:270:GPU")
                store.save(identity,original)
                val before = store.fingerprint()
                try { store.save(identity,original) { false }; fail("Canceled save committed") }
                catch (_: IllegalStateException) { }
                assertEquals(before,store.fingerprint())
                val artifact = CalibrationBundle.decode(File(temporary,"mgazenet-v2/calibration.bin").readBytes()) { it == identity }
                MgazeNetCalibrationStore(isolated).load(artifact).use { restored ->
                    MgazeNetCalibrationStore.syntheticQueries().forEach { query ->
                        assertArrayEquals(original.predict(query),restored.predict(query),0f); query.fill(0f)
                    }
                }
                assertEquals(listOf("calibration.bin"),File(temporary,"mgazenet-v2").list()!!.sorted())
            } } finally { features.forEach { it.fill(0f) }; labels.forEach { it.fill(0f) } }
        } finally { temporary.deleteRecursively() }
    }
}
