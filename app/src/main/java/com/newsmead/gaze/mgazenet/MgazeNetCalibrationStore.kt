package com.newsmead.gaze.mgazenet

import android.content.Context
import android.provider.Settings
import android.util.AtomicFile
import android.view.View
import com.newsmead.gaze.gazeCoordinateFrame
import com.newsmead.gaze.physicalDisplaySize
import java.io.File
import java.util.UUID

/** App-private, excluded from Android backup, distinct from every legacy calibration file. */
class MgazeNetCalibrationStore(context: Context) {
    private val context = context.applicationContext
    private val root get() = File(context.noBackupFilesDir,"mgazenet-v2")
    private val artifact get() = AtomicFile(File(root,"calibration.bin"))
    private fun device() = CalibrationIdentity.hash((android.os.Build.FINGERPRINT + ":" +
        Settings.Secure.getString(context.contentResolver,Settings.Secure.ANDROID_ID)).toByteArray())
    fun identity(view: View): CalibrationIdentity {
        val size = view.physicalDisplaySize()
        return CalibrationIdentity(device(),size.x,size.y,view.display.rotation,view.gazeCoordinateFrame())
    }
    fun compatible(view: View, identity: CalibrationIdentity): Boolean {
        val size = view.physicalDisplaySize()
        return identity.device == device() && identity.screenWidth == size.x &&
            identity.screenHeight == size.y && identity.rotation == view.display.rotation
    }
    private fun read(view: View): CalibrationBundle.Artifact {
        // Setup is strictly read-only, including after interrupted atomic writes.
        val file = File(root,"calibration.bin")
        require(!File(root,"calibration.bin.bak").exists() && !File(root,"calibration.bin.new").exists())
        require(file.length() in 1..17L*1024*1024)
        return CalibrationBundle.decode(file.readBytes()) { compatible(view,it) }
    }
    fun compatibilityIssue(view: View): String? = try {
        val saved = read(view)
        saved.x.fill(0); saved.y.fill(0)
        null
    } catch (_: Exception) { "A fresh MGazeNet 13-point calibration is required for this device and screen." }
    fun fingerprint(): String? = runCatching {
        val file = File(root,"calibration.bin"); require(file.length() in 1..17L*1024*1024)
        CalibrationIdentity.hash(file.readBytes())
    }.getOrNull()
    /** Capture immutable artifact/geometry on main before native work; never access a View on worker. */
    fun snapshot(view: View): CalibrationBundle.Artifact = read(view)
    fun load(saved: CalibrationBundle.Artifact): SvrCalibration = withModelFiles { x,y ->
        try { x.writeBytes(saved.x); y.writeBytes(saved.y); SvrCalibration.load(x,y) }
        finally { saved.x.fill(0); saved.y.fill(0) }
    }
    fun save(identity: CalibrationIdentity, model: SvrCalibration, mayCommit: () -> Boolean = { true }) = synchronized(STORAGE_LOCK) {
        check(mayCommit()) { "Calibration save canceled" }
        val bundle = withModelFiles { x,y ->
            model.save(x,y)
            // Real native serialization parity is required for each actual saved model.
            SvrCalibration.load(x,y).use { restored ->
                syntheticQueries().forEach { q ->
                    try { check(model.predict(q).contentEquals(restored.predict(q))) { "SVR persistence changed predictions" } }
                    finally { q.fill(0f) }
                }
            }
            CalibrationBundle.encode(CalibrationBundle.Artifact(identity,x.readBytes(),y.readBytes()))
        }
        root.mkdirs()
        check(mayCommit()) { "Calibration save canceled" }
        val output = artifact.startWrite()
        try {
            output.write(bundle)
            check(mayCommit()) { "Calibration save canceled" }
            artifact.finishWrite(output)
        }
        catch (e: Throwable) { artifact.failWrite(output); throw e }
        finally { bundle.fill(0) }
    }
    /** Native save API needs paths. Temporary *models* live only in the no-backup namespace. */
    private fun <T> withModelFiles(block: (File,File) -> T): T = synchronized(STORAGE_LOCK) {
        check(root.isDirectory || root.mkdirs())
        val id = UUID.randomUUID().toString()
        val x = File(root,"pending-$id-x.xml"); val y = File(root,"pending-$id-y.xml")
        try { block(x,y) } finally { x.delete(); y.delete() }
    }
    /** Runs only after explicit Start. A native serialization failure blocks all participant fitting. */
    fun verifyNativePersistence() = synchronized(STORAGE_LOCK) {
        // Only our interrupted temporary model files; never the committed calibration or legacy data.
        root.listFiles()?.filter { it.name.matches(Regex("pending-[a-f0-9-]{36}-[xy]\\.xml")) }
            ?.forEach { check(it.delete()) { "Cannot remove interrupted temporary model" } }
        val rowCount = CalibrationIdentity.FIT_TARGET_COUNT * CalibrationIdentity.SAMPLES_PER_TARGET
        val features = Array(rowCount) { i -> FloatArray(258) { j ->
            (((i / CalibrationIdentity.SAMPLES_PER_TARGET) * (j % 7 + 1) +
                (i % CalibrationIdentity.SAMPLES_PER_TARGET) * .001) /
                CalibrationIdentity.FIT_TARGET_COUNT).toFloat()
        } }
        val labels = Array(rowCount) { i ->
            CalibrationIdentity.TARGET_FRACTIONS[i / CalibrationIdentity.SAMPLES_PER_TARGET]
                .let { (x, y) -> floatArrayOf(x, y) }
        }
        try { SvrCalibration().use { original ->
            original.fit(features,labels)
            withModelFiles { x,y ->
                original.save(x,y)
                SvrCalibration.load(x,y).use { restored -> syntheticQueries().forEach { q ->
                    try { check(original.predict(q).contentEquals(restored.predict(q))) }
                    finally { q.fill(0f) }
                } }
            }
        } } finally { features.forEach { it.fill(0f) }; labels.forEach { it.fill(0f) } }
    }
    companion object {
        private val STORAGE_LOCK = Any()
        fun syntheticQueries() = List(20) { i -> FloatArray(258) { j -> (i*.031 + j%7*.1).toFloat() } }
    }
}
