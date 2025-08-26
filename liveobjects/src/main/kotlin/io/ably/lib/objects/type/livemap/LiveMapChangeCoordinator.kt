package io.ably.lib.objects.type.livemap

import io.ably.lib.objects.ObjectsSubscription
import io.ably.lib.objects.type.map.LiveMapChange
import io.ably.lib.objects.type.map.LiveMapUpdate
import io.ably.lib.util.EventEmitter
import io.ably.lib.util.Log

internal val noOpMapUpdate = LiveMapUpdate()

/**
 * Interface for handling live map changes by notifying subscribers of updates.
 * Implementations typically propagate updates through event emission to registered listeners.
 */
internal interface HandlesLiveMapChange {
  /**
   * Notifies all registered listeners about a map update by propagating the change through the event system.
   * This method is called when map data changes and triggers the emission of update events to subscribers.
   */
  fun notify(update: LiveMapUpdate)
}

internal abstract class LiveMapChangeCoordinator: LiveMapChange, HandlesLiveMapChange {
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
      event?.let { listener?.onUpdated(it) }
        ?: Log.w(tag, "Null event passed to LiveMapChange listener callback")
    } catch (t: Throwable) {
      Log.e(tag, "Error occurred while executing listener callback for event: $event", t)
    }
  }
}
