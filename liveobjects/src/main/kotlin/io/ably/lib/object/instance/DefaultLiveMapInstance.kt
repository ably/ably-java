package io.ably.lib.`object`.instance

import io.ably.lib.`object`.DefaultSubscription
import io.ably.lib.`object`.MapNode
import io.ably.lib.`object`.ObjectsBridge
import io.ably.lib.`object`.ResolvedValue
import io.ably.lib.`object`.Subscription
import io.ably.lib.`object`.WireMapRemove
import io.ably.lib.`object`.WireMapSet
import io.ably.lib.`object`.WireObjectMessage
import io.ably.lib.`object`.WireObjectOperation
import io.ably.lib.`object`.WireObjectOperationAction
import io.ably.lib.`object`.instance.types.LiveMapInstance
import io.ably.lib.`object`.invalidInputError
import io.ably.lib.`object`.message.toPublicMessage
import io.ably.lib.`object`.objectDataFrom
import io.ably.lib.`object`.resolve
import io.ably.lib.`object`.typeMismatchError
import io.ably.lib.`object`.value.LiveMapValue
import java.util.AbstractMap
import java.util.concurrent.CompletableFuture

/**
 * Typed Instance over an InternalLiveMap.
 *
 * Spec: RTTS10a
 */
internal class DefaultLiveMapInstance(
  bridge: ObjectsBridge,
  value: ResolvedValue,
) : DefaultBaseInstance(bridge, value), LiveMapInstance {

  /** The wrapped map node; mismatched as* wrappers fail per RTTS9d2 (92007). */
  private fun mapNodeOrThrow(): MapNode = (value as? ResolvedValue.MapRef)?.map
    ?: throw typeMismatchError("Instance does not wrap a LiveMap")

  private fun mapNodeOrNull(): MapNode? = (value as? ResolvedValue.MapRef)?.map

  /** Spec: RTINS3a / RTTS10a - non-null (wrapped object always has an id) */
  override fun getId(): String = mapNodeOrThrow().objectId

  /** Spec: RTINS5 */
  override fun get(key: String): Instance? {
    bridge.throwIfInvalidAccessApiConfiguration() // RTO25
    val node = mapNodeOrNull() ?: return null // RTTS9d1 / RTINS5d
    val resolved = node.get(key)?.resolve(bridge) ?: return null
    return from(bridge, resolved)
  }

  /** Spec: RTINS6 */
  override fun entries(): Iterable<Map.Entry<String, Instance>> {
    bridge.throwIfInvalidAccessApiConfiguration() // RTO25
    val node = mapNodeOrNull() ?: return emptyList() // RTTS9d1 / RTINS6c
    return node.entries().mapNotNull { (key, data) ->
      data.resolve(bridge)?.let { resolved ->
        AbstractMap.SimpleImmutableEntry<String, Instance>(key, from(bridge, resolved))
      }
    }
  }

  /** Spec: RTINS7 */
  override fun keys(): Iterable<String> = entries().map { it.key }

  /** Spec: RTINS8 */
  override fun values(): Iterable<Instance> = entries().map { it.value }

  /** Spec: RTINS9 / RTTS10a - non-null; counts resolvable entries only (RTLM10d/RTLM14) */
  override fun size(): Long {
    bridge.throwIfInvalidAccessApiConfiguration() // RTO25
    return mapNodeOrThrow().entries().count { (_, data) -> data.resolve(bridge) != null }.toLong()
  }

  /** Spec: RTINS12 */
  override fun set(key: String, value: LiveMapValue): CompletableFuture<Void> = bridge.launchWithVoidFuture {
    bridge.throwIfInvalidWriteApiConfiguration() // RTINS12b / RTO26
    if (key.isEmpty()) {
      throw invalidInputError("Map key must not be empty")
    }
    val node = mapNodeOrThrow() // RTINS12d - 92007 on mismatched wrapper
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

  /** Spec: RTINS13 */
  override fun remove(key: String): CompletableFuture<Void> = bridge.launchWithVoidFuture {
    bridge.throwIfInvalidWriteApiConfiguration() // RTINS13b / RTO26
    if (key.isEmpty()) {
      throw invalidInputError("Map key must not be empty")
    }
    val node = mapNodeOrThrow() // RTINS13d - 92007 on mismatched wrapper
    val message = WireObjectMessage(
      operation = WireObjectOperation(
        action = WireObjectOperationAction.MapRemove,
        objectId = node.objectId,
        mapRemove = WireMapRemove(key = key),
      )
    )
    bridge.publish(listOf(message))
  }

  /** Spec: RTINS16 / RTTS10a - delivers both object and message (RTINS16e) */
  override fun subscribe(listener: InstanceListener): Subscription {
    bridge.throwIfInvalidAccessApiConfiguration()
    val node = mapNodeOrThrow() // RTINS16c - subscribe is meaningful only on live objects
    val unsubscribe = bridge.subscribeToUpdates(node.objectId) { _, wireMessage ->
      val event = DefaultInstanceSubscriptionEvent(
        this, // RTINS16e1
        wireMessage?.toPublicMessage(bridge.channelName), // RTINS16e2
      )
      listener.onUpdated(event)
    }
    return DefaultSubscription(unsubscribe)
  }
}
