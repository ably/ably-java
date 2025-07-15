package io.ably.lib.objects

import io.ably.lib.util.EventEmitter

/**
 * @spec RTO2 - enum representing objects state
 */
internal enum class ObjectsState {
  INITIALIZED,
  SYNCING,
  SYNCED
}

public enum class ObjectsEvent {
  SYNCING,
  SYNCED
}

internal val objectsStateToEventMap = mapOf(
  ObjectsState.INITIALIZED to null,
  ObjectsState.SYNCING to ObjectsEvent.SYNCING,
  ObjectsState.SYNCED to ObjectsEvent.SYNCED
)

public fun interface ObjectsStateListener {
  public fun onStateChanged(state: ObjectsEvent)
}

internal class ObjectsStateEmitter : EventEmitter<ObjectsEvent, ObjectsStateListener>() {
  override fun apply(listener: ObjectsStateListener?, event: ObjectsEvent?, vararg args: Any?) {
    listener?.onStateChanged(event!!)
  }
}
