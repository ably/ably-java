package io.ably.lib.objects.type;

import io.ably.lib.objects.ObjectsSubscription;
import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for managing subscriptions to Object lifecycle events.
 * <p>
 * This interface provides methods to subscribe to and manage notifications about significant lifecycle
 * changes that occur to Object, such as deletion. More events can be added in the future.
 * Multiple listeners can be registered independently, and each can be managed separately.
 * <p>
 * Lifecycle events are different from data update events - they represent changes
 * to the object's existence state rather than changes to the object's data content.
 *
 * @see ObjectLifecycleEvent for the available lifecycle events
 */
public interface ObjectLifecycleChange {
    /**
     * Subscribes to a specific Object lifecycle event.
     *
     * <p>This method registers the provided listener to be notified when the specified
     * lifecycle event occurs. The returned subscription can be used to
     * unsubscribe later when the notifications are no longer needed.
     *
     * @param event the lifecycle event to subscribe to
     * @param listener the listener that will be called when the event occurs
     * @return a subscription object that can be used to unsubscribe from the event
     */
    @NonBlocking
    ObjectsSubscription on(@NotNull ObjectLifecycleEvent event, @NotNull ObjectLifecycleChange.Listener listener);

    /**
     * Unsubscribes the specified listener from all lifecycle events.
     *
     * <p>After calling this method, the provided listener will no longer receive
     * any lifecycle event notifications.
     *
     * @param listener the listener to unregister from all events
     */
    @NonBlocking
    void off(@NotNull ObjectLifecycleChange.Listener listener);

    /**
     * Unsubscribes all listeners from all lifecycle events.
     *
     * <p>After calling this method, no listeners will receive any lifecycle
     * event notifications until new listeners are registered.
     */
    @NonBlocking
    void offAll();

    /**
     * Interface for receiving notifications about Object lifecycle changes.
     * <p>
     * Implement this interface and register it with an ObjectLifecycleChange provider
     * to be notified when lifecycle events occur, such as object creation or deletion.
     */
    @FunctionalInterface
    interface Listener {
        /**
         * Called when a lifecycle event occurs.
         *
         * @param lifecycleEvent The lifecycle event that occurred
         */
        void onLifecycleEvent(@NotNull ObjectLifecycleEvent lifecycleEvent);
    }
}
