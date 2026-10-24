package com.newsmead.gaze.mgazenet

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.opencv.android.OpenCVLoader
import java.nio.ByteBuffer

/** Camera-free instrumentation. Compiling does not run these native checks. */
class NativePipelineTest {
    @Before fun initialize() { check(OpenCVLoader.initLocal()) }

    @Test fun rgbaStrideRotationAndNoMirror() {
        // Two rows, padding after row 0, no padding after last row.
        val raw = ByteArray(20)
        listOf(0,4,12,16).forEachIndexed { i, offset ->
            raw[offset] = ((i+1)*10).toByte(); raw[offset+1] = 5; raw[offset+2] = 7; raw[offset+3] = 255.toByte()
        }
        CameraFrames().use { f ->
            for ((rotation, red) in mapOf(0 to listOf(10,20,30,40),90 to listOf(30,10,40,20),180 to listOf(40,30,20,10),270 to listOf(20,40,10,30))) {
                val rgb = f.rgb(f.copyRgba(ByteBuffer.wrap(raw),2,2,12,rotation)).rgb
                assertEquals(red, (0..3).map { rgb[it*3].toInt() and 255 })
                assertTrue((0..3).all { rgb[it*3+1].toInt()==5 && rgb[it*3+2].toInt()==7 })
            }
            val tall = f.copyRgba(ByteBuffer.wrap(byteArrayOf(10,5,7,-1,20,5,7,-1,30,5,7,-1)),1,3,4,90)
            assertEquals(3,tall.width); assertEquals(1,tall.height)
            assertEquals(listOf(30,20,10), (0..2).map { f.rgb(tall).rgb[it*3].toInt() })
        }
    }
    @Test fun rgbPlanarPackingFlipsOnlyRightEye() {
        // At resize corners, INTER_LINEAR preserves endpoint colors.
        val frame = RgbFrame(2,1, byteArrayOf(-1,0,0,0,0,-1))
        val box = GazeGeometry.Box(0,0,2,1)
        Preprocessor().use {
            val p = it.prepare(frame, GazeGeometry.Crops(box,box,box,0.0,0.0))
            assertEquals(1f,p.face[0],0f); assertEquals(0f,p.face[223],0f)
            assertEquals(0f,p.face[224*224],0f); assertEquals(1f,p.face[2*224*224+223],0f)
            assertEquals(1f,p.left[0],0f); assertEquals(0f,p.right[0],0f)
            assertEquals(1f,p.right[2*112*112],0f)
        }
    }
    @Test fun bitmapAndPackedRgbPreprocessingAreExactlyEquivalent() {
        val pixels = intArrayOf(
            Color.rgb(255,0,0), Color.rgb(0,255,0), Color.rgb(0,0,255),
            Color.rgb(15,31,63), Color.rgb(127,191,223), Color.rgb(255,255,255),
        )
        val bitmap = Bitmap.createBitmap(3,2,Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels,0,3,0,0,3,2)
        }
        val packed = ByteArray(pixels.size * 3)
        pixels.forEachIndexed { index, color ->
            packed[index * 3] = Color.red(color).toByte()
            packed[index * 3 + 1] = Color.green(color).toByte()
            packed[index * 3 + 2] = Color.blue(color).toByte()
        }
        val box = GazeGeometry.Box(0,0,3,2)
        val crops = GazeGeometry.Crops(box,box,box,0.0,0.0)
        val packedInputs = Preprocessor().use { prep ->
            prep.prepare(RgbFrame(3,2,packed),crops).let {
                Preprocessor.Inputs(it.face.copyOf(),it.left.copyOf(),it.right.copyOf(),it.rect.copyOf())
            }
        }
        val bitmapInputs = Preprocessor().use { prep ->
            prep.prepare(bitmap,crops).let {
                Preprocessor.Inputs(it.face.copyOf(),it.left.copyOf(),it.right.copyOf(),it.rect.copyOf())
            }
        }
        assertArrayEquals(packedInputs.face,bitmapInputs.face,0f)
        assertArrayEquals(packedInputs.left,bitmapInputs.left,0f)
        assertArrayEquals(packedInputs.right,bitmapInputs.right,0f)
        assertArrayEquals(packedInputs.rect,bitmapInputs.rect,0f)
        bitmap.recycle()
    }
    @Test fun externallyRecycledUprightBitmapIsRecreatedBeforeTheNextFrame() {
        val raw = byteArrayOf(10,5,7,-1,20,5,7,-1)
        CameraFrames().use { frames ->
            val first = frames.copyRgba(ByteBuffer.wrap(raw),2,1,8,0)
            first.recycle()
            val second = frames.copyRgba(ByteBuffer.wrap(raw),2,1,8,0)
            assertNotSame(first,second)
            assertFalse(second.isRecycled)
            assertEquals(listOf(10,20),(0..1).map { frames.rgb(second).rgb[it*3].toInt() and 255 })
        }
    }
    @Test fun modelHas258StableFeaturesAndRejectsBadInputBeforeJni() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Preprocessor().use { prep -> MnnEstimator(context).use { model ->
            val f = SyntheticFixtures.images().first(); val p = prep.prepare(f.frame,f.crops)
            val a = model.infer(p); assertEquals(258,a.size); assertTrue(a.all { it.isFinite() })
            assertArrayEquals(a, model.infer(p), 1e-5f)
            try { model.infer(p.copy(face = FloatArray(3))); fail("Bad input reached JNI") }
            catch (_: IllegalArgumentException) { }
        } }
    }
    @Test fun nativeOpenCvSupportsFrameLevelSvr() {
        SvrCalibration().use { svr ->
            svr.fit(SyntheticFixtures.calibrationFeatures(),SyntheticFixtures.calibrationLabels())
            assertTrue(svr.trained)
            SyntheticFixtures.queries().forEach { assertTrue(svr.predict(it).all { v -> v.isFinite() }) }
        }
    }
}
