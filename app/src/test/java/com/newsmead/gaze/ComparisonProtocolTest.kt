package com.newsmead.gaze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test

class ComparisonProtocolTest {
    private val mgazenet = ComparisonBuildIdentity(
        "mgazenet",
        "bea2dacbfa8d95f6519ccd4fad1cb0dfd6028b22",
        "131f8d96f9bdc3f5a5c16855aa6347e119fd07e96609cba9f6792b161c682fd9",
    )

    @Test
    fun exactExplicitLaunchIsAccepted() {
        val spec = ComparisonProtocol.validateLaunch(
            ComparisonProtocol.PROTOCOL_ID,
            "a56_mgazenet_A",
            "A",
            mgazenet,
            "SM-A566B",
        )
        assertEquals("a56_mgazenet_A", spec.slot.id)
        assertEquals("A", spec.orderVariant)
    }

    @Test
    fun missingOrMutableInputsAreRejected() {
        rejected { ComparisonProtocol.validateLaunch(null, "a56_mgazenet_A", "A", mgazenet, "SM-A566B") }
        rejected { ComparisonProtocol.validateLaunch(ComparisonProtocol.PROTOCOL_ID, null, "A", mgazenet, "SM-A566B") }
        rejected {
            ComparisonProtocol.validateLaunch(
                ComparisonProtocol.PROTOCOL_ID,
                "a56_mgazenet_A",
                null,
                mgazenet,
                "SM-A566B",
            )
        }
        rejected {
            ComparisonProtocol.validateLaunch(
                ComparisonProtocol.PROTOCOL_ID,
                "a56_mgazenet_A",
                "a",
                mgazenet,
                "SM-A566B",
            )
        }
    }

    @Test
    fun slotEstimatorOrderDeviceAndBaseMustAllMatch() {
        rejected {
            ComparisonProtocol.validateLaunch(
                ComparisonProtocol.PROTOCOL_ID,
                "a56_current_A",
                "A",
                mgazenet,
                "SM-A566B",
            )
        }
        rejected {
            ComparisonProtocol.validateLaunch(
                ComparisonProtocol.PROTOCOL_ID,
                "g991b_mgazenet_B",
                "A",
                mgazenet,
                "SM-G991B",
            )
        }
        rejected {
            ComparisonProtocol.validateLaunch(
                ComparisonProtocol.PROTOCOL_ID,
                "a56_mgazenet_A",
                "A",
                mgazenet,
                "SM-G991B",
            )
        }
        rejected {
            ComparisonProtocol.validateLaunch(
                ComparisonProtocol.PROTOCOL_ID,
                "a56_mgazenet_A",
                "A",
                mgazenet.copy(baseApkSha256 = "0".repeat(64)),
                "SM-A566B",
            )
        }
    }

    @Test
    fun readingLayoutHashBindsRenderedTargets() {
        val lines = (0 until 20).map { line ->
            ReadingValidationLine(
                line,
                listOf(
                    ReadingValidationWord("left$line", line * 30, line * 30 + 6),
                    ReadingValidationWord("middle$line", line * 30 + 8, line * 30 + 16),
                    ReadingValidationWord("right$line", line * 30 + 18, line * 30 + 25),
                ),
            )
        }
        val words = ReadingValidationProtocol.localizationCheckpoints(lines, "A")
        val guided = ReadingValidationProtocol.lineReadingCheckpoints(lines, "A")
        val first = ComparisonProtocol.readingLayoutSha256(
            "passage", lines, words, guided, 60f, 44f, 1080, 1800,
        )
        val changed = ComparisonProtocol.readingLayoutSha256(
            "passage", lines, words.drop(1) + words.first(), guided, 60f, 44f, 1080, 1800,
        )
        assertNotEquals(first, changed)
    }

    private fun rejected(block: () -> Unit) {
        try {
            block()
            fail("Expected frozen comparison contract rejection")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
