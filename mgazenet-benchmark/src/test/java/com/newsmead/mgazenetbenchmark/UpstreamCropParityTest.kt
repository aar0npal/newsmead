package com.newsmead.mgazenetbenchmark

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Expectations produced by exact pinned Python upstream methods, not a second Kotlin port. */
@RunWith(Parameterized::class)
class UpstreamCropParityTest(private val name: String, private val row: List<String>) {
    @Test fun matchesUpstreamSyntheticCropDecisionsAndCoordinates() {
        val points = MutableList(478) { GazeGeometry.Landmark(row[3].toDouble(),row[4].toDouble()) }
        row[5].split(';').forEach {
            val p = it.split(':'); points[p[0].toInt()] = GazeGeometry.Landmark(p[1].toDouble(),p[2].toDouble())
        }
        val actual = GazeGeometry.crops(points,row[1].toInt(),row[2].toInt())
        if (row[6]=="false") assertNull(name,actual)
        else {
            assertNotNull(name,actual)
            val boxes = listOf(actual!!.face,actual.left,actual.right).flatMap { listOf(it.x,it.y,it.width,it.height) }
            assertEquals(name,row[7].split(',').map(String::toInt),boxes)
        }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}")
        fun cases(): Collection<Array<Any>> = requireNotNull(UpstreamCropParityTest::class.java.getResourceAsStream("/upstream-crops.tsv"))
            .bufferedReader().useLines { lines -> lines.filter { !it.startsWith('#') && it.isNotBlank() }
                .map { val row=it.split('\t'); arrayOf<Any>(row[0],row) }.toList() }
    }
}
