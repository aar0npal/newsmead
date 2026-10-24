package com.newsmead.mgazenetbenchmark

import org.junit.Assert.*
import org.junit.Test

class InputCheckSessionTest {
    private fun frame(capture: Double, output: Double, reason: String = "features",
                      features: FloatArray? = FloatArray(258), area: Double = 20.0,
                      sizes: List<List<Int>>? = listOf(listOf(200,220),listOf(80,60),listOf(82,61))) =
        AccuracySession.Frame(capture,output,features,area,area,null,reason,sizes)

    @Test fun initializationAndObservationHaveIndependentTwentySecondWindows() {
        val session = InputCheckSession("software check",100.0)
        session.tick(19_999.0)
        assertEquals(InputCheckSession.Phase.INITIALIZING,session.phase)
        session.ready(20_000.0)
        assertEquals(20_000.0,session.plannedStartMs!!,0.0)
        assertEquals(40_000.0,session.plannedEndMs!!,0.0)
        session.tick(39_999.0)
        assertEquals(InputCheckSession.Phase.OBSERVING,session.phase)
        session.tick(40_000.0)
        assertEquals(InputCheckSession.Phase.COMPLETE,session.phase)
        assertEquals(20_000.0,session.observedDurationMs(),0.0)
    }

    @Test fun inputCategoriesAndAggregateGeometryAreRetainedWithoutFeatures() {
        val session = InputCheckSession("aggregate check",0.0)
        session.ready(100.0)
        session.frame(frame(100.0,110.0,"no_face",null,0.0,null))
        session.frame(frame(120.0,130.0,"invalid_crops",null,0.0,null))
        session.frame(frame(140.0,150.0,area=8.0))
        val sourceFeatures = FloatArray(258) { it.toFloat() }
        session.frame(frame(160.0,170.0,features=sourceFeatures))
        session.tick(20_100.0)
        val report = InputCheckReport.payload(session,"invented","no_phone",
            mapOf("camera_clock" to "REALTIME verified"),mapOf("observed_busy_drops" to 2))
        assertEquals(mapOf("eligible" to 1,"no_face" to 1,"invalid_crops" to 1,
            "eye_area_rejected" to 1,"invalid_features" to 0),session.categories)
        assertEquals(2,(session.areaSummaries()["left_px2"] as Map<*,*>)["count"])
        assertEquals(2,(session.cropSummaries()["face_width_px"] as Map<*,*>)["count"])
        assertEquals("mgazenet_input_check_v1",report["schema"])
        assertEquals(false,report["svr_fit"])
        assertEquals(false,report["calibration_grid_presented"])
        assertEquals(false,report["features_retained"])
        assertFalse(report.containsKey("calibration_id"))
        assertFalse(report.containsKey("fit_block_ids"))
        val json = AccuracyReport.encode(report)
        assertFalse(json.contains("257.0"))
        assertFalse(json.contains("landmark_values"))
    }

    @Test fun initializationTimeoutIsAnIncompleteRetainedOutcome() {
        val session = InputCheckSession("timeout",500.0)
        session.tick(20_500.0)
        assertEquals(InputCheckSession.Phase.FAILED,session.phase)
        assertEquals("camera_initialization_timeout",session.failure)
        val report = InputCheckReport.payload(session,"invented","no_phone",emptyMap(),emptyMap())
        assertEquals("mgazenet_input_check_partial_v1",report["schema"])
        assertNull(report["planned_start_ms"])
        assertEquals(20_000.0,report["unobserved_planned_duration_ms"])
    }

    @Test fun stoppedCheckKeepsTheFullPlanSeparateFromObservedTime() {
        val session = InputCheckSession("stopped",0.0)
        session.ready(100.0)
        session.frame(frame(1000.0,1010.0))
        session.stop("activity_backgrounded",5100.0)
        val report = InputCheckReport.payload(session,"invented","no_phone",emptyMap(),emptyMap())
        assertEquals(20_000.0,report["planned_duration_ms"])
        assertEquals(5000.0,report["observed_duration_ms"])
        assertEquals(15_000.0,report["unobserved_planned_duration_ms"])
        assertEquals("mgazenet_input_check_partial_v1",report["schema"])
    }

    @Test fun callbackOffsetsExposeInitialInterResultAndFinalNoResultIntervals() {
        val session = InputCheckSession("gaps",0.0)
        session.ready(100.0)
        session.frame(frame(990.0,1000.0))
        session.frame(frame(2990.0,3000.0))
        session.tick(20_100.0)
        val timing = session.timingSummary()
        assertEquals(listOf(900.0,2900.0),timing["output_offsets_ms"])
        assertEquals(900.0,timing["initial_callback_gap_ms"])
        assertEquals(17_100.0,timing["final_callback_gap_ms"])
        assertEquals(17_100.0,timing["longest_callback_gap_ms"])
    }

    @Test fun nonmonotonicFrameClockCannotProduceACompleteCheck() {
        val session = InputCheckSession("clock",0.0)
        session.ready(100.0)
        session.frame(frame(200.0,210.0))
        session.frame(frame(200.0,220.0))
        assertEquals(InputCheckSession.Phase.FAILED,session.phase)
        assertFalse(session.clockConsistent)
        assertEquals("invalid_or_nonmonotonic_frame_clock",session.failure)
    }

    @Test fun resourceCloseErrorDowngradesACompletedWindowToPartialEvidence() {
        val session = InputCheckSession("close failure",0.0)
        session.ready(100.0)
        session.tick(20_100.0)
        val report = InputCheckReport.payload(session,"invented","no_phone",emptyMap(),
            mapOf("resource_close_errors" to listOf("invented close error")))
        assertEquals("mgazenet_input_check_partial_v1",report["schema"])
        assertEquals("failed",report["outcome"])
        assertEquals("resource_close_failed",report["failure"])
    }
}
