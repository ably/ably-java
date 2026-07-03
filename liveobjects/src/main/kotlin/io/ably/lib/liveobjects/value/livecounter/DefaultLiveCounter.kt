package io.ably.lib.liveobjects.value.livecounter

import io.ably.lib.liveobjects.DefaultRealtimeObject
import io.ably.lib.liveobjects.invalidInputError
import io.ably.lib.liveobjects.message.WireCounterCreate
import io.ably.lib.liveobjects.message.WireCounterCreateWithObjectId
import io.ably.lib.liveobjects.message.WireObjectMessage
import io.ably.lib.liveobjects.message.WireObjectOperation
import io.ably.lib.liveobjects.message.WireObjectOperationAction
import io.ably.lib.liveobjects.serialization.gson
import io.ably.lib.liveobjects.value.LiveCounter
import io.ably.lib.liveobjects.value.ObjectType

/**
 * Default implementation of the [LiveCounter] value type - an immutable holder for
 * the initial count of a LiveCounter object to be created. Mirrors ably-js
 * `LiveCounterValueType`.
 *
 * Instantiated reflectively by [LiveCounter.create] through the constructor that
 * takes the initial count; the count is retained internally with no public accessor
 * (Spec: RTLCV3d).
 *
 * Spec: RTLCV1, RTLCV2, RTLCV3
 */
internal class DefaultLiveCounter(
  internal val initialCount: Number,
) : LiveCounter() {

  /**
   * Evaluates this value type into a COUNTER_CREATE ObjectMessage. Mirrors ably-js
   * `LiveCounterValueType.createCounterCreateMessage`. The caller publishes the message
   * (evaluation itself has no side effects on the channel).
   *
   * Spec: RTLCV4
   */
  internal suspend fun createCounterCreateMessage(realtimeObject: DefaultRealtimeObject): WireObjectMessage {
    // RTLCV4a - validation deferred from create() to evaluation time
    if (initialCount.toDouble().isNaN() || initialCount.toDouble().isInfinite()) {
      throw invalidInputError("Counter value should be a valid number")
    }

    val counterCreate = WireCounterCreate(count = initialCount.toDouble()) // RTLCV4b, RTLCV4b1
    val initialValueJSONString = gson.toJson(counterCreate) // RTLCV4c
    // RTLCV4d..f - nonce, server time and RTO14 objectId derivation
    val (objectId, nonce) = realtimeObject.getObjectIdStringWithNonce(ObjectType.Counter, initialValueJSONString)

    return WireObjectMessage(
      operation = WireObjectOperation(
        action = WireObjectOperationAction.CounterCreate, // RTLCV4g1
        objectId = objectId, // RTLCV4g2
        counterCreateWithObjectId = WireCounterCreateWithObjectId(
          nonce = nonce, // RTLCV4g3
          initialValue = initialValueJSONString, // RTLCV4g4
          derivedFrom = counterCreate, // RTLCV4g5 - local use only (@Transient on the wire type)
        ),
      )
    ) // RTLCV4h
  }
}
