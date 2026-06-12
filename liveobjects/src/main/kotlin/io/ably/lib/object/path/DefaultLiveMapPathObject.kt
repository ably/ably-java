package io.ably.lib.`object`.path

import io.ably.lib.`object`.MapNode
import io.ably.lib.`object`.ObjectsBridge
import io.ably.lib.`object`.ResolvedValue
import io.ably.lib.`object`.WireMapRemove
import io.ably.lib.`object`.WireMapSet
import io.ably.lib.`object`.WireObjectMessage
import io.ably.lib.`object`.WireObjectOperation
import io.ably.lib.`object`.WireObjectOperationAction
import io.ably.lib.`object`.invalidInputError
import io.ably.lib.`object`.objectDataFrom
import io.ably.lib.`object`.path.types.LiveMapPathObject
import io.ably.lib.`object`.pathNotResolvedError
import io.ably.lib.`object`.resolve
import io.ably.lib.`object`.typeMismatchError
import io.ably.lib.`object`.value.LiveMapValue
import java.util.AbstractMap
import java.util.concurrent.CompletableFuture

/**
 * Typed PathObject expected to resolve to an InternalLiveMap.
 *
 * Spec: RTTS6a
 */
internal class DefaultLiveMapPathObject(
  bridge: ObjectsBridge,
  pathSegments: List<String>,
) : DefaultBasePathObject(bridge, pathSegments), LiveMapPathObject {

  /** Resolves and requires a map for write operations: 92005 when the path does not resolve (RTPO3c2), 92007 on type mismatch (RTTS5d2). */
  private fun mapNodeForWrite(): MapNode = when (val resolved = resolve()) {
    null -> throw pathNotResolvedError(path()) // RTPO3c2 - 92005
    is ResolvedValue.MapRef -> resolved.map
    else -> throw typeMismatchError("Value at path \"${path()}\" is not a LiveMap") // RTTS5d2 - 92007
  }

  private fun mapNodeOrNull(): MapNode? = (resolve() as? ResolvedValue.MapRef)?.map

  /**
   * Keys of the resolved map whose entries themselves resolve - entries
   * referencing missing/tombstoned objects are excluded, matching the
   * underlying map semantics (RTLM11d2/RTLM14).
   */
  private fun resolvableKeys(node: MapNode): List<String> =
    node.entries().filter { (_, data) -> data.resolve(bridge) != null }.map { it.key }

  /** Spec: RTPO5 - purely navigational, no resolution performed */
  override fun get(key: String): PathObject = DefaultPathObject(bridge, pathSegments + key)

  /** Spec: RTPO6 - purely navigational; dot-delimited with `\.` escapes */
  override fun at(path: String): PathObject =
    DefaultPathObject(bridge, pathSegments + parsePath(path))

  /** Spec: RTPO9 - empty when the path does not resolve to a map (RTPO3c1) */
  override fun entries(): Iterable<Map.Entry<String, PathObject>> {
    bridge.throwIfInvalidAccessApiConfiguration() // RTPO9a / RTO25
    val node = mapNodeOrNull() ?: return emptyList()
    return resolvableKeys(node).map { key ->
      AbstractMap.SimpleImmutableEntry<String, PathObject>(key, get(key)) // child paths as if by get(key)
    }
  }

  /** Spec: RTPO10 */
  override fun keys(): Iterable<String> {
    bridge.throwIfInvalidAccessApiConfiguration() // RTPO10a / RTO25
    val node = mapNodeOrNull() ?: return emptyList()
    return resolvableKeys(node)
  }

  /** Spec: RTPO11 */
  override fun values(): Iterable<PathObject> = entries().map { it.value }

  /** Spec: RTPO12 - null when the path does not resolve to a map; counts resolvable entries (RTLM10d) */
  override fun size(): Long? {
    bridge.throwIfInvalidAccessApiConfiguration() // RTPO12a / RTO25
    return mapNodeOrNull()?.let { resolvableKeys(it).size.toLong() }
  }

  /** Spec: RTPO15 */
  override fun set(key: String, value: LiveMapValue): CompletableFuture<Void> = bridge.launchWithVoidFuture {
    bridge.throwIfInvalidWriteApiConfiguration() // RTPO15a / RTO26
    if (key.isEmpty()) {
      throw invalidInputError("Map key must not be empty")
    }
    val node = mapNodeForWrite()
    // evaluate value-type arguments into create messages (RTLMV4/RTLCV4), publish together with the MAP_SET
    val nestedMessages = mutableListOf<WireObjectMessage>()
    val data = objectDataFrom(value, nestedMessages, bridge)
    val mapSetMessage = WireObjectMessage(
      operation = WireObjectOperation(
        action = WireObjectOperationAction.MapSet,
        objectId = node.objectId,
        mapSet = WireMapSet(key = key, value = data),
      )
    )
    bridge.publish(nestedMessages + mapSetMessage)
  }

  /** Spec: RTPO16 */
  override fun remove(key: String): CompletableFuture<Void> = bridge.launchWithVoidFuture {
    bridge.throwIfInvalidWriteApiConfiguration() // RTPO16a / RTO26
    if (key.isEmpty()) {
      throw invalidInputError("Map key must not be empty")
    }
    val node = mapNodeForWrite()
    val message = WireObjectMessage(
      operation = WireObjectOperation(
        action = WireObjectOperationAction.MapRemove,
        objectId = node.objectId,
        mapRemove = WireMapRemove(key = key),
      )
    )
    bridge.publish(listOf(message))
  }
}
