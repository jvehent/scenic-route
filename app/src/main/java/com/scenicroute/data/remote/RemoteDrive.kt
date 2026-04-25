package com.scenicroute.data.remote

data class RemoteDrive(
    val id: String,
    val ownerUid: String?,
    val ownerAnonHandle: String?,
    val title: String,
    val description: String,
    val visibility: String,
    val distanceM: Int,
    val durationS: Int,
    val startedAt: Long,
    val endedAt: Long?,
    val tags: List<String>,
    val coverPhotoUrl: String?,
    val centroidLat: Double?,
    val centroidLng: Double?,
    val trackUrl: String?,
)

data class RemoteWaypoint(
    val id: String,
    val lat: Double,
    val lng: Double,
    val recordedAt: Long,
    val note: String?,
    val photoUrls: List<String>,
    val vehicleReqsJson: String?,
)

data class RemoteTrackPoint(
    val seq: Int,
    val lat: Double,
    val lng: Double,
    val alt: Double?,
    val recordedAt: Long?,
)
