package io.ably.lib.realtime;

import io.ably.lib.types.MessageFilter;

import java.util.ArrayList;

/**
 * Interface for a class that maps user-provided message filters to wrapped listeners that are subscribed
 * to the channel internaly.
 */
public interface IFilteredListeners {
    /**
     * Adds a filtered listener.
     */
    ChannelBase.MessageListener addFilteredListener(MessageFilter filter, ChannelBase.MessageListener originalListener);

    /**
     * Removes a filtered listener and returns the ones that need to be returned from the ChannelBase subscriptions.
     * @param filter
     * @param originalListener
     * @return
     */
    ArrayList<ChannelBase.MessageListener> removeFilteredListener(MessageFilter filter, ChannelBase.MessageListener originalListener);

    /**
     * Removes all the listeners.
     */
    void clear();
}
