package com.scenicroute.data.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val authority = "${context.packageName}.fileprovider"
    val photosDir: File get() = File(context.filesDir, "waypoint_photos").apply { mkdirs() }

    fun newPhotoFile(): File {
        val name = "${UUID.randomUUID()}.jpg"
        return File(photosDir, name)
    }

    fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, authority, file)

    fun delete(path: String) {
        runCatching {
            // Defense-in-depth: only delete files inside our photos dir.
            val f = File(path).canonicalFile
            if (f.parentFile?.canonicalPath == photosDir.canonicalPath && f.exists()) f.delete()
        }
    }

    fun importFromUri(uri: Uri): String? = runCatching {
        val dst = newPhotoFile()
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { src -> dst.outputStream().use { out -> src.copyTo(out) } }

        // Reject files larger than the upload cap (Storage rule blocks >10 MB
        // server-side; we mirror the limit client-side to fail fast).
        if (dst.length() > MAX_PHOTO_BYTES) {
            dst.delete()
            return null
        }

        // Strip device-identifying EXIF tags before the file ever leaves the user's device.
        ExifScrubber.scrub(dst)

        dst.absolutePath
    }.getOrNull()

    /**
     * @return a content:// URI safe to share via Intent — null if the path escapes
     * the app's photo directory (defense-in-depth against arbitrary-path injection
     * via deep links or buggy callers).
     */
    fun shareUriFor(path: String): Uri? = runCatching {
        val f = File(path).canonicalFile
        if (f.parentFile?.canonicalPath != photosDir.canonicalPath) return null
        if (!f.exists()) return null
        uriFor(f)
    }.getOrNull()

    /** True iff `path` is a real, regular file inside [photosDir]. */
    fun isOwnedPhotoPath(path: String): Boolean = runCatching {
        val f = File(path).canonicalFile
        f.parentFile?.canonicalPath == photosDir.canonicalPath && f.exists() && f.isFile
    }.getOrDefault(false)

    companion object {
        const val MAX_PHOTO_BYTES = 10L * 1024 * 1024
    }
}
