package com.newsmead.gaze

import org.junit.Assert.*
import org.junit.Test

class GazeCoordinatesTest {
    @Test
    fun translationUsesBothMeasuredOriginsAndRoundTrips() {
        for ((x, y) in listOf(0 to 0, 0 to 101, 43 to 217, -19 to -71)) {
            val frame = GazeCoordinateFrame(x, y, 1080, 2239)
            for (local in listOf(0f to 0f, 540f to 1119.5f, -40f to 2400f)) {
                val screen = frame.toScreen(local.first, local.second)
                assertEquals(local.first + x, screen.x, 0f)
                assertEquals(local.second + y, screen.y, 0f)
                val restored = frame.toLocal(screen.x, screen.y)
                assertEquals(local.first, restored.x, 0f)
                assertEquals(local.second, restored.y, 0f)
            }
        }
        assertEquals(1220.5f, GazeCoordinateFrame(0, 101, 1080, 2239).toScreen(540f, 1119.5f).y, 0f)
    }

    @Test
    fun convertingTargetsOnlyTranslatesTheUnchangedMapperIncludingHeldOutAndExtrapolation() {
        val grid = buildList {
            for (y in listOf(.1f, .3667f, .6333f, .9f)) {
                for (x in listOf(.1f, .3667f, .6333f, .9f)) {
                    add(CalibrationSample(1080f * x, 2239f * y, x, y))
                }
            }
        }
        val frame = GazeCoordinateFrame(43, 217, 1080, 2239)
        for (heldOut in -1 until grid.size) {
            val training = grid.filterIndexed { index, _ -> index != heldOut }
            val oldMap = GazeMapper(training)
            val screenMap = GazeMapper(training.map {
                val screen = frame.toScreen(it.screenX, it.screenY)
                it.copy(screenX = screen.x, screenY = screen.y)
            })
            for (y in listOf(-2f, .05f, .5f, .95f, 3f)) {
                for (x in listOf(-1f, .1f, .5f, .9f, 2f)) {
                    val old = oldMap.map(x, y)
                    val actual = screenMap.map(x, y)
                    assertEquals(old[0] + frame.originX, actual[0], .002f)
                    assertEquals(old[1] + frame.originY, actual[1], .002f)
                }
            }
        }
    }

    @Test
    fun ninePointScreenObservationsDoNotCreateSpuriousInsetDrift() {
        val frame = GazeCoordinateFrame(57, 183, 1080, 2239)
        val observations = buildList {
            for (y in listOf(.2f, .5f, .8f)) for (x in listOf(.2f, .5f, .8f)) {
                val screen = frame.toScreen(frame.width * x, frame.height * y)
                add(DriftCorrection.Observation(screen.x, screen.y, screen.x, screen.y))
            }
        }
        val correction = requireNotNull(DriftCorrection.fit(observations))
        for (point in observations) {
            val corrected = correction.apply(point.predictedX, point.predictedY)
            assertEquals(point.targetX, corrected[0], .001f)
            assertEquals(point.targetY, corrected[1], .001f)
        }
        assertEquals(0f, requireNotNull(DriftCorrection.leaveOneOutMedianPx(observations)), .001f)
    }

    @Test
    fun coordinateMetadataMustMatchTheExactPayloadAndVersion() {
        val payload = "eye_local_width_average_v1\nscreen_x,screen_y,gaze_x,gaze_y\n540.0,1220.5,0.5,0.5\n"
        val metadata = GazeCoordinateContract.encode(payload)
        assertTrue(GazeCoordinateContract.matches(metadata, payload))
        assertFalse(GazeCoordinateContract.matches(null, payload))
        assertFalse(GazeCoordinateContract.matches("screen_px_v1\n", payload))
        assertFalse(GazeCoordinateContract.matches(metadata.replace("screen_px_v1", "screen_px_v2"), payload))
        assertFalse(GazeCoordinateContract.matches(metadata, payload.replace("1220.5", "1119.5")))
        assertFalse(GazeCoordinateContract.matches(metadata, payload.replace("eye_local_width_average_v1", "legacy")))
        assertFalse(GazeCoordinateContract.matches(metadata + "extra\n", payload))
    }
}
