package io.ably.lib.objects

import io.ably.lib.objects.state.ObjectsStateEvent
import io.ably.lib.objects.state.ObjectsStateListener
import io.ably.lib.util.EventEmitter

/**
 * @spec RTO2 - enum representing objects state
 */
internal enum class ObjectsState {
  INITIALIZED,
  SYNCING,
  SYNCED
}

internal val objectsStateToEventMap = mapOf(
  ObjectsState.INITIALIZED to null,
  ObjectsState.SYNCING to ObjectsStateEvent.SYNCING,
  ObjectsState.SYNCED to ObjectsStateEvent.SYNCED
)

internal class ObjectsStateEmitter : EventEmitter<ObjectsStateEvent, ObjectsStateListener>() {
  override fun apply(listener: ObjectsStateListener?, event: ObjectsStateEvent?, vararg args: Any?) {
    listener?.onStateChanged(event!!)
  }
}
