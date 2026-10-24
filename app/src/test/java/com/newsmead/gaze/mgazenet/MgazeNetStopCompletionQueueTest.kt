package com.newsmead.gaze.mgazenet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MgazeNetStopCompletionQueueTest {
    @Test
    fun terminalCallbacksWaitForOneCloseAndRunInRequestOrder() {
        val queue = MgazeNetStopCompletionQueue()
        val completed = ArrayList<String>()

        assertEquals(
            MgazeNetStopAction.START_CLOSE,
            queue.request { completed += "seal" },
        )
        assertEquals(
            MgazeNetStopAction.WAIT_FOR_CLOSE,
            queue.request { completed += "lifecycle" },
        )
        assertTrue(completed.isEmpty())

        queue.complete().forEach { it() }
        assertEquals(listOf("seal", "lifecycle"), completed)

        assertEquals(
            MgazeNetStopAction.ALREADY_CLOSED,
            queue.request { completed += "late" },
        )
        assertEquals(listOf("seal", "lifecycle"), completed)
    }
}
