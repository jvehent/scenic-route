package com.scenicroute.data.discovery

data class DiscoveryDrive(
    val driveId: String,
    val title: String,
    val distanceM: Int,
    val durationS: Int,
    val centroidLat: Double,
    val centroidLng: Double,
    val coverPhotoUrl: String?,
    val tags: List<String>,
    val ownerAnonHandle: String?,
    val trackUrl: String?,
    val distanceFromUserKm: Double,
)
