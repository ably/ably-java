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
import io.ably.lib.types.Callback
import java.util.concurrent.atomic.AtomicReference
import io.ably.lib.util.Log
import kotlinx.coroutines.runBlocking

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
  private val asyncScope get() = liveObjects.asyncScope

  override fun increment(amount: Number) = runBlocking { incrementAsync(amount.toDouble()) }

  override fun decrement(amount: Number) = runBlocking { incrementAsync(-amount.toDouble()) }

  override fun incrementAsync(amount: Number, callback: Callback<Void>) {
    asyncScope.launchWithVoidCallback(callback) { incrementAsync(amount.toDouble()) }
  }

  override fun decrementAsync(amount: Number, callback: Callback<Void>) {
    asyncScope.launchWithVoidCallback(callback) { incrementAsync(-amount.toDouble()) }
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

  private suspend fun incrementAsync(amount: Double) {
    // Validate write API configuration
    adapter.throwIfInvalidWriteApiConfiguration(channelName)

    // Validate input parameter
    if (amount.isNaN() || amount.isInfinite()) {
      throw objectError("Counter value increment should be a valid number")
    }

    // Create ObjectMessage with the COUNTER_INC operation
    val msg = ObjectMessage(
      operation = ObjectOperation(
        action = ObjectOperationAction.CounterInc,
        objectId = objectId,
        counterOp = ObjectCounterOp(amount = amount)
      )
    )

    // Publish the message
    liveObjects.publish(arrayOf(msg))
  }

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

    /**
     * Creates initial value operation for counter creation.
     */
    internal fun initialValue(count: Number): CounterCreatePayload {
      return CounterCreatePayload(
        counter = ObjectCounter(count = count.toDouble())
      )
    }
  }
}
