package com.scenicroute

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.scenicroute.recording.BufferController
import com.scenicroute.ui.nav.ScenicNavHost
import com.scenicroute.ui.theme.ScenicRouteTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var bufferController: BufferController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScenicRouteTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScenicNavHost()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        bufferController.tryStart()
    }
}
