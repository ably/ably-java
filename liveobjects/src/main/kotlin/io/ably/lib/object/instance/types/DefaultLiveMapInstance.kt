package io.ably.lib.`object`.instance.types

import com.google.gson.JsonObject
import io.ably.lib.`object`.DefaultRealtimeObject
import io.ably.lib.`object`.Subscription
import io.ably.lib.`object`.ValueType
import io.ably.lib.`object`.instance.DefaultInstance
import io.ably.lib.`object`.instance.Instance
import io.ably.lib.`object`.instance.InstanceListener
import io.ably.lib.`object`.onceSubscription
import io.ably.lib.`object`.value.LiveMapValue
import java.util.concurrent.CompletableFuture

/**
 * Default implementation of [LiveMapInstance], adding map reads, writes and subscribe on top
 * of [DefaultInstance]; all left unimplemented for now.
 *
 * Spec: RTTS10a
 */
internal class DefaultLiveMapInstance(
  channelObject: DefaultRealtimeObject,
) : DefaultInstance(channelObject), LiveMapInstance {

  override fun getType(): ValueType = ValueType.LIVE_MAP

  override fun compactJson(): JsonObject {
    channelObject.throwIfInvalidAccessApiConfiguration()
    TODO("Not yet implemented")
  }

  override fun asLiveMap(): LiveMapInstance = this

  override fun getId(): String = TODO("Not yet implemented")

  @Suppress("RedundantNullableReturnType")
  override fun get(key: String): Instance? {
    channelObject.throwIfInvalidAccessApiConfiguration()
    TODO("Not yet implemented")
  }

  override fun entries(): Iterable<Map.Entry<String, Instance>> {
    channelObject.throwIfInvalidAccessApiConfiguration()
    TODO("Not yet implemented")
  }

  override fun keys(): Iterable<String> {
    channelObject.throwIfInvalidAccessApiConfiguration()
    TODO("Not yet implemented")
  }

  override fun values(): Iterable<Instance> {
    channelObject.throwIfInvalidAccessApiConfiguration()
    TODO("Not yet implemented")
  }

  override fun size(): Long {
    channelObject.throwIfInvalidAccessApiConfiguration()
    TODO("Not yet implemented")
  }

  override fun set(key: String, value: LiveMapValue): CompletableFuture<Void> {
    channelObject.throwIfInvalidWriteApiConfiguration()
    TODO("Not yet implemented")
  }

  override fun remove(key: String): CompletableFuture<Void> {
    channelObject.throwIfInvalidWriteApiConfiguration()
    TODO("Not yet implemented")
  }

  override fun subscribe(listener: InstanceListener): Subscription {
    channelObject.throwIfInvalidAccessApiConfiguration()
    // TODO - subscribe logic goes here
    return onceSubscription {
      // TODO - remove InstanceListener
    }
  }
}
