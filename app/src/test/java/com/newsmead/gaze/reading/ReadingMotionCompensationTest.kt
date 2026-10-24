package com.newsmead.gaze.reading

import org.junit.Assert.*
import org.junit.Test

class ReadingMotionCompensationTest {
    private fun geometry(originY: Double = 0.0, id: String = "layout:1") = ReadingGeometry(
        id, 0.0, originY, ReadingRect(0.0, 0.0, 200.0, 1000.0), 20.0,
        listOf(ReadingLine(0, 1, 0.0, 200.0, 0.0, 1000.0)), ReadingTextDirection.LTR,
    )

    private fun sample(t: Double, screenY: Double = 300.0, originY: Double = 100.0) = ReadingSample(
        captureMs = t, deliveryMs = t + 80.0, rawX = 17.0, rawY = 19.0,
        line = 0, wordStartUtf16 = 0, wordEndUtf16 = 1, geometrySnapshotId = "layout:1",
        displayX = 50.0, displayY = screenY,
        coordinateStream = ReadingCoordinateStream.ARTICLE_SMOOTHED,
        motionCompensation = ReadingMotionCompensation(t, "layout:1", 0.0, originY, 50.0, screenY - originY),
    )

    @Test fun timelineUsesCaptureGeometryWithEightyMillisecondDeliveryLag() {
        val timeline = ReadingGeometryTimeline()
        assertTrue(timeline.record(100.0, geometry(100.0)))
        assertTrue(timeline.record(140.0, geometry(90.0)))
        assertTrue(timeline.record(180.0, geometry(80.0)))
        val captured = timeline.at(100.0, 100.0)!!
        assertEquals(100.0, captured.atMs, 0.0)
        assertEquals(100.0, captured.geometry.textOriginY, 0.0)
        assertEquals(140.0, timeline.at(179.0)!!.atMs, 0.0)
        assertNull(timeline.at(99.0))
        assertNull(timeline.at(181.0))
    }

    @Test fun timelineEnforcesRecordOrderAgeRetentionAndReset() {
        val timeline = ReadingGeometryTimeline(200.0)
        assertFalse(timeline.record(Double.NaN, geometry()))
        assertFalse(timeline.record(-1.0, geometry()))
        assertTrue(timeline.record(0.0, geometry(10.0)))
        assertTrue(timeline.record(200.0, geometry(20.0)))
        assertFalse(timeline.record(199.0, geometry()))
        assertTrue(timeline.record(200.0, geometry(30.0)))
        assertEquals(30.0, timeline.at(200.0)!!.geometry.textOriginY, 0.0)
        assertTrue(timeline.record(300.0, geometry()))
        assertNull(timeline.at(99.0))
        // A static floor remains causal for a recent capture; motion can impose max age.
        assertEquals(0.0, timeline.at(100.0)!!.atMs, 0.0)
        assertNotNull(timeline.at(100.0, 100.0))
        assertNull(timeline.at(100.0, 99.0))
        assertNull(timeline.at(Double.POSITIVE_INFINITY))
        assertNull(timeline.at(200.0, Double.NaN))
        assertNull(timeline.at(200.0, -1.0))
        timeline.clear()
        assertNull(timeline.at(200.0))
        assertTrue(timeline.record(1.0, geometry()))
    }

    @Test fun timelineBoundsResourceUseAndCopiesMutableLineLists() {
        val timeline = ReadingGeometryTimeline(10000.0)
        val lines = geometry().lines.toMutableList()
        assertTrue(timeline.record(0.0, geometry().copy(lines = lines)))
        lines.clear()
        assertEquals(1, timeline.at(0.0)!!.geometry.lines.size)
        for (t in 1..4100) assertTrue(timeline.record(t.toDouble(), geometry()))
        assertNull(timeline.at(0.0))
        assertNotNull(timeline.at(4100.0))
    }

    @Test fun bodyPointPursuitPreservesRawDisplayAndProcessingCoordinates() {
        for (i in 0..6) {
            val s = sample(100.0 + i * 50.0, 300.0 - i * 20.0, 100.0 - i * 20.0)
            assertEquals(17.0, s.rawX!!, 0.0)
            assertEquals(19.0, s.rawY!!, 0.0)
            assertEquals(s.displayY, s.processingY)
            assertEquals(200.0, s.analysisY!!, 0.0)
        }
    }

    @Test fun fixedScreenPointChangesContentCoordinatesDuringTranslation() {
        for (i in 0..6) {
            val s = sample(100.0 + i * 50.0, 300.0, 100.0 - i * 20.0)
            assertEquals(200.0 + i * 20.0, s.analysisY!!, 0.0)
        }
    }

    @Test fun absentMetadataKeepsDeclaredStreamCoordinates() {
        val s = sample(100.0).copy(motionCompensation = null)
        assertEquals(s.processingX, s.analysisX)
        assertEquals(s.processingY, s.analysisY)
        val raw = s.copy(coordinateStream = ReadingCoordinateStream.CALIBRATED_RAW)
        assertEquals(17.0, raw.analysisX!!, 0.0)
        assertEquals(19.0, raw.analysisY!!, 0.0)
    }

    @Test fun malformedMetadataAbstainsFromContentCoordinates() {
        val base = sample(300.0)
        val m = base.motionCompensation!!
        val bad = listOf(
            base.copy(coordinateStream = ReadingCoordinateStream.CALIBRATED_RAW),
            base.copy(motionCompensation = m.copy(modeId = "unknown")),
            base.copy(motionCompensation = m.copy(geometrySnapshotId = "other")),
            base.copy(motionCompensation = m.copy(geometryTimeMs = 301.0)),
            base.copy(motionCompensation = m.copy(geometryTimeMs = -1.0)),
            base.copy(motionCompensation = m.copy(contentY = 201.0)),
            base.copy(motionCompensation = m.copy(textOriginX = Double.NaN)),
            base.copy(displayY = Double.POSITIVE_INFINITY),
            base.copy(motionCompensation = m.copy(contentX = Double.NaN)),
        )
        for (invalid in bad) {
            assertFalse(invalid.motionCompensation!!.validFor(invalid))
            assertNull(invalid.analysisX)
            assertNull(invalid.analysisY)
        }
    }

    @Test fun compensationPreservesMissingWordAssignment() {
        val s = sample(150.0).copy(wordStartUtf16 = null, wordEndUtf16 = null)
        assertTrue(s.motionCompensation!!.validFor(s))
        assertNull(s.wordStartUtf16)
        assertNull(s.wordEndUtf16)
        assertEquals(0, s.line)
        assertEquals(200.0, s.analysisY!!, 0.0)
    }
}
