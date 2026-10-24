package com.newsmead.gaze.mgazenet

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Source-generated pixel-area checks, not validation of blink detection on people. */
@RunWith(Parameterized::class)
class UpstreamOpennessParityTest(private val name: String, private val row: List<String>) {
    @Test fun matchesUpstreamEyeAreasAndReferenceThresholdDecision() {
        val points = MutableList(478) { GazeGeometry.Landmark(.5, .5) }
        row[3].split(';').forEach {
            val values = it.split(':')
            points[values[0].toInt()] = GazeGeometry.Landmark(values[1].toDouble(), values[2].toDouble())
        }
        val actual = requireNotNull(GazeGeometry.crops(points, row[1].toInt(), row[2].toInt()))
        assertEquals(name, row[4].toDouble(), actual.leftOpenness, 0.0)
        assertEquals(name, row[5].toDouble(), actual.rightOpenness, 0.0)
        assertEquals(name, row[6].toBoolean(), actual.leftOpenness > 10 && actual.rightOpenness > 10)
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): Collection<Array<Any>> = requireNotNull(
            UpstreamOpennessParityTest::class.java.getResourceAsStream("/upstream-openness.tsv")
        ).bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith('#') }.map {
                val row = it.split('\t')
                arrayOf<Any>(row[0], row)
            }.toList()
        }
    }
}
