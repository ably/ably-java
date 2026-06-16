package io.ably.lib.`object`.path.types

import io.ably.lib.`object`.DefaultRealtimeObject
import io.ably.lib.`object`.path.DefaultPathObject

/**
 * Default implementation of [BooleanPathObject], a terminal primitive view that only adds a
 * type-narrowed [value]; left unimplemented for now.
 *
 * Spec: RTTS6c
 */
internal class DefaultBooleanPathObject(
  channelObject: DefaultRealtimeObject,
) : DefaultPathObject(channelObject), BooleanPathObject {

  @Suppress("RedundantNullableReturnType")
  override fun value(): Boolean? = TODO("Not yet implemented")
}
