package com.newsmead.gaze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingSpatialMetricsTest {

    @Test
    fun reportsEachTargetAndDoesNotLetMedianHideWorstPoint() {
        val observations = (0 until 9).map { index ->
            val dy = if (index == 8) 300f else 20f
            ReadingSpatialMetrics.Observation(index, "P${index + 1}", 0f, 0f, 0f, dy)
        }
        val summary = requireNotNull(ReadingSpatialMetrics.summarize(observations, 100f))

        assertEquals(9, summary.points.size)
        assertEquals(0.2f, summary.medianVerticalPx / 100f, 0.001f)
        assertEquals(3f, summary.maxVerticalPx / 100f, 0.001f)
        assertEquals(8, summary.worstVertical.id)
        assertEquals(8, summary.withinReference)
        assertFalse(summary.meetsProvisionalReference)
    }

    @Test
    fun provisionalReferenceRequiresEveryPoint() {
        val observations = listOf(
            ReadingSpatialMetrics.Observation(0, "left", 0f, 0f, 10f, 50f),
            ReadingSpatialMetrics.Observation(1, "right", 0f, 0f, -10f, -120f),
        )
        val summary = requireNotNull(ReadingSpatialMetrics.summarize(observations, 100f))

        assertEquals(1, summary.withinHalfLine)
        assertEquals(1, summary.withinOneLine)
        assertEquals(2, summary.withinReference)
        assertTrue(summary.meetsProvisionalReference)
        assertEquals(-120f, summary.points[1].dyPx, 0.001f)
    }
}
