package io.ably.lib.liveobjects.path.types

import com.google.gson.JsonArray
import io.ably.lib.liveobjects.DefaultRealtimeObject
import io.ably.lib.liveobjects.ValueType
import io.ably.lib.liveobjects.path.DefaultPathObject
import io.ably.lib.liveobjects.value.valueType

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
    if (resolveValueAtPath(path)?.valueType() != ValueType.JSON_ARRAY) return null // not a JSON array at this path -> no value
    // TODO - extract the primitive value from the resolved leaf, narrowed to JsonArray
    TODO("Not yet implemented")
  }
}
