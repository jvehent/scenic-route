package com.scenicroute.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "scenic_settings")

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            bufferEnabled = prefs[Keys.BUFFER_ENABLED] ?: true,
            bufferMinutes = prefs[Keys.BUFFER_MINUTES] ?: 30,
            discoveryRadiusKm = prefs[Keys.DISCOVERY_RADIUS_KM] ?: 25,
            wifiOnlyUploads = prefs[Keys.WIFI_ONLY_UPLOADS] ?: false,
        )
    }

    suspend fun setBufferEnabled(on: Boolean) {
        context.dataStore.edit { it[Keys.BUFFER_ENABLED] = on }
    }

    suspend fun setBufferMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.BUFFER_MINUTES] = minutes }
    }

    suspend fun setDiscoveryRadiusKm(km: Int) {
        context.dataStore.edit { it[Keys.DISCOVERY_RADIUS_KM] = km }
    }

    suspend fun setWifiOnlyUploads(on: Boolean) {
        context.dataStore.edit { it[Keys.WIFI_ONLY_UPLOADS] = on }
    }

    private object Keys {
        val BUFFER_ENABLED = booleanPreferencesKey("buffer_enabled")
        val BUFFER_MINUTES = intPreferencesKey("buffer_minutes")
        val DISCOVERY_RADIUS_KM = intPreferencesKey("discovery_radius_km")
        val WIFI_ONLY_UPLOADS = booleanPreferencesKey("wifi_only_uploads")
    }
}

data class UserSettings(
    val bufferEnabled: Boolean,
    val bufferMinutes: Int,
    val discoveryRadiusKm: Int,
    val wifiOnlyUploads: Boolean,
) {
    companion object {
        val VALID_BUFFER_MINUTES = listOf(0, 15, 30, 60, 120)
    }
}
