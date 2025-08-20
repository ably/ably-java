package io.ably.lib.objects.type;

/**
 * Represents lifecycle events for an Ably Object.
 * <p>
 * This enum notifies listeners about significant lifecycle changes that occur to an Object during its lifetime.
 * Clients can register a {@link ObjectLifecycleChange.Listener} to receive these events.
 */
public enum ObjectLifecycleEvent {
    /**
     * Indicates that an Object has been deleted (tombstoned).
     * Emitted once when the object is tombstoned server-side (i.e., deleted and no longer addressable).
     * Not re-emitted during client-side garbage collection of tombstones.
     */
    DELETED
}
