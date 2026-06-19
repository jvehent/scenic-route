package com.senikroute.data.io

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import com.senikroute.data.db.entities.DriveEntity
import com.senikroute.data.db.entities.TrackPointEntity
import com.senikroute.data.db.entities.WaypointEntity
import com.senikroute.data.db.entities.WaypointPhotoEntity
import com.senikroute.data.repo.DriveRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.DateFormat
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
        val xml = renderToString(driveId, format) ?: return@withContext null
        val drive = driveRepo.observeDrive(driveId).first() ?: return@withContext null
        val safe = drive.title.takeIf { it.isNotBlank() }
            ?.replace(Regex("[^A-Za-z0-9_-]+"), "_")
            ?: driveId.take(8)
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "$safe.${format.ext}")
        file.writeText(xml, Charsets.UTF_8)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Renders the drive at [driveId] in [format] as an in-memory string. Used by the
     * Google Drive takeout flow which uploads bytes directly to the Drive REST API
     * without going through the on-disk cache + FileProvider that share-intents need.
     * Returns null if the drive doesn't exist locally.
     */
    suspend fun renderToString(driveId: String, format: GeoFormat): String? = withContext(Dispatchers.IO) {
        val drive = driveRepo.observeDrive(driveId).first() ?: return@withContext null
        val track = driveRepo.observeTrack(driveId).first()
        val waypoints = driveRepo.observeWaypoints(driveId).first()
        val photos = driveRepo.observePhotos(driveId).first()
        when (format) {
            GeoFormat.GPX -> renderGpx(drive.title, drive.description, drive.startedAt, track, waypoints)
            GeoFormat.KML -> renderKml(drive.title, drive.description, track, waypoints)
            GeoFormat.HTML -> renderHtml(drive, track, waypoints, photos)
        }
    }


    /** Suggested filename for a drive's exported file, sanitized for filesystems. */
    suspend fun fileNameFor(driveId: String, format: GeoFormat): String {
        val drive = driveRepo.observeDrive(driveId).first()
        val safe = drive?.title?.takeIf { it.isNotBlank() }
            ?.replace(Regex("[^A-Za-z0-9_-]+"), "_")
            ?: driveId.take(8)
        return "$safe.${format.ext}"
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

    private fun renderHtml(
        drive: DriveEntity,
        track: List<TrackPointEntity>,
        waypoints: List<WaypointEntity>,
        photos: List<WaypointPhotoEntity>,
    ): String {
        val trackJson = buildJsonArray {
            for (p in track) {
                add(buildJsonObject {
                    put("lat", p.lat)
                    put("lng", p.lng)
                    p.alt?.let { put("alt", it) }
                    put("time", p.recordedAt)
                })
            }
        }.toString()

        val waypointsJson = buildJsonArray {
            for (wp in waypoints) {
                add(buildJsonObject {
                    put("id", wp.id)
                    put("lat", wp.lat)
                    put("lng", wp.lng)
                    put("time", wp.recordedAt)
                    wp.note?.let { put("note", it) }

                    wp.vehicleReqs?.let { reqs ->
                        put("vehicleReqs", buildJsonObject {
                            reqs.requires4wd?.let { put("requires4wd", it) }
                            reqs.rvFriendly?.let { put("rvFriendly", it) }
                            reqs.maxWidthM?.let { put("maxWidthM", it) }
                            reqs.maxHeightM?.let { put("maxHeightM", it) }
                            put("tags", buildJsonArray {
                                reqs.tags.forEach { add(it) }
                            })
                            reqs.notes?.let { put("notes", it) }
                        })
                    }

                    val wpPhotos = photos.filter { it.waypointId == wp.id }
                    put("photos", buildJsonArray {
                        for (p in wpPhotos) {
                            add(buildJsonObject {
                                put("id", p.id)
                                p.remoteUrl?.let { put("remoteUrl", it) }
                                getBase64Image(p.localPath)?.let { put("base64", it) }
                            })
                        }
                    })
                })
            }
        }.toString()

        val formattedDate = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(drive.startedAt))
        val formattedStats = String.format(Locale.US, "%.1f km · %d min", (drive.distanceM ?: 0) / 1000.0, (drive.durationS ?: 0) / 60)

        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${esc(drive.title.ifBlank { "Scenic Drive" })} - Senik</title>
    <!-- Google Fonts -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <!-- Leaflet Map library -->
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=" crossorigin=""/>
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js" integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=" crossorigin=""></script>
    <style>
        :root {
            --bg-color: #0f172a;
            --panel-bg: #1e293b;
            --card-bg: #334155;
            --accent-color: #6366f1;
            --accent-hover: #4f46e5;
            --text-main: #f8fafc;
            --text-muted: #94a3b8;
            --border-color: #475569;
        }
        
        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }
        
        body {
            font-family: 'Outfit', sans-serif;
            background-color: var(--bg-color);
            color: var(--text-main);
            overflow-y: auto;
            height: auto;
            display: flex;
            flex-direction: column;
        }

        header {
            background-color: var(--panel-bg);
            padding: 1rem 1.5rem;
            border-bottom: 1px solid var(--border-color);
            display: flex;
            justify-content: space-between;
            align-items: center;
            z-index: 10;
            flex-shrink: 0;
        }

        .header-title h1 {
            font-size: 1.5rem;
            font-weight: 700;
            color: var(--text-main);
        }

        .header-title p {
            font-size: 0.875rem;
            color: var(--text-muted);
            margin-top: 0.25rem;
        }

        .stats-badge {
            background: linear-gradient(135deg, var(--accent-color), #818cf8);
            padding: 0.5rem 1rem;
            border-radius: 9999px;
            font-weight: 600;
            font-size: 0.875rem;
            box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
        }

        .main-container {
            display: flex;
            flex-direction: column;
            position: relative;
        }

        .sidebar {
            width: 100%;
            background-color: var(--panel-bg);
            border-bottom: 1px solid var(--border-color);
            display: flex;
            flex-direction: column;
            overflow-y: visible;
            height: auto;
            z-index: 5;
        }

        .sidebar-section {
            padding: 1.5rem;
            border-bottom: 1px solid var(--border-color);
        }

        .sidebar-section h2 {
            font-size: 1.125rem;
            font-weight: 600;
            margin-bottom: 1rem;
            color: var(--text-main);
        }

        .drive-desc {
            font-size: 0.95rem;
            line-height: 1.5;
            color: var(--text-muted);
            white-space: pre-wrap;
        }

        .tags-container {
            display: flex;
            flex-wrap: wrap;
            gap: 0.5rem;
            margin-top: 1rem;
        }

        .tag {
            background-color: var(--card-bg);
            color: var(--text-main);
            padding: 0.25rem 0.75rem;
            border-radius: 9999px;
            font-size: 0.75rem;
            font-weight: 500;
        }

        .waypoint-list {
            display: flex;
            flex-direction: column;
            gap: 1rem;
        }

        .waypoint-card {
            background-color: var(--card-bg);
            border-radius: 12px;
            padding: 1rem;
            cursor: pointer;
            transition: transform 0.2s, box-shadow 0.2s, border-color 0.2s;
            border: 2px solid transparent;
        }

        .waypoint-card:hover {
            transform: translateY(-2px);
            box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.3);
            border-color: var(--accent-color);
        }

        .waypoint-card.active {
            border-color: var(--accent-color);
            background-color: #3b4252;
        }

        .waypoint-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 0.5rem;
        }

        .waypoint-time {
            font-size: 0.75rem;
            color: var(--text-muted);
        }

        .waypoint-note {
            font-size: 0.9rem;
            line-height: 1.4;
            color: var(--text-main);
            margin-bottom: 0.75rem;
            white-space: pre-wrap;
        }

        .waypoint-reqs {
            display: flex;
            flex-wrap: wrap;
            gap: 0.35rem;
            margin-bottom: 0.75rem;
        }

        .req-badge {
            background-color: rgba(239, 68, 68, 0.2);
            color: #ef4444;
            font-size: 0.7rem;
            font-weight: 600;
            padding: 0.15rem 0.5rem;
            border-radius: 4px;
            border: 1px solid rgba(239, 68, 68, 0.3);
        }
        
        .req-badge.rv {
            background-color: rgba(245, 158, 11, 0.2);
            color: #f59e0b;
            border-color: rgba(245, 158, 11, 0.3);
        }

        .waypoint-photos {
            display: flex;
            gap: 0.5rem;
            overflow-x: auto;
            padding-bottom: 0.25rem;
        }

        .waypoint-photo {
            width: 80px;
            height: 80px;
            border-radius: 8px;
            object-fit: cover;
            flex-shrink: 0;
            cursor: zoom-in;
            transition: opacity 0.2s;
        }

        .waypoint-photo:hover {
            opacity: 0.8;
        }

        #map-container {
            order: -1;
            height: 400px;
            flex-shrink: 0;
            position: relative;
        }

        #map {
            width: 100%;
            height: 100%;
            background-color: var(--bg-color);
        }

        /* Invert tiles for custom dark mode map */
        .leaflet-tile-container {
            filter: invert(100%) hue-rotate(180deg) brightness(95%) contrast(90%);
        }

        /* Leaflet popups customization */
        .leaflet-popup-content-wrapper {
            background-color: var(--panel-bg);
            color: var(--text-main);
            border-radius: 12px;
            border: 1px solid var(--border-color);
            padding: 0.5rem;
            box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.5);
        }

        .leaflet-popup-tip {
            background-color: var(--panel-bg);
            border: 1px solid var(--border-color);
        }

        .wp-popup {
            max-width: 250px;
            font-family: 'Outfit', sans-serif;
        }

        .wp-popup-note {
            font-size: 0.875rem;
            line-height: 1.4;
            margin-bottom: 0.5rem;
        }

        .wp-popup-img {
            width: 100%;
            max-height: 150px;
            object-fit: cover;
            border-radius: 6px;
            cursor: zoom-in;
        }

        /* Lightbox modal styling */
        #lightbox {
            display: none;
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background-color: rgba(15, 23, 42, 0.95);
            z-index: 1000;
            justify-content: center;
            align-items: center;
            cursor: zoom-out;
        }

        #lightbox-img {
            max-width: 90%;
            max-height: 90%;
            object-fit: contain;
            border-radius: 8px;
            box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
            animation: zoomIn 0.25s ease-out;
        }

        @keyframes zoomIn {
            from { transform: scale(0.9); opacity: 0; }
            to { transform: scale(1); opacity: 1; }
        }

        #lightbox-close {
            position: absolute;
            top: 20px;
            right: 20px;
            background: none;
            border: none;
            color: var(--text-main);
            font-size: 2rem;
            cursor: pointer;
        }

        /* Responsive overrides for desktop / landscape screens */
        @media (min-width: 769px) and (orientation: landscape) {
            body {
                height: 100vh;
                overflow: hidden;
            }

            .main-container {
                flex-direction: row;
                flex: 1;
                overflow: hidden;
            }

            .sidebar {
                width: 400px;
                height: 100vh;
                border-right: 1px solid var(--border-color);
                border-bottom: none;
                overflow-y: auto;
            }

            #map-container {
                order: 0;
                flex: 1;
                height: 100%;
            }
        }
    </style>
