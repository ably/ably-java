package io.ably.lib.liveobjects.path.types

import com.google.gson.JsonObject
import io.ably.lib.liveobjects.DefaultRealtimeObject
import io.ably.lib.liveobjects.ValueType
import io.ably.lib.liveobjects.path.DefaultPathObject
import io.ably.lib.liveobjects.value.ResolvedValue
import io.ably.lib.liveobjects.value.valueType

/**
 * Default implementation of [JsonObjectPathObject], a terminal primitive view that only adds
 * a type-narrowed [value].
 *
 * Spec: RTTS6c
 */
internal class DefaultJsonObjectPathObject(
  channelObject: DefaultRealtimeObject,
  path: String,
) : DefaultPathObject(channelObject, path), JsonObjectPathObject {

  override fun value(): JsonObject? {
    channelObject.throwIfInvalidAccessApiConfiguration()
    val resolved = resolveValueAtCurrentPath() ?: return null
    if (resolved.valueType() != ValueType.JSON_OBJECT) return null // RTTS6c - exact type only
    return (resolved as ResolvedValue.Leaf).data.json!!.asJsonObject
  }
}
