package io.ably.lib.`object`.value

import io.ably.lib.`object`.ValueType
import io.ably.lib.`object`.message.WireObjectData

/**
 * The result of resolving a path segment / map entry against the objects
 * graph: either a node view of a live object, or a primitive leaf carried as
 * wire ObjectData.
 */
internal sealed interface ResolvedValue {
  data class MapRef(val map: LiveMap) : ResolvedValue // TODO: LiveMap will be replaced by InternalLiveMap
  data class CounterRef(val counter: LiveCounter) : ResolvedValue // TODO: LiveCounter will be replaced by InternalLiveCounter
  data class Leaf(val data: WireObjectData) : ResolvedValue
}

/**
 * Maps a resolved value to the public ValueType enum.
 *
 * Only ever invoked on a value that resolved to something - absence at a path is
 * represented by a `null` [ResolvedValue] and surfaced as a `null` type by the
 * caller, never as [ValueType.UNKNOWN]. UNKNOWN is reserved for a value that is
 * present but matches none of the known categories.
 *
 * Spec: RTTS2a, RTTS4b3
 */
internal fun ResolvedValue.valueType(): ValueType = when (this) {
  is ResolvedValue.MapRef -> ValueType.LIVE_MAP
  is ResolvedValue.CounterRef -> ValueType.LIVE_COUNTER
  is ResolvedValue.Leaf -> when {
    data.string != null -> ValueType.STRING
    data.number != null -> ValueType.NUMBER
    data.boolean != null -> ValueType.BOOLEAN
    data.bytes != null -> ValueType.BINARY
    data.json?.isJsonObject == true -> ValueType.JSON_OBJECT
    data.json?.isJsonArray == true -> ValueType.JSON_ARRAY
    else -> ValueType.UNKNOWN
  }
}
