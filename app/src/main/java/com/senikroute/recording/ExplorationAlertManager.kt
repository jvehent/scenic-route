package com.senikroute.recording

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.senikroute.MainActivity
import com.senikroute.R
import com.senikroute.data.discovery.DiscoveryRepository
import com.senikroute.data.prefs.SettingsStore
import com.senikroute.ui.nav.Destinations
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches the user's location stream while exploration alerts are enabled and shows a
 * notification when a public drive comes within the configured radius. Wired into
 * [BufferService.handleFix] — that service already runs while the user is moving and
 * already holds the location subscription, so we piggyback on it instead of standing up
 * a second foreground service.
 *
 * Two layers of throttling protect against pathological behavior:
 *  - **Query throttle**: Firestore `findNearby` runs at most once per `MIN_QUERY_INTERVAL_MS`.
 *    GPS fixes can come in faster than the user's speed actually moves them across the geohash
 *    grid — querying every fix would burn Firestore reads with zero new information.
 *  - **Per-drive cooldown**: a given drive ID won't fire a second notification within
 *    `RENOTIFY_COOLDOWN_MS`. Without this, sitting near a drive (e.g. parked) would
 *    re-buzz the user every minute.
 *
 * State is in-process only — restarting the app resets both throttles. That's intentional
 * for v1: the simpler implementation, and re-firing a notification once after a process
 * restart is an acceptable failure mode.
 */
@Singleton
class ExplorationAlertManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val discoveryRepo: DiscoveryRepository,
    private val settings: SettingsStore,
) {

    private var lastQueryAtMs: Long = 0L
    private val notifiedAtMs: MutableMap<String, Long> = mutableMapOf()

    suspend fun maybeNotify(lat: Double, lng: Double) {
        val s = runCatching { settings.settings.first() }.getOrNull() ?: return
        if (!s.exploreAlertsEnabled) return

        val now = System.currentTimeMillis()
        if (now - lastQueryAtMs < MIN_QUERY_INTERVAL_MS) return
        lastQueryAtMs = now

        val nearby = runCatching { discoveryRepo.findNearby(lat, lng, s.exploreAlertsRadiusKm) }
            .onFailure { Log.w(TAG, "findNearby failed: ${it.message}") }
            .getOrNull()
            ?: return

        // Pick the closest drive we haven't notified for recently.
        val candidate = nearby.firstOrNull { d ->
            val last = notifiedAtMs[d.driveId] ?: 0L
            now - last >= RENOTIFY_COOLDOWN_MS
        } ?: return

        notifiedAtMs[candidate.driveId] = now
        ensureChannel()
        showNotification(
            driveId = candidate.driveId,
            title = candidate.title.ifBlank { "Scenic drive nearby" },
            distanceKm = candidate.distanceFromUserKm,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService<NotificationManager>() ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.explore_alerts_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.explore_alerts_channel_desc)
            setShowBadge(true)
        }
        nm.createNotificationChannel(channel)
    }

    private fun showNotification(driveId: String, title: String, distanceKm: Double) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        // Tap intent: open MainActivity with the public-drive deep link. The nav graph
        // already has a navDeepLink for senikroute://drive/{id} that lands on PublicDriveScreen.
        val deepLink = Uri.parse(Destinations.shareUrlFor(driveId))
        val viewIntent = Intent(Intent.ACTION_VIEW, deepLink, context, MainActivity::class.java)
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pending = PendingIntent.getActivity(
            context,
            // Use the driveId hash as request code so concurrent notifications for different
            // drives get distinct PendingIntents (otherwise FLAG_UPDATE_CURRENT collapses them).
            driveId.hashCode(),
            viewIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = "%.1f km away — tap to view".format(distanceKm)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        // Use the driveId hash as the notification ID so each drive gets its own slot
        // and a new alert doesn't replace a previous one the user hasn't dismissed yet.
        nm.notify(NOTIFICATION_ID_BASE + (driveId.hashCode() and 0x0fff_ffff), notification)
    }

    companion object {
        const val CHANNEL_ID = "exploration_alerts"
        const val NOTIFICATION_ID_BASE = 0x5C_E3_0000.toInt()
        // Don't re-query Firestore more than once per minute even if GPS fixes come faster.
        private const val MIN_QUERY_INTERVAL_MS = 60_000L
        // Don't re-notify the same drive within 6 hours of the last alert for it.
        private const val RENOTIFY_COOLDOWN_MS = 6L * 60 * 60 * 1000
        private const val TAG = "ExploreAlerts"
    }
}
