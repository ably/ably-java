package io.ably.lib.`object`.path.types

import io.ably.lib.`object`.DefaultRealtimeObject
import io.ably.lib.`object`.path.DefaultPathObject
import java.util.concurrent.CompletableFuture

/**
 * Default implementation of [LiveCounterPathObject].
 *
 * Counters are terminal nodes (no navigation), so this only adds the counter read/write
 * operations on top of [DefaultPathObject]; they are left unimplemented for now.
 *
 * Spec: RTTS6b
 */
internal class DefaultLiveCounterPathObject(
  channelObject: DefaultRealtimeObject,
  path: String,
) : DefaultPathObject(channelObject, path), LiveCounterPathObject {

  @Suppress("RedundantNullableReturnType")
  override fun value(): Double? = TODO("Not yet implemented")

  override fun increment(): CompletableFuture<Void> = TODO("Not yet implemented")

  override fun increment(amount: Number): CompletableFuture<Void> = TODO("Not yet implemented")

  override fun decrement(): CompletableFuture<Void> = TODO("Not yet implemented")

  override fun decrement(amount: Number): CompletableFuture<Void> = TODO("Not yet implemented")
}
