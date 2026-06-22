package io.ably.lib.`object`.path.types

import io.ably.lib.`object`.DefaultRealtimeObject
import io.ably.lib.`object`.path.DefaultPathObject
import io.ably.lib.`object`.pathNotResolvedError
import io.ably.lib.`object`.typeMismatchError
import io.ably.lib.`object`.value.ResolvedValue
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

  override fun value(): Double? {
    channelObject.throwIfInvalidAccessApiConfiguration()
    if (resolveValueAtPath(path) !is ResolvedValue.CounterRef) return null // not a LiveCounter (or unresolved) -> null
    // TODO - return the resolved counter's value
    TODO("Not yet implemented")
  }

  override fun increment(): CompletableFuture<Void> {
    channelObject.throwIfInvalidWriteApiConfiguration()
    val resolvedValue = resolveValueAtPath(path) ?: throw pathNotResolvedError(path)
    if (resolvedValue !is ResolvedValue.CounterRef) {
      throw typeMismatchError("Cannot increment a non-LiveCounter object at path: \"$path\"")
    }
    // TODO - delegate the COUNTER_INC (amount 1) to the resolved LiveCounter
    TODO("Not yet implemented")
  }

  override fun increment(amount: Number): CompletableFuture<Void> {
    channelObject.throwIfInvalidWriteApiConfiguration()
    val resolvedValue = resolveValueAtPath(path) ?: throw pathNotResolvedError(path)
    if (resolvedValue !is ResolvedValue.CounterRef) {
      throw typeMismatchError("Cannot increment a non-LiveCounter object at path: \"$path\"")
    }
    // TODO - delegate the COUNTER_INC to the resolved LiveCounter
    TODO("Not yet implemented")
  }

  override fun decrement(): CompletableFuture<Void> {
    channelObject.throwIfInvalidWriteApiConfiguration()
    val resolvedValue = resolveValueAtPath(path) ?: throw pathNotResolvedError(path)
    if (resolvedValue !is ResolvedValue.CounterRef) {
      throw typeMismatchError("Cannot decrement a non-LiveCounter object at path: \"$path\"")
    }
    // TODO - delegate the COUNTER_INC (negated amount 1) to the resolved LiveCounter
    TODO("Not yet implemented")
  }

  override fun decrement(amount: Number): CompletableFuture<Void> {
    channelObject.throwIfInvalidWriteApiConfiguration()
    val resolvedValue = resolveValueAtPath(path) ?: throw pathNotResolvedError(path)
    if (resolvedValue !is ResolvedValue.CounterRef) {
      throw typeMismatchError("Cannot decrement a non-LiveCounter object at path: \"$path\"")
    }
    // TODO - delegate the COUNTER_INC (negated amount) to the resolved LiveCounter
    TODO("Not yet implemented")
  }
}
