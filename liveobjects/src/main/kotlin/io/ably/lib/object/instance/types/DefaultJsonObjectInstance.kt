package io.ably.lib.`object`.instance.types

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.ably.lib.`object`.DefaultRealtimeObject
import io.ably.lib.`object`.ValueType
import io.ably.lib.`object`.instance.DefaultInstance

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

  override fun compactJson(): JsonElement = TODO("Not yet implemented")

  override fun asJsonObject(): JsonObjectInstance = this

  override fun value(): JsonObject = TODO("Not yet implemented")
}
