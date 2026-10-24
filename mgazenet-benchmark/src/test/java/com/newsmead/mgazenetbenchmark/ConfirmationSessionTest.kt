package com.newsmead.mgazenetbenchmark

import org.junit.Assert.*
import org.junit.Test

class ConfirmationSessionTest {
    private fun layout() = AccuracySession.Layout(1080,2340,
        AccuracySession.Rect(40.0,101.0,1040.0,1901.0),60.0,
        List(30) { AccuracySession.Rect(40.0,101.0+it*60,1040.0,161.0+it*60) })

    private fun fresh(order: AccuracySession.ValidationOrder =
        AccuracySession.ValidationOrder.FORWARD_THEN_REVERSE) =
        ConfirmationSession(layout(),"synthetic confirmation",order)

    private fun frame(capture: Double, marker: Float = 0f,
                      prediction: FloatArray? = null, area: Double = 20.0,
                      features: FloatArray? = FloatArray(258) { marker+it/1000f }) =
        AccuracySession.Frame(capture,capture+10,features,area,area,prediction,"no_face")

    private fun audit(training: CalibrationAuditEngine.Training) = CalibrationAuditEngine.Result(folds=
        training.groups.map { group -> CalibrationAuditEngine.Fold(group.id,
            group.rows.first().normalizedLabel.copyOf(),group.rows.map { row ->
                CalibrationAuditEngine.Prediction(row.captureMs,row.sourceOutputMs,
                    row.normalizedLabel.copyOf())
            }) })

    private fun calibrateAndFit(session: ConfirmationSession): Double {
        var now = 100.0
        repeat(17) { index ->
            session.presented(session.target!!.token,now)
            repeat(45) { row -> session.frame(frame(now+1500+row*33,index.toFloat())) }
            now += 1500+44*33+10+500
            session.tick(now)
            now++
        }
        assertEquals(ConfirmationSession.Phase.FITTING,session.phase)
        val training = session.training()
        session.fitted(audit(training),true,now)
        assertEquals(ConfirmationSession.Phase.SCREEN,session.phase)
        return now
    }

    private fun finishScreen(session: ConfirmationSession, start: Double,
                             verticalErrorLines: Double): Double {
        var now = start
        repeat(session.screenBlocks.size) { index ->
            session.presented(session.target!!.token,now)
            val block = session.screenBlocks[index]
            val prediction = session.layout.label(AccuracySession.Point(
                block.point.x,block.point.y+verticalErrorLines*session.layout.lineHeight))
            repeat(10) { sample ->
                session.frame(frame(block.startMs!!+10+sample*20,prediction=prediction))
            }
            now = block.endMs!!+ConfirmationSession.DRAIN_MS
            session.tick(now)
            now++
        }
        return now
    }

    @Test fun geometryFreezesSixteenFitFiveScreenAndTwoCounterbalancedSweeps() {
        val forward = fresh()
        val reverse = fresh(AccuracySession.ValidationOrder.REVERSE_THEN_FORWARD)
        assertEquals(16,forward.fitPoints.count { !it.practice })
        assertEquals(CalibrationAuditSession.VALIDATION_FRACTIONS.mapIndexed { index,pair ->
            "screen_validation_${index+1}" to forward.layout.fromFraction(pair.first,pair.second)
        },forward.screenBlocks.map { it.id to it.point })
        assertTrue(forward.screenBlocks.none { screen ->
            forward.fitPoints.filterNot { it.practice }.any { it.point == screen.point }
        })
        val expected = listOf(2,8,13,15,22,24,31,33,38,44)
        assertEquals(expected,forward.confirmationBlocks.take(10).map { it.gridIndex })
        assertEquals(expected.reversed(),forward.confirmationBlocks.drop(10).map { it.gridIndex })
        assertEquals(expected.reversed(),reverse.confirmationBlocks.take(10).map { it.gridIndex })
        assertEquals(expected,reverse.confirmationBlocks.drop(10).map { it.gridIndex })
        assertTrue(forward.confirmationBlocks.groupBy { it.locationId }.values.all { repeated ->
            repeated.size == 2 && repeated[0].point == repeated[1].point
        })
        assertTrue(forward.confirmationBlocks.all { forward.layout.lines[it.line].contains(it.point) })
        assertTrue(forward.confirmationBlocks.none { confirmation ->
            forward.screenBlocks.any { it.point == confirmation.point } ||
                forward.fitPoints.filterNot { it.practice }.any { it.point == confirmation.point }
        })
    }

    @Test fun passAndFailScreensBothSealAndEnterTheSameConfirmationSequence() {
        val passing = fresh()
        val failing = fresh()
        val passNow = finishScreen(passing,calibrateAndFit(passing),0.2)
        val failNow = finishScreen(failing,calibrateAndFit(failing),2.0)

        assertEquals(ConfirmationSession.Phase.CONFIRMATION,passing.phase)
        assertEquals(ConfirmationSession.Phase.CONFIRMATION,failing.phase)
        assertTrue(passing.screenResult!!.candidatePass)
        assertFalse(failing.screenResult!!.candidatePass)
        assertEquals(passing.confirmationBlocks.map { it.id },failing.confirmationBlocks.map { it.id })
        assertEquals(passing.confirmationBlocks.first().id,passing.target!!.id)
        assertEquals(failing.confirmationBlocks.first().id,failing.target!!.id)
        assertTrue(passing.screenResult!!.sealedMs < passNow)
        assertTrue(failing.screenResult!!.sealedMs < failNow)
    }

