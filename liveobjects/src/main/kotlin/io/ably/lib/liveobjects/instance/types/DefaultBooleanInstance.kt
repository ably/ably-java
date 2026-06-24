package io.ably.lib.liveobjects.instance.types

import com.google.gson.JsonPrimitive
import io.ably.lib.liveobjects.DefaultRealtimeObject
import io.ably.lib.liveobjects.ValueType
import io.ably.lib.liveobjects.instance.DefaultInstance

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
