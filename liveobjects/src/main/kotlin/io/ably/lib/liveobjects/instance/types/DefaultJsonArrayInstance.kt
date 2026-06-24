package io.ably.lib.liveobjects.instance.types

import com.google.gson.JsonArray
import io.ably.lib.liveobjects.DefaultRealtimeObject
import io.ably.lib.liveobjects.ValueType
import io.ably.lib.liveobjects.instance.DefaultInstance

/**
 * Default implementation of [JsonArrayInstance], a read-only primitive view that only adds
 * a type-narrowed, non-null [value]; left unimplemented for now.
 *
 * Spec: RTTS10c
 */
internal class DefaultJsonArrayInstance(
  channelObject: DefaultRealtimeObject,
) : DefaultInstance(channelObject), JsonArrayInstance {

  override fun getType(): ValueType = ValueType.JSON_ARRAY

  override fun compactJson(): JsonArray {
    channelObject.throwIfInvalidAccessApiConfiguration()
    TODO("Not yet implemented")
  }

  override fun asJsonArray(): JsonArrayInstance = this

  override fun value(): JsonArray {
    channelObject.throwIfInvalidAccessApiConfiguration()
    TODO("Not yet implemented")
  }
}
