package io.ably.lib.liveobjects.path

import io.ably.lib.liveobjects.DefaultRealtimeObject
import io.ably.lib.liveobjects.Subscription
import io.ably.lib.liveobjects.message.ObjectMessage
import io.ably.lib.liveobjects.onceSubscription
import io.ably.lib.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Registry for PathObject subscriptions and path-event dispatch. One per RealtimeObject.
 * Mirrors ably-js pathobjectsubscriptionregister.ts.
 *
 * Subscriptions may be added/removed from any thread (RTPO19 is non-blocking); dispatch always
 * happens on the sequential scope via LiveObject notifications.
 *
 * Spec: RTO24, RTO24a
 */
internal class PathObjectSubscriptionRegister(private val channelObject: DefaultRealtimeObject) {

  private val tag = "PathObjectSubscriptionRegister"

  private class SubscriptionEntry(
    val listener: PathObjectListener,
    val depth: Int?, // RTPO19c1 - null = infinite depth
    val segments: List<String>, // copied at subscribe time
  )

  private val subscriptions = ConcurrentHashMap<Long, SubscriptionEntry>()
  private val nextId = AtomicLong()

  /** Registers a subscription for [segments]. Spec: RTPO19f */
  internal fun subscribe(segments: List<String>, listener: PathObjectListener, depth: Int?): Subscription {
    val id = nextId.getAndIncrement()
    subscriptions[id] = SubscriptionEntry(listener, depth, segments.toList())
    return onceSubscription { subscriptions.remove(id) }
  }

  /**
   * Dispatches one path event: each subscription covering any candidate path is notified at
   * most once, at the first (most-preferred) covered candidate. Listener errors are caught and
   * logged without affecting other subscriptions (RTO24b2c).
   *
   * Spec: RTO24b2b
   */
  internal fun notifyPathEvent(candidatePaths: List<List<String>>, message: ObjectMessage?) {
    for (entry in subscriptions.values) {
      val chosen = candidatePaths.firstOrNull { covers(entry, it) } ?: continue // RTO24b2b
      try {
        entry.listener.onUpdated(
          DefaultPathObjectSubscriptionEvent(
            DefaultPathObject(channelObject, PathSegments.join(chosen)), // RTO24b2b1 / RTPO19e1
            message, // RTO24b2b2 / RTPO19e2
          )
        )
      } catch (t: Throwable) {
        Log.e(tag, "Error in PathObject subscription listener; path=$chosen", t) // RTO24b2c
      }
    }
  }

  /** Drops all subscriptions; called when the owning RealtimeObject is disposed. */
  internal fun dispose() = subscriptions.clear()

  /**
   * A subscription covers [eventPath] iff its path is a prefix of it (exact match included)
   * and the relative depth is within the subscription's depth window.
   *
   * Spec: RTO24c1 (worked examples RTO24c2)
   */
  private fun covers(entry: SubscriptionEntry, eventPath: List<String>): Boolean {
    val subPath = entry.segments
    if (subPath.size > eventPath.size) return false
    for (i in subPath.indices) {
      if (eventPath[i] != subPath[i]) return false
    }
    val depth = entry.depth ?: return true // null = infinite depth
    return eventPath.size - subPath.size + 1 <= depth
  }
}
