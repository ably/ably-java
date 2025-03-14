package com.ably

import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.rest.AblyRest
import io.ably.lib.types.ClientOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

suspend fun waitFor(timeoutInMs: Long = 10_000, block: suspend () -> Boolean) {
  withContext(Dispatchers.Default) {
    withTimeout(timeoutInMs) {
      do {
        val success = block()
        delay(100)
      } while (!success)
    }
  }
}

fun createAblyRealtime(port: Int): AblyRealtime {
  val options = ClientOptions("xxxxx:yyyyyyy").apply {
    this.port = port
    useBinaryProtocol = false
    realtimeHost = "localhost"
    restHost = "localhost"
    tls = false
    autoConnect = false
  }

  return AblyRealtime(options)
}

fun createAblyRest(port: Int): AblyRest {
  val options = ClientOptions("xxxxx:yyyyyyy").apply {
    this.port = port
    useBinaryProtocol = false
    realtimeHost = "localhost"
    restHost = "localhost"
    tls = false
    autoConnect = false
  }

  return AblyRest(options)
}
