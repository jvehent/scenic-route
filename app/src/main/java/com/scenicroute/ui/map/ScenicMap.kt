package com.scenicroute.ui.map

import android.graphics.Color
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.scenicroute.data.db.entities.TrackPointEntity
import com.scenicroute.data.db.entities.WaypointEntity
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val TAG = "ScenicMap"
// Stadia Maps "Stamen Terrain" — hand-drawn terrain rendering, free for non-commercial
// up to 200k req/mo without an API key. See README/TODO if/when we exceed the free tier.
// Attribution shown automatically by the MapLibre Android SDK; do not suppress.
private const val STYLE_URL = "https://tiles.stadiamaps.com/styles/stamen_terrain.json"

private const val SRC_TRACK = "scenic-track"
private const val SRC_WAYPOINTS = "scenic-waypoints"
private const val LYR_TRACK = "scenic-track-line"
private const val LYR_WP_OUTLINE = "scenic-waypoint-outline"
private const val LYR_WP_DOT = "scenic-waypoint-dot"

enum class CameraBehavior {
    FollowLatest,
    FitBounds,
    Manual,
}

@Composable
fun ScenicMap(
    modifier: Modifier = Modifier,
    track: List<TrackPointEntity> = emptyList(),
    waypoints: List<WaypointEntity> = emptyList(),
    cameraBehavior: CameraBehavior = CameraBehavior.Manual,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    remember { MapLibre.getInstance(context, null, WellKnownTileServer.MapLibre) }

    val mapView = remember { MapView(context) }
    val holder = remember { MapHolder() }

    // Compose state — mutating either triggers a recomposition that re-runs the update lambda.
    var styleReady by remember { mutableStateOf(false) }
    var mapReady by remember { mutableStateOf<MapLibreMap?>(null) }

    val currentTrack by rememberUpdatedState(track)
    val currentWaypoints by rememberUpdatedState(waypoints)
    val currentBehavior by rememberUpdatedState(cameraBehavior)

    DisposableEffect(lifecycleOwner) {
        mapView.onCreate(null)
        mapView.getMapAsync { map ->
            if (holder.disposed) return@getMapAsync
            mapReady = map
            map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                if (holder.disposed) return@setStyle
                runCatching {
                    style.addSource(GeoJsonSource(SRC_TRACK))
                    style.addSource(GeoJsonSource(SRC_WAYPOINTS))
                    style.addLayer(
                        LineLayer(LYR_TRACK, SRC_TRACK).withProperties(
                            PropertyFactory.lineColor(Color.parseColor("#2E6E4B")),
                            PropertyFactory.lineWidth(5f),
                            PropertyFactory.lineOpacity(0.9f),
                        ),
                    )
                    style.addLayer(
                        CircleLayer(LYR_WP_OUTLINE, SRC_WAYPOINTS).withProperties(
                            PropertyFactory.circleRadius(9f),
                            PropertyFactory.circleColor(Color.WHITE),
                        ),
                    )
                    style.addLayer(
                        CircleLayer(LYR_WP_DOT, SRC_WAYPOINTS).withProperties(
                            PropertyFactory.circleRadius(6f),
                            PropertyFactory.circleColor(Color.parseColor("#B55A21")),
                        ),
                    )
                    applyData(style, currentTrack, currentWaypoints)
                    styleReady = true
                }.onFailure { Log.e(TAG, "Failed to initialize style", it) }
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder.disposed = true
            runCatching {
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            }
        }
    }

    AndroidView(modifier = modifier, factory = { mapView }, update = {
        if (holder.disposed || !styleReady) return@AndroidView
        val map = mapReady ?: return@AndroidView
        val style = map.style ?: return@AndroidView
        runCatching {
            applyData(style, currentTrack, currentWaypoints)
            applyCamera(map, holder, currentTrack, currentWaypoints, currentBehavior)
        }.onFailure { Log.e(TAG, "Update failed", it) }
    })
}

private class MapHolder {
    var disposed: Boolean = false
    var boundsFitted: Boolean = false
}

private fun applyData(style: Style, track: List<TrackPointEntity>, waypoints: List<WaypointEntity>) {
    (style.getSource(SRC_TRACK) as? GeoJsonSource)?.setGeoJson(
        if (track.size >= 2) {
            FeatureCollection.fromFeature(
                Feature.fromGeometry(
                    LineString.fromLngLats(track.map { Point.fromLngLat(it.lng, it.lat) }),
                ),
            )
        } else {
            FeatureCollection.fromFeatures(emptyArray<Feature>())
        },
    )
    (style.getSource(SRC_WAYPOINTS) as? GeoJsonSource)?.setGeoJson(
        FeatureCollection.fromFeatures(
            waypoints.map { Feature.fromGeometry(Point.fromLngLat(it.lng, it.lat)) }.toTypedArray(),
        ),
    )
}

private fun applyCamera(
    map: MapLibreMap,
    holder: MapHolder,
    track: List<TrackPointEntity>,
    waypoints: List<WaypointEntity>,
    behavior: CameraBehavior,
) {
    if (map.width <= 0 || map.height <= 0) return
    when (behavior) {
        CameraBehavior.Manual -> Unit
        CameraBehavior.FollowLatest -> {
            val last = track.lastOrNull() ?: return
            val current = map.cameraPosition
            if (current.zoom < 13.0) {
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(last.lat, last.lng))
                    .zoom(15.0)
                    .build()
            } else {
                map.animateCamera(CameraUpdateFactory.newLatLng(LatLng(last.lat, last.lng)), 500)
            }
        }
        CameraBehavior.FitBounds -> {
            if (holder.boundsFitted) return
            val coords = track.map { it.lat to it.lng } + waypoints.map { it.lat to it.lng }
            if (coords.isEmpty()) return
            // Single coordinate (e.g. a featured drive whose only known location is its
            // centroid pin) — center on it at a regional zoom so the user sees context.
            if (coords.size == 1) {
                val (lat, lng) = coords[0]
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(lat, lng))
                    .zoom(9.0)
                    .build()
                holder.boundsFitted = true
                return
            }
            val builder = LatLngBounds.Builder()
            coords.forEach { (lat, lng) -> builder.include(LatLng(lat, lng)) }
            val bounds = runCatching { builder.build() }.getOrNull() ?: return
            if (bounds.northEast == bounds.southWest) {
                map.cameraPosition = CameraPosition.Builder()
                    .target(bounds.center)
                    .zoom(15.0)
                    .build()
            } else {
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
            }
            holder.boundsFitted = true
        }
    }
}
