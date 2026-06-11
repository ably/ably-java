package io.ably.lib.`object`.instance

import com.google.gson.JsonElement
import io.ably.lib.`object`.ObjectsBridge
import io.ably.lib.`object`.ResolvedValue
import io.ably.lib.`object`.ValueType
import io.ably.lib.`object`.compactJson
import io.ably.lib.`object`.instance.types.BinaryInstance
import io.ably.lib.`object`.instance.types.BooleanInstance
import io.ably.lib.`object`.instance.types.JsonArrayInstance
import io.ably.lib.`object`.instance.types.JsonObjectInstance
import io.ably.lib.`object`.instance.types.LiveCounterInstance
import io.ably.lib.`object`.instance.types.LiveMapInstance
import io.ably.lib.`object`.instance.types.NumberInstance
import io.ably.lib.`object`.instance.types.StringInstance
import io.ably.lib.`object`.valueType

/**
 * Base implementation of the typed [Instance] hierarchy: wraps a value that
 * was already resolved against the objects graph and implements everything
 * the base class exposes per RTTS7 (compactJson, getType, the as* helpers).
 *
 * Spec: RTINS2, RTTS7, RTTS8, RTTS9
 */
internal abstract class DefaultBaseInstance(
  internal val bridge: ObjectsBridge,
  internal val value: ResolvedValue,
) : Instance {

  /** Spec: RTTS8a - never UNKNOWN in normal operation (value resolved at construction) */
  override fun getType(): ValueType = value.valueType()

  /** Spec: RTINS11, RTINS11c (non-null), RTTS7a */
  override fun compactJson(): JsonElement {
    bridge.throwIfInvalidAccessApiConfiguration() // RTINS11a / RTO25
    return value.compactJson(bridge)
  }

  // RTTS9 - as* cast helpers: re-wrap without validation, never throw (RTTS9d)
  override fun asLiveMap(): LiveMapInstance = DefaultLiveMapInstance(bridge, value) // RTTS9a
  override fun asLiveCounter(): LiveCounterInstance = DefaultLiveCounterInstance(bridge, value) // RTTS9b
  override fun asNumber(): NumberInstance = DefaultNumberInstance(bridge, value) // RTTS9c
  override fun asString(): StringInstance = DefaultStringInstance(bridge, value)
  override fun asBoolean(): BooleanInstance = DefaultBooleanInstance(bridge, value)
  override fun asBinary(): BinaryInstance = DefaultBinaryInstance(bridge, value)
  override fun asJsonObject(): JsonObjectInstance = DefaultJsonObjectInstance(bridge, value)
  override fun asJsonArray(): JsonArrayInstance = DefaultJsonArrayInstance(bridge, value)

  internal companion object {
    /**
     * Wraps an already-resolved value in the Instance subclass matching its
     * type (see e.g. RTPO8c, RTINS5c - an Instance is always constructed from
     * a resolved value).
     */
    internal fun from(bridge: ObjectsBridge, value: ResolvedValue): DefaultBaseInstance = when (value.valueType()) {
      ValueType.LIVE_MAP -> DefaultLiveMapInstance(bridge, value)
      ValueType.LIVE_COUNTER -> DefaultLiveCounterInstance(bridge, value)
      ValueType.NUMBER -> DefaultNumberInstance(bridge, value)
      ValueType.STRING -> DefaultStringInstance(bridge, value)
      ValueType.BOOLEAN -> DefaultBooleanInstance(bridge, value)
      ValueType.BINARY -> DefaultBinaryInstance(bridge, value)
      ValueType.JSON_OBJECT -> DefaultJsonObjectInstance(bridge, value)
      ValueType.JSON_ARRAY -> DefaultJsonArrayInstance(bridge, value)
      // RTTS2a9 - cannot occur for a resolved value; fall back to a map view
      ValueType.UNKNOWN -> DefaultLiveMapInstance(bridge, value)
    }
  }
}
