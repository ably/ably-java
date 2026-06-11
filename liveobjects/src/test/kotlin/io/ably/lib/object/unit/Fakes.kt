package io.ably.lib.`object`.unit

import io.ably.lib.`object`.CounterNode
import io.ably.lib.`object`.MapNode
import io.ably.lib.`object`.ObjectsBridge
import io.ably.lib.`object`.ObjectsNode
import io.ably.lib.`object`.WireObjectData
import io.ably.lib.`object`.WireObjectMessage

/**
 * Minimal in-memory ObjectsBridge for unit tests; also documents the contract
 * a real bridge implementation must provide.
 */
internal class FakeBridge : ObjectsBridge() {

  internal val nodes = mutableMapOf<String, ObjectsNode>()
  internal val published = mutableListOf<WireObjectMessage>()
  internal var rootId: String? = "root"

  internal fun addMap(objectId: String, entries: MutableMap<String, WireObjectData> = mutableMapOf()): FakeMapNode =
    FakeMapNode(objectId, entries).also { nodes[objectId] = it }

  internal fun addCounter(objectId: String, count: Double): FakeCounterNode =
    FakeCounterNode(objectId, count).also { nodes[objectId] = it }

  override val channelName: String = "test-channel"

  override fun getRootNode(): MapNode? = rootId?.let { nodes[it] as? MapNode }

  override fun getNode(objectId: String): ObjectsNode? = nodes[objectId]

  override fun throwIfInvalidAccessApiConfiguration() = Unit

  override fun throwIfInvalidWriteApiConfiguration() = Unit

  override suspend fun publish(messages: List<WireObjectMessage>) {
    published.addAll(messages)
  }

  override suspend fun getServerTime(): Long = 1_718_000_000_000L

  override suspend fun ensureAttachedAndSynced() = Unit
}

internal class FakeMapNode(
  override val objectId: String,
  internal val data: MutableMap<String, WireObjectData> = mutableMapOf(),
) : MapNode {
  override fun entries(): Map<String, WireObjectData> = data.toMap()
  override fun get(key: String): WireObjectData? = data[key]
}

internal class FakeCounterNode(
  override val objectId: String,
  internal var value: Double,
) : CounterNode {
  override fun count(): Double = value
}
