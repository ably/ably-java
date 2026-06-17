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
import io.ably.lib.`object`.value.ResolvedValue
import io.ably.lib.`object`.value.valueType

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
  internal val path: String
) : PathObject {

  override fun path(): String = path

  override fun getType(): ValueType? = resolveValueAtPath(path)?.valueType()

  override fun instance(): Instance? {
    val resolvedValue = resolveValueAtPath(path) ?: return null // unresolved path -> no instance
    return when (resolvedValue) {
      is ResolvedValue.Leaf -> null // primitives have no Instance; only live objects do
      // TODO - wrap the resolved live object (LiveMap/LiveCounter) in an Instance
      is ResolvedValue.MapRef, is ResolvedValue.CounterRef -> TODO("Not yet implemented")
    }
  }

  override fun compactJson(): JsonElement? {
    resolveValueAtPath(path) ?: return null // unresolved path -> null
    // TODO - build the compacted JSON snapshot (LiveMap -> JsonObject, LiveCounter -> number, leaf -> JSON value)
    TODO("Not yet implemented")
  }

  override fun exists(): Boolean = resolveValueAtPath(path) != null

  override fun asLiveMap(): LiveMapPathObject = DefaultLiveMapPathObject(channelObject, path)

  override fun asLiveCounter(): LiveCounterPathObject = DefaultLiveCounterPathObject(channelObject, path)

  override fun asNumber(): NumberPathObject = DefaultNumberPathObject(channelObject, path)

  override fun asString(): StringPathObject = DefaultStringPathObject(channelObject, path)

  override fun asBoolean(): BooleanPathObject = DefaultBooleanPathObject(channelObject, path)

  override fun asBinary(): BinaryPathObject = DefaultBinaryPathObject(channelObject, path)

  override fun asJsonObject(): JsonObjectPathObject = DefaultJsonObjectPathObject(channelObject, path)

  override fun asJsonArray(): JsonArrayPathObject = DefaultJsonArrayPathObject(channelObject, path)

  override fun subscribe(listener: PathObjectListener): Subscription = subscribe(listener, null)

  override fun subscribe(listener: PathObjectListener, options: PathObjectSubscriptionOptions?): Subscription {
    // TODO - subscribe logic goes here
    return onceSubscription {
      // TODO - remove PathObjectListener from list
    }
  }

  protected fun resolveValueAtPath(path: String): ResolvedValue? {
    // TODO - resolve the path against the live objects graph and return the value at that position
    return null
  }
}
