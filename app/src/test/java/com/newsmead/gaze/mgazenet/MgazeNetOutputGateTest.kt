package com.newsmead.gaze.mgazenet

import org.junit.Assert.*
import org.junit.Test

class MgazeNetOutputGateTest {
    @Test fun finiteOffscreenPredictionsAreNotClampedOrSmoothed() {
        val gate = MgazeNetOutputGate(1080,2340)
        val first = gate.evaluate(1000.0,1200.0,true,floatArrayOf(-.1f,1.1f),"features")
        assertTrue(first.emitted); assertEquals(-108f,first.rawX!!,0f)
        val next = gate.evaluate(1100.0,1300.0,true,floatArrayOf(.9f,.2f),"features")
        assertTrue(next.emitted); assertEquals(468f,next.rawY!!,0f)
    }
    @Test fun stalePredictionRemainsObservableButCannotBeEmittedOrReplayed() {
        val gate = MgazeNetOutputGate(1080,2340)
        val stale = gate.evaluate(1000.0,1501.0,true,floatArrayOf(.5f,.5f),"features")
        assertFalse(stale.emitted); assertEquals("stale",stale.reason); assertEquals(540f,stale.rawX!!,0f)
        assertFalse(gate.evaluate(1000.0,1502.0,true,floatArrayOf(.5f,.5f),"features").emitted)
    }
    @Test fun readingAgeLimitCanBeConfiguredWithoutChangingTheDefault() {
        val reading = MgazeNetOutputGate(1080, 2340, 650.0)
        assertTrue(reading.evaluate(1000.0, 1600.0, true,
            floatArrayOf(.5f, .5f), "features").emitted)
        assertEquals("stale", reading.evaluate(1100.0, 1751.0, true,
            floatArrayOf(.5f, .5f), "features").reason)
        val default = MgazeNetOutputGate(1080, 2340)
        assertEquals("stale", default.evaluate(1000.0, 1600.0, true,
            floatArrayOf(.5f, .5f), "features").reason)
    }
    @Test fun noFaceBadShapeNonfiniteAndBadClocksNeverEmit() {
        val gate = MgazeNetOutputGate(1080,2340)
        assertEquals("no_face",gate.evaluate(1.0,2.0,false,null,"no_face").reason)
        assertEquals("invalid_prediction",gate.evaluate(3.0,4.0,true,FloatArray(1),"features").reason)
        assertFalse(gate.evaluate(5.0,6.0,true,floatArrayOf(Float.NaN,.5f),"features").emitted)
        assertFalse(gate.evaluate(7.0,6.0,true,floatArrayOf(.5f,.5f),"features").emitted)
        assertFalse(gate.evaluate(Double.NaN,10.0,true,floatArrayOf(.5f,.5f),"features").emitted)
        assertEquals("eye_area",gate.evaluate(11.0,12.0,false,floatArrayOf(.5f,.5f),"eye_area").reason)
    }
}
