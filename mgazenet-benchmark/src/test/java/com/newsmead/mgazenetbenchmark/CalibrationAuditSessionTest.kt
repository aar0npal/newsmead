package com.newsmead.mgazenetbenchmark

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CalibrationAuditSessionTest {
    private fun layout() = AccuracySession.Layout(1080,2340,
        AccuracySession.Rect(40.0,101.0,1040.0,1901.0),60.0,
        List(30) { AccuracySession.Rect(40.0,101.0+it*60,1040.0,161.0+it*60) })
    private fun fresh() = CalibrationAuditSession(layout(),"synthetic calibration audit")
    private fun frame(capture: Double, marker: Float = 0f, prediction: FloatArray? = null,
                      area: Double = 20.0, features: FloatArray? = FloatArray(258) { marker+it/1000f }) =
        AccuracySession.Frame(capture,capture+10,features,area,area,prediction,"no_face")
    private fun calibrate(session: CalibrationAuditSession): Pair<Double,CalibrationAuditEngine.Training> {
        var now = 100.0
        repeat(17) { index ->
            val target = session.target!!
            session.presented(target.token,now)
            repeat(45) { row -> session.frame(frame(now+1500+row*33,index.toFloat())) }
            now += 1500+44*33+10+500
            session.tick(now)
            now++
        }
        assertEquals(CalibrationAuditSession.Phase.FITTING,session.phase)
        return now to session.training()
    }
    private fun audit(training: CalibrationAuditEngine.Training) = CalibrationAuditEngine.Result(folds=
        training.groups.map { group -> CalibrationAuditEngine.Fold(group.id,group.rows[0].normalizedLabel.copyOf(),
            group.rows.map { row -> CalibrationAuditEngine.Prediction(
                row.captureMs,row.sourceOutputMs,row.normalizedLabel.copyOf()) }) })

    @Test fun fixedNewsMeadGeometryProducesExactly720GroupedRows() {
        val session = fresh()
        assertEquals(17,session.fitPoints.size)
        assertEquals(16,session.fitPoints.count { !it.practice })
        val expected = CalibrationAuditSession.GRID_FRACTIONS.flatMap { y ->
            CalibrationAuditSession.GRID_FRACTIONS.map { x -> session.layout.fromFraction(x,y) }
        }
        assertEquals(expected,session.fitPoints.drop(1).map { it.point })
        assertEquals("fit_6",session.driftFitId)
        assertEquals(session.fitPoints[6].point,session.verificationBlocks.first().point)
        assertEquals(CalibrationAuditSession.VALIDATION_FRACTIONS.map { session.layout.fromFraction(it.first,it.second) },
            session.verificationBlocks.drop(1).map { it.point })
        assertTrue(session.verificationBlocks.drop(1).none { it.point in expected })

        val (_,training) = calibrate(session)
        assertEquals(720,session.trainingRows)
        assertEquals((1..16).map { "fit_$it" },training.groups.map { it.id })
        assertTrue(training.groups.all { it.rows.size == 45 })
        assertEquals(45,session.fitPoints.first().accepted)
        assertTrue(session.fitPoints.drop(1).all { it.featureDispersionRms != null })
        assertNotNull(session.fitDigest)
    }

    @Test fun sourceBuffersAndReturnedTrainingAreIndependentAndCanBeZeroed() {
        val session = fresh()
        var now = 100.0
        repeat(17) { index ->
            session.presented(session.target!!.token,now)
            repeat(45) { row ->
                val values = FloatArray(258) { index.toFloat() }
                session.frame(frame(now+1500+row*33,features=values))
                values.fill(-100f)
            }
            now += 1500+44*33+510
            session.tick(now)
            now++
        }
        val training = session.training()
        assertEquals(1f,training.groups.first().rows.first().features[0],0f)
        assertEquals(16f,training.groups.last().rows.last().features[0],0f)
        CalibrationAuditEngine.clear(training)
        assertTrue(training.groups.flatMap { it.rows }.all { row ->
            row.features.all { it == 0f } && row.normalizedLabel.all { it == 0f }
        })
    }

    @Test fun auditFailureCannotContinueIntoVerification() {
        val session = fresh()
        val (now,training) = calibrate(session)
        val incomplete = audit(training).copy(folds=audit(training).folds.dropLast(1))
        session.fitted(incomplete,true,now)
        assertEquals(CalibrationAuditSession.Phase.FAILED,session.phase)
        assertEquals("calibration_audit_or_fit_failed",session.failure)
        assertNull(session.target)
    }

    @Test fun fixedVerificationWindowsRemainDiagnosticOnlyAndComplete() {
        val session = fresh()
        var (now,training) = calibrate(session)
        val digest = session.fitDigest
        session.fitted(audit(training),true,now)
        assertEquals(CalibrationAuditSession.Phase.VERIFICATION,session.phase)
        repeat(6) { index ->
            session.presented(session.target!!.token,now)
            val block = session.verificationBlocks[index]
            assertEquals(now+3000,block.startMs!!,0.0)
            assertEquals(now+5500,block.endMs!!,0.0)
            session.frame(frame(block.startMs!!+10,prediction=session.layout.label(block.point)))
            session.frame(frame(block.startMs!!+50,features=null))
            now += 5750
            session.tick(now)
            now++
        }
        assertEquals(CalibrationAuditSession.Phase.COMPLETE,session.phase)
        assertEquals(digest,session.fitDigest)
        assertEquals(720,session.trainingRows)
        assertTrue(session.verificationBlocks.all { it.samples.size == 2 })
        assertThrows(IllegalStateException::class.java) { session.training() }
    }

    @Test fun setupTimingInvalidInputsAndCancellationAreRetainedAsIncomplete() {
        val unpresented = fresh()
        unpresented.frame(frame(1600.0))
        assertEquals(0,unpresented.fitPoints.first().accepted)
        unpresented.presented(999,2000.0)
        unpresented.frame(frame(3500.0))
        assertEquals(0,unpresented.fitPoints.first().accepted)

        val invalid = fresh()
        invalid.presented(invalid.target!!.token,0.0)
        invalid.frame(frame(1500.0,area=10.0))
        invalid.frame(frame(1533.0,features=FloatArray(257)))
        invalid.frame(frame(1566.0,features=FloatArray(258) { Float.NaN }))
        assertEquals(3,invalid.fitPoints.first().rejected.values.sum())
        invalid.frame(frame(1566.0))
        assertEquals(CalibrationAuditSession.Phase.FAILED,invalid.phase)

        val cancelled = fresh()
        cancelled.stop("activity_backgrounded",50.0)
        val report = CalibrationAuditReport.payload(cancelled,"cancelled","no_phone",
            mapOf("source" to "fake"),"synthetic_contract")
        assertEquals("mgazenet_calibration_audit_partial_v3",report["schema"])
        assertFalse(AccuracyReport.encode(report).contains("\"features\":"))
    }

    @Test fun completeExportContainsPredictionsButNoFeatureVectorsOrDecision() {
        val session = fresh()
        var (now,training) = calibrate(session)
        session.fitted(audit(training),true,now)
        repeat(6) {
            session.presented(session.target!!.token,now)
            val block = session.verificationBlocks[it]
            session.frame(frame(block.startMs!!+10,prediction=session.layout.label(block.point)))
            now += 5750
            session.tick(now)
            now++
        }
        val payload = CalibrationAuditReport.payload(session,"synthetic_audit","no_phone",
            mapOf("source" to "invented_features_and_predictions"),"synthetic_contract")
        val text = AccuracyReport.encode(payload)
        assertEquals("mgazenet_calibration_audit_v3",payload["schema"])
        assertNull(payload["accuracy_gate_pass"])
        assertEquals("not_evaluated",payload["promotion_decision"])
        assertFalse(text.contains("\"features\":"))
        assertFalse(text.contains("NaN"))
        assertEquals(16,(payload["loo_folds"] as List<*>).size)
        val generated = File("build/calibration-audit-contract-jvm/complete.json")
        requireNotNull(generated.parentFile).mkdirs()
        generated.writeText(text)
        val temp = Files.createTempDirectory("calibration-audit-evidence-test").toFile()
        val child = File(temp,"new_run")
        val file = CalibrationAuditReport.writeNew(child,payload)
        assertEquals(text,file.readText())
        file.delete(); File(child,"report.sha256").delete(); child.delete(); temp.delete()
    }
}
