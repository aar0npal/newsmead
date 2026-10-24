package com.newsmead.mgazenetbenchmark

import android.view.WindowManager
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RadioButton
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Opens setup or an artificial target view only. Never clicks Start or requests a camera. */
class AccuracyHarnessTest {
    private fun descendants(view: View): List<View> = listOf(view) +
        (if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
         else emptyList())

    @Test fun openingInputCheckSetupKeepsCameraOffAndCreatesNoRecord() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.filesDir,"input-check")
        val before = directory.list()?.toSet() ?: emptySet()
        ActivityScenario.launch(InputCheckActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(0,activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        assertEquals(before,directory.list()?.toSet() ?: emptySet<String>())
    }

    @Test fun openingSetupDoesNotCreateACalibrationOrRequestActiveSession() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.filesDir,"accuracy")
        val before = directory.list()?.toSet() ?: emptySet()
        ActivityScenario.launch(AccuracyActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(0,activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val orderChoices = descendants(activity.window.decorView).filterIsInstance<RadioButton>()
                assertEquals(2,orderChoices.size)
                assertTrue(orderChoices.none { it.isChecked })
            }
        }
        assertEquals(before,directory.list()?.toSet() ?: emptySet<String>())
    }

    @Test fun openingCalibrationAuditSetupKeepsCameraOffAndCreatesNoRecord() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.filesDir,"calibration-audit")
        val before = directory.list()?.toSet() ?: emptySet()
        ActivityScenario.launch(CalibrationAuditActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(0,activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                assertEquals(0,descendants(activity.window.decorView).filterIsInstance<RadioButton>().size)
            }
        }
        assertEquals(before,directory.list()?.toSet() ?: emptySet<String>())
    }

    @Test fun openingConfirmationSetupKeepsCameraOffHasNoDefaultOrderAndCreatesNoRecord() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.filesDir,"confirmation")
        val before = directory.list()?.toSet() ?: emptySet()
        ActivityScenario.launch(ConfirmationActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(0,activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val orderChoices = descendants(activity.window.decorView).filterIsInstance<RadioButton>()
                assertEquals(2,orderChoices.size)
                assertTrue(orderChoices.none { it.isChecked })
            }
        }
        assertEquals(before,directory.list()?.toSet() ?: emptySet<String>())
    }

    @Test fun confirmationMeasurementLayoutSupportsTheFrozenTargetPlanWithoutCamera() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.filesDir,"confirmation")
        val before = directory.list()?.toSet() ?: emptySet()
        var targetView: AccuracyTargetView? = null
        var acknowledged = -1
        ActivityScenario.launch(ConfirmationActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val measurement = ConfirmationMeasurementLayout.create(activity) {
                    fail("Camera-free layout check must not invoke Stop")
                }
                targetView = measurement.target.apply {
                    onPresented = { token, _ -> acknowledged = token }
                }
                activity.setContentView(measurement.root)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(0,activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val view = targetView!!
                val layout = view.snapshot()
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                assertTrue("measurement target surface has no width",view.width > 0)
                assertTrue("measurement target surface has no height",view.height > 0)
                assertEquals(location[0] + 12.0*view.resources.displayMetrics.density,layout.viewport.left,.001)
                assertEquals(location[1] + 12.0*view.resources.displayMetrics.density,layout.viewport.top,.001)

                val confirmation = ConfirmationSession(layout,"camera_free_layout_check",
                    AccuracySession.ValidationOrder.FORWARD_THEN_REVERSE)
                assertEquals(16,confirmation.fitPoints.count { !it.practice })
                assertEquals(5,confirmation.screenBlocks.size)
                assertEquals(20,confirmation.confirmationBlocks.size)
                assertEquals(10,confirmation.confirmationBlocks.map { it.locationId }.distinct().size)
                assertTrue(confirmation.confirmationBlocks.groupBy { it.locationId }.values.all { repeated ->
                    repeated.size == 2 && repeated[0].point == repeated[1].point
                })
                val allPoints = confirmation.fitPoints.map { it.point } +
                    confirmation.screenBlocks.map { it.point } +
                    confirmation.confirmationBlocks.map { it.point }
                assertTrue(allPoints.all { point ->
                    point.x >= layout.viewport.left && point.x <= layout.viewport.right &&
                        point.y >= layout.viewport.top && point.y <= layout.viewport.bottom
                })
                view.target = confirmation.target
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { targetView!!.invalidate() }
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertEquals(0,acknowledged) }
        }
        assertEquals(before,directory.list()?.toSet() ?: emptySet<String>())
    }

    @Test fun insetViewUsesPhysicalScreenCoordinatesAndAcknowledgesDrawnTarget() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var targetView: AccuracyTargetView? = null
        var acknowledged = -1
        ActivityScenario.launch(AccuracyActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = FrameLayout(activity).apply { setPadding(17,29,19,31) }
                targetView = AccuracyTargetView(activity).also { view ->
                    view.onPresented = { token, _ -> acknowledged = token }
                    root.addView(view,FrameLayout.LayoutParams(-1,-1))
                }
                activity.setContentView(root)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                val view = targetView!!; val layout = view.snapshot()
                val location = IntArray(2); view.getLocationOnScreen(location)
                assertEquals(location[0] + 12.0*view.resources.displayMetrics.density,layout.viewport.left,.001)
                assertEquals(location[1] + 12.0*view.resources.displayMetrics.density,layout.viewport.top,.001)
                val session = AccuracySession(layout,"synthetic_view_only",
                    AccuracySession.ValidationOrder.FORWARD_THEN_REVERSE)
                assertEquals(session.fitPoints.first().point.y/layout.screenHeight,
                    layout.label(session.fitPoints.first().point)[1].toDouble(),1e-7)
                val locations = session.blocks.distinctBy { it.locationId }
                assertEquals(10,locations.size)
                assertEquals(10,locations.map { it.point }.distinct().size)
                assertTrue(locations.none { block ->
                    session.fitPoints.filterNot { it.practice }.any { it.point == block.point }
                })
                assertTrue(session.blocks.groupBy { it.locationId }.values.all { repeated ->
                    repeated.size == 2 && repeated[0].point == repeated[1].point
                })
                view.target = session.target

                val audit = CalibrationAuditSession(layout,"synthetic_view_only")
                assertEquals(16,audit.fitPoints.count { !it.practice })
                assertEquals("fit_6",audit.driftFitId)
                assertEquals(6,audit.verificationBlocks.size)
                assertEquals(audit.fitPoints.first { it.id == audit.driftFitId }.point,
                    audit.verificationBlocks.first().point)
                assertTrue(audit.verificationBlocks.drop(1).none { verification ->
                    audit.fitPoints.filterNot { it.practice }.any { it.point == verification.point }
                })

                val confirmation = ConfirmationSession(layout,"synthetic_view_only",
                    AccuracySession.ValidationOrder.FORWARD_THEN_REVERSE)
                assertEquals(16,confirmation.fitPoints.count { !it.practice })
                assertEquals(5,confirmation.screenBlocks.size)
                assertEquals(20,confirmation.confirmationBlocks.size)
                assertTrue(confirmation.screenBlocks.none { screen ->
                    confirmation.fitPoints.filterNot { it.practice }.any { it.point == screen.point }
                })
                assertTrue(confirmation.confirmationBlocks.none { block ->
                    confirmation.fitPoints.filterNot { it.practice }.any { it.point == block.point } ||
                        confirmation.screenBlocks.any { it.point == block.point }
                })
            }
            instrumentation.waitForIdleSync()
            // Force a UI draw traversal before checking the posted acknowledgement.
            scenario.onActivity { targetView!!.invalidate() }
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertEquals(0,acknowledged) }
        }
    }
}
