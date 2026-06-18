package io.ably.lib.`object`

import io.ably.lib.`object`.adapter.AblyClientAdapter
import io.ably.lib.`object`.path.types.LiveMapPathObject
import io.ably.lib.`object`.state.ObjectStateChange
import io.ably.lib.`object`.state.ObjectStateEvent
import java.util.concurrent.CompletableFuture

/**
 * Default implementation of [RealtimeObject], the entry point to the strongly-typed,
 * path-based LiveObjects API for a single channel.
 *
 * This is currently a skeleton: the path-based read and subscribe operations are not yet
 * implemented. The method bodies will be filled in as the path-based API is built out.
 *
 * Spec: RTO23
 */
internal class DefaultRealtimeObject(
  internal val channelName: String,
  internal val adapter: AblyClientAdapter,
) : RealtimeObject {

  override fun get(): CompletableFuture<LiveMapPathObject> = TODO("Not yet implemented")

  override fun on(event: ObjectStateEvent, listener: ObjectStateChange.Listener): Subscription {
    // TODO - subscribe logic goes here
    return onceSubscription {
      // TODO - remove ObjectStateChange.Listener
    }
  }

  override fun off(listener: ObjectStateChange.Listener): Unit = TODO("Not yet implemented")

  override fun offAll(): Unit = TODO("Not yet implemented")

  /** Validates the channel is configured for access (read/subscribe) operations. Spec: RTO25 */
  internal fun throwIfInvalidAccessApiConfiguration() = adapter.throwIfInvalidAccessApiConfiguration(channelName)

  /** Validates the channel is configured for write (mutation) operations. Spec: RTO26 */
  internal fun throwIfInvalidWriteApiConfiguration() = adapter.throwIfInvalidWriteApiConfiguration(channelName)
}
