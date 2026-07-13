package io.ably.lib.liveobjects.instance.types

import com.google.gson.JsonObject
import io.ably.lib.liveobjects.DefaultRealtimeObject
import io.ably.lib.liveobjects.ValueType
import io.ably.lib.liveobjects.instance.DefaultInstance

/**
 * Default implementation of [JsonObjectInstance], a read-only primitive view bound to its
 * extracted value (RTINS2a).
 *
 * Spec: RTTS10c
 */
internal class DefaultJsonObjectInstance(
  channelObject: DefaultRealtimeObject,
  internal val value: JsonObject,
) : DefaultInstance(channelObject), JsonObjectInstance {

  override fun getType(): ValueType = ValueType.JSON_OBJECT

  override fun compactJson(): JsonObject {
    channelObject.throwIfInvalidAccessApiConfiguration() // RTINS11a
    return value // RTTS7a1
  }

  override fun asJsonObject(): JsonObjectInstance = this

  override fun value(): JsonObject {
    channelObject.throwIfInvalidAccessApiConfiguration() // RTINS4a
    return value // RTINS4c; RTTS10c non-null
  }
}
