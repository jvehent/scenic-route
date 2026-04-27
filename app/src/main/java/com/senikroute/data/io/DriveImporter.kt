package com.senikroute.data.io

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import com.senikroute.auth.AuthRepository
import com.senikroute.auth.AuthState
import com.senikroute.data.repo.DriveRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driveRepo: DriveRepository,
    private val authRepo: AuthRepository,
) {
    suspend fun importFromUri(uri: Uri): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val signedIn = authRepo.authState.first() as? AuthState.SignedIn
                ?: error("not signed in")
            require(signedIn.isEmailVerified) { "email not verified" }

            val name = displayNameFor(uri)
            val format = formatFor(name) ?: error("Pick a .gpx or .kml file")
            // Buffer the whole file into memory with a hard cap before parsing — defends
            // against XML DoS (billion-laughs / quadratic blowup) and runaway files. 10 MB
            // is well above any realistic GPX/KML the app would legitimately import.
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                readAllCapped(input, MAX_IMPORT_BYTES)
            } ?: error("Could not open the file")
            val parsedDrives: List<ImportedDrive> = bytes.inputStream().use { input ->
                when (format) {
                    GeoFormat.GPX -> listOf(parseGpx(input))
                    GeoFormat.KML -> parseKml(input)
                }
            }

            require(parsedDrives.isNotEmpty()) { "Nothing to import" }

            val createdIds = mutableListOf<String>()
            val fallbackTitle = name.removeSuffix(".${format.ext}")
            for (parsed in parsedDrives) {
                if (parsed.trackPoints.isEmpty()) continue
                val driveId = createDrive(parsed, signedIn.uid, fallbackTitle)
                createdIds += driveId
            }
            require(createdIds.isNotEmpty()) { "No valid drives in file" }
            createdIds
        }
    }

    private suspend fun createDrive(parsed: ImportedDrive, uid: String, fallbackTitle: String): String {
        val first = parsed.trackPoints.first()
        val drive = driveRepo.startDrive(
            ownerUid = uid,
            startLat = first.lat,
            startLng = first.lng,
            startedAt = first.timeMs ?: System.currentTimeMillis(),
        )
        driveRepo.appendTrackPoints(
            drive.id,
            parsed.trackPoints.map {
                DriveRepository.PendingTrackPoint(
                    lat = it.lat, lng = it.lng, alt = it.alt,
                    speed = null, accuracy = null,
                    recordedAt = it.timeMs ?: System.currentTimeMillis(),
                )
            },
        )
        for (wp in parsed.waypoints) {
            driveRepo.addWaypoint(
                driveId = drive.id,
                lat = wp.lat,
                lng = wp.lng,
                recordedAt = wp.timeMs ?: System.currentTimeMillis(),
                note = listOfNotNull(wp.name, wp.description).joinToString("\n").takeIf { it.isNotBlank() },
            )
        }
        val endedAt = parsed.trackPoints.last().timeMs ?: System.currentTimeMillis()
        driveRepo.stopDrive(drive.id, endedAt)
        driveRepo.updateDriveMeta(
            driveId = drive.id,
            title = parsed.title.ifBlank { fallbackTitle },
            description = parsed.description,
            visibility = com.senikroute.data.model.Visibility.PRIVATE,
            tags = emptyList(),
            coverWaypointId = null,
            commentsEnabled = false,
        )
        return drive.id
    }

    private fun displayNameFor(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return c.getString(idx) ?: uri.lastPathSegment.orEmpty()
            }
        }
        return uri.lastPathSegment.orEmpty()
    }

    private fun formatFor(filename: String): GeoFormat? = when {
        filename.endsWith(".gpx", ignoreCase = true) -> GeoFormat.GPX
        filename.endsWith(".kml", ignoreCase = true) -> GeoFormat.KML
        else -> null
    }
}

private val ISO_PARSER = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    .apply { timeZone = TimeZone.getTimeZone("UTC") }

private fun parseTimeOrNull(s: String?): Long? {
    if (s.isNullOrBlank()) return null
    val trimmed = s.substringBefore('.').removeSuffix("Z").removeSuffix("+00:00")
    return runCatching { ISO_PARSER.parse(trimmed)?.time }.getOrNull()
}

private const val MAX_IMPORT_BYTES = 10L * 1024 * 1024

