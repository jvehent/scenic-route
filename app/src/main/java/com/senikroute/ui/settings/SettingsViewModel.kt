package com.senikroute.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senikroute.data.prefs.SettingsStore
import com.senikroute.data.prefs.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
) : ViewModel() {

    val settings: StateFlow<UserSettings> = store.settings
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            UserSettings(
                bufferEnabled = true,
                bufferMinutes = 30,
                discoveryRadiusKm = 25,
                wifiOnlyUploads = false,
                gpsSamplingSeconds = UserSettings.DEFAULT_GPS_SAMPLING_SECONDS,
                driveAutoSave = false,
                driveFolderName = UserSettings.DEFAULT_DRIVE_FOLDER,
                exploreAlertsEnabled = false,
                exploreAlertsRadiusKm = UserSettings.DEFAULT_EXPLORE_ALERTS_RADIUS_KM,
            ),
        )

    fun setBufferEnabled(on: Boolean) = viewModelScope.launch { store.setBufferEnabled(on) }
    fun setBufferMinutes(m: Int) = viewModelScope.launch { store.setBufferMinutes(m) }
    fun setDiscoveryRadius(km: Int) = viewModelScope.launch { store.setDiscoveryRadiusKm(km) }
    fun setWifiOnlyUploads(on: Boolean) = viewModelScope.launch { store.setWifiOnlyUploads(on) }
    fun setGpsSamplingSeconds(s: Int) = viewModelScope.launch { store.setGpsSamplingSeconds(s) }
    fun setExploreAlertsEnabled(on: Boolean) = viewModelScope.launch { store.setExploreAlertsEnabled(on) }
    fun setExploreAlertsRadiusKm(km: Int) = viewModelScope.launch { store.setExploreAlertsRadiusKm(km) }
}
