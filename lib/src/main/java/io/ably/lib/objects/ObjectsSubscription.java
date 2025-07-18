package io.ably.lib.objects;

/**
 * Represents a objects subscription that can be unsubscribed from.
 * This interface provides a way to clean up and remove subscriptions when they are no longer needed.
 * Example usage:
 * <pre>
 * {@code
 * ObjectsSubscription s = objects.subscribe(ObjectsStateEvent.SYNCING, new ObjectsStateListener() {});
 * // Later when done with the subscription
 * s.unsubscribe();
 * }
 * </pre>
 */
public interface ObjectsSubscription {
    /**
     * This method should be called when the subscription is no longer needed,
     * it will make sure no further events will be sent to the subscriber and
     * that references to the subscriber are cleaned up.
     */
    void unsubscribe();
}
