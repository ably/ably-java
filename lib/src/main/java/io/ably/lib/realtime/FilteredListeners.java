package io.ably.lib.realtime;

import io.ably.lib.types.Message;
import io.ably.lib.types.MessageFilter;

import java.util.*;

public class FilteredListeners implements IFilteredListeners {

    /**
     * All the filtered subscriptions, by filter.
     */
    private final Map<ChannelBase.MessageListener, HashMap<MessageFilter, ArrayList<FilteredMessageListener>>> filteredListeners = new HashMap<>();

    public Map<ChannelBase.MessageListener, HashMap<MessageFilter, ArrayList<FilteredMessageListener>>> getFilteredListeners() {
        return filteredListeners;
    }

    public synchronized ChannelBase.MessageListener addFilteredListener(MessageFilter filter, ChannelBase.MessageListener originalListener) {
        MessageExtrasFilter extrasFilter = new MessageExtrasFilter(filter);
        FilteredMessageListener filteredListener = new FilteredMessageListener(originalListener, extrasFilter);

        if (!filteredListeners.containsKey(originalListener)) {
            filteredListeners.put(originalListener, new HashMap<>());
        }

        HashMap<MessageFilter, ArrayList<FilteredMessageListener>> filteredListeners = this.filteredListeners.get(originalListener);
        if (!filteredListeners.containsKey(filter)) {
            filteredListeners.put(filter, new ArrayList<>());
        }

        Objects.requireNonNull(filteredListeners.get(filter)).add(filteredListener);

        return filteredListener;
    }

    public synchronized ArrayList<ChannelBase.MessageListener> removeFilteredListener(MessageFilter filter, ChannelBase.MessageListener originalListener) {

        // Only a filter passed, remove all listeners for that filter
        if (originalListener == null && filter != null) {
            return removeAllListenersForFilter(filter);
        }

        // Nothing for this listener
        if (originalListener == null || !filteredListeners.containsKey(originalListener)) {
            return new ArrayList<>();
        }

        // No filter, so remove all listeners for the specified original
        if (filter == null) {
            return removeAllFilteredListenersForListener(originalListener);
        }

        // Remove all listeners for the specified original that match the filter.
        return removeAllFilteredListenersForListenerAndFilter(originalListener, filter);
    }

    private ArrayList<ChannelBase.MessageListener> removeAllListenersForFilter(MessageFilter filter) {
        Iterator<Map.Entry<ChannelBase.MessageListener, HashMap<MessageFilter, ArrayList<FilteredMessageListener>>>> iterator = filteredListeners.entrySet().iterator();

        ArrayList<ChannelBase.MessageListener> listenersToRemove = new ArrayList<>();
        while (iterator.hasNext()) {
            Map.Entry<ChannelBase.MessageListener, HashMap<MessageFilter, ArrayList<FilteredMessageListener>>> entriesForListener = iterator.next();

            // Remove all the entries for the given filter, if it exists for the listener
            if (entriesForListener.getValue().containsKey(filter)) {
                listenersToRemove.addAll(entriesForListener.getValue().get(filter));
                entriesForListener.getValue().remove(filter);

                // If there are no filters left for the liftener now, remove the listener completely
                if (entriesForListener.getValue().isEmpty()) {
                    iterator.remove();
                }
            }
        }

        return listenersToRemove;
    }

    private ArrayList<ChannelBase.MessageListener> removeAllFilteredListenersForListener(ChannelBase.MessageListener originalListener) {
        ArrayList<ChannelBase.MessageListener> listenersToRemove = new ArrayList<>();

        HashMap<MessageFilter, ArrayList<FilteredMessageListener>> filteredListenersForListener = filteredListeners.get(originalListener);
        Iterator<Map.Entry<MessageFilter, ArrayList<FilteredMessageListener>>> iterator = filteredListenersForListener.entrySet().iterator();

        // Cycle each filter that the listener has, add the filtered listeners, and then remove the listener
        while (iterator.hasNext()) {
            listenersToRemove.addAll(iterator.next().getValue());
        }

        this.filteredListeners.remove(originalListener);

        return listenersToRemove;
    }

    private ArrayList<ChannelBase.MessageListener> removeAllFilteredListenersForListenerAndFilter(ChannelBase.MessageListener originalListener, MessageFilter filter) {

        HashMap<MessageFilter, ArrayList<FilteredMessageListener>> filteredListenersForListener = filteredListeners.get(originalListener);
        if (!filteredListenersForListener.containsKey(filter)) {
            return new ArrayList<>();
        }

        ArrayList<ChannelBase.MessageListener> listenersToRemove = new ArrayList<ChannelBase.MessageListener>() {{
            addAll(filteredListenersForListener.get(filter));
        }};

        filteredListenersForListener.remove(filter);

        if (filteredListenersForListener.isEmpty()) {
            filteredListeners.remove(originalListener);
        }

        return listenersToRemove;
    }

    /**
     * Interface for a class that can filter messages for us, based on the user-provided
     * message filters.
     */
    public interface IMessageFilter {
        boolean onMessage(Message message);
    }

    /**
     * Filtered message listener that only sends the event to the actual listener if
     * the filter passes.
     */
    private static class FilteredMessageListener implements ChannelBase.MessageListener {
        private final ChannelBase.MessageListener listener;

        private final IMessageFilter filter;

        private FilteredMessageListener(ChannelBase.MessageListener listener, IMessageFilter filter) {
            this.listener = listener;
            this.filter = filter;
        }

        @Override
        public void onMessage(Message message) {
            if (filter != null && !filter.onMessage(message)) {
                return;
            }

            listener.onMessage(message);
        }
    }
}
