package com.senikroute.recording

data class RecordingUiState(
    val status: Status = Status.IDLE,
    val activeDriveId: String? = null,
    val startedAt: Long? = null,
    val elapsedMs: Long = 0,
    val distanceM: Int = 0,
    val waypointCount: Int = 0,
    val lastLat: Double? = null,
    val lastLng: Double? = null,
    val lastAccuracyM: Float? = null,
    val error: String? = null,
) {
    enum class Status { IDLE, STARTING, RECORDING, STOPPING }
}
