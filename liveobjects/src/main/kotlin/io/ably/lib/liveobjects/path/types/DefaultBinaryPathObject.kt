package io.ably.lib.liveobjects.path.types

import io.ably.lib.liveobjects.DefaultRealtimeObject
import io.ably.lib.liveobjects.ValueType
import io.ably.lib.liveobjects.path.DefaultPathObject
import io.ably.lib.liveobjects.value.ResolvedValue
import io.ably.lib.liveobjects.value.valueType
import java.util.Base64

/**
 * Default implementation of [BinaryPathObject], a terminal primitive view that only adds a
 * type-narrowed [value].
 *
 * Spec: RTTS6c
 */
internal class DefaultBinaryPathObject(
  channelObject: DefaultRealtimeObject,
  path: String,
) : DefaultPathObject(channelObject, path), BinaryPathObject {

  override fun value(): ByteArray? {
    channelObject.throwIfInvalidAccessApiConfiguration()
    val resolved = resolveValueAtPath(path) ?: return null
    if (resolved.valueType() != ValueType.BINARY) return null // RTTS6c - exact type only
    return Base64.getDecoder().decode((resolved as ResolvedValue.Leaf).data.bytes) // wire form is base64 (OD2d)
  }
}
