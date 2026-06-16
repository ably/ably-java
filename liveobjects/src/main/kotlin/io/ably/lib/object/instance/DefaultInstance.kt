package io.ably.lib.`object`.instance

import io.ably.lib.`object`.DefaultRealtimeObject
import io.ably.lib.`object`.instance.types.BinaryInstance
import io.ably.lib.`object`.instance.types.BooleanInstance
import io.ably.lib.`object`.instance.types.JsonArrayInstance
import io.ably.lib.`object`.instance.types.JsonObjectInstance
import io.ably.lib.`object`.instance.types.LiveCounterInstance
import io.ably.lib.`object`.instance.types.LiveMapInstance
import io.ably.lib.`object`.instance.types.NumberInstance
import io.ably.lib.`object`.instance.types.StringInstance

/**
 * Default implementation of [Instance], the identity-addressed node in the LiveObjects graph.
 *
 * An instance is always bound to a specific resolved value of a known type, so this base is
 * abstract: each concrete sub-type supplies [getType] and [compactJson] (left abstract here)
 * and overrides only the single `as*` cast matching its own type to return `this`. The
 * remaining `as*` casts fall through to the implementations here, which fail fast because the
 * wrapped value is not of the requested type.
 *
 * Only the channel's [channelObject] context is carried; unlike a path object there is no
 * parent/child path, since an instance is identity-addressed.
 *
 * Spec: RTINS1, RTTS7
 */
internal abstract class DefaultInstance(
  internal val channelObject: DefaultRealtimeObject,
) : Instance {

  override fun asLiveMap(): LiveMapInstance = throw IllegalStateException("Not a LiveMap instance")

  override fun asLiveCounter(): LiveCounterInstance = throw IllegalStateException("Not a LiveCounter instance")

  override fun asNumber(): NumberInstance = throw IllegalStateException("Not a Number instance")

  override fun asString(): StringInstance = throw IllegalStateException("Not a String instance")

  override fun asBoolean(): BooleanInstance = throw IllegalStateException("Not a Boolean instance")

  override fun asBinary(): BinaryInstance = throw IllegalStateException("Not a Binary instance")

  override fun asJsonObject(): JsonObjectInstance = throw IllegalStateException("Not a JsonObject instance")

  override fun asJsonArray(): JsonArrayInstance = throw IllegalStateException("Not a JsonArray instance")
}
