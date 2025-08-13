package io.ably.lib.objects

import io.ably.lib.objects.state.ObjectsStateChange
import io.ably.lib.objects.state.ObjectsStateEvent
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
  ObjectsState.Syncing to ObjectsStateEvent.SYNCING,
  ObjectsState.Synced to ObjectsStateEvent.SYNCED
)

/**
 * An interface for managing and communicating changes in the synchronization state of live objects.
 *
 * Implementations should ensure thread-safe event emission and proper synchronization
 * between state change notifications.
 */
internal interface HandlesObjectsStateChange {
  /**
   * Handles changes in the state of live objects by notifying all registered listeners.
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


internal abstract class ObjectsStateCoordinator : ObjectsStateChange, HandlesObjectsStateChange {
  private val tag = "ObjectsStateCoordinator"
  private val internalObjectStateEmitter = ObjectsStateEmitter()
  // related to RTC10, should have a separate EventEmitter for users of the library
  private val externalObjectStateEmitter = ObjectsStateEmitter()

  override fun on(event: ObjectsStateEvent, listener: ObjectsStateChange.Listener): ObjectsSubscription {
    externalObjectStateEmitter.on(event, listener)
    return ObjectsSubscription {
      externalObjectStateEmitter.off(event, listener)
    }
  }

  override fun off(listener: ObjectsStateChange.Listener) = externalObjectStateEmitter.off(listener)

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
      internalObjectStateEmitter.once(ObjectsStateEvent.SYNCED) {
        Log.v(tag, "Objects state changed to SYNCED, resuming ensureSynced")
        deferred.complete(Unit)
      }
      deferred.await()
    }
  }

  override fun disposeObjectsStateListeners() = offAll()
}

private class ObjectsStateEmitter : EventEmitter<ObjectsStateEvent, ObjectsStateChange.Listener>() {
  private val tag = "ObjectsStateEmitter"
  override fun apply(listener: ObjectsStateChange.Listener?, event: ObjectsStateEvent?, vararg args: Any?) {
    try {
      listener?.onStateChanged(event!!)
    } catch (t: Throwable) {
      Log.e(tag, "Error occurred while executing listener callback for event: $event", t)
    }
  }
}
