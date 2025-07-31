package io.ably.lib.objects

import io.ably.lib.types.AblyException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile

internal object ServerTime {
  @Volatile
  private var serverTimeOffset: Long? = null
  private val mutex = Mutex()

  @Throws(AblyException::class)
  internal suspend fun getCurrentTime(adapter: LiveObjectsAdapter): Long {
    if (serverTimeOffset == null) {
      mutex.withLock {
        if (serverTimeOffset == null) { // Double-checked locking to ensure thread safety
          val serverTime: Long = withContext(Dispatchers.IO) { adapter.time }
          serverTimeOffset = serverTime - System.currentTimeMillis()
          return serverTime
        }
      }
    }
    return System.currentTimeMillis() + serverTimeOffset!!
  }
}
