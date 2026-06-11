package io.ably.lib.`object`

import io.ably.lib.`object`.value.LiveCounter

/**
 * Implementation of the [LiveCounter] value type: an immutable holder for the
 * initial count of a new LiveCounter object to be created by a mutation.
 *
 * Instantiated reflectively by `io.ably.lib.object.value.LiveCounter#create` —
 * the class name and the single `(java.lang.Number)` constructor are a frozen
 * contract with the `lib` module and must not change.
 *
 * Spec: RTLCV1, RTLCV2a, RTLCV3b, RTLCV3d
 */
public class DefaultLiveCounter(count: Number) : LiveCounter() {
  /** Internal initial count (RTLCV2a). */
  internal val count: Number = count
}
