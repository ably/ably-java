package io.ably.lib.objects.state;

/**
 * Interface for receiving notifications about Live Objects synchronization state changes.
 * <p>
 * Implement this interface and register it with an ObjectsStateEmitter to be notified
 * when synchronization state transitions occur.
 */
public interface ObjectsStateListener {
    /**
     * Called when the synchronization state changes.
     *
     * @param objectsStateEvent The new state event (SYNCING or SYNCED)
     */
    void onStateChanged(ObjectsStateEvent objectsStateEvent);
}
