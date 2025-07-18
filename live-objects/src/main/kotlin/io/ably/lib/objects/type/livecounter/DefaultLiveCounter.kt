package io.ably.lib.objects.type.livecounter

import io.ably.lib.objects.*
import io.ably.lib.objects.ObjectOperation
import io.ably.lib.objects.ObjectState
import io.ably.lib.objects.type.BaseLiveObject
import io.ably.lib.objects.type.ObjectType
import io.ably.lib.types.Callback

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
   * Counter data value
   */
  internal var data: Long = 0 // RTLC3

  /**
   * liveCounterManager instance for managing LiveMap operations
   */
  private val liveCounterManager = LiveCounterManager(this)

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

  override fun value(): Long {
    TODO("Not yet implemented")
  }

  override fun applyObjectState(objectState: ObjectState): Map<String, Long> {
    return liveCounterManager.applyState(objectState)
  }

  override fun applyObjectOperation(operation: ObjectOperation, message: ObjectMessage) {
    liveCounterManager.applyOperation(operation)
  }

  override fun clearData(): Map<String, Long> {
    return mapOf("amount" to data).apply { data = 0 }
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
