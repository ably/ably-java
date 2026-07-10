package io.ably.lib.liveobjects

import io.ably.lib.liveobjects.state.ObjectStateChange
import io.ably.lib.liveobjects.state.ObjectStateEvent
import io.ably.lib.types.AblyException
import io.ably.lib.util.EventEmitter
import io.ably.lib.util.Log
import kotlinx.coroutines.*

/**
 * @spec RTO2 - enum representing objects state
 */
internal enum class ObjectsState {
  Initialized,
  Syncing,
  Synced
}

/**
 * Maps internal ObjectsState values to their corresponding public ObjectsStateEvent values.
 * Used to determine which events should be emitted when state changes occur.
 * INITIALIZED maps to null (no event), while SYNCING and SYNCED map to their respective events.
 */
private val objectsStateToEventMap = mapOf(
  ObjectsState.Initialized to null,
  ObjectsState.Syncing to ObjectStateEvent.SYNCING,
  ObjectsState.Synced to ObjectStateEvent.SYNCED
)

/**
 * An interface for managing and communicating changes in the synchronization state of objects.
 *
 * Implementations should ensure thread-safe event emission and proper synchronization
 * between state change notifications.
 */
internal interface HandlesObjectsStateChange {
  /**
   * Handles changes in the state of objects by notifying all registered listeners.
   * Implementations should ensure thread-safe event emission to both internal and public listeners.
   * Makes sure every event is processed in the order they were received.
   * @param newState The new state of the objects, SYNCING or SYNCED.
   */
  fun objectsStateChanged(newState: ObjectsState)

  /**
   * Suspends the current coroutine until objects are synchronized.
   * Returns immediately if state is already SYNCED, otherwise waits for the SYNCED event.
   *
   * @param currentState The current state of objects to determine if waiting is necessary
   */
  suspend fun ensureSynced(currentState: ObjectsState)

  /**
   * Disposes all registered state change listeners and cancels any pending operations.
   * Should be called when the associated RealtimeObjects instance is no longer needed.
   */
  fun disposeObjectsStateListeners()
}


internal abstract class ObjectsStateCoordinator : ObjectStateChange, HandlesObjectsStateChange {
  private val tag = "ObjectsStateCoordinator"
  private val internalObjectStateEmitter = ObjectsStateEmitter()
  // related to RTC10, should have a separate EventEmitter for users of the library
  private val externalObjectStateEmitter = ObjectsStateEmitter()

  /**
   * Pending publishAndApply waiters (RTO20e): each suspends until SYNCED, and is failed (RTO20e1) — rather
   * than orphaned — if the channel enters DETACHED/SUSPENDED/FAILED while waiting. All access happens on the
   * single sequential scope (see [DefaultRealtimeObject]), so no additional synchronization is required.
   */
  private val pendingSyncWaiters = mutableSetOf<CompletableDeferred<Unit>>()

  override fun on(event: ObjectStateEvent, listener: ObjectStateChange.Listener): Subscription {
    externalObjectStateEmitter.on(event, listener)
    return onceSubscription {
      externalObjectStateEmitter.off(event, listener)
    }
  }

  override fun off(listener: ObjectStateChange.Listener) = externalObjectStateEmitter.off(listener)

  override fun offAll() = externalObjectStateEmitter.off()

  override fun objectsStateChanged(newState: ObjectsState) {
    objectsStateToEventMap[newState]?.let { objectsStateEvent ->
      internalObjectStateEmitter.emit(objectsStateEvent)
      externalObjectStateEmitter.emit(objectsStateEvent)
    }
  }

  override suspend fun ensureSynced(currentState: ObjectsState) {
    if (currentState != ObjectsState.Synced) {
      val deferred = CompletableDeferred<Unit>()
      val syncedListener = ObjectStateChange.Listener {
        Log.v(tag, "Objects state changed to SYNCED, resuming ensureSynced")
        deferred.complete(Unit)
      }
      internalObjectStateEmitter.once(ObjectStateEvent.SYNCED, syncedListener)
      try {
        deferred.await()
      } finally {
        // off() the one-shot on either path (same cleanup pattern as awaitSyncCompletion) so it never
        // lingers if the waiting coroutine is cancelled (e.g. dispose while a get() awaits sync).
        internalObjectStateEmitter.off(ObjectStateEvent.SYNCED, syncedListener)
      }
    }
  }

  /**
   * Suspends until objects transition to SYNCED (via a one-shot SYNCED listener), or throws if the channel
   * leaves a usable state while waiting ([failSyncWaiters], RTO20e1). Unlike [ensureSynced], the waiter is
   * tracked in [pendingSyncWaiters] so it can be failed rather than orphaned across re-syncs (the SYNCED
   * event resolves whichever sync ultimately completes, regardless of how many sync cycles occur while
   * waiting).
   *
   * Spec: RTO20e, RTO20e1
   */
  protected suspend fun awaitSyncCompletion() {
    val deferred = CompletableDeferred<Unit>()
    pendingSyncWaiters.add(deferred)
    // Keep a reference to the one-shot listener so it can be removed on either resolution path (mirrors
    // ably-js publishAndApply's cleanup()). The once() semantics already drop it when SYNCED fires, but we
    // off() it explicitly in finally so it never lingers when the wait ends via failSyncWaiters (RTO20e1).
    val syncedListener = ObjectStateChange.Listener {
      Log.v(tag, "Objects state changed to SYNCED, resuming pending publishAndApply")
      deferred.complete(Unit)
    }
    internalObjectStateEmitter.once(ObjectStateEvent.SYNCED, syncedListener)
    try {
      deferred.await()
    } finally {
      pendingSyncWaiters.remove(deferred)
      internalObjectStateEmitter.off(ObjectStateEvent.SYNCED, syncedListener)
    }
  }

  /**
   * Fails every pending [awaitSyncCompletion] waiter — called when the channel enters
   * DETACHED/SUSPENDED/FAILED while a publishAndApply is waiting for SYNCED. Matches ably-js, which only
   * catches channel-state transitions that fire after a waiter has started waiting (once() semantics).
   *
   * Spec: RTO20e1
   */
  fun failSyncWaiters(error: AblyException) {
    if (pendingSyncWaiters.isEmpty()) return
    val waiters = pendingSyncWaiters.toList()
    pendingSyncWaiters.clear()
    waiters.forEach { it.completeExceptionally(error) }
  }

  override fun disposeObjectsStateListeners() = offAll()
}

private class ObjectsStateEmitter : EventEmitter<ObjectStateEvent, ObjectStateChange.Listener>() {
  private val tag = "ObjectsStateEmitter"
  override fun apply(listener: ObjectStateChange.Listener?, event: ObjectStateEvent?, vararg args: Any?) {
    try {
      event?.let { listener?.onStateChanged(it) }
        ?: Log.w(tag, "Null event passed to ObjectsStateChange Listener callback")
    } catch (t: Throwable) {
      Log.e(tag, "Error occurred while executing listener callback for event: $event", t)
    }
  }
}
