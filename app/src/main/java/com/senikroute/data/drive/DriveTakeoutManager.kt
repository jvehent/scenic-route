package com.senikroute.data.drive

import android.util.Log
import com.senikroute.auth.AuthRepository
import com.senikroute.auth.AuthState
import com.senikroute.data.io.DriveExporter
import com.senikroute.data.io.GeoFormat
import com.senikroute.data.prefs.SettingsStore
import com.senikroute.data.repo.DriveRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates the user's "export everything to Google Drive" flow + the auto-save-on-finalize
 * hook. Both flows share the same primitives: render KML, ensure folder, upload.
 *
 * Auth — the caller supplies a Drive access token (obtained via [GoogleDriveAuth] in an
 * Activity scope so we can run the consent flow). This class doesn't touch Activities.
 */
@Singleton
class DriveTakeoutManager @Inject constructor(
    private val driveRepo: DriveRepository,
    private val driveExporter: DriveExporter,
    private val driveClient: GoogleDriveClient,
    private val settings: SettingsStore,
    private val authRepo: AuthRepository,
) {

    sealed interface Progress {
        data class Started(val total: Int) : Progress
        data class Item(val index: Int, val total: Int, val title: String) : Progress
        data class Failed(val title: String, val reason: String) : Progress
        data class Done(val uploaded: Int, val failed: Int, val folderName: String) : Progress
    }

    /**
     * Render every drive owned by the signed-in user as KML and upload each into the
     * configured Drive folder. Idempotency note: each upload creates a NEW Drive file,
     * even if a file with the same name already exists in the folder. That's by design —
     * Drive treats names as non-unique, and overwriting via search-and-update is more
     * complex than the user benefit warrants for v1.
     */
    suspend fun exportAll(
        accessToken: String,
        progress: (Progress) -> Unit = {},
    ): Result<Int> = runCatching {
        val s = settings.settings.first()
        val signedIn = (authRepo.authState.first() as? AuthState.SignedIn)
            ?: error("not signed in")
        val folderId = driveClient.findOrCreateFolder(accessToken, s.driveFolderName)
        val drives = driveRepo.observeDrives(signedIn.uid).first()
            .filter { it.deletedAt == null }
        progress(Progress.Started(drives.size))
        var uploaded = 0
        var failed = 0
        for ((idx, drive) in drives.withIndex()) {
            val title = drive.title.ifBlank { "Untitled drive" }
            progress(Progress.Item(idx + 1, drives.size, title))
            val outcome = runCatching {
                val kml = driveExporter.renderToString(drive.id, GeoFormat.KML)
                    ?: error("could not render KML")
                val name = driveExporter.fileNameFor(drive.id, GeoFormat.KML)
                driveClient.uploadFile(
                    accessToken = accessToken,
                    parentId = folderId,
                    fileName = name,
                    mimeType = MIME_KML,
                    content = kml,
                )
            }
            if (outcome.isSuccess) {
                uploaded++
            } else {
                failed++
                Log.w(TAG, "exportAll: drive ${drive.id} failed: ${outcome.exceptionOrNull()?.message}")
                progress(Progress.Failed(title, outcome.exceptionOrNull()?.message ?: "unknown"))
            }
        }
        progress(Progress.Done(uploaded = uploaded, failed = failed, folderName = s.driveFolderName))
        uploaded
    }

    /**
     * Single-drive upload — used by the auto-save hook when a drive transitions to
     * draft/published. Returns the new Drive file ID, or null on any failure (including
     * "auto-save isn't enabled" — caller doesn't have to gate the call themselves).
     */
    suspend fun autoSave(driveId: String, accessToken: String): String? = runCatching {
        val s = settings.settings.first()
        if (!s.driveAutoSave) return@runCatching null
        val kml = driveExporter.renderToString(driveId, GeoFormat.KML) ?: return@runCatching null
        val fileName = driveExporter.fileNameFor(driveId, GeoFormat.KML)
        val folderId = driveClient.findOrCreateFolder(accessToken, s.driveFolderName)
        driveClient.uploadFile(
            accessToken = accessToken,
            parentId = folderId,
            fileName = fileName,
            mimeType = MIME_KML,
            content = kml,
        )
    }.onFailure { Log.w(TAG, "autoSave: ${it.message}") }.getOrNull()

    private companion object {
        const val TAG = "DriveTakeout"
        const val MIME_KML = "application/vnd.google-earth.kml+xml"
    }
}
