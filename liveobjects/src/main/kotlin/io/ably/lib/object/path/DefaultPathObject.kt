package io.ably.lib.`object`.path

import com.google.gson.JsonElement
import io.ably.lib.`object`.DefaultRealtimeObject
import io.ably.lib.`object`.Subscription
import io.ably.lib.`object`.ValueType
import io.ably.lib.`object`.instance.Instance
import io.ably.lib.`object`.onceSubscription
import io.ably.lib.`object`.path.types.BinaryPathObject
import io.ably.lib.`object`.path.types.BooleanPathObject
import io.ably.lib.`object`.path.types.DefaultBinaryPathObject
import io.ably.lib.`object`.path.types.DefaultBooleanPathObject
import io.ably.lib.`object`.path.types.DefaultJsonArrayPathObject
import io.ably.lib.`object`.path.types.DefaultJsonObjectPathObject
import io.ably.lib.`object`.path.types.DefaultLiveCounterPathObject
import io.ably.lib.`object`.path.types.DefaultLiveMapPathObject
import io.ably.lib.`object`.path.types.DefaultNumberPathObject
import io.ably.lib.`object`.path.types.DefaultStringPathObject
import io.ably.lib.`object`.path.types.JsonArrayPathObject
import io.ably.lib.`object`.path.types.JsonObjectPathObject
import io.ably.lib.`object`.path.types.LiveCounterPathObject
import io.ably.lib.`object`.path.types.LiveMapPathObject
import io.ably.lib.`object`.path.types.NumberPathObject
import io.ably.lib.`object`.path.types.StringPathObject

/**
 * Default implementation of [PathObject], the untyped node in the path-addressed view of
 * the LiveObjects graph.
 *
 * This is a skeleton. The `as*` casts return a typed view of the same position; the
 * operations that require resolving the path against the live objects graph are left
 * unimplemented for now and will be filled in as the path-based API is built out.
 *
 * Spec: RTPO1, RTPO2, RTTS3
 */
internal open class DefaultPathObject(
  internal val channelObject: DefaultRealtimeObject,
) : PathObject {

  override fun path(): String = TODO("Not yet implemented")

  override fun getType(): ValueType = TODO("Not yet implemented")

  override fun instance(): Instance? = TODO("Not yet implemented")

  override fun compactJson(): JsonElement? = TODO("Not yet implemented")

  override fun exists(): Boolean = TODO("Not yet implemented")

  override fun asLiveMap(): LiveMapPathObject = DefaultLiveMapPathObject(channelObject)

  override fun asLiveCounter(): LiveCounterPathObject = DefaultLiveCounterPathObject(channelObject)

  override fun asNumber(): NumberPathObject = DefaultNumberPathObject(channelObject)

  override fun asString(): StringPathObject = DefaultStringPathObject(channelObject)

  override fun asBoolean(): BooleanPathObject = DefaultBooleanPathObject(channelObject)

  override fun asBinary(): BinaryPathObject = DefaultBinaryPathObject(channelObject)

  override fun asJsonObject(): JsonObjectPathObject = DefaultJsonObjectPathObject(channelObject)

  override fun asJsonArray(): JsonArrayPathObject = DefaultJsonArrayPathObject(channelObject)

  override fun subscribe(listener: PathObjectListener): Subscription = subscribe(listener, null)

  override fun subscribe(listener: PathObjectListener, options: PathObjectSubscriptionOptions?): Subscription {
    // TODO - subscribe logic goes here
    return onceSubscription {
      // TODO - remove PathObjectListener from list
    }
  }
}
