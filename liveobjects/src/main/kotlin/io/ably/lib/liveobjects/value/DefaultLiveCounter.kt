package io.ably.lib.liveobjects.value

/**
 * Default implementation of the [LiveCounter] value type - an immutable holder for
 * the initial count of a LiveCounter object to be created. Mirrors ably-js
 * `LiveCounterValueType`.
 *
 * Instantiated reflectively by [LiveCounter.create] through the constructor that
 * takes the initial count; the count is retained internally with no public accessor
 * (Spec: RTLCV3d).
 *
 * This is currently a skeleton: it only retains the initial value. Producing the
 * `COUNTER_CREATE` operation/message from this count is not yet implemented.
 *
 * Spec: RTLCV1, RTLCV2, RTLCV3
 */
internal class DefaultLiveCounter(
  internal val initialCount: Number,
) : LiveCounter() {
  // TODO - build the COUNTER_CREATE ObjectMessage from `initialCount`, mirroring
  //  ably-js LiveCounterValueType.createCounterCreateMessage. Spec: RTO12f
}