</head>
<body>
    <header>
        <div class="header-title">
            <h1>${esc(drive.title.ifBlank { "Untitled drive" })}</h1>
            <p>$formattedDate</p>
        </div>
        <div class="stats-badge">
            $formattedStats
        </div>
    </header>

    <div class="main-container">
        <div class="sidebar">
            <div class="sidebar-section">
                <h2>About this journey</h2>
                <p class="drive-desc">${esc(drive.description.ifBlank { "No description provided." })}</p>
                <div class="tags-container">
                    ${drive.tags.joinToString("\n") { "<span class=\"tag\">#${esc(it)}</span>" }}
                </div>
            </div>
            
            <div class="sidebar-section">
                <h2>Waypoints</h2>
                <div class="waypoint-list">
                    <!-- Dynamic rendering in JS -->
                </div>
            </div>
        </div>

        <div id="map-container">
            <div id="map"></div>
        </div>
    </div>

    <div id="lightbox" onclick="closeLightbox()">
        <button id="lightbox-close">&times;</button>
        <img id="lightbox-img" src="" alt="Enlarged photo">
    </div>

    <script>
        const driveData = {
            track: $trackJson,
            waypoints: $waypointsJson
        };

        const map = L.map('map');
        
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19,
            attribution: '&copy; <a href="https://openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        }).addTo(map);

        // Custom Marker Icons
        const startIcon = L.divIcon({
            html: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><circle cx="12" cy="12" r="8" fill="#2ed573" stroke="#ffffff" stroke-width="2"/></svg>',
            className: 'custom-div-icon',
            iconSize: [20, 20],
            iconAnchor: [10, 10]
        });

        const endIcon = L.divIcon({
            html: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><circle cx="12" cy="12" r="8" fill="#ff4757" stroke="#ffffff" stroke-width="2"/></svg>',
            className: 'custom-div-icon',
            iconSize: [20, 20],
            iconAnchor: [10, 10]
        });

        const waypointIcon = L.divIcon({
            html: '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M12 2C8.13 2 5 5.13 5 9C5 14.25 12 22 12 22C12 22 19 14.25 19 9C19 5.13 15.87 2 12 2ZM12 11.5C10.62 11.5 9.5 10.38 9.5 9C9.5 7.62 10.62 6.5 12 6.5C13.38 6.5 14.5 7.62 14.5 9C14.5 10.38 13.38 11.5 12 11.5Z" fill="#6366f1" stroke="#ffffff" stroke-width="1.5"/></svg>',
            className: 'custom-div-icon',
            iconSize: [24, 24],
            iconAnchor: [12, 24],
            popupAnchor: [0, -24]
        });

        const markers = {};

        // Draw track
        const latlngs = driveData.track.map(p => [p.lat, p.lng]);
        if (latlngs.length > 0) {
            const polyline = L.polyline(latlngs, {color: '#818cf8', weight: 5, opacity: 0.85}).addTo(map);
            
            // Start & End markers
            L.marker(latlngs[0], {icon: startIcon}).addTo(map).bindPopup('Start Point');
            if (latlngs.length > 1) {
                L.marker(latlngs[latlngs.length - 1], {icon: endIcon}).addTo(map).bindPopup('End Point');
            }
            
            map.fitBounds(polyline.getBounds(), { padding: [50, 50] });
        } else {
            map.setView([0, 0], 2);
        }

        // Draw Waypoints on Map & Render in Sidebar
        const listContainer = document.querySelector('.waypoint-list');
        
        driveData.waypoints.forEach((wp, idx) => {
            // Map Marker
            const marker = L.marker([wp.lat, wp.lng], {icon: waypointIcon}).addTo(map);
            markers[wp.id] = marker;
            
            let popupContent = '<div class="wp-popup">';
            if (wp.note) {
                popupContent += '<p class="wp-popup-note">' + escapeHtml(wp.note) + '</p>';
            }
            if (wp.photos && wp.photos.length > 0) {
                const photoSrc = wp.photos[0].base64 ? 'data:image/jpeg;base64,' + wp.photos[0].base64 : wp.photos[0].remoteUrl;
                if (photoSrc) {
                    popupContent += '<img src="' + photoSrc + '" class="wp-popup-img" onclick="openLightbox(\'' + photoSrc + '\', event)" />';
                }
            }
            popupContent += '</div>';
            
            marker.bindPopup(popupContent);
            
            // Sidebar Card
            const card = document.createElement('div');
            card.className = 'waypoint-card';
            card.setAttribute('data-id', wp.id);
            
            let vehicleBadges = '';
            if (wp.vehicleReqs) {
                const reqs = wp.vehicleReqs;
                if (reqs.requires4wd) {
                    vehicleBadges += '<span class="req-badge">4WD Required</span>';
                }
                if (reqs.rvFriendly === false) {
                    vehicleBadges += '<span class="req-badge rv">No RVs</span>';
                }
                if (reqs.maxWidthM) {
                    vehicleBadges += '<span class="req-badge rv">Max Width ' + reqs.maxWidthM + 'm</span>';
                }
                if (reqs.maxHeightM) {
                    vehicleBadges += '<span class="req-badge rv">Max Height ' + reqs.maxHeightM + 'm</span>';
                }
                if (reqs.tags) {
                    reqs.tags.forEach(t => {
                        vehicleBadges += '<span class="req-badge rv">' + escapeHtml(t.replace('_', ' ')) + '</span>';
                    });
                }
            }
            
            let photoHtml = '';
            if (wp.photos && wp.photos.length > 0) {
                photoHtml += '<div class="waypoint-photos">';
                wp.photos.forEach(p => {
                    const photoSrc = p.base64 ? 'data:image/jpeg;base64,' + p.base64 : p.remoteUrl;
                    if (photoSrc) {
                        photoHtml += '<img src="' + photoSrc + '" class="waypoint-photo" onclick="openLightbox(\'' + photoSrc + '\', event)" />';
                    }
                });
                photoHtml += '</div>';
            }

            const formattedTime = new Date(wp.time).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
            
            let innerHTML = '<div class="waypoint-header">';
            innerHTML += '<strong>Waypoint #' + (idx + 1) + '</strong>';
            innerHTML += '<span class="waypoint-time">' + formattedTime + '</span>';
            innerHTML += '</div>';
            if (wp.note) {
                innerHTML += '<p class="waypoint-note">' + escapeHtml(wp.note) + '</p>';
            }
            if (vehicleBadges) {
                innerHTML += '<div class="waypoint-reqs">' + vehicleBadges + '</div>';
            }
            innerHTML += photoHtml;
            card.innerHTML = innerHTML;
            
            card.addEventListener('click', (e) => {
                if (e.target.classList.contains('waypoint-photo')) return;
                
                document.querySelectorAll('.waypoint-card').forEach(c => c.classList.remove('active'));
                card.classList.add('active');
                
                map.setView([wp.lat, wp.lng], 16);
                marker.openPopup();
            });
            
            listContainer.appendChild(card);
        });

        // Sync map click with sidebar active styling
        map.on('popupopen', (e) => {
            const openMarker = e.popup._source;
            if (!openMarker) return;
            const entry = Object.entries(markers).find(([id, m]) => m === openMarker);
            if (entry) {
                const id = entry[0];
                document.querySelectorAll('.waypoint-card').forEach(c => {
                    if (c.getAttribute('data-id') === id) {
                        c.classList.add('active');
                        c.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                    } else {
                        c.classList.remove('active');
                    }
                });
            }
        });

        function escapeHtml(unsafe) {
            return unsafe
                 .replace(/&/g, "&amp;")
                 .replace(/</g, "&lt;")
                 .replace(/>/g, "&gt;")
                 .replace(/"/g, "&quot;")
                 .replace(/'/g, "&#039;");
        }

        // Lightbox Functions
        function openLightbox(src, event) {
            if (event) event.stopPropagation();
            const lightbox = document.getElementById('lightbox');
            const lightboxImg = document.getElementById('lightbox-img');
            lightboxImg.src = src;
            lightbox.style.display = 'flex';
        }

        function closeLightbox() {
            document.getElementById('lightbox').style.display = 'none';
        }
    </script>
</body>
</html>
""".trimIndent()
    }

    private fun getBase64Image(localPath: String?): String? {
        if (localPath.isNullOrBlank()) return null
        val file = File(localPath)
        if (!file.exists()) return null
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(localPath, options)
            val srcWidth = options.outWidth
            val srcHeight = options.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return null

            val maxDim = 1024
            var sampleSize = 1
            while (srcWidth / sampleSize > maxDim || srcHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val bitmap = BitmapFactory.decodeFile(localPath, decodeOptions) ?: return null
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
            bitmap.recycle()
            val bytes = outputStream.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

