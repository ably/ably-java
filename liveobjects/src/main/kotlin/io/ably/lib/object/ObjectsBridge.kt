package io.ably.lib.`object`

import io.ably.lib.util.Log
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import io.ably.lib.types.AblyException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.launch

/**
 * The single abstract seam between this package and the realtime objects
 * system. The path/instance implementation classes depend ONLY on this
 * contract; a bridge (implemented outside this package, alongside the
 * realtime internals) provides the graph views, preconditions, publishing and
 * update fan-in. This keeps `io.ably.lib.object` free of any dependency on
 * `io.ably.lib.objects`.
 */
internal abstract class ObjectsBridge {

  /** The channel this objects instance belongs to (used for PAOM2e/PAOM3b). */
  internal abstract val channelName: String

  /** The root InternalLiveMap view (objectId `root`), or null if unavailable. Spec: RTO3, RTPO2b */
  internal abstract fun getRootNode(): MapNode?

  /** Looks up a non-tombstoned object view by id, or null. Spec: RTO3a */
  internal abstract fun getNode(objectId: String): ObjectsNode?

  /** Access API preconditions; throws ErrorInfo-carrying AblyException on failure. Spec: RTO25 */
  internal abstract fun throwIfInvalidAccessApiConfiguration()

  /** Write API preconditions; throws ErrorInfo-carrying AblyException on failure. Spec: RTO26 */
  internal abstract fun throwIfInvalidWriteApiConfiguration()

  /** Publishes the messages and applies them locally on ACK. Spec: RTO15, RTO20 */
  internal abstract suspend fun publish(messages: List<WireObjectMessage>)

  /** Current server time in epoch milliseconds. Spec: RTO16 */
  internal abstract suspend fun getServerTime(): Long

  /** Ensures the channel is attached and objects are SYNCED. Spec: RTO23b, RTO23c, RTO23e */
  internal abstract suspend fun ensureAttachedAndSynced()

  /**
   * Registry for path-based subscriptions (RTPO19). Bridge implementations
   * feed it via [notifyUpdated].
   */
  internal val pathSubscriptionRegister = PathObjectSubscriptionRegister(this)

  /** Scope used to expose suspend write operations as CompletableFutures. */
  private val asyncScope =
    CoroutineScope(Dispatchers.Default + CoroutineName("ObjectsBridge") + SupervisorJob())

  /** Per-object message-carrying update listeners (instance subscriptions, RTINS16). */
  private val updateListeners =
    ConcurrentHashMap<String, CopyOnWriteArrayList<(Set<String>, WireObjectMessage?) -> Unit>>()

  /**
   * Subscribes to updates applied to the object with [objectId]. The listener
   * receives the set of updated map keys (empty for counters) and the source
   * message when the update originated from an operation. Returns an
   * unsubscribe handle.
   */
  internal fun subscribeToUpdates(objectId: String, listener: (Set<String>, WireObjectMessage?) -> Unit): () -> Unit {
    val listeners = updateListeners.computeIfAbsent(objectId) { CopyOnWriteArrayList() }
    listeners.add(listener)
    return { listeners.remove(listener) }
  }

  /**
   * Entry point for bridge implementations: call after an update has been
   * applied to an object, with the keys that changed (empty for counters) and
   * the source ObjectMessage when the update came from an operation (null for
   * sync-induced changes). Fans out to instance subscriptions (RTINS16) and
   * path subscriptions (RTPO19).
   */
  internal fun notifyUpdated(objectId: String, updatedKeys: Set<String>, message: WireObjectMessage?) {
    updateListeners[objectId]?.forEach { listener ->
      try {
        listener(updatedKeys, message)
      } catch (t: Throwable) {
        Log.e("ObjectsBridge", "Error in update listener for objectId=$objectId", t)
      }
    }
    pathSubscriptionRegister.notifyObjectUpdated(objectId, updatedKeys, message)
  }

  /**
   * Runs a suspend write and exposes it as a CompletableFuture<Void>;
   * failures complete exceptionally with the underlying AblyException.
   */
  internal fun launchWithVoidFuture(block: suspend () -> Unit): CompletableFuture<Void> {
    val future = CompletableFuture<Void>()
    asyncScope.launch {
      try {
        block()
        future.complete(null)
      } catch (throwable: Throwable) {
        when (throwable) {
          is AblyException -> future.completeExceptionally(throwable)
          else -> future.completeExceptionally(
            objectsException("Error executing operation", ObjectErrorCode.BadRequest, cause = throwable)
          )
        }
      }
    }
    return future
  }
}
