package io.ably.lib.`object`.path.types

import com.google.gson.JsonObject
import io.ably.lib.`object`.DefaultRealtimeObject
import io.ably.lib.`object`.ValueType
import io.ably.lib.`object`.path.DefaultPathObject
import io.ably.lib.`object`.value.valueType

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
    channelObject.throwIfInvalidAccessApiConfiguration()
    if (resolveValueAtPath(path)?.valueType() != ValueType.JSON_OBJECT) return null // not a JSON object at this path -> no value
    // TODO - extract the primitive value from the resolved leaf, narrowed to JsonObject
    TODO("Not yet implemented")
  }
}
