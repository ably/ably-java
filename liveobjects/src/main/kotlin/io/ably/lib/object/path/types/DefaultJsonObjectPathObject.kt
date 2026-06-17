package io.ably.lib.`object`.path.types

import com.google.gson.JsonObject
import io.ably.lib.`object`.DefaultRealtimeObject
import io.ably.lib.`object`.path.DefaultPathObject
import io.ably.lib.`object`.value.ResolvedValue

/**
 * Default implementation of [JsonObjectPathObject], a terminal primitive view that only adds
 * a type-narrowed [value]; left unimplemented for now.
 *
 * Spec: RTTS6c
 */
internal class DefaultJsonObjectPathObject(
  channelObject: DefaultRealtimeObject,
  path: String,
) : DefaultPathObject(channelObject, path), JsonObjectPathObject {

  override fun value(): JsonObject? {
    if (resolveValueAtPath(path) !is ResolvedValue.Leaf) return null // live object or unresolved -> no primitive value
    // TODO - extract the primitive value from the resolved leaf, narrowed to JsonObject
    TODO("Not yet implemented")
  }
}
