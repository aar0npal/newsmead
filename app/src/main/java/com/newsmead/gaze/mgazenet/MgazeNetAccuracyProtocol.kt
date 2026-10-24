package com.newsmead.gaze.mgazenet

import com.newsmead.gaze.GazeCoordinateFrame

/** Fixed validation geometry: eight held-out locations plus one centre repeat. */
internal object MgazeNetAccuracyProtocol {
    const val VERSION = "newsmead_mgazenet_accuracy_v2"
    val AXIS_FRACTIONS = listOf(.20f, .50f, .80f)
    val TARGET_FRACTIONS = AXIS_FRACTIONS.flatMap { y -> AXIS_FRACTIONS.map { x -> x to y } }
        .also { targets ->
            require(targets.size == 9 && targets.distinct().size == 9)
            require(targets.toSet().intersect(CalibrationIdentity.TARGET_FRACTIONS.toSet()) ==
                setOf(.50f to .50f))
        }

    fun target(frame: GazeCoordinateFrame, index: Int) = TARGET_FRACTIONS[index].let { (x, y) ->
        frame.toScreen(x * frame.width, y * frame.height)
    }
}
