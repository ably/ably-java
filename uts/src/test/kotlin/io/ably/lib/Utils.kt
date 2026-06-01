package io.ably.lib

import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.ChannelStateListener
import io.ably.lib.realtime.ConnectionState
import io.ably.lib.realtime.ConnectionStateListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

suspend fun awaitState(
  client: AblyRealtime,
  target: ConnectionState,
  timeout: Duration = 5.seconds
) {
  // withContext uses a real-thread dispatcher so withTimeout measures wall-clock time,
  // not virtual (kotlinx.coroutines.test) time.
  // Listener is registered BEFORE the state check to avoid the race where the target
  // state fires on the ActionHandler thread between the check and the registration.
  withContext(Dispatchers.Default.limitedParallelism(1)) {
    withTimeout(timeout) {
      suspendCancellableCoroutine { cont ->
        val listener = ConnectionStateListener { change ->
          if (change.current == target && cont.isActive) cont.resume(Unit)
        }
        client.connection.on(listener)
        if (client.connection.state == target && cont.isActive) cont.resume(Unit)
        cont.invokeOnCancellation { client.connection.off(listener) }
      }
    }
  }
}

suspend fun awaitChannelState(
  channel: Channel,
  target: ChannelState,
  timeout: Duration = 5.seconds
) {
  withContext(Dispatchers.Default.limitedParallelism(1)) {
    withTimeout(timeout) {
      suspendCancellableCoroutine { cont ->
        val listener = ChannelStateListener { change ->
          if (change.current == target && cont.isActive) cont.resume(Unit)
        }
        channel.on(listener)
        if (channel.state == target && cont.isActive) cont.resume(Unit)
        cont.invokeOnCancellation { channel.off(listener) }
      }
    }
  }
}
