package io.ably.lib.`object`.instance.types

import com.google.gson.JsonPrimitive
import io.ably.lib.`object`.DefaultRealtimeObject
import io.ably.lib.`object`.ValueType
import io.ably.lib.`object`.instance.DefaultInstance

/**
 * Default implementation of [NumberInstance], a read-only primitive view that only adds a
 * type-narrowed, non-null [value]; left unimplemented for now.
 *
 * Spec: RTTS10c
 */
internal class DefaultNumberInstance(
  channelObject: DefaultRealtimeObject,
) : DefaultInstance(channelObject), NumberInstance {

  override fun getType(): ValueType = ValueType.NUMBER

  override fun compactJson(): JsonPrimitive {
    channelObject.throwIfInvalidAccessApiConfiguration()
    TODO("Not yet implemented")
  }

  override fun asNumber(): NumberInstance = this

  override fun value(): Number {
    channelObject.throwIfInvalidAccessApiConfiguration()
    TODO("Not yet implemented")
  }
}
