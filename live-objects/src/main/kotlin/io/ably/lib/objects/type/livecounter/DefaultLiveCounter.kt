package io.ably.lib.objects.type.livecounter

import io.ably.lib.objects.*
import io.ably.lib.objects.ObjectOperation
import io.ably.lib.objects.ObjectState
import io.ably.lib.objects.type.BaseLiveObject
import io.ably.lib.objects.type.ObjectType
import io.ably.lib.types.Callback
import java.util.concurrent.atomic.AtomicReference

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

  override fun incrementAsync(callback: Callback<Void>) {
    TODO("Not yet implemented")
  }

  override fun decrement() {
    TODO("Not yet implemented")
  }

  override fun decrementAsync(callback: Callback<Void>) {
    TODO("Not yet implemented")
  }

  override fun value(): Double {
    TODO("Not yet implemented")
  }

  override fun validate(state: ObjectState) = liveCounterManager.validate(state)

  override fun applyObjectState(objectState: ObjectState): Map<String, Double> {
    return liveCounterManager.applyState(objectState)
  }

  override fun applyObjectOperation(operation: ObjectOperation, message: ObjectMessage) {
    liveCounterManager.applyOperation(operation)
  }

  override fun clearData(): Map<String, Double> {
    return mapOf("amount" to data.get()).apply { data.set(0.0) }
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
