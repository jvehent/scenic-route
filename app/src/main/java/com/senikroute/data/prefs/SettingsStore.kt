package com.senikroute.data.prefs

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

private val Context.dataStore by preferencesDataStore(name = "senik_settings")

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
            gpsSamplingSeconds = (prefs[Keys.GPS_SAMPLING_SECONDS] ?: UserSettings.DEFAULT_GPS_SAMPLING_SECONDS)
                .coerceIn(UserSettings.GPS_SAMPLING_RANGE),
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

    suspend fun setGpsSamplingSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(UserSettings.GPS_SAMPLING_RANGE)
        context.dataStore.edit { it[Keys.GPS_SAMPLING_SECONDS] = clamped }
    }

    private object Keys {
        val BUFFER_ENABLED = booleanPreferencesKey("buffer_enabled")
        val BUFFER_MINUTES = intPreferencesKey("buffer_minutes")
        val DISCOVERY_RADIUS_KM = intPreferencesKey("discovery_radius_km")
        val WIFI_ONLY_UPLOADS = booleanPreferencesKey("wifi_only_uploads")
        val GPS_SAMPLING_SECONDS = intPreferencesKey("gps_sampling_seconds")
    }
}

data class UserSettings(
    val bufferEnabled: Boolean,
    val bufferMinutes: Int,
    val discoveryRadiusKm: Int,
    val wifiOnlyUploads: Boolean,
    val gpsSamplingSeconds: Int,
) {
    companion object {
        val VALID_BUFFER_MINUTES = listOf(0, 15, 30, 60, 120)
        const val DEFAULT_GPS_SAMPLING_SECONDS = 10
        val GPS_SAMPLING_RANGE = 1..60
    }
}
