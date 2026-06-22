package io.ably.lib.`object`.path.types

import io.ably.lib.`object`.DefaultRealtimeObject
import io.ably.lib.`object`.ValueType
import io.ably.lib.`object`.path.DefaultPathObject
import io.ably.lib.`object`.value.valueType

/**
 * Default implementation of [NumberPathObject], a terminal primitive view that only adds a
 * type-narrowed [value]; left unimplemented for now.
 *
 * Spec: RTTS6c
 */
internal class DefaultNumberPathObject(
  channelObject: DefaultRealtimeObject,
  path: String,
) : DefaultPathObject(channelObject, path), NumberPathObject {

  override fun value(): Number? {
    channelObject.throwIfInvalidAccessApiConfiguration()
    if (resolveValueAtPath(path)?.valueType() != ValueType.NUMBER) return null // not a Number at this path -> no value
    // TODO - extract the primitive value from the resolved leaf, narrowed to Number
    TODO("Not yet implemented")
  }
}
