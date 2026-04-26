package com.senikroute.ui.recording

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.senikroute.data.storage.ExifScrubber
import com.senikroute.data.storage.PhotoStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject

@HiltViewModel
class WaypointPhotosViewModel @Inject constructor(
    private val storage: PhotoStorage,
) : ViewModel() {

    fun newPhotoTarget(): Pair<String, Uri> {
        val file = storage.newPhotoFile()
        return file.absolutePath to storage.uriFor(file)
    }

    /**
     * Finalize a photo just captured by the system camera at [path]: enforce size cap and
     * strip device-identifying EXIF. Returns true if the photo is kept; false if it was rejected
     * (e.g. oversized) and deleted.
     */
    fun finalizeCaptured(path: String): Boolean {
        val f = File(path)
        if (!f.exists() || f.length() == 0L) return false
        if (f.length() > PhotoStorage.MAX_PHOTO_BYTES) {
            storage.delete(path)
            return false
        }
        ExifScrubber.scrub(f)
        return true
    }

    fun discard(path: String) {
        storage.delete(path)
    }

    fun importFromUri(uri: Uri): String? = storage.importFromUri(uri)
}
