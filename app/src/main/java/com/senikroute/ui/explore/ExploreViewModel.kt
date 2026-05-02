package com.senikroute.ui.explore

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.senikroute.data.discovery.DiscoveryDrive
import com.senikroute.data.discovery.DiscoveryRepository
import com.senikroute.data.prefs.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val discovery: DiscoveryRepository,
    settings: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ExploreUiState())
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()

    private val _featured = MutableStateFlow<List<DiscoveryDrive>>(emptyList())
    val featured: StateFlow<List<DiscoveryDrive>> = _featured.asStateFlow()

    init {
        loadFeatured()
    }

    private fun loadFeatured() {
        viewModelScope.launch {
            _featured.value = discovery.fetchFeatured(limit = 5)
        }
    }

    val radiusKm: StateFlow<Int> = settings.settings
        .map { it.discoveryRadiusKm }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 25)

    @SuppressLint("MissingPermission")
    fun refresh() {
        val granted = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            _state.value = _state.value.copy(needsLocationPermission = true, loading = false)
            return
        }
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching {
                val client = LocationServices.getFusedLocationProviderClient(appContext)
                val loc = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
                    ?: error("No location fix")
                queryAt(loc.latitude, loc.longitude, userLat = loc.latitude, userLng = loc.longitude)
            }.onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Failed")
            }
        }
    }

    /**
     * Called by the map when the user finishes panning/zooming. Updates camera-center
     * state so the UI can decide whether to show the "Search this area" button. Doesn't
     * trigger a Firestore query — that's only run via [searchHere], which is the user's
     * explicit ask. Avoids burning reads on incidental panning.
     */
    fun onCameraMoved(lat: Double, lng: Double) {
        _state.value = _state.value.copy(cameraLat = lat, cameraLng = lng)
    }

    /** Re-runs findNearby at the current camera center. Triggered by the in-map button. */
    fun searchHere() {
        val s = _state.value
        val lat = s.cameraLat ?: s.userLat ?: return
        val lng = s.cameraLng ?: s.userLng ?: return
        viewModelScope.launch {
            queryAt(lat, lng, userLat = s.userLat, userLng = s.userLng)
        }
    }

    private suspend fun queryAt(lat: Double, lng: Double, userLat: Double?, userLng: Double?) {
        _state.value = _state.value.copy(loading = true, error = null)
        runCatching {
            val drives = discovery.findNearby(lat, lng, radiusKm.value)
            _state.value = ExploreUiState(
                loading = false,
                userLat = userLat,
                userLng = userLng,
                searchLat = lat,
                searchLng = lng,
                cameraLat = lat,
                cameraLng = lng,
                drives = drives,
            )
        }.onFailure { e ->
            _state.value = _state.value.copy(loading = false, error = e.message ?: "Failed")
        }
    }

    fun onPermissionGranted() {
        _state.value = _state.value.copy(needsLocationPermission = false)
        refresh()
    }

    fun reloadFeatured() = loadFeatured()
}

data class ExploreUiState(
    val loading: Boolean = false,
    /** Last GPS fix for the user — drives the "you are here" pin. Sticky across pans. */
    val userLat: Double? = null,
    val userLng: Double? = null,
    /** Center of the most recent findNearby query. The drive list reflects this. */
    val searchLat: Double? = null,
    val searchLng: Double? = null,
    /** Where the map is currently looking. Differs from search* once the user pans. */
    val cameraLat: Double? = null,
    val cameraLng: Double? = null,
    val drives: List<DiscoveryDrive> = emptyList(),
    val error: String? = null,
    val needsLocationPermission: Boolean = false,
) {
    /** True once the camera has drifted >2 km from the search center — surfaces the button. */
    val searchHerePromptVisible: Boolean
        get() {
            val sLat = searchLat ?: return false
            val sLng = searchLng ?: return false
            val cLat = cameraLat ?: return false
            val cLng = cameraLng ?: return false
            return haversineKm(sLat, sLng, cLat, cLng) > 2.0
        }
}

private fun haversineKm(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(bLat - aLat)
    val dLng = Math.toRadians(bLng - aLng)
    val lat1 = Math.toRadians(aLat)
    val lat2 = Math.toRadians(bLat)
    val a = Math.sin(dLat / 2).let { it * it } +
        Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2).let { it * it }
    return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}
