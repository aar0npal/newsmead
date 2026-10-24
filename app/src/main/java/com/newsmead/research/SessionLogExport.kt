package com.newsmead.research

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.newsmead.logging.AppLog
import java.io.File

/**
 * Hands the recorded session files to the person running the study.
 *
 * Export is always an explicit action — nothing here uploads on its own.
 * Research observations stay on the device until someone deliberately sends
 * them somewhere, which is the difference between this layer and the
 * Crashlytics/Analytics layer.
 *
 * [sessionFiles] only ever returns files that start with
 * [SessionLog.FILE_PREFIX]. `noBackupFilesDir` is also where the gaze
 * branches' `ReadingMeasurementLog` (com.newsmead.gaze) writes raw
 * eye-tracking recordings; without this filter, exporting ordinary app-usage
 * logs from a merged build would silently bundle a participant's
 * gaze-tracking data into the same share action. Keep the prefix check even
 * if it looks redundant with the `.jsonl` check — the extension alone is not
 * enough to tell the two systems' files apart.
 */
object SessionLogExport {

    private const val TAG = "SessionLogExport"
    private const val AUTHORITY_SUFFIX = ".research.fileprovider"

    /**
     * Finished session files this system wrote, newest first. Excludes the
     * one still open, and — by the [SessionLog.FILE_PREFIX] check — excludes
     * anything written by a different log family that happens to share this
     * directory (see class doc).
     */
    fun sessionFiles(context: Context): List<File> {
        val open = ResearchSession.sessionId
        return context.noBackupFilesDir
            .listFiles { file ->
                file.isFile &&
                    file.name.startsWith(SessionLog.FILE_PREFIX) &&
                    file.name.endsWith(".jsonl")
            }
            .orEmpty()
            .filter { it.nameWithoutExtension != open }
            .sortedByDescending { it.lastModified() }
    }

    /**
     * Builds a share chooser for every finished session file.
     *
     * The files are copied into `cacheDir` first: `FileProvider` has no path
     * type covering `noBackupFilesDir`, and moving the originals would risk
     * losing data that has not been collected yet.
     *
     * @return An intent ready for `startActivity`, or null if there is
     *         nothing to export.
     */
    fun buildShareIntent(context: Context): Intent? {
        val files = sessionFiles(context)
        if (files.isEmpty()) {
            AppLog.i(TAG, "no session files to export")
            return null
        }

        val staging = File(context.cacheDir, "research-export").apply { mkdirs() }
        val authority = context.packageName + AUTHORITY_SUFFIX
        val uris = ArrayList<Uri>(files.size)

        for (file in files) {
            try {
                val copy = File(staging, file.name)
                file.copyTo(copy, overwrite = true)
                uris.add(FileProvider.getUriForFile(context, authority, copy))
            } catch (exception: Exception) {
                AppLog.e(TAG, "could not stage ${file.name} for export", exception)
            }
        }

        if (uris.isEmpty()) return null

        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/x-ndjson"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.let { Intent.createChooser(it, "Export session logs") }
    }

    /**
     * Removes staged copies once the study runner has collected them. The
     * originals in `noBackupFilesDir` are left alone.
     */
    fun clearStaging(context: Context) {
        File(context.cacheDir, "research-export").deleteRecursively()
    }
}
