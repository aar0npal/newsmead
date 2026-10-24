package com.newsmead.mgazenetbenchmark

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AccuracySessionTest {
    private fun layout(offset: Double = 101.0) = AccuracySession.Layout(1080,2340,
        AccuracySession.Rect(40.0,offset,1040.0,offset+1800),60.0,
        List(30) { AccuracySession.Rect(40.0,offset+it*60,1040.0,offset+(it+1)*60) })
    private fun fresh(order: AccuracySession.ValidationOrder = AccuracySession.ValidationOrder.FORWARD_THEN_REVERSE) =
        AccuracySession(layout(),"synthetic state-machine check",order)
    private fun frame(capture: Double, features: FloatArray? = FloatArray(258) { it/1000f },
                      prediction: FloatArray? = null, area: Double = 20.0) =
        AccuracySession.Frame(capture,capture+10,features,area,area,prediction,"no_face")
    private fun calibrate(session: AccuracySession): Double {
        var now = 100.0
        repeat(14) {
            val target = session.target!!
            session.presented(target.token,now)
            repeat(45) { n -> session.frame(frame(now+1500+n*33)) }
            now += 1500+44*33+10+500
            session.tick(now)
            now += 1
        }
        assertEquals(AccuracySession.Phase.FITTING,session.phase)
        return now
    }
    @Test fun practiceAndSettleFramesCannotLeakIntoExactly585TrainingRows() {
        val session = fresh()
        val now = calibrate(session)
        val training = session.training()
        assertEquals(585,training.features.size)
        assertEquals(585,training.labels.size)
        assertEquals(45,session.fitPoints.first().accepted)
        assertEquals(13,session.fitPoints.count { !it.practice })
        assertArrayEquals(session.layout.label(session.fitPoints[1].point),training.labels.first(),0f)
        assertArrayEquals(session.layout.label(session.fitPoints.last().point),training.labels.last(),0f)
        session.fitted(true,now)
        assertEquals(AccuracySession.Phase.VALIDATION,session.phase)
        assertNotNull(session.fitDigest)
    }
    @Test fun targetMustBePresentedAndCaptureMustFollowSettle() {
        val session = fresh()
        session.frame(frame(10.0))
        session.presented(999,100.0)
        session.frame(frame(2000.0))
        assertEquals(0,session.fitPoints.first().accepted)
        session.presented(session.target!!.token,3000.0)
        session.frame(frame(4499.0))
        session.frame(frame(4500.0))
        assertEquals(1,session.fitPoints.first().accepted)
    }
    @Test fun invalidFeaturesAndClosedEyesDoNotCountAndTargetTimesOut() {
        val session = fresh(); session.presented(session.target!!.token,0.0)
        session.frame(frame(1500.0,area=10.0))
        session.frame(frame(1533.0,features=FloatArray(257)))
        session.frame(frame(1566.0,features=FloatArray(258) { Float.NaN }))
        session.frame(frame(1600.0,features=null))
        assertEquals(0,session.fitPoints.first().accepted)
        assertEquals(4,session.fitPoints.first().rejected.values.sum())
        session.tick(30_000.0)
        assertEquals(AccuracySession.Phase.FAILED,session.phase)
        assertEquals("calibration_target_timeout",session.failure)
        assertThrows(IllegalStateException::class.java) { session.training() }
    }
    @Test fun collectedTrainingCopiesAreIndependentOfSourceBufferReuse() {
        val session = fresh()
        var now = 100.0
        repeat(14) { index ->
            session.presented(session.target!!.token,now)
            repeat(45) { n ->
                val values = FloatArray(258) { index.toFloat() }
                session.frame(frame(now+1500+n*33,values)); values.fill(-100f)
            }
            now += 1500+44*33+510
            session.tick(now); now++
        }
        val training = session.training()
        assertEquals(1f,training.features.first()[0],0f)
        assertEquals(13f,training.features.last()[0],0f)
    }
    @Test fun emptyHeldOutBlocksKeepFixedWindowsAndNeverTrain() {
        val session = fresh(); var now = calibrate(session)
        session.training(); session.fitted(true,now)
        val digest = session.fitDigest
        repeat(session.blocks.size) {
            session.presented(session.target!!.token,now)
            val block = session.blocks[it]
            assertEquals(now+3000,block.startMs!!,0.0)
            assertEquals(now+5500,block.endMs!!,0.0)
            now += 5750; session.tick(now); now++
        }
        assertEquals(AccuracySession.Phase.COMPLETE,session.phase)
        assertEquals(20,session.blocks.count { it.samples.isEmpty() })
        assertEquals(digest,session.fitDigest)
        assertEquals(585,session.trainingRows)
        assertThrows(IllegalStateException::class.java) { session.training() }
    }
    @Test fun delayedFrameUsesItsCapturedTargetNotTheCurrentlyDrawnTarget() {
        val session = fresh(); var now = calibrate(session)
        session.training(); session.fitted(true,now)
        session.presented(session.target!!.token,now)
        val old = session.blocks.first()
        val capture = old.endMs!!-1
        now += 5750; session.tick(now); session.presented(session.target!!.token,now+1)
        val delayed = frame(capture,prediction=session.layout.label(old.point)).copy(outputMs=now+20)
        session.frame(delayed)
        assertEquals(1,old.samples.size)
        assertEquals(0,session.blocks[1].samples.size)
        assertEquals(0.0,old.samples.first().point!!.x-old.point.x,.001)
    }
    @Test fun cancellationDuringFitIsTerminalAndPreservesPlannedTestsAsIncomplete() {
        val session = fresh(); val now = calibrate(session)
        session.training(); session.stop("activity_backgrounded",now)
        session.fitted(true,now+100)
        assertEquals(AccuracySession.Phase.STOPPED,session.phase)
        assertNull(session.target)
        assertEquals(20,session.blocks.size)
        val report = AccuracyReport.payload(session,"synthetic_cancel","no_phone",mapOf("source" to "fake"),"synthetic_contract")
        assertEquals("mgazenet_accuracy_partial_v2",report["schema"])
        assertFalse(AccuracyReport.encode(report).contains("\"features\":"))
    }
    @Test fun screenOriginAndDisplayDimensionsDefineCalibrationLabels() {
        val session = fresh()
        val p = session.fitPoints[1].point
        val label = session.layout.label(p)
        assertEquals(p.y/2340,label[1].toDouble(),1e-7)
        assertEquals(p.x,session.layout.pixels(label).x,1e-4)
        assertEquals(p.y,session.layout.pixels(label).y,1e-4)
        assertTrue(p.y > 101)
        assertTrue(session.blocks.all { session.layout.lines[it.line].contains(it.point) })
        assertFalse(session.blocks.any { b -> session.fitPoints.any { it.point == b.point } })
    }
    @Test fun repeatedLocationsHaveFrozenCounterbalancedSweepMetadata() {
        val forwardFirst = fresh()
        val reverseFirst = fresh(AccuracySession.ValidationOrder.REVERSE_THEN_FORWARD)
        val expected = listOf(2,8,13,15,22,24,31,33,38,44)
        assertEquals(expected,forwardFirst.blocks.take(10).map { it.gridIndex })
        assertEquals(expected.reversed(),forwardFirst.blocks.drop(10).map { it.gridIndex })
        assertEquals(expected.reversed(),reverseFirst.blocks.take(10).map { it.gridIndex })
        assertEquals(expected,reverseFirst.blocks.drop(10).map { it.gridIndex })
        assertEquals(listOf("forward","reverse"),forwardFirst.blocks.chunked(10).map { it.first().sweepDirection })
        assertEquals(listOf("reverse","forward"),reverseFirst.blocks.chunked(10).map { it.first().sweepDirection })
        assertEquals(20,forwardFirst.blocks.map { it.id }.distinct().size)
        assertEquals(10,forwardFirst.blocks.map { it.locationId }.distinct().size)
        assertTrue(forwardFirst.blocks.groupBy { it.locationId }.values.all { repeated ->
            repeated.size == 2 && repeated[0].point == repeated[1].point
        })
        assertEquals((1..10).toList(),forwardFirst.blocks.take(10).map { it.orderInSweep })
        assertEquals((1..10).toList(),forwardFirst.blocks.drop(10).map { it.orderInSweep })
        assertEquals(listOf(50,100,200,500),AccuracySession.ANALYSIS_AGE_LIMITS_MS)
    }
    @Test fun invalidClockCannotCreateACompleteEvidenceRecord() {
        val session = fresh(); session.presented(session.target!!.token,0.0)
        session.frame(frame(1500.0)); session.frame(frame(1500.0))
        assertEquals(AccuracySession.Phase.FAILED,session.phase)
        assertEquals("invalid_or_nonmonotonic_frame_clock",session.failure)
    }
    @Test fun exportCompleteSyntheticSessionForIndependentPythonScoring() {
        val session = fresh(); var now = calibrate(session)
        session.training(); session.fitted(true,now)
        repeat(session.blocks.size) { index ->
            session.presented(session.target!!.token,now)
            val block = session.blocks[index]
            if (index < session.blocks.lastIndex) {
                session.frame(frame(block.startMs!!+10,prediction=session.layout.label(block.point)))
                session.frame(frame(block.startMs!!+50,features=null))
            }
            now += 5750; session.tick(now); now++
        }
        val payload = AccuracyReport.payload(session,"synthetic_export","no_phone",
            mapOf("source" to "invented_features_and_predictions"),"synthetic_contract")
        val text = AccuracyReport.encode(payload)
        assertFalse(text.contains("NaN")); assertFalse(text.contains("\"features\":"))
        assertEquals("mgazenet_accuracy_v2",payload["schema"])
        assertNull(payload["max_output_age_ms"])
        assertEquals(listOf(50,100,200,500),payload["analysis_age_limits_ms"])
        val generated = File("build/accuracy-contract-jvm/complete.json")
        requireNotNull(generated.parentFile).mkdirs(); generated.writeText(text)
        val temp = Files.createTempDirectory("accuracy-evidence-test").toFile()
        val child = File(temp,"new_run")
        val file = AccuracyReport.writeNew(child,payload)
        assertEquals(text,file.readText())
        assertEquals(AccuracyReport.sha256(text.toByteArray()),File(child,"report.sha256").readText().trim())
        assertThrows(IllegalStateException::class.java) { AccuracyReport.writeNew(child,payload) }
        // Exact files created by this test only; no recursive directory removal.
        file.delete(); File(child,"report.sha256").delete(); child.delete(); temp.delete()
    }
}
