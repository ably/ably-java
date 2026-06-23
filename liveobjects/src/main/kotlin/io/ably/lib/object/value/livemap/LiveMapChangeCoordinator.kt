package io.ably.lib.objects.type.livemap

import io.ably.lib.`object`.Subscription
import io.ably.lib.`object`.instance.InstanceListener
import io.ably.lib.`object`.instance.InstanceSubscriptionEvent
import io.ably.lib.`object`.onceSubscription
import io.ably.lib.`object`.value.ObjectUpdate
import io.ably.lib.util.EventEmitter
import io.ably.lib.util.Log

internal val noOpMapUpdate = ObjectUpdate(null)

/**
 * Interface for handling live map changes by notifying subscribers of updates.
 * Implementations typically propagate updates through event emission to registered listeners.
 */
internal interface HandlesLiveMapChange {
  /**
   * Notifies all registered listeners about a map update by propagating the change through the event system.
   * This method is called when map data changes and triggers the emission of update events to subscribers.
   */
  fun notify(update: LiveMapChangeEvent)
}

internal interface LiveMapChangeListener : InstanceListener

internal interface LiveMapChangeEvent : InstanceSubscriptionEvent

internal abstract class LiveMapChangeCoordinator: HandlesLiveMapChange {
  private val mapChangeEmitter = LiveMapChangeEmitter()

  fun subscribe(listener: LiveMapChangeListener): Subscription {
    mapChangeEmitter.on(listener)
    return onceSubscription {
      mapChangeEmitter.off(listener)
    }
  }

  override fun notify(update: LiveMapChangeEvent) = mapChangeEmitter.emit(update)
}

private class LiveMapChangeEmitter : EventEmitter<LiveMapChangeEvent, LiveMapChangeListener>() {
  private val tag = "LiveMapChangeEmitter"

  override fun apply(listener: LiveMapChangeListener?, event: LiveMapChangeEvent?, vararg args: Any?) {
    try {
      event?.let { listener?.onUpdated(it) }
        ?: Log.w(tag, "Null event passed to LiveMapChange listener callback")
    } catch (t: Throwable) {
      Log.e(tag, "Error occurred while executing listener callback for event: $event", t)
    }
  }
}
