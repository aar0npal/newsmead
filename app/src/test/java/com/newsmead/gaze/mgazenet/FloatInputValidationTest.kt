package com.newsmead.gaze.mgazenet

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class FloatInputValidationTest {
    @Test fun signedZeroSubnormalsAndFiniteExtremesRemainValid() {
        assertTrue(FloatInputValidation.allFinite(floatArrayOf(0f,-0f,Float.MIN_VALUE,-Float.MIN_VALUE,Float.MAX_VALUE,-Float.MAX_VALUE)))
    }
    @Test fun infinitiesAndNanPayloadsAreRejectedWhereverTheyAppear() {
        for (bits in listOf(0x7f800000,0xff800000.toInt(),0x7f800001,0xff800001.toInt(),0x7fc00000)) {
            for (index in 0..2) {
                val values=floatArrayOf(0f,1f,-1f); values[index]=Float.fromBits(bits)
                assertFalse(FloatInputValidation.allFinite(values))
            }
        }
    }
    @Test fun agreesWithStandardPredicateAcrossFixedRandomBitPatterns() {
        val random=Random(20260907)
        repeat(20000) {
            val value=Float.fromBits(random.nextInt())
            assertEquals(value.isFinite(),FloatInputValidation.allFinite(floatArrayOf(value)))
        }
    }
}
