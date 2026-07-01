package io.ably.lib.liveobjects.value.livecounter

import io.ably.lib.liveobjects.*
import io.ably.lib.liveobjects.DefaultRealtimeObject
import io.ably.lib.liveobjects.adapter.AblyClientAdapter
import io.ably.lib.liveobjects.invalidInputError
import io.ably.lib.liveobjects.message.*
import io.ably.lib.liveobjects.message.WireCounterInc
import io.ably.lib.liveobjects.message.WireObjectMessage
import io.ably.lib.liveobjects.message.WireObjectOperation
import io.ably.lib.liveobjects.message.WireObjectOperationAction
import io.ably.lib.liveobjects.message.WireObjectState
import io.ably.lib.liveobjects.throwIfInvalidAccessApiConfiguration
import io.ably.lib.liveobjects.throwIfInvalidWriteApiConfiguration
import io.ably.lib.liveobjects.value.BaseRealtimeObject
import io.ably.lib.liveobjects.value.ObjectType
import io.ably.lib.liveobjects.value.ObjectUpdate
import io.ably.lib.liveobjects.value.noOp
import java.util.concurrent.atomic.AtomicReference
import io.ably.lib.util.Log

/**
 * @spec RTLC1/RTLC2 - LiveCounter implementation extends BaseRealtimeObject
 */
internal class InternalLiveCounter private constructor(
  objectId: String,
  private val realtimeObjects: DefaultRealtimeObject,
) : BaseRealtimeObject(objectId, ObjectType.Counter, realtimeObjects.clock) {

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
  private val adapter: AblyClientAdapter get() = realtimeObjects.adapter

  suspend fun increment(amount: Number) = incrementAsync(amount.toDouble())

  suspend fun decrement(amount: Number) = incrementAsync(-amount.toDouble())

  fun value(): Double {
    adapter.throwIfInvalidAccessApiConfiguration(channelName)
    return data.get()
  }

  fun subscribe(listener: LiveCounterChangeListener): Subscription {
    adapter.throwIfInvalidAccessApiConfiguration(channelName)
    return liveCounterManager.subscribe(listener)
  }

  override fun validate(state: WireObjectState) = liveCounterManager.validate(state)

  private suspend fun incrementAsync(amount: Double) {
    // RTLC12b, RTLC12c, RTLC12d - Validate write API configuration
    adapter.throwIfInvalidWriteApiConfiguration(channelName)

    // RTLC12e1 - Validate input parameter
    if (amount.isNaN() || amount.isInfinite()) {
      throw invalidInputError("Counter value increment should be a valid number")
    }

    // RTLC12e2, RTLC12e3, RTLC12e4 - Create ObjectMessage with the COUNTER_INC operation
    val msg = WireObjectMessage(
      operation = WireObjectOperation(
        action = WireObjectOperationAction.CounterInc,
        objectId = objectId,
        counterInc = WireCounterInc(number = amount)
      )
    )

    // RTLC12g - publish and apply locally on ACK
    realtimeObjects.publishAndApply(arrayOf(msg))
  }

  override fun applyObjectState(wireObjectState: WireObjectState, message: WireObjectMessage): ObjectUpdate {
    return liveCounterManager.applyState(wireObjectState, message.serialTimestamp)
  }

  override fun applyObjectOperation(operation: WireObjectOperation, message: WireObjectMessage): Boolean {
    return liveCounterManager.applyOperation(operation, message.serialTimestamp)
  }

  override fun clearData(): ObjectUpdate {
    return liveCounterManager.calculateUpdateFromDataDiff(data.get(), 0.0).apply { this@InternalLiveCounter.data.set(0.0) }
  }

  override fun notifyUpdated(update: ObjectUpdate) {
    if (update.noOp) {
      return
    }
    Log.v(tag, "Object $objectId updated: $update")

    // TODO - Current cast for emitting event is wrong, need to fix the same.
    liveCounterManager.notify(update as LiveCounterChangeEvent)
  }

  override fun onGCInterval(gcGracePeriod: Long) {
    // Nothing to GC for a counter object
    return
  }

  companion object {
    /**
     * Creates a zero-value counter object.
     * @spec RTLC4 - Returns LiveCounter with 0 value
     */
    internal fun zeroValue(objectId: String, realtimeObjects: DefaultRealtimeObject): InternalLiveCounter {
      return InternalLiveCounter(objectId, realtimeObjects)
    }

    /**
     * Creates initial value payload for counter creation.
     * Spec: RTO12f12
     */
    internal fun initialValue(count: Number): WireCounterCreate {
      return WireCounterCreate(count = count.toDouble())
    }
  }
}
