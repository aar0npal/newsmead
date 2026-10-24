package com.newsmead.mgazenetbenchmark

import org.junit.Assert.*
import org.junit.Test

class CalibrationAuditEngineTest {
    private fun training(): CalibrationAuditEngine.Training {
        var clock = 100.0
        return CalibrationAuditEngine.Training((1..16).map { group ->
            CalibrationAuditEngine.Group("fit_$group",List(45) { row ->
                clock += 20
                CalibrationAuditEngine.Row(
                    FloatArray(258) { index -> if (index == 0) group.toFloat() else row/100f },
                    floatArrayOf(group/20f,group/25f),clock,clock+7)
            })
        })
    }

    @Test fun everyFoldOmitsOneCompleteTargetGroup() {
        val fittedMarkers = mutableListOf<Set<Int>>()
        val closeCount = intArrayOf(0)
        val result = CalibrationAuditEngine {
            object : CalibrationRegressor {
                override fun fit(features: Array<FloatArray>, normalizedLabels: Array<FloatArray>) {
                    assertEquals(675,features.size)
                    assertEquals(675,normalizedLabels.size)
                    fittedMarkers.add(features.map { it[0].toInt() }.toSet())
                }
                override fun predict(features: FloatArray) = floatArrayOf(features[0]/20f,features[0]/25f)
                override fun close() { closeCount[0]++ }
            }
        }.audit(training())
        assertEquals(CalibrationAuditEngine.METHOD,result.method)
        assertEquals((1..16).map { "fit_$it" },result.folds.map { it.heldOutId })
        result.folds.forEachIndexed { index, fold ->
            assertEquals(45,fold.predictions.size)
            assertFalse(fittedMarkers[index].contains(index+1))
            assertEquals((1..16).filter { it != index+1 }.toSet(),fittedMarkers[index])
            assertTrue(fold.predictions.all { it.normalizedPoint.contentEquals(fold.normalizedTarget) })
        }
        assertEquals(16,closeCount[0])
    }

    @Test fun malformedOrLeakyTrainingContractsAreRejectedBeforeFit() {
        val mutations: List<(CalibrationAuditEngine.Training) -> CalibrationAuditEngine.Training> = listOf(
            { it.copy(groups=it.groups.dropLast(1)) },
            { it.copy(groups=it.groups.toMutableList().also { groups -> groups[15] = groups[15].copy(id="fit_15") }) },
            { it.copy(groups=it.groups.toMutableList().also { groups -> groups[0] = groups[0].copy(id="changed") }) },
            { it.copy(groups=it.groups.toMutableList().also { groups -> groups[0] = groups[0].copy(rows=groups[0].rows.dropLast(1)) }) },
            { it.also { value -> value.groups[0].rows[0].features[0] = Float.NaN } },
            { it.also { value -> value.groups[0].rows[0].normalizedLabel[0] = Float.NaN } },
            { it.copy(groups=it.groups.toMutableList().also { groups ->
                groups[0] = groups[0].copy(rows=groups[0].rows.toMutableList().also { rows ->
                    rows[1] = rows[1].copy(captureMs=rows[0].captureMs)
                })
            }) }
        )
        mutations.forEach { mutate ->
            var fits = 0
            val bad = mutate(training())
            assertThrows(IllegalArgumentException::class.java) {
                CalibrationAuditEngine { object : CalibrationRegressor {
                    override fun fit(features: Array<FloatArray>, normalizedLabels: Array<FloatArray>) { fits++ }
                    override fun predict(features: FloatArray) = floatArrayOf(0f,0f)
                    override fun close() = Unit
                } }.audit(bad)
            }
            assertEquals(0,fits)
        }
    }

    @Test fun nonFiniteOrWrongShapePredictionFailsTheAuditAndClosesTheFold() {
        listOf(floatArrayOf(Float.NaN,0f),floatArrayOf(0f)).forEach { prediction ->
            var closes = 0
            assertThrows(IllegalArgumentException::class.java) {
                CalibrationAuditEngine { object : CalibrationRegressor {
                    override fun fit(features: Array<FloatArray>, normalizedLabels: Array<FloatArray>) = Unit
                    override fun predict(features: FloatArray) = prediction
                    override fun close() { closes++ }
                } }.audit(training())
            }
            assertEquals(1,closes)
        }
    }
}
