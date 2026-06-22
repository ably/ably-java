package io.ably.lib.`object`.value

/**
 * Default implementation of the [LiveMap] value type - an immutable holder for the
 * initial entries of a LiveMap object to be created. Mirrors ably-js
 * `LiveMapValueType`.
 *
 * Instantiated reflectively by [LiveMap.create] through the constructor that takes
 * the initial entries map; the entries are retained internally with no public
 * accessor (Spec: RTLMV3d).
 *
 * This is currently a skeleton: it only retains the initial value. Producing the
 * `MAP_CREATE` operation/message from these entries (including nested object create
 * messages for nested [LiveMap]/[LiveCounter] value types) is not yet implemented.
 *
 * Spec: RTLMV1, RTLMV2, RTLMV3
 */
internal class DefaultLiveMap(
  internal val entries: Map<String, LiveMapValue>,
) : LiveMap() {
  // TODO - build the MAP_CREATE ObjectMessage (plus nested object create messages)
  //  from `entries`, mirroring ably-js LiveMapValueType.createMapCreateMessage.
  //  Spec: RTO11f
}