    @Test fun sealedScreenCannotAcceptLateScreenOutputAndCompletionCannotMutate() {
        val session = fresh()
        var now = finishScreen(session,calibrateAndFit(session),0.2)
        val result = session.screenResult!!
        val sizes = session.screenBlocks.map { it.samples.size }
        val last = session.screenBlocks.last()
        val lateCapture = last.endMs!!-1
        session.frame(frame(lateCapture,prediction=session.layout.label(last.point)).copy(outputMs=now+10))
        assertSame(result,session.screenResult)
        assertEquals(sizes,session.screenBlocks.map { it.samples.size })

        repeat(session.confirmationBlocks.size) { index ->
            session.presented(session.target!!.token,now)
            val block = session.confirmationBlocks[index]
            repeat(10) { sample ->
                session.frame(frame(block.startMs!!+10+sample*20,
                    prediction=session.layout.label(block.point)))
            }
            now = block.endMs!!+ConfirmationSession.DRAIN_MS
            session.tick(now)
            now++
        }
        assertEquals(ConfirmationSession.Phase.COMPLETE,session.phase)
        val finalSizes = session.confirmationBlocks.map { it.samples.size }
        session.frame(frame(now+100,prediction=floatArrayOf(.5f,.5f)))
        assertEquals(finalSizes,session.confirmationBlocks.map { it.samples.size })
        assertSame(result,session.screenResult)
    }

    @Test fun completeNumericExportContainsSealedResultButNoFeaturesOrPromotion() {
        val session = fresh()
        var now = finishScreen(session,calibrateAndFit(session),0.2)
        repeat(session.confirmationBlocks.size) { index ->
            session.presented(session.target!!.token,now)
            val block = session.confirmationBlocks[index]
            repeat(10) { sample -> session.frame(frame(block.startMs!!+10+sample*20,
                prediction=session.layout.label(block.point))) }
            now = block.endMs!!+ConfirmationSession.DRAIN_MS
            session.tick(now)
            now++
        }
        val payload = ConfirmationReport.payload(session,"synthetic_confirmation","no_phone",
            mapOf("source" to "invented_features_and_predictions"),"synthetic_contract")
        val text = AccuracyReport.encode(payload)
        assertEquals("mgazenet_direct_validation_confirmation_v1",payload["schema"])
        assertEquals(true,payload["screen_candidate_pass"])
        assertNull(payload["confirmation_accuracy_pass"])
        assertNull(payload["accuracy_gate_pass"])
        assertEquals("not_evaluated",payload["promotion_decision"])
        assertEquals(5,(payload["screen_blocks"] as List<*>).size)
        assertEquals(20,(payload["confirmation_blocks"] as List<*>).size)
        assertEquals(16,(payload["loo_folds"] as List<*>).size)
        assertFalse(text.contains("\"features\":"))
        assertFalse(text.contains("NaN"))
        val generated = java.io.File("build/confirmation-contract-jvm/complete.json")
        requireNotNull(generated.parentFile).mkdirs()
        generated.writeText(text)
    }

    @Test fun sparseScreenFailsButDoesNotAbortAndStoppedRecordRemainsPartial() {
        val session = fresh()
        var now = calibrateAndFit(session)
        repeat(session.screenBlocks.size) { index ->
            session.presented(session.target!!.token,now)
            val block = session.screenBlocks[index]
            if (index > 0) repeat(9) { sample -> session.frame(frame(block.startMs!!+10+sample*20,
                prediction=session.layout.label(block.point))) }
            now = block.endMs!!+ConfirmationSession.DRAIN_MS
            session.tick(now)
            now++
        }
        assertEquals(ConfirmationSession.Phase.CONFIRMATION,session.phase)
        assertFalse(session.screenResult!!.candidatePass)
        assertFalse(session.screenResult!!.allFiveTargetsContribute)
        session.stop("activity_backgrounded",now)
        val payload = ConfirmationReport.payload(session,"partial","no_phone",
            mapOf("source" to "fake"),"synthetic_contract")
        assertEquals("mgazenet_direct_validation_confirmation_partial_v1",payload["schema"])
        assertEquals("stopped",payload["outcome"])
        assertFalse(AccuracyReport.encode(payload).contains("\"features\":"))
    }

    @Test fun incompleteAuditCannotReachScreen() {
        val session = fresh()
        var now = 100.0
        repeat(17) { index ->
            session.presented(session.target!!.token,now)
            repeat(45) { row -> session.frame(frame(now+1500+row*33,index.toFloat())) }
            now += 1500+44*33+510
            session.tick(now)
            now++
        }
        val training = session.training()
        val incomplete = audit(training).let { it.copy(folds=it.folds.dropLast(1)) }
        session.fitted(incomplete,true,now)
        assertEquals(ConfirmationSession.Phase.FAILED,session.phase)
        assertNull(session.screenResult)
        assertNull(session.target)
    }
}
