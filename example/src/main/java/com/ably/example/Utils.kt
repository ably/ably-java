package com.ably.example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.ably.lib.objects.RealtimeObjects
import io.ably.lib.objects.ObjectsCallback
import io.ably.lib.objects.type.counter.LiveCounter
import io.ably.lib.objects.type.counter.LiveCounterUpdate
import io.ably.lib.objects.type.map.LiveMap
import io.ably.lib.objects.type.map.LiveMapUpdate
import io.ably.lib.objects.type.map.LiveMapUpdate.Change.UPDATED
import io.ably.lib.objects.type.map.LiveMapValue
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.ChannelStateListener
import io.ably.lib.types.AblyException
import io.ably.lib.types.ChannelMode
import io.ably.lib.types.ChannelOptions
import io.ably.lib.types.ErrorInfo
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

suspend fun RealtimeObjects.getRootCoroutines(): LiveMap = suspendCancellableCoroutine { continuation ->
  getRootAsync(object : ObjectsCallback<LiveMap> {
    override fun onSuccess(result: LiveMap?) {
      continuation.resume(result!!)
    }

    override fun onError(exception: AblyException?) {
      continuation.cancel(exception)
    }
  })
}

suspend fun RealtimeObjects.createCounterCoroutine(): LiveCounter = suspendCancellableCoroutine { continuation ->
  createCounterAsync(object : ObjectsCallback<LiveCounter> {
    override fun onSuccess(result: LiveCounter?) {
      continuation.resume(result!!)
    }

    override fun onError(exception: AblyException?) {
      continuation.cancel(exception)
    }
  })
}

suspend fun RealtimeObjects.createMapCoroutine(): LiveMap = suspendCancellableCoroutine { continuation ->
  createMapAsync(object : ObjectsCallback<LiveMap> {
    override fun onSuccess(result: LiveMap?) {
      continuation.resume(result!!)
    }

    override fun onError(exception: AblyException?) {
      continuation.cancel(exception)
    }
  })
}

suspend fun LiveCounter.incrementCoroutine(amount: Int): Unit = suspendCancellableCoroutine { continuation ->
  incrementAsync(amount, object : ObjectsCallback<Void> {
    override fun onSuccess(result: Void?) {
      continuation.resume(Unit)
    }

    override fun onError(exception: AblyException?) {
      continuation.cancel(exception)
    }
  })
}

suspend fun LiveCounter.decrementCoroutine(amount: Int): Unit = suspendCancellableCoroutine { continuation ->
  decrementAsync(amount, object : ObjectsCallback<Void> {
    override fun onSuccess(result: Void?) {
      continuation.resume(Unit)
    }

    override fun onError(exception: AblyException?) {
      continuation.cancel(exception)
    }
  })
}

suspend fun Channel.updateOptions(options: ChannelOptions): Unit = suspendCancellableCoroutine { continuation ->
  setOptions(options, object : io.ably.lib.realtime.CompletionListener {
    override fun onSuccess() {
      continuation.resume(Unit)
    }

    override fun onError(reason: ErrorInfo?) {
      continuation.cancel(AblyException.fromErrorInfo(reason))
    }
  })
}

suspend fun getOrCreateCounter(channel: Channel, root: LiveMap?, path: String): LiveCounter {
  val mapValue = root?.get(path)
  if (mapValue == null) {
    val counter = channel.objects.createCounterCoroutine()
    root?.setCoroutine(path, LiveMapValue.of(counter))
    return counter
  } else {
    return mapValue.asLiveCounter
  }
}

suspend fun getOrCreateMap(channel: Channel, root: LiveMap?, path: String): LiveMap {
  val mapValue = root?.get(path)
  if (mapValue == null) {
    val map = channel.objects.createMapCoroutine()
    root?.setCoroutine(path, LiveMapValue.of(map))
    return map
  } else {
    return mapValue.asLiveMap
  }
}

suspend fun LiveMap.setCoroutine(key: String, value: LiveMapValue) = suspendCancellableCoroutine<Unit> { continuation ->
  setAsync(key, value, object : ObjectsCallback<Void> {
    override fun onSuccess(result: Void?) {
      continuation.resume(Unit)
    }

    override fun onError(exception: AblyException?) {
      continuation.cancel(exception)
    }
  })
}

suspend fun LiveMap.removeCoroutine(key: String) = suspendCancellableCoroutine<Unit> { continuation ->
  removeAsync(key, object : ObjectsCallback<Void> {
    override fun onSuccess(result: Void?) {
      continuation.resume(Unit)
    }

    override fun onError(exception: AblyException?) {
      continuation.cancel(exception)
    }
  })
}

@Composable
fun observeCounter(channel: Channel, root: LiveMap?, path: String): CounterState {
  var counter by remember { mutableStateOf<LiveCounter?>(null) }
  var counterValue by remember { mutableStateOf<Int?>(null) }

  LaunchedEffect(root) {
    counter = getOrCreateCounter(channel, root, path)
  }

  DisposableEffect(counter) {
    counterValue = counter?.value()?.toInt()

    val listener: (LiveCounterUpdate) -> Unit = {
      counter?.value()?.let {
        counterValue = it.toInt()
      }
    }

    counter?.subscribe(listener)

    onDispose {
      counter?.unsubscribe(listener)
    }
  }

  DisposableEffect(root) {
    val listener: (LiveMapUpdate) -> Unit = { rootUpdate ->
      val counterHasBeenRemoved = rootUpdate.update
        .filter { (_, change) -> change == UPDATED }
        .any { (keyName) -> keyName == path }

      if (counterHasBeenRemoved) root?.get(path)?.asLiveCounter?.let { counter = it }
    }

    root?.subscribe(listener)

    onDispose {
      root?.unsubscribe(listener)
    }
  }

  return CounterState(counterValue, counter) {
    coroutineScope {
      launch {
        counter = channel.objects.createCounterCoroutine().also {
          root?.setCoroutine(path, LiveMapValue.of(it))
        }
      }
    }
  }
}

data class CounterState(val value: Int?, val counter: LiveCounter?, val reset: suspend () -> Unit)

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
fun observeMap(channel: Channel, root: LiveMap?, path: String): Pair<Map<String, String>, LiveMap?> {
  var map by remember { mutableStateOf<LiveMap?>(null) }
  var mapValue by remember { mutableStateOf<Map<String, String>>(mapOf()) }

  LaunchedEffect(root) {
    map = getOrCreateMap(channel, root, path)
  }

  DisposableEffect(map) {
    map?.entries()?.associate { (key, value) -> key to value.asString }?.let {
      mapValue = it
    }

    val listener: (LiveMapUpdate) -> Unit = {
      map?.entries()?.associate { (key, value) -> key to value.asString }?.let {
        mapValue = it
      }
    }

    map?.subscribe(listener)

    onDispose {
      map?.unsubscribe(listener)
    }
  }

  return mapValue to map
}

@Composable
fun observeRootObject(channel: Channel): LiveMap? {
  val channelState = observeChannelState(channel)
  var root: LiveMap? by remember { mutableStateOf(null) }

  LaunchedEffect(channelState) {
    if (channelState == ChannelState.attached) {
      root = channel.objects.getRootCoroutines()
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
