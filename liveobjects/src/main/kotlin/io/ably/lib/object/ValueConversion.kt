package io.ably.lib.`object`

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.util.Base64

/**
 * The result of resolving a path segment / map entry against the objects
 * graph: either a node view of a live object, or a primitive leaf carried as
 * wire ObjectData.
 */
internal sealed interface ResolvedValue {
  data class MapRef(val map: MapNode) : ResolvedValue
  data class CounterRef(val counter: CounterNode) : ResolvedValue
  data class Leaf(val data: WireObjectData) : ResolvedValue
}

/**
 * Resolves entry data to its value: a node view when it references another
 * object (null when the reference cannot be resolved), or a primitive leaf.
 *
 * Spec: RTLM5d2 (resolution semantics)
 */
internal fun WireObjectData.resolve(bridge: ObjectsBridge): ResolvedValue? {
  objectId?.let { refId ->
    return when (val refNode = bridge.getNode(refId)) {
      is MapNode -> ResolvedValue.MapRef(refNode)
      is CounterNode -> ResolvedValue.CounterRef(refNode)
      else -> null
    }
  }
  return ResolvedValue.Leaf(this)
}

/**
 * Maps a resolved value to the public ValueType enum.
 *
 * Spec: RTTS2a, RTTS4b3
 */
internal fun ResolvedValue?.valueType(): ValueType = when (this) {
  null -> ValueType.UNKNOWN
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

/** Decodes the wire base64 binary leaf value, if present. */
internal fun WireObjectData.decodedBytes(): ByteArray? = bytes?.let { Base64.getDecoder().decode(it) }

/**
 * Builds the compact JSON representation of a resolved value.
 *
 * - Map node: JSON object of its entries, recursively compacted; on
 *   revisiting an object (cyclic graph) a `{"objectId": ...}` stub is emitted
 *   instead of recursing.
 * - Counter node: its numeric value.
 * - Primitive leaves: the value itself; binary as a base64 string.
 *
 * Spec: RTPO14 / RTINS11 (mirrors ably-js livemap.ts#compactJson)
 */
internal fun ResolvedValue.compactJson(bridge: ObjectsBridge): JsonElement = when (this) {
  is ResolvedValue.CounterRef -> JsonPrimitive(counter.count())
  is ResolvedValue.Leaf -> data.leafJson()
  is ResolvedValue.MapRef -> map.compactJson(bridge, visited = mutableSetOf())
}

private fun MapNode.compactJson(bridge: ObjectsBridge, visited: MutableSet<String>): JsonElement {
  visited.add(objectId)
  val result = JsonObject()
  for ((key, data) in entries()) {
    when (val resolved = data.resolve(bridge)) {
      null -> Unit
      is ResolvedValue.CounterRef -> result.add(key, JsonPrimitive(resolved.counter.count()))
      is ResolvedValue.Leaf -> result.add(key, resolved.data.leafJson())
      is ResolvedValue.MapRef ->
        if (resolved.map.objectId in visited) {
          // cycle - emit a reference stub instead of recursing forever
          result.add(key, JsonObject().apply { addProperty("objectId", resolved.map.objectId) })
        } else {
          result.add(key, resolved.map.compactJson(bridge, visited))
        }
    }
  }
  visited.remove(objectId)
  return result
}

private fun WireObjectData.leafJson(): JsonElement = when {
  string != null -> JsonPrimitive(string)
  number != null -> JsonPrimitive(number)
  boolean != null -> JsonPrimitive(boolean)
  bytes != null -> JsonPrimitive(bytes) // already base64; compact JSON carries binary as base64 string
  json != null -> json!!
  else -> JsonObject()
}
