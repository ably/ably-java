package io.ably.lib.`object`.path.types

import com.google.gson.JsonArray
import io.ably.lib.`object`.DefaultRealtimeObject
import io.ably.lib.`object`.path.DefaultPathObject

/**
 * Default implementation of [JsonArrayPathObject], a terminal primitive view that only adds
 * a type-narrowed [value]; left unimplemented for now.
 *
 * Spec: RTTS6c
 */
internal class DefaultJsonArrayPathObject(
  channelObject: DefaultRealtimeObject,
  path: String,
) : DefaultPathObject(channelObject, path), JsonArrayPathObject {

  @Suppress("RedundantNullableReturnType")
  override fun value(): JsonArray? = TODO("Not yet implemented")
}
