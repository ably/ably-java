package io.ably.lib.objects.type.map;

import io.ably.lib.objects.ObjectsSubscription;
import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.NotNull;

/**
 * Provides methods to subscribe to real-time updates on LiveMap objects.
 * Enables clients to receive notifications when map entries are added, updated, or removed.
 * Uses last-write-wins conflict resolution when multiple clients modify the same key.
 */
public interface LiveMapChange {

    /**
     * Subscribes to real-time updates on this LiveMap object.
     * Multiple listeners can be subscribed to the same object independently.
     * 
     * @param listener the listener to be notified of map updates
     * @return an ObjectsSubscription for managing this specific listener
     */
    @NonBlocking
    @NotNull ObjectsSubscription subscribe(@NotNull Listener listener);

    /**
     * Unsubscribes a specific listener from receiving updates.
     * Has no effect if the listener is not currently subscribed.
     * 
     * @param listener the listener to be unsubscribed
     */
    @NonBlocking
    void unsubscribe(@NotNull Listener listener);

    /**
     * Unsubscribes all listeners from receiving updates.
     * No notifications will be delivered until new listeners are subscribed.
     */
    @NonBlocking
    void unsubscribeAll();

    /**
     * Listener interface for receiving LiveMap updates.
     */
    interface Listener {
        /**
         * Called when the LiveMap has been updated.
         * Should execute quickly as it's called from the real-time processing thread.
         * 
         * @param update details about which keys were modified and how
         */
        void onUpdated(@NotNull LiveMapUpdate update);
    }
}
