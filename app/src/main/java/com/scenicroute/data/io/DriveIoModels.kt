package com.scenicroute.data.io

data class ImportedDrive(
    val title: String,
    val description: String,
    val trackPoints: List<ImportedTrackPoint>,
    val waypoints: List<ImportedWaypoint>,
)

data class ImportedTrackPoint(
    val lat: Double,
    val lng: Double,
    val alt: Double?,
    val timeMs: Long?,
)

data class ImportedWaypoint(
    val lat: Double,
    val lng: Double,
    val name: String?,
    val description: String?,
    val timeMs: Long?,
)

enum class GeoFormat(val ext: String, val mime: String) {
    GPX("gpx", "application/gpx+xml"),
    KML("kml", "application/vnd.google-earth.kml+xml"),
}
