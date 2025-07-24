package io.ably.lib.objects.type.counter;

import io.ably.lib.objects.ObjectsSubscription;
import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.NotNull;

/**
 * Provides methods to subscribe to real-time updates on LiveCounter objects.
 * Enables clients to receive notifications when counter values change due to
 * operations performed by any client connected to the same channel.
 */
public interface LiveCounterChange {

    /**
     * Subscribes to real-time updates on this LiveCounter object.
     * Multiple listeners can be subscribed to the same object independently.
     * Spec: RTLO4b
     *
     * @param listener the listener to be notified of counter updates
     * @return an ObjectsSubscription for managing this specific listener
     */
    @NonBlocking
    @NotNull ObjectsSubscription subscribe(@NotNull Listener listener);

    /**
     * Unsubscribes a specific listener from receiving updates.
     * Has no effect if the listener is not currently subscribed.
     * Spec: RTLO4c
     *
     * @param listener the listener to be unsubscribed
     */
    @NonBlocking
    void unsubscribe(@NotNull Listener listener);

    /**
     * Unsubscribes all listeners from receiving updates.
     * No notifications will be delivered until new listeners are subscribed.
     * Spec: RTLO4d
     */
    @NonBlocking
    void unsubscribeAll();

    /**
     * Listener interface for receiving LiveCounter updates.
     * Spec: RTLO4b3
     */
    interface Listener {
        /**
         * Called when the LiveCounter has been updated.
         * Should execute quickly as it's called from the real-time processing thread.
         *
         * @param update details about the counter change
         */
        void onUpdated(@NotNull LiveCounterUpdate update);
    }
}
