package com.senikroute.data.sync

import kotlinx.serialization.Serializable

@Serializable
data class TrackGeoJson(val type: String, val features: List<TrackFeature>)

@Serializable
data class TrackFeature(
    val type: String,
    val geometry: LineStringGeometry,
    val properties: TrackProps,
)

@Serializable
data class LineStringGeometry(val type: String, val coordinates: List<List<Double>>)

@Serializable
data class TrackProps(
    val recordedAt: List<Long> = emptyList(),
    val speed: List<Double> = emptyList(),
    val accuracy: List<Double> = emptyList(),
)
