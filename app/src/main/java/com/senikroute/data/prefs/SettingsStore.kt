package com.senikroute.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
            driveAutoSave = prefs[Keys.DRIVE_AUTO_SAVE] ?: false,
            driveFolderName = prefs[Keys.DRIVE_FOLDER_NAME] ?: UserSettings.DEFAULT_DRIVE_FOLDER,
            exploreAlertsEnabled = prefs[Keys.EXPLORE_ALERTS_ENABLED] ?: false,
            exploreAlertsRadiusKm = (prefs[Keys.EXPLORE_ALERTS_RADIUS_KM] ?: UserSettings.DEFAULT_EXPLORE_ALERTS_RADIUS_KM)
                .coerceIn(UserSettings.EXPLORE_ALERTS_RADIUS_RANGE),
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

    suspend fun setDriveAutoSave(on: Boolean) {
        context.dataStore.edit { it[Keys.DRIVE_AUTO_SAVE] = on }
    }

    suspend fun setDriveFolderName(name: String) {
        val safe = name.trim().take(120).ifBlank { UserSettings.DEFAULT_DRIVE_FOLDER }
        context.dataStore.edit { it[Keys.DRIVE_FOLDER_NAME] = safe }
    }

    suspend fun setExploreAlertsEnabled(on: Boolean) {
        context.dataStore.edit { it[Keys.EXPLORE_ALERTS_ENABLED] = on }
    }

    suspend fun setExploreAlertsRadiusKm(km: Int) {
        val clamped = km.coerceIn(UserSettings.EXPLORE_ALERTS_RADIUS_RANGE)
        context.dataStore.edit { it[Keys.EXPLORE_ALERTS_RADIUS_KM] = clamped }
    }

    private object Keys {
        val BUFFER_ENABLED = booleanPreferencesKey("buffer_enabled")
        val BUFFER_MINUTES = intPreferencesKey("buffer_minutes")
        val DISCOVERY_RADIUS_KM = intPreferencesKey("discovery_radius_km")
        val WIFI_ONLY_UPLOADS = booleanPreferencesKey("wifi_only_uploads")
        val GPS_SAMPLING_SECONDS = intPreferencesKey("gps_sampling_seconds")
        val DRIVE_AUTO_SAVE = booleanPreferencesKey("drive_auto_save")
        val DRIVE_FOLDER_NAME = stringPreferencesKey("drive_folder_name")
        val EXPLORE_ALERTS_ENABLED = booleanPreferencesKey("explore_alerts_enabled")
        val EXPLORE_ALERTS_RADIUS_KM = intPreferencesKey("explore_alerts_radius_km")
    }
}

data class UserSettings(
    val bufferEnabled: Boolean,
    val bufferMinutes: Int,
    val discoveryRadiusKm: Int,
    val wifiOnlyUploads: Boolean,
    val gpsSamplingSeconds: Int,
    val driveAutoSave: Boolean,
    val driveFolderName: String,
    val exploreAlertsEnabled: Boolean,
    val exploreAlertsRadiusKm: Int,
) {
    companion object {
        val VALID_BUFFER_MINUTES = listOf(0, 15, 30, 60, 120)
        const val DEFAULT_GPS_SAMPLING_SECONDS = 10
        val GPS_SAMPLING_RANGE = 1..60
        const val DEFAULT_DRIVE_FOLDER = "Senik Drives"
        const val DEFAULT_EXPLORE_ALERTS_RADIUS_KM = 2
        val EXPLORE_ALERTS_RADIUS_RANGE = 1..10
    }
}
