package io.ably.lib.liveobjects.instance.types

import com.google.gson.JsonObject
import io.ably.lib.liveobjects.DefaultRealtimeObject
import io.ably.lib.liveobjects.ValueType
import io.ably.lib.liveobjects.instance.DefaultInstance

/**
 * Default implementation of [JsonObjectInstance], a read-only primitive view that only adds
 * a type-narrowed, non-null [value]; left unimplemented for now.
 *
 * Spec: RTTS10c
 */
internal class DefaultJsonObjectInstance(
  channelObject: DefaultRealtimeObject,
) : DefaultInstance(channelObject), JsonObjectInstance {

  override fun getType(): ValueType = ValueType.JSON_OBJECT

  override fun compactJson(): JsonObject {
    channelObject.throwIfInvalidAccessApiConfiguration()
    TODO("Not yet implemented")
  }

  override fun asJsonObject(): JsonObjectInstance = this

  override fun value(): JsonObject {
    channelObject.throwIfInvalidAccessApiConfiguration()
    TODO("Not yet implemented")
  }
}
