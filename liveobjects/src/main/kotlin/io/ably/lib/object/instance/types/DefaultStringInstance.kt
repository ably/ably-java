package io.ably.lib.`object`.instance.types

import com.google.gson.JsonElement
import io.ably.lib.`object`.DefaultRealtimeObject
import io.ably.lib.`object`.ValueType
import io.ably.lib.`object`.instance.DefaultInstance

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

  override fun compactJson(): JsonElement = TODO("Not yet implemented")

  override fun asString(): StringInstance = this

  override fun value(): String = TODO("Not yet implemented")
}
