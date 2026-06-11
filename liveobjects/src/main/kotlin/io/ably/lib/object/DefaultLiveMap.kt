package io.ably.lib.`object`

import io.ably.lib.`object`.value.LiveMap
import io.ably.lib.`object`.value.LiveMapValue

/**
 * Implementation of the [LiveMap] value type: an immutable holder for the
 * initial entries of a new LiveMap object to be created by a mutation.
 *
 * Instantiated reflectively by `io.ably.lib.object.value.LiveMap#create` —
 * the class name and the single `(java.util.Map)` constructor are a frozen
 * contract with the `lib` module and must not change.
 *
 * Spec: RTLMV1, RTLMV2a, RTLMV3b, RTLMV3d
 */
public class DefaultLiveMap(entries: Map<String, LiveMapValue>) : LiveMap() {
  /** Internal initial entries (RTLMV2a); defensively copied for immutability (RTLMV3d). */
  internal val entries: Map<String, LiveMapValue> = HashMap(entries)
}
