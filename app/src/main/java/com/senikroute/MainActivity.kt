package com.senikroute

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.senikroute.recording.BufferController
import com.senikroute.recording.RecordingService
import com.senikroute.ui.nav.SenikNavHost
import com.senikroute.ui.theme.SenikTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var bufferController: BufferController

    /** Receives the in-app ACTION_EXIT_APP broadcast from the notification's "Stop & exit". */
    private val exitReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finishAndRemoveTask()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SenikTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SenikNavHost()
                }
            }
        }

        // Register the exit-broadcast receiver. RECEIVER_NOT_EXPORTED on Android 13+
        // keeps the broadcast in-process — this is purely for the notification → activity
        // hand-off, no external app should be able to dismiss our UI.
        val filter = IntentFilter(RecordingService.ACTION_EXIT_APP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(exitReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(exitReceiver, filter)
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(exitReceiver) }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        bufferController.tryStart()
    }
}
