package io.ably.lib.objects.type.livemap

import io.ably.lib.objects.ObjectsSubscription
import io.ably.lib.objects.type.map.LiveMapChange
import io.ably.lib.objects.type.map.LiveMapUpdate
import io.ably.lib.util.EventEmitter
import io.ably.lib.util.Log

internal val noOpMapUpdate = LiveMapUpdate()

internal interface HandlesLiveMapChange {
  fun notify(update: LiveMapUpdate)
}

internal abstract class LiveMapChangeCoordinator: LiveMapChange, HandlesLiveMapChange {
  private val tag = "DefaultLiveMapChangeCoordinator"

  private val mapChangeEmitter = LiveMapChangeEmitter()

  override fun subscribe(listener: LiveMapChange.Listener): ObjectsSubscription {
    mapChangeEmitter.on(listener)
    return ObjectsSubscription {
      mapChangeEmitter.off(listener)
    }
  }

  override fun unsubscribe(listener: LiveMapChange.Listener) = mapChangeEmitter.off(listener)

  override fun unsubscribeAll() = mapChangeEmitter.off()

  override fun notify(update: LiveMapUpdate) = mapChangeEmitter.emit(update)
}

private class LiveMapChangeEmitter : EventEmitter<LiveMapUpdate, LiveMapChange.Listener>() {
  private val tag = "LiveMapChangeEmitter"

  override fun apply(listener: LiveMapChange.Listener?, event: LiveMapUpdate?, vararg args: Any?) {
    try {
      listener?.onUpdated(event!!)
    } catch (t: Throwable) {
      Log.e(tag, "Error occurred while executing listener callback for event: $event", t)
    }
  }
}
