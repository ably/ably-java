package io.ably.lib.`object`.instance.types

import com.google.gson.JsonPrimitive
import io.ably.lib.`object`.DefaultRealtimeObject
import io.ably.lib.`object`.ValueType
import io.ably.lib.`object`.instance.DefaultInstance

/**
 * Default implementation of [BinaryInstance], a read-only primitive view that only adds a
 * type-narrowed, non-null [value]; left unimplemented for now.
 *
 * Spec: RTTS10c
 */
internal class DefaultBinaryInstance(
  channelObject: DefaultRealtimeObject,
) : DefaultInstance(channelObject), BinaryInstance {

  override fun getType(): ValueType = ValueType.BINARY

  override fun compactJson(): JsonPrimitive {
    channelObject.throwIfInvalidAccessApiConfiguration()
    TODO("Not yet implemented")
  }

  override fun asBinary(): BinaryInstance = this

  override fun value(): ByteArray {
    channelObject.throwIfInvalidAccessApiConfiguration()
    TODO("Not yet implemented")
  }
}
