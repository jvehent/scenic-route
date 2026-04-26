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
                val km = radiusKm.value
                val drives = discovery.findNearby(loc.latitude, loc.longitude, km)
                _state.value = ExploreUiState(
                    loading = false,
                    userLat = loc.latitude,
                    userLng = loc.longitude,
                    drives = drives,
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Failed")
            }
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
    val userLat: Double? = null,
    val userLng: Double? = null,
    val drives: List<DiscoveryDrive> = emptyList(),
    val error: String? = null,
    val needsLocationPermission: Boolean = false,
)
