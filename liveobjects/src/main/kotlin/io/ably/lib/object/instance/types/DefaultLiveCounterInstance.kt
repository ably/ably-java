package io.ably.lib.`object`.instance.types

import com.google.gson.JsonPrimitive
import io.ably.lib.`object`.DefaultRealtimeObject
import io.ably.lib.`object`.Subscription
import io.ably.lib.`object`.ValueType
import io.ably.lib.`object`.instance.DefaultInstance
import io.ably.lib.`object`.instance.InstanceListener
import io.ably.lib.`object`.onceSubscription
import java.util.concurrent.CompletableFuture

/**
 * Default implementation of [LiveCounterInstance], adding counter operations and subscribe
 * on top of [DefaultInstance]; all left unimplemented for now.
 *
 * Spec: RTTS10b
 */
internal class DefaultLiveCounterInstance(
  channelObject: DefaultRealtimeObject,
) : DefaultInstance(channelObject), LiveCounterInstance {

  override fun getType(): ValueType = ValueType.LIVE_COUNTER

  override fun compactJson(): JsonPrimitive {
    channelObject.throwIfInvalidAccessApiConfiguration()
    TODO("Not yet implemented")
  }

  override fun asLiveCounter(): LiveCounterInstance = this

  override fun getId(): String = TODO("Not yet implemented")

  override fun value(): Double {
    channelObject.throwIfInvalidAccessApiConfiguration()
    TODO("Not yet implemented")
  }

  override fun increment(): CompletableFuture<Void> {
    channelObject.throwIfInvalidWriteApiConfiguration()
    TODO("Not yet implemented")
  }

  override fun increment(amount: Number): CompletableFuture<Void> {
    channelObject.throwIfInvalidWriteApiConfiguration()
    TODO("Not yet implemented")
  }

  override fun decrement(): CompletableFuture<Void> {
    channelObject.throwIfInvalidWriteApiConfiguration()
    TODO("Not yet implemented")
  }

  override fun decrement(amount: Number): CompletableFuture<Void> {
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
