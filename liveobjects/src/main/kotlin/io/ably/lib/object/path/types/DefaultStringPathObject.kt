package io.ably.lib.`object`.path.types

import io.ably.lib.`object`.DefaultRealtimeObject
import io.ably.lib.`object`.ValueType
import io.ably.lib.`object`.path.DefaultPathObject
import io.ably.lib.`object`.value.valueType

/**
 * Default implementation of [StringPathObject], a terminal primitive view that only adds a
 * type-narrowed [value]; left unimplemented for now.
 *
 * Spec: RTTS6c
 */
internal class DefaultStringPathObject(
  channelObject: DefaultRealtimeObject,
  path: String,
) : DefaultPathObject(channelObject, path), StringPathObject {

  override fun value(): String? {
    channelObject.throwIfInvalidAccessApiConfiguration()
    if (resolveValueAtPath(path)?.valueType() != ValueType.STRING) return null // not a String at this path -> no value
    // TODO - extract the primitive value from the resolved leaf, narrowed to String
    TODO("Not yet implemented")
  }
}
