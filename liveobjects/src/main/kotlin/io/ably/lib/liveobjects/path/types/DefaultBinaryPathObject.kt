package io.ably.lib.liveobjects.path.types

import io.ably.lib.liveobjects.DefaultRealtimeObject
import io.ably.lib.liveobjects.ValueType
import io.ably.lib.liveobjects.path.DefaultPathObject
import io.ably.lib.liveobjects.value.valueType

/**
 * Default implementation of [BinaryPathObject], a terminal primitive view that only adds a
 * type-narrowed [value]; left unimplemented for now.
 *
 * Spec: RTTS6c
 */
internal class DefaultBinaryPathObject(
  channelObject: DefaultRealtimeObject,
  path: String,
) : DefaultPathObject(channelObject, path), BinaryPathObject {

  override fun value(): ByteArray? {
    channelObject.throwIfInvalidAccessApiConfiguration()
    if (resolveValueAtPath(path)?.valueType() != ValueType.BINARY) return null // not a Binary value at this path -> no value
    // TODO - extract the primitive value from the resolved leaf, narrowed to ByteArray (base64-decoded)
    TODO("Not yet implemented")
  }
}
