package com.ably.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ably.example.screen.MainScreen
import com.ably.example.ui.theme.AblyTheme
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.types.ClientOptions
import io.ably.lib.util.Log

class MainActivity : ComponentActivity() {
  private val realtimeClient: AblyRealtime by lazy {
    AblyRealtime(
      ClientOptions().apply {
        key = BuildConfig.ABLY_KEY
        logLevel = Log.VERBOSE
        autoConnect = false
      }
    )
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      AblyTheme {
        MainScreen(realtimeClient)
      }
    }
  }

  override fun onStart() {
    super.onStart()
    realtimeClient.connect()
  }

  override fun onStop() {
    super.onStop()
    realtimeClient.close()
  }

  override fun onResume() {
    super.onResume()
    realtimeClient.connect()
  }

  override fun onPause() {
    super.onPause()
    realtimeClient.close()
  }
}
