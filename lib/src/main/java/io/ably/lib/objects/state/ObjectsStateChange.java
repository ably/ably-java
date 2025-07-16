package io.ably.lib.objects.state;

import io.ably.lib.objects.ObjectsSubscription;
import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.NotNull;

public interface ObjectsStateChange {
    /**
     * Subscribes to a specific Live Objects synchronization state event.
     *
     * <p>This method registers the provided listener to be notified when the specified
     * synchronization state event occurs. The returned subscription can be used to
     * unsubscribe later when the notifications are no longer needed.
     *
     * @param event the synchronization state event to subscribe to (SYNCING or SYNCED)
     * @param listener the listener that will be called when the event occurs
     * @return a subscription object that can be used to unsubscribe from the event
     */
    @NonBlocking
    ObjectsSubscription on(@NotNull ObjectsStateEvent event, @NotNull ObjectsStateChange.Listener listener);

    /**
     * Unsubscribes the specified listener from all synchronization state events.
     *
     * <p>After calling this method, the provided listener will no longer receive
     * any synchronization state event notifications.
     *
     * @param listener the listener to unregister from all events
     */
    @NonBlocking
    void off(@NotNull ObjectsStateChange.Listener listener);

    /**
     * Unsubscribes all listeners from all synchronization state events.
     *
     * <p>After calling this method, no listeners will receive any synchronization
     * state event notifications until new listeners are registered.
     */
    @NonBlocking
    void offAll();

    /**
     * Interface for receiving notifications about Live Objects synchronization state changes.
     * <p>
     * Implement this interface and register it with an ObjectsStateEmitter to be notified
     * when synchronization state transitions occur.
     */
    interface Listener {
        /**
         * Called when the synchronization state changes.
         *
         * @param objectsStateEvent The new state event (SYNCING or SYNCED)
         */
        void onStateChanged(ObjectsStateEvent objectsStateEvent);
    }
}
