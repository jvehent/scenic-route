package com.senikroute.data.storage

import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Strips device-identifying EXIF tags from a JPEG in place.
 *
 * Per DESIGN.md §13:
 *  - GPS tags are RETAINED (the user wants location on photos).
 *  - Device-identifying tags (camera serial, make/model, software, owner name,
 *    user comment, etc.) are CLEARED.
 *
 * Idempotent and safe to call on photos imported from gallery as well as freshly
 * captured ones. If the file isn't a JPEG / TIFF EXIF can't parse, this is a no-op.
 */
object ExifScrubber {
    private const val TAG = "ExifScrubber"

    private val IDENTIFYING_TAGS = arrayOf(
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_BODY_SERIAL_NUMBER,
        ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_LENS_SERIAL_NUMBER,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_USER_COMMENT,
        ExifInterface.TAG_IMAGE_UNIQUE_ID,
        ExifInterface.TAG_CAMERA_OWNER_NAME,
        ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION,
        ExifInterface.TAG_GPS_PROCESSING_METHOD, // contains the device GPS source name
    )

    fun scrub(file: File) {
        if (!file.exists() || file.length() == 0L) return
        runCatching {
            val exif = ExifInterface(file.absolutePath)
            for (tag in IDENTIFYING_TAGS) exif.setAttribute(tag, null)
            exif.saveAttributes()
        }.onFailure { Log.w(TAG, "scrub failed (likely non-JPEG; OK)", it) }
    }
}
