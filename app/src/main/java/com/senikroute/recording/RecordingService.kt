package com.senikroute.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.senikroute.MainActivity
import com.senikroute.R
import com.senikroute.data.prefs.SettingsStore
import com.senikroute.data.prefs.UserSettings
import com.senikroute.data.repo.haversineMeters
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class RecordingService : LifecycleService() {

    @Inject lateinit var controller: RecordingController
    @Inject lateinit var settingsStore: SettingsStore

    private lateinit var fusedClient: FusedLocationProviderClient
    // Constructed in startRecording() once we've read the user's chosen sampling interval.
    private var sampler: LocationSampler = LocationSampler()
    private var lastAcceptedForDistance: Location? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (fix in result.locations) handleFix(fix)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        ensureNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_STICKY
    }

    private fun startRecording() {
        startForegroundCompat()
        // Read the user's configured sampling interval. runBlocking is acceptable here:
        // this is a one-shot read on the main thread, fed by an in-memory DataStore cache
        // after first read, and gated by foreground-service start which is already heavy.
        val intervalSeconds = runBlocking {
            runCatching { settingsStore.settings.first().gpsSamplingSeconds }
                .getOrDefault(UserSettings.DEFAULT_GPS_SAMPLING_SECONDS)
        }
        val intervalMs = intervalSeconds * 1_000L
        sampler = LocationSampler(minIntervalMs = intervalMs)
        // Match the FusedLocationProvider request rate to the sampling rate so we don't
        // wake the GPS chip more often than we'll keep results.
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs)
            .setMinUpdateDistanceMeters(0f)
            .build()
        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            controller.reportError("Location permission missing")
            stopSelf()
        }
    }

    private fun stopRecording() {
        runCatching { fusedClient.removeLocationUpdates(callback) }
        sampler.reset()
        lastAcceptedForDistance = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleFix(fix: Location) {
        val point = sampler.accept(fix) ?: return
        val prev = lastAcceptedForDistance
        val delta = if (prev != null) {
            haversineMeters(prev.latitude, prev.longitude, fix.latitude, fix.longitude)
        } else 0.0
        lastAcceptedForDistance = fix
        controller.onPointRecorded(point, delta)
        updateNotification()
    }

    private fun startForegroundCompat() {
        val note = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, note)
        }
    }

    private fun updateNotification() {
        val nm = getSystemService<NotificationManager>() ?: return
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val state = controller.state.value
        val distanceKm = state.distanceM / 1000.0
        val elapsedMin = state.elapsedMs / 60_000
        val text = "%.1f km · %d min".format(distanceKm, elapsedMin)

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService<NotificationManager>() ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.recording_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.recording_notification_channel_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.senikroute.recording.START"
        const val ACTION_STOP = "com.senikroute.recording.STOP"
        const val CHANNEL_ID = "recording"
        const val NOTIFICATION_ID = 0x5C_E1
    }
}
