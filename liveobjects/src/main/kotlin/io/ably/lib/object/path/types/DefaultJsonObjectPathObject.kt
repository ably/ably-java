package io.ably.lib.`object`.path.types

import com.google.gson.JsonObject
import io.ably.lib.`object`.DefaultRealtimeObject
import io.ably.lib.`object`.path.DefaultPathObject

/**
 * Default implementation of [JsonObjectPathObject], a terminal primitive view that only adds
 * a type-narrowed [value]; left unimplemented for now.
 *
 * Spec: RTTS6c
 */
internal class DefaultJsonObjectPathObject(
  channelObject: DefaultRealtimeObject,
) : DefaultPathObject(channelObject), JsonObjectPathObject {

  @Suppress("RedundantNullableReturnType")
  override fun value(): JsonObject? = TODO("Not yet implemented")
}