private fun readAllCapped(input: InputStream, cap: Long): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    var total = 0L
    while (true) {
        val n = input.read(buf)
        if (n <= 0) break
        total += n
        require(total <= cap) { "Import file too large (>${cap / (1024 * 1024)} MB)" }
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

// Android's XmlPullParser (KXmlParser) does NOT expand external entities by default,
// but explicitly disabling DOCTYPE processing is belt-and-braces against future
// runtime swaps. Wrapped in runCatching because some pullparser impls reject features
// they consider already-default and we don't want a feature mismatch to break imports.
private fun harden(parser: org.xmlpull.v1.XmlPullParser) {
    runCatching { parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-docdecl", false) }
    runCatching { parser.setFeature("http://xmlpull.org/v1/doc/features.html#validation", false) }
}

internal fun parseGpx(input: InputStream): ImportedDrive {
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    harden(parser)
    parser.setInput(input, "UTF-8")

    var driveTitle = ""
    var driveDesc = ""
    val track = mutableListOf<ImportedTrackPoint>()
    val waypoints = mutableListOf<ImportedWaypoint>()

    var inMetadata = false
    var inTrk = false
    var inWpt = false
    var pendingPointLat: Double? = null
    var pendingPointLng: Double? = null
    var pendingPointAlt: Double? = null
    var pendingPointTime: Long? = null
    var pendingWpName: String? = null
    var pendingWpDesc: String? = null
    var inTrkpt = false

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "metadata" -> inMetadata = true
                "trk" -> inTrk = true
                "trkpt" -> {
                    inTrkpt = true
                    pendingPointLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                    pendingPointLng = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                    pendingPointAlt = null
                    pendingPointTime = null
                }
                "wpt" -> {
                    inWpt = true
                    pendingPointLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                    pendingPointLng = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                    pendingPointTime = null
                    pendingWpName = null
                    pendingWpDesc = null
                }
                "name" -> {
                    val text = parser.nextText()
                    when {
                        inWpt -> pendingWpName = text
                        inMetadata -> driveTitle = text
                    }
                }
                "desc" -> {
                    val text = parser.nextText()
                    when {
                        inWpt -> pendingWpDesc = text
                        inMetadata -> driveDesc = text
                    }
                }
                "ele" -> if (inTrkpt) pendingPointAlt = parser.nextText().toDoubleOrNull()
                "time" -> if (inTrkpt || inWpt) pendingPointTime = parseTimeOrNull(parser.nextText())
            }
            XmlPullParser.END_TAG -> when (parser.name) {
                "metadata" -> inMetadata = false
                "trk" -> inTrk = false
                "trkpt" -> {
                    val lat = pendingPointLat
                    val lng = pendingPointLng
                    if (lat != null && lng != null) {
                        track += ImportedTrackPoint(lat, lng, pendingPointAlt, pendingPointTime)
                    }
                    inTrkpt = false
                }
                "wpt" -> {
                    val lat = pendingPointLat
                    val lng = pendingPointLng
                    if (lat != null && lng != null) {
                        waypoints += ImportedWaypoint(lat, lng, pendingWpName, pendingWpDesc, pendingPointTime)
                    }
                    inWpt = false
                }
            }
        }
        event = parser.next()
    }
    return ImportedDrive(driveTitle, driveDesc, track, waypoints)
}

/**
 * Each KML Placemark becomes its own [ImportedDrive]:
 *  - LineString → drive whose track is the polyline
 *  - Point → single-coordinate drive (a saved place)
 * Title/description on each drive come from that Placemark's name/description.
 * Document-level name is unused (Document just groups Placemarks).
 */
internal fun parseKml(input: InputStream): List<ImportedDrive> {
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    harden(parser)
    parser.setInput(input, "UTF-8")

    val out = mutableListOf<ImportedDrive>()

    var inPlacemark = false
    var pendingName: String? = null
    var pendingDesc: String? = null
    var pendingPoint: Triple<Double, Double, Double?>? = null
    var pendingLine: List<Triple<Double, Double, Double?>>? = null

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "Placemark" -> {
                    inPlacemark = true
                    pendingName = null
                    pendingDesc = null
                    pendingPoint = null
                    pendingLine = null
                }
                "name" -> if (inPlacemark) pendingName = parser.nextText()
                "description" -> if (inPlacemark) pendingDesc = parser.nextText()
                "coordinates" -> if (inPlacemark) {
                    val raw = parser.nextText().trim()
                    val coords = raw.split(Regex("\\s+")).mapNotNull { triple ->
                        val parts = triple.split(',')
                        val lng = parts.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
                        val lat = parts.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
                        val alt = parts.getOrNull(2)?.toDoubleOrNull()
                        Triple(lat, lng, alt)
                    }
                    when {
                        coords.size == 1 -> pendingPoint = coords[0]
                        coords.size > 1 -> pendingLine = coords
                    }
                }
            }
            XmlPullParser.END_TAG -> if (parser.name == "Placemark") {
                val title = pendingName.orEmpty()
                val desc = pendingDesc.orEmpty()
                pendingPoint?.let { (lat, lng, alt) ->
                    out += ImportedDrive(
                        title = title,
                        description = desc,
                        trackPoints = listOf(ImportedTrackPoint(lat, lng, alt, null)),
                        waypoints = emptyList(),
                    )
                }
                pendingLine?.let { line ->
                    out += ImportedDrive(
                        title = title,
                        description = desc,
                        trackPoints = line.map { (lat, lng, alt) ->
                            ImportedTrackPoint(lat, lng, alt, null)
                        },
                        waypoints = emptyList(),
                    )
                }
                inPlacemark = false
            }
        }
        event = parser.next()
    }
    return out
}
