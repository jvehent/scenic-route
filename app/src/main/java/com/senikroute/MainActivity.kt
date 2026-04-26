package com.senikroute

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.senikroute.recording.BufferController
import com.senikroute.ui.nav.SenikNavHost
import com.senikroute.ui.theme.SenikTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var bufferController: BufferController

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
    }

    override fun onResume() {
        super.onResume()
        bufferController.tryStart()
    }
}
