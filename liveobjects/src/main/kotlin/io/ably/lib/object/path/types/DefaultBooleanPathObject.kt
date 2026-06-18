package io.ably.lib.`object`.path.types

import io.ably.lib.`object`.DefaultRealtimeObject
import io.ably.lib.`object`.ValueType
import io.ably.lib.`object`.path.DefaultPathObject
import io.ably.lib.`object`.value.valueType

/**
 * Default implementation of [BooleanPathObject], a terminal primitive view that only adds a
 * type-narrowed [value]; left unimplemented for now.
 *
 * Spec: RTTS6c
 */
internal class DefaultBooleanPathObject(
  channelObject: DefaultRealtimeObject,
  path: String,
) : DefaultPathObject(channelObject, path), BooleanPathObject {

  override fun value(): Boolean? {
    channelObject.throwIfInvalidAccessApiConfiguration()
    if (resolveValueAtPath(path)?.valueType() != ValueType.BOOLEAN) return null // not a Boolean at this path -> no value
    // TODO - extract the primitive value from the resolved leaf, narrowed to Boolean
    TODO("Not yet implemented")
  }
}
