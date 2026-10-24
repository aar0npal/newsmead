package com.newsmead.gaze.mgazenet

object SyntheticFixtures {
    data class Fixture(val name: String, val frame: RgbFrame, val crops: GazeGeometry.Crops)
    fun images(): List<Fixture> {
        fun make(name: String, w: Int, h: Int, b: List<GazeGeometry.Box>, bars: Boolean = false): Fixture {
            val pixels = ByteArray(w * h * 3)
            for (y in 0 until h) for (x in 0 until w) for (c in 0..2) {
                pixels[(y * w + x) * 3 + c] = (if (bars) {
                    if (x % 3 == c) 255 else 0
                } else (x * 17 + y * 29 + c * 71) % 256).toByte()
            }
            return Fixture(name, RgbFrame(w, h, pixels), GazeGeometry.Crops(b[0], b[1], b[2], 100.0, 100.0))
        }
        return listOf(
            make("ramp", 17, 13, listOf(GazeGeometry.Box(1,2,13,9), GazeGeometry.Box(2,3,5,3), GazeGeometry.Box(9,4,6,4))),
            make("clipped", 17, 13, listOf(GazeGeometry.Box(3,2,20,20), GazeGeometry.Box(1,1,8,4), GazeGeometry.Box(12,8,9,8))),
            make("channels", 17, 13, listOf(GazeGeometry.Box(0,0,17,13), GazeGeometry.Box(1,1,6,4), GazeGeometry.Box(8,2,7,5)), true),
        )
    }
    fun calibrationFeatures() = Array(13 * 45) { i ->
        FloatArray(258) { j -> ((i / 45 * 17 + i % 45 * 3 + j * 13) % 101) / 100f }
    }
    fun calibrationLabels() = Array(13 * 45) { i -> floatArrayOf((i / 45) / 12f, ((i / 45 * 5) % 13) / 12f) }
    fun queries() = Array(7) { i -> FloatArray(258) { j -> ((i * 11 + j * 7) % 101) / 100f } }
}
