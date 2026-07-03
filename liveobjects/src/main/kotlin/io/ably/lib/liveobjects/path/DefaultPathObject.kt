package io.ably.lib.liveobjects.path

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import io.ably.lib.liveobjects.DefaultRealtimeObject
import io.ably.lib.liveobjects.ROOT_OBJECT_ID
import io.ably.lib.liveobjects.Subscription
import io.ably.lib.liveobjects.ValueType
import io.ably.lib.liveobjects.instance.Instance
import io.ably.lib.liveobjects.instance.toInstance
import io.ably.lib.liveobjects.path.types.BinaryPathObject
import io.ably.lib.liveobjects.path.types.BooleanPathObject
import io.ably.lib.liveobjects.path.types.DefaultBinaryPathObject
import io.ably.lib.liveobjects.path.types.DefaultBooleanPathObject
import io.ably.lib.liveobjects.path.types.DefaultJsonArrayPathObject
import io.ably.lib.liveobjects.path.types.DefaultJsonObjectPathObject
import io.ably.lib.liveobjects.path.types.DefaultLiveCounterPathObject
import io.ably.lib.liveobjects.path.types.DefaultLiveMapPathObject
import io.ably.lib.liveobjects.path.types.DefaultNumberPathObject
import io.ably.lib.liveobjects.path.types.DefaultStringPathObject
import io.ably.lib.liveobjects.path.types.JsonArrayPathObject
import io.ably.lib.liveobjects.path.types.JsonObjectPathObject
import io.ably.lib.liveobjects.path.types.LiveCounterPathObject
import io.ably.lib.liveobjects.path.types.LiveMapPathObject
import io.ably.lib.liveobjects.path.types.NumberPathObject
import io.ably.lib.liveobjects.path.types.StringPathObject
import io.ably.lib.liveobjects.value.ResolvedValue
import io.ably.lib.liveobjects.value.livemap.InternalLiveMap
import io.ably.lib.liveobjects.value.livemap.toCompactJsonElement
import io.ably.lib.liveobjects.value.valueType

/**
 * Default implementation of [PathObject], the untyped node in the path-addressed view of
 * the LiveObjects graph.
 *
 * The `as*` casts return a typed view of the same position without resolving it; operations
 * that need a value re-resolve the stored path against the live objects graph on every call
 * via [resolveValueAtPath], so a PathObject never holds a stale reference.
 *
 * Spec: RTPO1, RTPO2, RTTS3
 */
internal open class DefaultPathObject(
  internal val channelObject: DefaultRealtimeObject,
  internal val path: String
) : PathObject {

  override fun path(): String = path

  override fun getType(): ValueType? {
    channelObject.throwIfInvalidAccessApiConfiguration()
    return resolveValueAtPath(path)?.valueType()
  }

  override fun instance(): Instance? {
    channelObject.throwIfInvalidAccessApiConfiguration() // RTPO8a
    val resolvedValue = resolveValueAtPath(path) ?: return null // RTPO8e - unresolved path -> no instance
    return when (resolvedValue) {
      is ResolvedValue.Leaf -> null // RTPO8d - primitives have no Instance here; only live objects do
      // RTPO8c - wrap the resolved live object in its typed Instance (RTTS6e: primitive
      // *PathObject sub-types inherit this and resolve to leaves, so they return null)
      is ResolvedValue.MapRef, is ResolvedValue.CounterRef -> resolvedValue.toInstance(channelObject)
    }
  }

  override fun compactJson(): JsonElement? {
    channelObject.throwIfInvalidAccessApiConfiguration() // RTPO14a / RTO25
    return when (val resolved = resolveValueAtPath(path)) {
      null -> null // RTPO3c1 - unresolved path
      is ResolvedValue.MapRef -> resolved.map.compactJson() // RTPO13c
      is ResolvedValue.CounterRef -> JsonPrimitive(resolved.counter.value()) // RTPO13d
      is ResolvedValue.Leaf -> resolved.data.toCompactJsonElement() // RTPO13e, RTPO14b1
    }
  }

  override fun exists(): Boolean {
    channelObject.throwIfInvalidAccessApiConfiguration()
    return resolveValueAtPath(path) != null
  }

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
    channelObject.throwIfInvalidAccessApiConfiguration() // RTPO19b
    // depth validation happens in PathObjectSubscriptionOptions(int) - RTPO19c1a
    val segments = PathSegments.parseStored(path) // empty stored path = root = zero segments
    // RTPO19f - pure registration, no side effects on channel state (RTPO19g)
    return channelObject.pathObjectSubscriptionRegister.subscribe(segments, listener, options?.depth)
  }

  /**
   * RTPO3 path resolution against the local objects graph, evaluated freshly at call time.
   * Returns null on resolution failure; read callers degrade per RTPO3c1, write callers
   * throw 92005 per RTPO3c2.
   */
  protected fun resolveValueAtPath(path: String): ResolvedValue? {
    // root is always present and always an InternalLiveMap (RTO3b); the pool never replaces
    // the root instance (RTO4b2, RTO5c2a), so looking it up per call is equivalent to
    // holding the RTPO2b root reference
    val root = channelObject.objectsPool.get(ROOT_OBJECT_ID) as InternalLiveMap
    var current: ResolvedValue = ResolvedValue.MapRef(root)
    // parseStored: an empty stored path is the root itself - zero segments (RTPO3b)
    for (segment in PathSegments.parseStored(path)) {
      val map = (current as? ResolvedValue.MapRef)?.map ?: return null // RTPO3a1 - non-map mid-path
      current = map.get(segment) ?: return null // RTPO3a2 - via RTLM5
    }
    return current // RTPO3a3
  }
}
