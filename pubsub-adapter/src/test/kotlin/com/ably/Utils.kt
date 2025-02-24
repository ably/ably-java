package com.ably

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
