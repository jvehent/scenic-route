package com.senikroute.data.io

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.senikroute.data.repo.DriveRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

private val ISO_UTC: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    .apply { timeZone = TimeZone.getTimeZone("UTC") }

@Singleton
class DriveExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driveRepo: DriveRepository,
) {
    suspend fun export(driveId: String, format: GeoFormat): Uri? = withContext(Dispatchers.IO) {
        val drive = driveRepo.observeDrive(driveId).first() ?: return@withContext null
        val track = driveRepo.observeTrack(driveId).first()
        val waypoints = driveRepo.observeWaypoints(driveId).first()

        val xml = when (format) {
            GeoFormat.GPX -> renderGpx(drive.title, drive.description, drive.startedAt, track, waypoints)
            GeoFormat.KML -> renderKml(drive.title, drive.description, track, waypoints)
        }

        val safe = drive.title.takeIf { it.isNotBlank() }
            ?.replace(Regex("[^A-Za-z0-9_-]+"), "_")
            ?: driveId.take(8)
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "$safe.${format.ext}")
        file.writeText(xml, Charsets.UTF_8)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun renderGpx(
        title: String,
        description: String,
        startedAt: Long,
        track: List<com.senikroute.data.db.entities.TrackPointEntity>,
        waypoints: List<com.senikroute.data.db.entities.WaypointEntity>,
    ): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append('\n')
        append("""<gpx version="1.1" creator="Senik" xmlns="http://www.topografix.com/GPX/1/1">""")
        append('\n')
        append("  <metadata>\n")
        append("    <name>").append(esc(title.ifBlank { "Untitled drive" })).append("</name>\n")
        if (description.isNotBlank()) {
            append("    <desc>").append(esc(description)).append("</desc>\n")
        }
        if (startedAt > 0) append("    <time>").append(ISO_UTC.format(Date(startedAt))).append("</time>\n")
        append("  </metadata>\n")
        for (wp in waypoints) {
            append("  <wpt lat=\"").append(wp.lat).append("\" lon=\"").append(wp.lng).append("\">\n")
            append("    <time>").append(ISO_UTC.format(Date(wp.recordedAt))).append("</time>\n")
            wp.note?.takeIf { it.isNotBlank() }?.let { append("    <name>").append(esc(it.lineSequence().first().take(60))).append("</name>\n") }
            wp.note?.takeIf { it.isNotBlank() }?.let { append("    <desc>").append(esc(it)).append("</desc>\n") }
            append("  </wpt>\n")
        }
        append("  <trk>\n")
        append("    <name>").append(esc(title.ifBlank { "Track" })).append("</name>\n")
        append("    <trkseg>\n")
        for (p in track) {
            append("      <trkpt lat=\"").append(p.lat).append("\" lon=\"").append(p.lng).append("\">\n")
            p.alt?.let { append("        <ele>").append(it).append("</ele>\n") }
            append("        <time>").append(ISO_UTC.format(Date(p.recordedAt))).append("</time>\n")
            append("      </trkpt>\n")
        }
        append("    </trkseg>\n  </trk>\n</gpx>\n")
    }

    private fun renderKml(
        title: String,
        description: String,
        track: List<com.senikroute.data.db.entities.TrackPointEntity>,
        waypoints: List<com.senikroute.data.db.entities.WaypointEntity>,
    ): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append('\n')
        append("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
        append('\n')
        append("  <Document>\n")
        append("    <name>").append(esc(title.ifBlank { "Untitled drive" })).append("</name>\n")
        if (description.isNotBlank()) {
            append("    <description>").append(esc(description)).append("</description>\n")
        }
        for (wp in waypoints) {
            append("    <Placemark>\n")
            wp.note?.takeIf { it.isNotBlank() }?.let {
                append("      <name>").append(esc(it.lineSequence().first().take(60))).append("</name>\n")
                append("      <description>").append(esc(it)).append("</description>\n")
            }
            append("      <Point><coordinates>")
                .append(wp.lng).append(',').append(wp.lat).append(",0")
                .append("</coordinates></Point>\n")
            append("    </Placemark>\n")
        }
        if (track.size >= 2) {
            append("    <Placemark>\n")
            append("      <name>Track</name>\n")
            append("      <LineString><coordinates>")
            track.forEach { p ->
                append(p.lng).append(',').append(p.lat).append(',').append(p.alt ?: 0.0).append(' ')
            }
            append("</coordinates></LineString>\n")
            append("    </Placemark>\n")
        }
        append("  </Document>\n</kml>\n")
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
