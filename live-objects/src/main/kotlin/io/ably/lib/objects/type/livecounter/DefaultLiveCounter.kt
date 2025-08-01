package io.ably.lib.objects.type.livecounter

import io.ably.lib.objects.*
import io.ably.lib.objects.ObjectOperation
import io.ably.lib.objects.ObjectState
import io.ably.lib.objects.type.BaseLiveObject
import io.ably.lib.objects.type.LiveObjectUpdate
import io.ably.lib.objects.type.ObjectType
import io.ably.lib.objects.type.counter.LiveCounter
import io.ably.lib.objects.type.counter.LiveCounterChange
import io.ably.lib.objects.type.counter.LiveCounterUpdate
import io.ably.lib.objects.type.noOp
import java.util.concurrent.atomic.AtomicReference
import io.ably.lib.util.Log

/**
 * Implementation of LiveObject for LiveCounter.
 *
 * @spec RTLC1/RTLC2 - LiveCounter implementation extends LiveObject
 */
internal class DefaultLiveCounter private constructor(
  objectId: String,
  private val liveObjects: DefaultLiveObjects,
) : LiveCounter, BaseLiveObject(objectId, ObjectType.Counter) {

  override val tag = "LiveCounter"

  /**
   * Thread-safe reference to hold the counter data value.
   * Accessed from public API for LiveCounter and updated by LiveCounterManager.
   */
  internal val data = AtomicReference<Double>(0.0) // RTLC3

  /**
   * liveCounterManager instance for managing LiveMap operations
   */
  private val liveCounterManager = LiveCounterManager(this)

  private val channelName = liveObjects.channelName
  private val adapter: LiveObjectsAdapter get() = liveObjects.adapter

  override fun increment() {
    TODO("Not yet implemented")
  }

  override fun incrementAsync(callback: ObjectsCallback<Void>) {
    TODO("Not yet implemented")
  }

  override fun decrement() {
    TODO("Not yet implemented")
  }

  override fun decrementAsync(callback: ObjectsCallback<Void>) {
    TODO("Not yet implemented")
  }

  override fun value(): Double {
    adapter.throwIfInvalidAccessApiConfiguration(channelName)
    return data.get()
  }

  override fun subscribe(listener: LiveCounterChange.Listener): ObjectsSubscription {
    adapter.throwIfInvalidAccessApiConfiguration(channelName)
    return liveCounterManager.subscribe(listener)
  }

  override fun unsubscribe(listener: LiveCounterChange.Listener) = liveCounterManager.unsubscribe(listener)

  override fun unsubscribeAll() = liveCounterManager.unsubscribeAll()

  override fun validate(state: ObjectState) = liveCounterManager.validate(state)

  override fun applyObjectState(objectState: ObjectState, message: ObjectMessage): LiveCounterUpdate {
    return liveCounterManager.applyState(objectState, message.serialTimestamp)
  }

  override fun applyObjectOperation(operation: ObjectOperation, message: ObjectMessage) {
    liveCounterManager.applyOperation(operation, message.serialTimestamp)
  }

  override fun clearData(): LiveCounterUpdate {
    return LiveCounterUpdate(data.get()).apply { data.set(0.0) }
  }

  override fun notifyUpdated(update: LiveObjectUpdate) {
    if (update.noOp) {
      return
    }
    Log.v(tag, "Object $objectId updated: $update")
    liveCounterManager.notify(update as LiveCounterUpdate)
  }

  override fun onGCInterval() {
    // Nothing to GC for a counter object
    return
  }

  companion object {
    /**
     * Creates a zero-value counter object.
     * @spec RTLC4 - Returns LiveCounter with 0 value
     */
    internal fun zeroValue(objectId: String, liveObjects: DefaultLiveObjects): DefaultLiveCounter {
      return DefaultLiveCounter(objectId, liveObjects)
    }
  }
}
