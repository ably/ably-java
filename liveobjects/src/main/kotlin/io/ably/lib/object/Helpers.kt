package io.ably.lib.`object`

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wraps [onUnsubscribe] in a [Subscription] that runs the cleanup at most once; further
 * calls are no-ops. Use it wherever a [Subscription] is returned: `EventEmitter.off` is
 * `synchronized`, so this avoids re-acquiring that lock (and re-running teardown) on
 * repeated unsubscribe calls. Thread-safe.
 *
 * Spec: SUB2a, SUB2b
 */
internal fun onceSubscription(onUnsubscribe: () -> Unit): Subscription {
  val unsubscribed = AtomicBoolean(false)
  return Subscription {
    if (unsubscribed.compareAndSet(false, true)) {
      onUnsubscribe()
    }
  }
}
