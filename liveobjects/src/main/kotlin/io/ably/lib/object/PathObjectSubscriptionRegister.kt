package io.ably.lib.`object`

import io.ably.lib.`object`.message.toPublicMessage
import io.ably.lib.`object`.path.DefaultPathObject
import io.ably.lib.`object`.path.DefaultPathObjectSubscriptionEvent
import io.ably.lib.`object`.path.PathObjectListener
import io.ably.lib.`object`.path.PathObjectSubscriptionOptions
import io.ably.lib.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Registry for path-based subscriptions: stores listeners keyed by
 * subscription id, matches applied object updates against subscription paths
 * using the depth coverage rule, and delivers PathObjectSubscriptionEvents.
 *
 * Mirrors ably-js `pathobjectsubscriptionregister.ts`.
 *
 * Spec: RTPO19, RTO24c1
 */
internal class PathObjectSubscriptionRegister(private val bridge: ObjectsBridge) {

  private val tag = "PathObjectSubscriptionRegister"

  private data class SubscriptionEntry(
    val listener: PathObjectListener,
    val depth: Int?, // null = infinite depth (RTPO19c1)
    val path: List<String>,
  )

  private val subscriptions = ConcurrentHashMap<Long, SubscriptionEntry>()
  private val nextSubscriptionId = AtomicLong(0)

  /**
   * Registers a listener for updates at (and below, per the depth rule) the
   * given path. Depth validity (RTPO19c1a) is enforced by the
   * [PathObjectSubscriptionOptions] constructor and needs no re-check here.
   *
   * Spec: RTPO19
   */
  internal fun subscribe(
    path: List<String>,
    listener: PathObjectListener,
    options: PathObjectSubscriptionOptions?,
  ): Subscription {
    val id = nextSubscriptionId.incrementAndGet()
    subscriptions[id] = SubscriptionEntry(listener, options?.depth, path)
    return DefaultSubscription { subscriptions.remove(id) } // SUB2a
  }

  /**
   * Routes an applied object update to covered subscriptions. Candidate paths
   * are priority-ordered: each full path to the updated object first, then -
   * for map updates - the path to each updated key. The first candidate
   * covered by a subscription determines the event's PathObject.
   *
   * Mirrors ably-js `liveobject.ts#_notifyPathSubscriptions` +
   * `pathobjectsubscriptionregister.ts#notifyPathEvent`.
   */
  internal fun notifyObjectUpdated(objectId: String, updatedKeys: Set<String>, message: WireObjectMessage?) {
    if (subscriptions.isEmpty()) return // fast path - path API unused on this channel

    val fullPaths = PathFinder.findFullPaths(bridge, objectId)
    if (fullPaths.isEmpty()) return // object not reachable from root

    val candidatePaths = fullPaths.flatMap { fullPath ->
      listOf(fullPath) + updatedKeys.map { key -> fullPath + key }
    }

    val publicMessage = message?.let {
      try {
        it.toPublicMessage(bridge.channelName)
      } catch (t: Throwable) {
        Log.w(tag, "Failed to build public ObjectMessage for path event", t)
        null
      }
    }

    for (entry in subscriptions.values) {
      // first candidate covered by this subscription wins (priority order)
      val coveredPath = candidatePaths.firstOrNull { coversPath(entry.path, entry.depth, it) } ?: continue
      val event = DefaultPathObjectSubscriptionEvent(
        DefaultPathObject(bridge, coveredPath), // RTPO19e1
        publicMessage, // RTPO19e2
      )
      try {
        entry.listener.onUpdated(event)
      } catch (t: Throwable) {
        Log.e(tag, "Error in PathObjectListener callback", t)
      }
    }
  }

  /**
   * The subscription coverage rule: the event path must start with the
   * subscription path and extend it by at most `depth - 1` further segments;
   * null depth means no limit.
   *
   * Spec: RTO24c1 (worked examples in RTO24c2)
   */
  internal fun coversPath(subscriptionPath: List<String>, depth: Int?, eventPath: List<String>): Boolean {
    if (eventPath.size < subscriptionPath.size) return false
    for (i in subscriptionPath.indices) {
      if (subscriptionPath[i] != eventPath[i]) return false
    }
    if (depth == null) return true // infinite depth
    val relativeDepth = eventPath.size - subscriptionPath.size + 1
    return relativeDepth <= depth
  }
}
