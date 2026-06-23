package io.ably.lib.`object`.value.livecounter

import io.ably.lib.`object`.Subscription
import io.ably.lib.`object`.instance.InstanceListener
import io.ably.lib.`object`.instance.InstanceSubscriptionEvent
import io.ably.lib.`object`.onceSubscription
import io.ably.lib.`object`.value.ObjectUpdate
import io.ably.lib.util.EventEmitter
import io.ably.lib.util.Log

internal val noOpCounterUpdate = ObjectUpdate(null)

/**
 * Interface for handling live counter changes by notifying subscribers of updates.
 * Implementations typically propagate updates through event emission to registered listeners.
 */
internal interface HandlesLiveCounterChange {
  /**
   * Notifies all registered listeners about a counter update by propagating the change through the event system.
   * This method is called when counter data changes and triggers the emission of update events to subscribers.
   */
  fun notify(update: LiveCounterChangeEvent)
}

internal interface LiveCounterChangeListener : InstanceListener

internal interface LiveCounterChangeEvent : InstanceSubscriptionEvent

internal abstract class LiveCounterChangeCoordinator: HandlesLiveCounterChange {
  private val counterChangeEmitter = LiveCounterChangeEmitter()

  fun subscribe(listener: LiveCounterChangeListener): Subscription {
    counterChangeEmitter.on(listener)
    return onceSubscription {
      counterChangeEmitter.off(listener)
    }
  }

  override fun notify(update: LiveCounterChangeEvent) = counterChangeEmitter.emit(update)
}

private class LiveCounterChangeEmitter : EventEmitter<LiveCounterChangeEvent, LiveCounterChangeListener>() {
  private val tag = "LiveCounterChangeEmitter"

  override fun apply(listener: LiveCounterChangeListener?, event: LiveCounterChangeEvent, vararg args: Any?) {
    try {
      event?.let { listener?.onUpdated(it) }
        ?: Log.w(tag, "Null event passed to LiveCounterChange listener callback")
    } catch (t: Throwable) {
      Log.e(tag, "Error occurred while executing listener callback for event: $event", t)
    }
  }
}
