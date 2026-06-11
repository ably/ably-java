package io.ably.lib.`object`

/**
 * Abstract view over the live objects graph, implemented by the bridge that
 * connects this package to the realtime objects system (kept abstract so this
 * package has no dependency on `io.ably.lib.objects`).
 *
 * Contract for implementations:
 * - tombstoned objects are never returned by [ObjectsBridge.getNode] /
 *   [ObjectsBridge.getRootNode];
 * - [MapNode.entries] / [MapNode.get] expose only non-tombstoned entries that
 *   carry data; values referencing other objects carry `objectId` in their
 *   [WireObjectData].
 */
internal interface ObjectsNode {
  val objectId: String
}

/** View over an InternalLiveMap (RTLM1). */
internal interface MapNode : ObjectsNode {
  /** Snapshot of the current non-tombstoned entries. */
  fun entries(): Map<String, WireObjectData>

  /** The current non-tombstoned entry for [key], or null. */
  fun get(key: String): WireObjectData?
}

/** View over an InternalLiveCounter (RTLC1). */
internal interface CounterNode : ObjectsNode {
  /** The current counter value (RTLC5). */
  fun count(): Double
}
