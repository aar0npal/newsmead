package com.newsmead.gaze.mgazenet

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.gaze.GazeCoordinateFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.opencv.android.OpenCVLoader
import java.io.File
import java.nio.ByteBuffer

/** Camera-free two-invocation check. Pass mgazenetRestartPhase=prepare, force-stop, then pass verify. */
class MgazeNetProcessRestartTest {
    private fun root(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val parent = context.noBackupFilesDir.canonicalFile
        return File(parent,"mgazenet-process-restart-test-v1").canonicalFile.also {
            check(it.parentFile == parent) { "Restart-test storage escaped the no-backup directory" }
        }
    }
    private fun context(root: File) = object : ContextWrapper(
        InstrumentationRegistry.getInstrumentation().targetContext
    ) {
        override fun getApplicationContext() = this
        override fun getNoBackupFilesDir() = root
    }
    private fun identity() = CalibrationIdentity(
        "b".repeat(64),1080,2340,0,GazeCoordinateFrame(0,101,1080,2239),"640x480:270:GPU"
    )
    private fun predictionHash(model: SvrCalibration): String {
        val queries = MgazeNetCalibrationStore.syntheticQueries()
        val bytes = ByteBuffer.allocate(queries.size * 2 * 4)
        queries.forEach { query ->
            try {
                model.predict(query).forEach { value ->
                    check(value.isFinite())
                    bytes.putInt(value.toRawBits())
                }
            } finally { query.fill(0f) }
        }
        val data = bytes.array()
        return try { CalibrationIdentity.hash(data) } finally { data.fill(0) }
    }

    @Test fun syntheticCalibrationSurvivesProcessRestart() {
        val phase = InstrumentationRegistry.getArguments().getString("mgazenetRestartPhase")
        assumeTrue("Run explicitly with mgazenetRestartPhase=prepare or verify",phase == "prepare" || phase == "verify")
        check(OpenCVLoader.initLocal())
        if (phase == "prepare") prepare() else verify()
    }

    private fun prepare() {
        val root = root()
        if (root.exists()) check(root.deleteRecursively())
        check(root.mkdirs())
        val store = MgazeNetCalibrationStore(context(root))
        val features = SyntheticFixtures.calibrationFeatures()
        val labels = SyntheticFixtures.calibrationLabels()
        try {
            SvrCalibration().use { model ->
                model.fit(features,labels)
                val expected = predictionHash(model)
                store.save(identity(),model)
                val fingerprint = checkNotNull(store.fingerprint())
                File(root,"restart-proof.txt").writeText(
                    listOf("mgazenet-process-restart-test-v1",android.os.Process.myPid(),fingerprint,expected)
                        .joinToString("\n",postfix="\n")
                )
                android.util.Log.i("MgazeNetRestartCheck","prepared pid=${android.os.Process.myPid()}")
            }
        } finally {
            features.forEach { it.fill(0f) }
            labels.forEach { it.fill(0f) }
        }
    }

    private fun verify() {
        val root = root()
        try {
            val proof = File(root,"restart-proof.txt").readLines()
            assertEquals(4,proof.size)
            assertEquals("mgazenet-process-restart-test-v1",proof[0])
            assertNotEquals("Instrumentation process was not restarted",proof[1].toInt(),android.os.Process.myPid())
            val calibration = File(root,"mgazenet-v2/calibration.bin")
            assertEquals("Calibration artifact changed across restart",proof[2],CalibrationIdentity.hash(calibration.readBytes()))
            val saved = CalibrationBundle.decode(calibration.readBytes()) { it == identity() }
            MgazeNetCalibrationStore(context(root)).load(saved).use { restored ->
                assertEquals("Predictions changed across process restart",proof[3],predictionHash(restored))
            }
            android.util.Log.i(
                "MgazeNetRestartCheck",
                "verified prepared_pid=${proof[1]} current_pid=${android.os.Process.myPid()} artifact_and_predictions_match=true"
            )
        } finally {
            if (root.exists()) check(root.deleteRecursively())
        }
    }
}
