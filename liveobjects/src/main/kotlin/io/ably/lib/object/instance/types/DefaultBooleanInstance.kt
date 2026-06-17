package io.ably.lib.`object`.instance.types

import com.google.gson.JsonPrimitive
import io.ably.lib.`object`.DefaultRealtimeObject
import io.ably.lib.`object`.ValueType
import io.ably.lib.`object`.instance.DefaultInstance

/**
 * Default implementation of [BooleanInstance], a read-only primitive view that only adds a
 * type-narrowed, non-null [value]; left unimplemented for now.
 *
 * Spec: RTTS10c
 */
internal class DefaultBooleanInstance(
  channelObject: DefaultRealtimeObject,
) : DefaultInstance(channelObject), BooleanInstance {

  override fun getType(): ValueType = ValueType.BOOLEAN

  override fun compactJson(): JsonPrimitive {
    channelObject.throwIfInvalidAccessApiConfiguration()
    TODO("Not yet implemented")
  }

  override fun asBoolean(): BooleanInstance = this

  override fun value(): Boolean {
    channelObject.throwIfInvalidAccessApiConfiguration()
    TODO("Not yet implemented")
  }
}
