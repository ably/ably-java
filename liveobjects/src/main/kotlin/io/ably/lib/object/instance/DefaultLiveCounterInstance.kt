package io.ably.lib.`object`.instance

import io.ably.lib.`object`.CounterNode
import io.ably.lib.`object`.DefaultSubscription
import io.ably.lib.`object`.ObjectsBridge
import io.ably.lib.`object`.ResolvedValue
import io.ably.lib.`object`.Subscription
import io.ably.lib.`object`.WireCounterInc
import io.ably.lib.`object`.WireObjectMessage
import io.ably.lib.`object`.WireObjectOperation
import io.ably.lib.`object`.WireObjectOperationAction
import io.ably.lib.`object`.instance.types.LiveCounterInstance
import io.ably.lib.`object`.invalidInputError
import io.ably.lib.`object`.message.toPublicMessage
import io.ably.lib.`object`.typeMismatchError
import java.util.concurrent.CompletableFuture

/**
 * Typed Instance over an InternalLiveCounter.
 *
 * Spec: RTTS10b
 */
internal class DefaultLiveCounterInstance(
  bridge: ObjectsBridge,
  value: ResolvedValue,
) : DefaultBaseInstance(bridge, value), LiveCounterInstance {

  /** The wrapped counter node; mismatched as* wrappers fail per RTTS9d2 (92007). */
  private fun counterNodeOrThrow(): CounterNode = (value as? ResolvedValue.CounterRef)?.counter
    ?: throw typeMismatchError("Instance does not wrap a LiveCounter")

  /** Spec: RTINS3a / RTTS10b - non-null (wrapped object always has an id) */
  override fun getId(): String = counterNodeOrThrow().objectId

  /** Spec: RTINS4 / RTTS10b - non-null Double */
  override fun value(): Double {
    bridge.throwIfInvalidAccessApiConfiguration() // RTO25
    return counterNodeOrThrow().count()
  }

  /** Spec: RTINS14 - amount defaults to 1 */
  override fun increment(): CompletableFuture<Void> = increment(1)

  /** Spec: RTINS14 */
  override fun increment(amount: Number): CompletableFuture<Void> = applyIncrement(amount.toDouble())

  /** Spec: RTINS15 - amount defaults to 1 */
  override fun decrement(): CompletableFuture<Void> = decrement(1)

  /** Spec: RTINS15 */
  override fun decrement(amount: Number): CompletableFuture<Void> = applyIncrement(-amount.toDouble())

  private fun applyIncrement(amount: Double): CompletableFuture<Void> = bridge.launchWithVoidFuture {
    bridge.throwIfInvalidWriteApiConfiguration() // RTINS14b/RTINS15b / RTO26
    if (amount.isNaN() || amount.isInfinite()) {
      throw invalidInputError("Counter amount must be a valid number")
    }
    val node = counterNodeOrThrow() // RTINS14d/RTINS15d - 92007 on mismatched wrapper
    val message = WireObjectMessage(
      operation = WireObjectOperation(
        action = WireObjectOperationAction.CounterInc,
        objectId = node.objectId,
        counterInc = WireCounterInc(number = amount),
      )
    )
    bridge.publish(listOf(message))
  }

  /** Spec: RTINS16 / RTTS10b - delivers both object and message (RTINS16e) */
  override fun subscribe(listener: InstanceListener): Subscription {
    bridge.throwIfInvalidAccessApiConfiguration()
    val node = counterNodeOrThrow() // RTINS16c
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
