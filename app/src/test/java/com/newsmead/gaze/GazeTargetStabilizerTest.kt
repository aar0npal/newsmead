package com.newsmead.gaze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GazeTargetStabilizerTest {

    @Test
    fun confirmsLineChangesBeforeUsingThem() {
        val stabilizer = GazeTargetStabilizer(lineConfirmMs = 120, wordConfirmMs = 300)
        assertEquals(4, stabilizer.update(TextTarget(4, 20), 0).lineIndex)
        assertEquals(4, stabilizer.update(TextTarget(5, 20), 100).lineIndex)
        assertEquals(5, stabilizer.update(TextTarget(5, 20), 220).lineIndex)
    }

    @Test
    fun wordTargetUsesLongerHorizontalDebounce() {
        val stabilizer = GazeTargetStabilizer(lineConfirmMs = 100, wordConfirmMs = 300)
        val word = TextTarget(4, 20, wordStart = 10, wordEnd = 15)
        assertFalse(stabilizer.update(word, 0).hasWord)
        assertFalse(stabilizer.update(word, 299).hasWord)
        assertTrue(stabilizer.update(word, 300).hasWord)
    }

    @Test
    fun briefOffTextSampleDoesNotClearReadingTarget() {
        val stabilizer = GazeTargetStabilizer(offTextGraceMs = 200)
        stabilizer.update(TextTarget(4, 20), 0)
        assertEquals(4, stabilizer.update(TextTarget.INVALID, 100).lineIndex)
        assertFalse(stabilizer.update(TextTarget.INVALID, 300).isValid)
    }
}
