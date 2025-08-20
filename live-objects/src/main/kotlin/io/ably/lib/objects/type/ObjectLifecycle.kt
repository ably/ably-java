package io.ably.lib.objects.type

import io.ably.lib.objects.ObjectsSubscription
import io.ably.lib.util.EventEmitter
import io.ably.lib.util.Log

/**
 * Internal enum representing object lifecycle states
 */
internal enum class ObjectLifecycle {
  Created,
  Active,
  Deleted
}

/**
 * Maps internal ObjectLifecycle values to their corresponding public ObjectLifecycleEvent values.
 * Used to determine which events should be emitted when lifecycle changes occur.
 * CREATED and ACTIVE map to null (no public event), while DELETED maps to the public DELETED event.
 */
private val objectLifecycleToEventMap = mapOf(
  ObjectLifecycle.Created to null,
  ObjectLifecycle.Active to null,
  ObjectLifecycle.Deleted to ObjectLifecycleEvent.DELETED
)

/**
 * An interface for managing and communicating changes in the lifecycle state of objects.
 *
 * Implementations should ensure thread-safe event emission and proper lifecycle
 * event notifications.
 */
internal interface HandlesObjectLifecycleChange {
  /**
   * Handles changes in the lifecycle of objects by notifying all registered listeners.
   * Implementations should ensure thread-safe event emission to both internal and public listeners.
   * Makes sure every event is processed in the order they were received.
   * @param newLifecycle The new lifecycle state of the object.
   */
  fun objectLifecycleChanged(newLifecycle: ObjectLifecycle)

  /**
   * Disposes all registered lifecycle change listeners and cancels any pending operations.
   * Should be called when the associated object is no longer needed.
   */
  fun disposeObjectLifecycleListeners()
}

internal abstract class ObjectLifecycleCoordinator : ObjectLifecycleChange, HandlesObjectLifecycleChange {
  private val tag = "ObjectLifecycleCoordinator"
  // EventEmitter for users of the library
  private val objectLifecycleEmitter = ObjectLifecycleEmitter()

  override fun on(event: ObjectLifecycleEvent, listener: ObjectLifecycleChange.Listener): ObjectsSubscription {
    objectLifecycleEmitter.on(event, listener)
    return ObjectsSubscription {
      objectLifecycleEmitter.off(event, listener)
    }
  }

  override fun off(listener: ObjectLifecycleChange.Listener) = objectLifecycleEmitter.off(listener)

  override fun offAll() = objectLifecycleEmitter.off()

  override fun objectLifecycleChanged(newLifecycle: ObjectLifecycle) {
    objectLifecycleToEventMap[newLifecycle]?.let { objectLifecycleEvent ->
      objectLifecycleEmitter.emit(objectLifecycleEvent)
    }
  }

  override fun disposeObjectLifecycleListeners() = offAll()
}

private class ObjectLifecycleEmitter : EventEmitter<ObjectLifecycleEvent, ObjectLifecycleChange.Listener>() {
  private val tag = "ObjectLifecycleEmitter"
  override fun apply(listener: ObjectLifecycleChange.Listener?, event: ObjectLifecycleEvent?, vararg args: Any?) {
    try {
      event?.let { listener?.onLifecycleEvent(it) }
        ?: Log.w(tag, "Null event passed to ObjectLifecycleChange listener callback")
    } catch (t: Throwable) {
      Log.e(tag, "Error occurred while executing listener callback for event: $event", t)
    }
  }
}
