package io.ably.lib.objects.type.livecounter

import io.ably.lib.objects.ObjectsSubscription
import io.ably.lib.objects.type.counter.LiveCounterChange
import io.ably.lib.objects.type.counter.LiveCounterUpdate
import io.ably.lib.util.EventEmitter
import io.ably.lib.util.Log

internal val noOpCounterUpdate = LiveCounterUpdate()

internal interface HandlesLiveCounterChange {
  fun notify(update: LiveCounterUpdate)
}

internal abstract class LiveCounterChangeCoordinator: LiveCounterChange, HandlesLiveCounterChange {
  private val tag = "DefaultLiveCounterChangeCoordinator"

  private val counterChangeEmitter = LiveCounterChangeEmitter()

  override fun subscribe(listener: LiveCounterChange.Listener): ObjectsSubscription {
    counterChangeEmitter.on(listener)
    return ObjectsSubscription {
      counterChangeEmitter.off(listener)
    }
  }

  override fun unsubscribe(listener: LiveCounterChange.Listener) = counterChangeEmitter.off(listener)

  override fun unsubscribeAll() = counterChangeEmitter.off()

  override fun notify(update: LiveCounterUpdate) = counterChangeEmitter.emit(update)
}

private class LiveCounterChangeEmitter : EventEmitter<LiveCounterUpdate, LiveCounterChange.Listener>() {
  private val tag = "LiveCounterChangeEmitter"

  override fun apply(listener: LiveCounterChange.Listener?, event: LiveCounterUpdate?, vararg args: Any?) {
    try {
      listener?.onUpdated(event!!)
    } catch (t: Throwable) {
      Log.e(tag, "Error occurred while executing listener callback for event: $event", t)
    }
  }
}
