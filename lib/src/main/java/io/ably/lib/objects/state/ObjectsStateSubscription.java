package io.ably.lib.objects.state;

/**
 * Represents a subscription that can be unsubscribed from.
 * This interface provides a way to clean up and remove subscriptions when they
 * are no longer needed.
 * Example usage:
 * ```java
 * ObjectsStateSubscription s = objects.subscribe(ObjectsStateEvent.SYNCING, new ObjectsStateListener() {});
 * // Later when done with the subscription
 * s.unsubscribe();
 */
public interface ObjectsStateSubscription {
    /**
     * This method should be called when the subscription is no longer needed,
     * it will make sure no further events will be sent to the subscriber and
     * that references to the subscriber are cleaned up.
     */
    void unsubscribe();
}
