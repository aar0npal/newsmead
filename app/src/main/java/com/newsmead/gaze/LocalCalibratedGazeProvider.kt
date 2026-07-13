package com.newsmead.gaze

import androidx.lifecycle.LifecycleOwner

/**
 * Local Stage 3 provider: maps phone-side raw gaze features through the saved
 * 16-point calibration and emits calibrated full-screen phone pixels.
 */
class LocalCalibratedGazeProvider(
    private val mapper: GazeMapper,
    private val rawSource: LocalRawGazeSource,
) : GazeProvider {

    private val medianX = MedianFilter()
    private val medianY = MedianFilter()
    private val smoothX = OneEuroFilter(minCutoff = 0.7, beta = 0.005)
    private val smoothY = OneEuroFilter(minCutoff = 0.7, beta = 0.005)
    private var onGaze: GazeProvider.OnGaze? = null

    override fun setOnGaze(listener: GazeProvider.OnGaze) {
        onGaze = listener
    }

    override fun start(owner: LifecycleOwner) {
        rawSource.setOnRawGaze { gazeX, gazeY, timestampMs ->
            val fx = smoothX.filter(medianX.filter(gazeX), timestampMs)
            val fy = smoothY.filter(medianY.filter(gazeY), timestampMs)
            val screen = mapper.map(fx, fy)
            onGaze?.onGaze(screen[0], screen[1])
        }
        rawSource.start(owner)
    }

    override fun stop() {
        rawSource.stop()
    }
}
