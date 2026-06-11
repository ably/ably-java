package io.ably.lib.`object`

import io.ably.lib.`object`.Subscription

/**
 * Implementation of the public [Subscription] handle returned by the
 * `subscribe` methods of the path/instance APIs.
 *
 * Spec: SUB1, SUB2a, SUB2b (idempotent unsubscribe)
 */
internal class DefaultSubscription(private val onUnsubscribe: () -> Unit) : Subscription {

  @Volatile
  private var unsubscribed = false

  override fun unsubscribe() {
    if (unsubscribed) return // SUB2b - subsequent calls are no-ops
    unsubscribed = true
    onUnsubscribe()
  }
}
