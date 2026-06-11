package io.ably.lib.`object`.path

import io.ably.lib.`object`.CounterNode
import io.ably.lib.`object`.ObjectsBridge
import io.ably.lib.`object`.ResolvedValue
import io.ably.lib.`object`.WireCounterInc
import io.ably.lib.`object`.WireObjectMessage
import io.ably.lib.`object`.WireObjectOperation
import io.ably.lib.`object`.WireObjectOperationAction
import io.ably.lib.`object`.invalidInputError
import io.ably.lib.`object`.path.types.LiveCounterPathObject
import io.ably.lib.`object`.pathNotResolvedError
import io.ably.lib.`object`.typeMismatchError
import java.util.concurrent.CompletableFuture

/**
 * Typed PathObject expected to resolve to an InternalLiveCounter.
 *
 * Spec: RTTS6b
 */
internal class DefaultLiveCounterPathObject(
  bridge: ObjectsBridge,
  pathSegments: List<String>,
) : DefaultBasePathObject(bridge, pathSegments), LiveCounterPathObject {

  /** Resolves and requires a counter for write operations: 92005 / 92007 per RTPO3c2 / RTTS5d2. */
  private fun counterNodeForWrite(): CounterNode = when (val resolved = resolve()) {
    null -> throw pathNotResolvedError(path()) // RTPO3c2 - 92005
    is ResolvedValue.CounterRef -> resolved.counter
    else -> throw typeMismatchError("Value at path \"${path()}\" is not a LiveCounter") // RTTS5d2 - 92007
  }

  /** Spec: RTPO7 / RTTS6b - null when the path does not resolve to a counter (RTPO3c1, RTPO7e) */
  override fun value(): Double? {
    bridge.throwIfInvalidAccessApiConfiguration() // RTPO7a / RTO25
    return (resolve() as? ResolvedValue.CounterRef)?.counter?.count()
  }

  /** Spec: RTPO17 - amount defaults to 1 */
  override fun increment(): CompletableFuture<Void> = increment(1)

  /** Spec: RTPO17 */
  override fun increment(amount: Number): CompletableFuture<Void> = applyIncrement(amount.toDouble())

  /** Spec: RTPO18 - amount defaults to 1 */
  override fun decrement(): CompletableFuture<Void> = decrement(1)

  /** Spec: RTPO18 */
  override fun decrement(amount: Number): CompletableFuture<Void> = applyIncrement(-amount.toDouble())

  private fun applyIncrement(amount: Double): CompletableFuture<Void> = bridge.launchWithVoidFuture {
    bridge.throwIfInvalidWriteApiConfiguration() // RTPO17a/RTPO18a / RTO26
    if (amount.isNaN() || amount.isInfinite()) {
      throw invalidInputError("Counter amount must be a valid number")
    }
    val node = counterNodeForWrite()
    val message = WireObjectMessage(
      operation = WireObjectOperation(
        action = WireObjectOperationAction.CounterInc,
        objectId = node.objectId,
        counterInc = WireCounterInc(number = amount),
      )
    )
    bridge.publish(listOf(message))
  }
}
