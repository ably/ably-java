package io.ably.lib.`object`.path.types

import com.google.gson.JsonArray
import io.ably.lib.`object`.DefaultRealtimeObject
import io.ably.lib.`object`.path.DefaultPathObject
import io.ably.lib.`object`.value.ResolvedValue

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

  override fun value(): JsonArray? {
    channelObject.throwIfInvalidAccessApiConfiguration()
    if (resolveValueAtPath(path) !is ResolvedValue.Leaf) return null // live object or unresolved -> no primitive value
    // TODO - extract the primitive value from the resolved leaf, narrowed to JsonArray
    TODO("Not yet implemented")
  }
}
