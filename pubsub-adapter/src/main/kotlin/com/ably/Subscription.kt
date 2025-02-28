package com.ably

/**
 * An unsubscription handle, returned by various functions (mostly subscriptions)
 * where unsubscription is required.
 */
public fun interface Subscription {
  /**
   * Handle unsubscription (unsubscribe listeners, clean up)
   */
  public fun unsubscribe()
}
