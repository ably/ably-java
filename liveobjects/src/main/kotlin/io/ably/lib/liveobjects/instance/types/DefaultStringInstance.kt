package io.ably.lib.liveobjects.instance.types

import com.google.gson.JsonPrimitive
import io.ably.lib.liveobjects.DefaultRealtimeObject
import io.ably.lib.liveobjects.ValueType
import io.ably.lib.liveobjects.instance.DefaultInstance

/**
 * Default implementation of [StringInstance], a read-only primitive view that only adds a
 * type-narrowed, non-null [value]; left unimplemented for now.
 *
 * Spec: RTTS10c
 */
internal class DefaultStringInstance(
  channelObject: DefaultRealtimeObject,
) : DefaultInstance(channelObject), StringInstance {

  override fun getType(): ValueType = ValueType.STRING

  override fun compactJson(): JsonPrimitive {
    channelObject.throwIfInvalidAccessApiConfiguration()
    TODO("Not yet implemented")
  }

  override fun asString(): StringInstance = this

  override fun value(): String {
    channelObject.throwIfInvalidAccessApiConfiguration()
    TODO("Not yet implemented")
  }
}
