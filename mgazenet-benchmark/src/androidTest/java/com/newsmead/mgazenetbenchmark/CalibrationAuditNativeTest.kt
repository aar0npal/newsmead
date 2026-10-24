package com.newsmead.mgazenetbenchmark

import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.opencv.android.OpenCVLoader

/** Camera-free execution check for every native SVR fit used by calibration audit v3. */
class CalibrationAuditNativeTest {
    @Before fun initialize() { check(OpenCVLoader.initLocal()) }

    @Test fun realSvrRunsSixteenWholeTargetFoldsAndFinalFit() {
        var clock = 100.0
        val fractions = CalibrationAuditSession.GRID_FRACTIONS
        val training = CalibrationAuditEngine.Training((0 until 16).map { groupIndex ->
            val x = fractions[groupIndex%4].toFloat()
            val y = fractions[groupIndex/4].toFloat()
            CalibrationAuditEngine.Group("fit_${groupIndex+1}",List(45) { rowIndex ->
                clock += 20
                val features = FloatArray(258) { featureIndex ->
                    if (featureIndex == 0) groupIndex.toFloat()
                    else ((groupIndex*17+rowIndex*3+featureIndex*13)%101)/100f
                }
                CalibrationAuditEngine.Row(features,floatArrayOf(x,y),clock,clock+7)
            })
        })
        val fitSizes = mutableListOf<Int>()
        val fitMarkers = mutableListOf<Set<Int>>()
        val supportCounts = mutableListOf<List<Int>>()
        val auditStarted = SystemClock.elapsedRealtimeNanos()
        try {
            val audit = CalibrationAuditEngine {
                val delegate = SvrCalibration()
                object : CalibrationRegressor {
                    override fun fit(features: Array<FloatArray>, normalizedLabels: Array<FloatArray>) {
                        fitSizes.add(features.size)
                        fitMarkers.add(features.map { it[0].toInt() }.toSet())
                        delegate.fit(features,normalizedLabels)
                        supportCounts.add(delegate.supportVectorCounts)
                    }
                    override fun predict(features: FloatArray) = delegate.predict(features)
                    override fun close() = delegate.close()
                }
            }.audit(training)
            val auditElapsedMs = (SystemClock.elapsedRealtimeNanos()-auditStarted)/1e6
            assertEquals((1..16).map { "fit_$it" },audit.folds.map { it.heldOutId })
            assertEquals(List(16) { 675 },fitSizes)
            assertEquals(16,supportCounts.size)
            supportCounts.flatten().forEach { assertTrue(it in 1..675) }
            fitMarkers.forEachIndexed { heldOutIndex, markers ->
                assertEquals((0 until 16).filter { it != heldOutIndex }.toSet(),markers)
            }
            assertTrue(audit.folds.all { fold ->
                fold.predictions.size == 45 && fold.predictions.all { prediction ->
                    prediction.normalizedPoint.size == 2 && prediction.normalizedPoint.all(Float::isFinite)
                }
            })

            val finalStarted = SystemClock.elapsedRealtimeNanos()
            val (features,labels) = CalibrationAuditEngine.flattened(training)
            SvrCalibration().use { finalFit ->
                finalFit.fit(features,labels)
                assertTrue(finalFit.trained)
                assertTrue(finalFit.supportVectorCounts.all { it in 1..720 })
                training.groups.forEach { group ->
                    assertTrue(finalFit.predict(group.rows.first().features).all(Float::isFinite))
                }
            }
            val finalElapsedMs = (SystemClock.elapsedRealtimeNanos()-finalStarted)/1e6
            assertTrue(auditElapsedMs > 0 && finalElapsedMs > 0)
            InstrumentationRegistry.getInstrumentation().sendStatus(0,Bundle().apply {
                putString("native_calibration_audit_method",audit.method)
                putInt("native_calibration_audit_folds",audit.folds.size)
                putInt("native_calibration_audit_rows_per_fold",fitSizes.first())
                putDouble("native_calibration_audit_ms",auditElapsedMs)
                putDouble("native_calibration_final_fit_ms",finalElapsedMs)
            })
        } finally {
            CalibrationAuditEngine.clear(training)
        }
        assertTrue(training.groups.flatMap { it.rows }.all { row ->
            row.features.all { it == 0f } && row.normalizedLabel.all { it == 0f }
        })
    }
}
