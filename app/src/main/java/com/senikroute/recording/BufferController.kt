package com.senikroute.recording

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.senikroute.data.prefs.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BufferController @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settings: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started: Boolean = false

    fun observeSettings() {
        scope.launch {
            settings.settings
                .map { it.bufferEnabled && it.bufferMinutes > 0 }
                .distinctUntilChanged()
                .collect { shouldRun -> if (shouldRun) tryStart() else stop() }
        }
    }

    fun tryStart() {
        if (started) return
        if (!hasLocationPermission()) return
        val intent = Intent(appContext, BufferService::class.java).apply {
            action = BufferService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
        started = true
    }

    fun stop() {
        if (!started) return
        val intent = Intent(appContext, BufferService::class.java).apply {
            action = BufferService.ACTION_STOP
        }
        appContext.startService(intent)
        started = false
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
}
