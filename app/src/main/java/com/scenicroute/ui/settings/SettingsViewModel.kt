package com.scenicroute.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenicroute.data.prefs.SettingsStore
import com.scenicroute.data.prefs.UserSettings
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
            UserSettings(bufferEnabled = true, bufferMinutes = 30, discoveryRadiusKm = 25, wifiOnlyUploads = false),
        )

    fun setBufferEnabled(on: Boolean) = viewModelScope.launch { store.setBufferEnabled(on) }
    fun setBufferMinutes(m: Int) = viewModelScope.launch { store.setBufferMinutes(m) }
    fun setDiscoveryRadius(km: Int) = viewModelScope.launch { store.setDiscoveryRadiusKm(km) }
    fun setWifiOnlyUploads(on: Boolean) = viewModelScope.launch { store.setWifiOnlyUploads(on) }
}
