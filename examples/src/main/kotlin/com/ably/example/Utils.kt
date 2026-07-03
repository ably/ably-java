/**
 * The LiveObjects bridge layer for the example app: Compose observers plus coroutine
 * wrappers over the path-based API (`io.ably.lib.liveobjects`).
 *
 * The app works exclusively with [io.ably.lib.liveobjects.path.PathObject]s — references
 * to a *location* in the channel's objects graph, not to a particular object. A PathObject
 * resolves its path lazily on every call, so a stored reference stays valid even when the
 * object at that location is replaced (e.g. "Reset all" swaps in brand-new counters), and
 * a path subscription automatically observes whatever object currently lives there. The
 * identity-bound alternative, [io.ably.lib.liveobjects.instance.Instance] (obtained via
 * `pathObject.instance()`), tracks one specific object wherever it moves — not needed here,
 * since every screen wants location semantics.
 *
 * ably-java's typed flavour of the API: the base PathObject exposes only type-agnostic
 * methods; type-specific reads/writes live on sub-type views reached via `as*` casts
 * (`asLiveCounter()`, `asLiveMap()`, `asString()`, ...). The casts never throw — a
 * wrong-typed view degrades gracefully (reads return null/empty, writes fail with an
 * AblyException). The root obtained from `channel.object.get()` is already a
 * [LiveMapPathObject], so no cast is needed there.
 */
package com.ably.example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.ably.lib.liveobjects.path.types.LiveCounterPathObject
import io.ably.lib.liveobjects.path.types.LiveMapPathObject
import io.ably.lib.liveobjects.value.LiveCounter
import io.ably.lib.liveobjects.value.LiveMap
import io.ably.lib.liveobjects.value.LiveMapValue
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.ChannelStateListener
import io.ably.lib.types.ChannelMode
import io.ably.lib.types.ChannelOptions
import kotlinx.coroutines.future.await

/**
 * Returns the counter path object at [key] under [root], creating and linking a fresh
 * zero-value counter when nothing exists at that path yet. Path objects re-resolve on
 * every call, so the returned reference stays valid even if another client replaces the
 * counter object at this key.
 */
suspend fun getOrCreateCounter(root: LiveMapPathObject, key: String): LiveCounterPathObject {
  val path = root.get(key)
  if (!path.exists()) {
    // Creates the counter and links it under root in a single published operation
    root.set(key, LiveMapValue.of(LiveCounter.create())).await()
  }
  return path.asLiveCounter()
}

/**
 * Returns the map path object at [key] under [root], creating and linking a fresh empty
 * map when nothing exists at that path yet.
 */
suspend fun getOrCreateMap(root: LiveMapPathObject, key: String): LiveMapPathObject {
  val path = root.get(key)
  if (!path.exists()) {
    root.set(key, LiveMapValue.of(LiveMap.create())).await()
  }
  return path.asLiveMap()
}

@Composable
fun observeCounter(root: LiveMapPathObject?, key: String): CounterState {
  var counter by remember { mutableStateOf<LiveCounterPathObject?>(null) }
  var counterValue by remember { mutableStateOf<Int?>(null) }

  LaunchedEffect(root) {
    root?.let {
      supressCoroutineExceptions {
        counter = getOrCreateCounter(it, key)
      }
    }
  }

  DisposableEffect(counter) {
    counterValue = counter?.value()?.toInt()

    // The path subscription fires both for increments on the counter and for the key
    // being replaced with a new counter (e.g. "Reset all" on another device); the path
    // re-resolves on read, so no explicit rebinding is needed.
    val subscription = counter?.subscribe {
      counterValue = counter?.value()?.toInt()
    }

    onDispose {
      subscription?.unsubscribe()
    }
  }

  return CounterState(counterValue, counter) {
    // Reset by replacing the object at this key with a brand-new zero-value counter;
    // fire-and-forget - the path subscription refreshes the displayed value on ack
    root?.set(key, LiveMapValue.of(LiveCounter.create()))
  }
}

data class CounterState(val value: Int?, val counter: LiveCounterPathObject?, val reset: () -> Unit)

@Composable
fun observeChannelState(channel: Channel): ChannelState {
  var channelState by remember { mutableStateOf(channel.state) }

  DisposableEffect(channel) {
    val listener: (ChannelStateListener.ChannelStateChange) -> Unit = {
      channelState = it.current
    }

    channel.on(listener)

    onDispose {
      channel.off(listener)
    }
  }

  return channelState
}

@Composable
fun observeMap(root: LiveMapPathObject?, key: String): Pair<Map<String, String>, LiveMapPathObject?> {
  var map by remember { mutableStateOf<LiveMapPathObject?>(null) }
  var mapValue by remember { mutableStateOf<Map<String, String>>(mapOf()) }

  fun readEntries(liveMap: LiveMapPathObject?): Map<String, String> =
    liveMap?.entries()
      ?.mapNotNull { (entryKey, valuePath) -> valuePath.asString().value()?.let { entryKey to it } }
      ?.toMap()
      ?: mapOf()

  LaunchedEffect(root) {
    root?.let {
      supressCoroutineExceptions {
        map = getOrCreateMap(it, key)
      }
    }
  }

  DisposableEffect(map) {
    mapValue = readEntries(map)

    // Fires for every entry change on the map at this path (default subscription depth
    // covers nested updates), after which entries are re-read from the resolved map.
    val subscription = map?.subscribe {
      mapValue = readEntries(map)
    }

    onDispose {
      subscription?.unsubscribe()
    }
  }

  return mapValue to map
}

@Composable
fun observeRootObject(channel: Channel): LiveMapPathObject? {
  val channelState = observeChannelState(channel)
  var root: LiveMapPathObject? by remember { mutableStateOf(null) }

  LaunchedEffect(channelState) {
    if (channelState == ChannelState.attached) {
      supressCoroutineExceptions {
        // Completes once the objects synchronization state has reached SYNCED
        root = channel.`object`.get().await()
      }
    }
  }

  return root
}

@Composable
fun getRealtimeChannel(realtimeClient: AblyRealtime, channelName: String): Channel {
  val channel = realtimeClient.channels.get(channelName)

  DisposableEffect(channel) {
    channel.setOptions(ChannelOptions().apply {
      attachOnSubscribe = false
      modes = arrayOf(ChannelMode.object_publish, ChannelMode.object_subscribe)
    })

    channel.attach()

    onDispose {
      channel.detach()
    }
  }

  return channel
}

suspend fun supressCoroutineExceptions(block: suspend () -> Unit) {
  try {
    block()
  } catch (_: Exception) {
  }
}
