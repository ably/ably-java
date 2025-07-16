package io.ably.lib.objects.state;

/**
 * Represents the synchronization state of Ably Live Objects.
 * <p>
 * This enum is used to notify listeners about state changes in the synchronization process.
 * Clients can register an {@link ObjectsStateListener} to receive these events.
 */
public enum ObjectsStateEvent {
    /**
     * Indicates that synchronization between local and remote objects is in progress.
     */
    SYNCING,

    /**
     * Indicates that synchronization has completed successfully and objects are in sync.
     */
    SYNCED
}
