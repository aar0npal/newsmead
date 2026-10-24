package com.newsmead.gaze

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

/** All saves use an isolated cache subdirectory, never the participant's files. */
@RunWith(AndroidJUnit4::class)
class CalibrationCoordinateStoreTest {
    private fun isolatedStore(test: (ContextWrapper, File) -> Unit) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = Files.createTempDirectory(base.cacheDir.toPath(), "coordinate-store-test-").toFile()
        val context = object : ContextWrapper(base) {
            override fun getFilesDir(): File = directory
        }
        try {
            test(context, directory)
        } finally {
            directory.listFiles()?.forEach { check(it.delete()) }
            check(directory.delete())
        }
    }

    private fun samples() = listOf(
        CalibrationSample(200f, 300f, .2f, .2f),
        CalibrationSample(800f, 300f, .8f, .2f),
        CalibrationSample(200f, 1900f, .2f, .8f),
        CalibrationSample(800f, 1900f, .8f, .8f),
    )

    @Test
    fun legacyAndStaleMetadataAreRejectedWithoutChangingFiles() = isolatedStore { context, dir ->
        assertNotNull(CalibrationStore.compatibilityIssue(context))
        val csv = File(dir, "calibration_16point.csv")
        val original = CalibrationCsvCodec.HEADER + "\n" + samples().joinToString("\n", transform = CalibrationCsvCodec::encode)
        csv.writeText(original)
        File(dir, "calibration_feature_mode.txt").writeText(LocalGazeSources.ACTIVE_FEATURE_MODE.logLabel)
        val historicalLog = File(dir, "calibration_session_history.json").apply { writeText("preserve me") }
        assertTrue(CalibrationStore.compatibilityIssue(context)!!.contains("screen-coordinate"))
        assertNull(CalibrationStore.load(context))
        assertEquals(original, csv.readText())
        assertFalse(File(dir, "calibration_coordinates.txt").exists())

        CalibrationStore.saveScreenCalibration(context, samples())
        assertNull(CalibrationStore.compatibilityIssue(context))
        assertEquals(4, CalibrationStore.load(context)!!.size)
        val certified = csv.readText()
        csv.writeText(certified.replace("1900.0", "1799.0")) // Simulate an old writer after rollback.
        assertNotNull(CalibrationStore.compatibilityIssue(context))
        assertNull(CalibrationStore.load(context))
        csv.writeText(certified)
        assertNull(CalibrationStore.compatibilityIssue(context))
        File(dir, "calibration_feature_mode.txt").writeText("legacy")
        assertNull(CalibrationStore.load(context))
        assertEquals("preserve me", historicalLog.readText())
    }

    @Test
    fun driftRequiresMatchingCalibrationAndCoordinateMarker() = isolatedStore { context, dir ->
        val identity = DriftCorrection(doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0))
        CalibrationStore.saveScreenCalibration(context, samples())
        CalibrationStore.saveDriftCorrection(context, identity, 10f, 5f)
        assertNotNull(CalibrationStore.loadDriftCorrection(context))
        val csv = File(dir, "drift_correction.csv")
        val metadata = File(dir, "drift_correction_coordinates.txt")
        val oldCsv = csv.readText()
        val oldMetadata = metadata.readText()
        metadata.delete()
        assertNull(CalibrationStore.loadDriftCorrection(context))
        metadata.writeText(oldMetadata)
        csv.appendText("changed\n")
        assertNull(CalibrationStore.loadDriftCorrection(context))

        CalibrationStore.saveScreenCalibration(context, samples().map { it.copy(screenY = it.screenY + 50) })
        assertFalse(csv.exists())
        assertFalse(metadata.exists())
        csv.writeText(oldCsv)
        metadata.writeText(oldMetadata)
        assertNull(CalibrationStore.loadDriftCorrection(context))
        CalibrationStore.saveDriftCorrection(context, identity, 10f, 5f)
        assertNotNull(CalibrationStore.loadDriftCorrection(context))
    }
}
