package io.ably.lib.`object`.path

import com.google.gson.JsonElement
import io.ably.lib.`object`.MapNode
import io.ably.lib.`object`.ObjectsBridge
import io.ably.lib.`object`.ResolvedValue
import io.ably.lib.`object`.Subscription
import io.ably.lib.`object`.ValueType
import io.ably.lib.`object`.compactJson
import io.ably.lib.`object`.instance.DefaultBaseInstance
import io.ably.lib.`object`.instance.Instance
import io.ably.lib.`object`.path.types.BinaryPathObject
import io.ably.lib.`object`.path.types.BooleanPathObject
import io.ably.lib.`object`.path.types.JsonArrayPathObject
import io.ably.lib.`object`.path.types.JsonObjectPathObject
import io.ably.lib.`object`.path.types.LiveCounterPathObject
import io.ably.lib.`object`.path.types.LiveMapPathObject
import io.ably.lib.`object`.resolve
import io.ably.lib.`object`.path.types.NumberPathObject
import io.ably.lib.`object`.path.types.StringPathObject
import io.ably.lib.`object`.valueType

/**
 * Base implementation of the typed [PathObject] hierarchy: a cheap
 * navigational handle holding the path segments (RTPO2a); the underlying
 * value is resolved against the objects graph on every call (RTPO3), exactly
 * like ably-js `pathobject.ts`.
 *
 * Spec: RTPO1, RTPO2, RTPO3, RTTS3, RTTS4, RTTS5
 */
internal abstract class DefaultBasePathObject(
  internal val bridge: ObjectsBridge,
  internal val pathSegments: List<String>,
) : PathObject {

  /**
   * The path resolution procedure: walks the stored segments from the root
   * map. Returns null on any resolution failure (RTPO3c).
   *
   * Spec: RTPO3
   */
  internal fun resolve(): ResolvedValue? {
    val root = bridge.getRootNode() ?: return null
    var current: ResolvedValue = ResolvedValue.MapRef(root)
    for (segment in pathSegments) {
      val map = (current as? ResolvedValue.MapRef)?.map ?: return null // RTPO3b - intermediate must be a map
      current = map.get(segment)?.resolve(bridge) ?: return null // RTPO3c - missing/unresolvable entry
    }
    return current
  }

  /** Spec: RTPO4 - dot-delimited; dots inside segments escaped as `\.` (RTPO4b) */
  override fun path(): String = pathSegments.joinToString(".") { it.replace(".", "\\.") }

  /** Spec: RTPO8, RTTS3b - wraps live objects only; null for primitives/unresolved (RTPO8d) */
  override fun instance(): Instance? {
    bridge.throwIfInvalidAccessApiConfiguration() // RTPO8a / RTO25
    return when (val resolved = resolve()) {
      is ResolvedValue.MapRef, is ResolvedValue.CounterRef -> DefaultBaseInstance.from(bridge, resolved)
      else -> null
    }
  }

  /** Spec: RTPO14, RTTS3c - null when the path does not resolve (RTPO3c1) */
  override fun compactJson(): JsonElement? {
    bridge.throwIfInvalidAccessApiConfiguration() // RTPO14a / RTO25
    return resolve()?.compactJson(bridge)
  }

  /** Spec: RTTS4a - best-effort existence check at call time */
  override fun exists(): Boolean {
    bridge.throwIfInvalidAccessApiConfiguration() // RTTS4a1 / RTO25
    return resolve() != null // RTTS4a2, RTTS4a3
  }

  /** Spec: RTTS4b - UNKNOWN when resolution fails (RTTS4b3) */
  override fun getType(): ValueType {
    bridge.throwIfInvalidAccessApiConfiguration() // RTTS4b1 / RTO25
    return resolve().valueType() // RTTS4b2, RTTS4b3
  }

  // RTTS5 - as* cast helpers: re-wrap sharing path and root, never throw (RTTS5d)
  override fun asLiveMap(): LiveMapPathObject = DefaultLiveMapPathObject(bridge, pathSegments) // RTTS5a
  override fun asLiveCounter(): LiveCounterPathObject = DefaultLiveCounterPathObject(bridge, pathSegments) // RTTS5b
  override fun asNumber(): NumberPathObject = DefaultNumberPathObject(bridge, pathSegments) // RTTS5c
  override fun asString(): StringPathObject = DefaultStringPathObject(bridge, pathSegments)
  override fun asBoolean(): BooleanPathObject = DefaultBooleanPathObject(bridge, pathSegments)
  override fun asBinary(): BinaryPathObject = DefaultBinaryPathObject(bridge, pathSegments)
  override fun asJsonObject(): JsonObjectPathObject = DefaultJsonObjectPathObject(bridge, pathSegments)
  override fun asJsonArray(): JsonArrayPathObject = DefaultJsonArrayPathObject(bridge, pathSegments)

  /** Spec: RTPO19, RTTS3d */
  override fun subscribe(listener: PathObjectListener): Subscription =
    subscribe(listener, null)

  /** Spec: RTPO19, RTTS3d - depth validation (RTPO19c1a) is done by the options constructor */
  override fun subscribe(listener: PathObjectListener, options: PathObjectSubscriptionOptions?): Subscription {
    bridge.throwIfInvalidAccessApiConfiguration() // RTPO19a / RTO25
    return bridge.pathSubscriptionRegister.subscribe(pathSegments, listener, options)
  }

  internal companion object {
    /**
     * Parses a dot-delimited path string into segments: splits on unescaped
     * dots; a backslash-escaped dot (`\.`) is a literal dot within a segment;
     * a backslash before any other character is kept as-is. Exact port of the
     * ably-js algorithm (pathobject.ts#at) so the two SDKs agree on every
     * input, including escaped backslashes and trailing backslashes.
     *
     * Spec: RTPO6 (and the inverse of RTPO4b)
     */
    internal fun parsePath(path: String): List<String> {
      val segments = mutableListOf<String>()
      val current = StringBuilder()
      var escaping = false
      for (c in path) {
        if (escaping) {
          // keep the escape character if not escaping a dot
          if (c != '.') current.append('\\')
          current.append(c)
          escaping = false
          continue
        }
        when (c) {
          '\\' -> escaping = true
          '.' -> {
            segments.add(current.toString())
            current.setLength(0)
          }
          else -> current.append(c)
        }
      }
      if (escaping) {
        current.append('\\')
      }
      segments.add(current.toString())
      return segments
    }
  }
}

/**
 * Concrete untyped PathObject - returned wherever the static type is the base
 * [PathObject] (get/at results, subscription event objects); callers narrow
 * via the as* helpers (RTTS3g).
 */
internal class DefaultPathObject(
  bridge: ObjectsBridge,
  pathSegments: List<String>,
) : DefaultBasePathObject(bridge, pathSegments)
