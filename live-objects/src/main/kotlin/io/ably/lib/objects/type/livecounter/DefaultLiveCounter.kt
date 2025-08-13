package io.ably.lib.objects.type.livecounter

import io.ably.lib.objects.*
import io.ably.lib.objects.ObjectOperation
import io.ably.lib.objects.ObjectState
import io.ably.lib.objects.type.BaseRealtimeObject
import io.ably.lib.objects.type.ObjectUpdate
import io.ably.lib.objects.type.ObjectType
import io.ably.lib.objects.type.counter.LiveCounter
import io.ably.lib.objects.type.counter.LiveCounterChange
import io.ably.lib.objects.type.counter.LiveCounterUpdate
import io.ably.lib.objects.type.noOp
import java.util.concurrent.atomic.AtomicReference
import io.ably.lib.util.Log
import kotlinx.coroutines.runBlocking

/**
 * @spec RTLC1/RTLC2 - LiveCounter implementation extends BaseRealtimeObject
 */
internal class DefaultLiveCounter private constructor(
  objectId: String,
  private val realtimeObjects: DefaultRealtimeObjects,
) : LiveCounter, BaseRealtimeObject(objectId, ObjectType.Counter) {

  override val tag = "LiveCounter"

  /**
   * Thread-safe reference to hold the counter data value.
   * Accessed from public API for LiveCounter and updated by LiveCounterManager.
   */
  internal val data = AtomicReference<Double>(0.0) // RTLC3

  /**
   * liveCounterManager instance for managing LiveCounter operations
   */
  private val liveCounterManager = LiveCounterManager(this)

  private val channelName = realtimeObjects.channelName
  private val adapter: ObjectsAdapter get() = realtimeObjects.adapter
  private val asyncScope get() = realtimeObjects.asyncScope

  override fun increment(amount: Number) = runBlocking { incrementAsync(amount.toDouble()) }

  override fun decrement(amount: Number) = runBlocking { incrementAsync(-amount.toDouble()) }

  override fun incrementAsync(amount: Number, callback: ObjectsCallback<Void>) {
    asyncScope.launchWithVoidCallback(callback) { incrementAsync(amount.toDouble()) }
  }

  override fun decrementAsync(amount: Number, callback: ObjectsCallback<Void>) {
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
    // RTLC12b, RTLC12c, RTLC12d - Validate write API configuration
    adapter.throwIfInvalidWriteApiConfiguration(channelName)

    // RTLC12e1 - Validate input parameter
    if (amount.isNaN() || amount.isInfinite()) {
      throw invalidInputError("Counter value increment should be a valid number")
    }

    // RTLC12e2, RTLC12e3, RTLC12e4 - Create ObjectMessage with the COUNTER_INC operation
    val msg = ObjectMessage(
      operation = ObjectOperation(
        action = ObjectOperationAction.CounterInc,
        objectId = objectId,
        counterOp = ObjectsCounterOp(amount = amount)
      )
    )

    // RTLC12f - Publish the message
    realtimeObjects.publish(arrayOf(msg))
  }

  override fun applyObjectState(objectState: ObjectState, message: ObjectMessage): LiveCounterUpdate {
    return liveCounterManager.applyState(objectState, message.serialTimestamp)
  }

  override fun applyObjectOperation(operation: ObjectOperation, message: ObjectMessage) {
    liveCounterManager.applyOperation(operation, message.serialTimestamp)
  }

  override fun clearData(): LiveCounterUpdate {
    return liveCounterManager.calculateUpdateFromDataDiff(data.get(), 0.0).apply { data.set(0.0) }
  }

  override fun notifyUpdated(update: ObjectUpdate) {
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
    internal fun zeroValue(objectId: String, realtimeObjects: DefaultRealtimeObjects): DefaultLiveCounter {
      return DefaultLiveCounter(objectId, realtimeObjects)
    }

    /**
     * Creates initial value operation for counter creation.
     * Spec: RTO12f2
     */
    internal fun initialValue(count: Number): CounterCreatePayload {
      return CounterCreatePayload(
        counter = ObjectsCounter(count = count.toDouble())
      )
    }
  }
}
