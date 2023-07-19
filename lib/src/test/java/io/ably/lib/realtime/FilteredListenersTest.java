package io.ably.lib.realtime;

import com.google.gson.JsonObject;
import io.ably.lib.types.Message;
import io.ably.lib.types.MessageExtras;
import io.ably.lib.types.MessageFilter;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.*;

public class FilteredListenersTest {

    private MessageFilter getFilter(String name) {
        return new MessageFilter(null, null, null, name, null);
    }

    private class MockListener implements ChannelBase.MessageListener {
        public final ArrayList<Message> calls = new ArrayList<>();


        @Override
        public void onMessage(Message message) {
            calls.add(message);
        }
    }

    @Test
    public void it_adds_a_listener_with_a_filter() {
        MessageFilter filter = getFilter("this-name-is-important");
        Message message1 = new Message("this-name-is-important", new Object(), "client", new MessageExtras(new JsonObject()));
        Message message2 = new Message("this-name-is-not-important", new Object(), "client", new MessageExtras(new JsonObject()));

        MockListener listener = new MockListener();
        FilteredListeners filteredListeners = new FilteredListeners();

        ChannelBase.MessageListener filtered = filteredListeners.addFilteredListener(filter, listener);
        filtered.onMessage(message1);
        filtered.onMessage(message2);

        assertEquals(1, listener.calls.size());
        assertEquals(message1, listener.calls.get(0));

        assertEquals(1, filteredListeners.getFilteredListeners().size());
        assertEquals(1, filteredListeners.getFilteredListeners().get(listener).size());
        assertEquals(1, filteredListeners.getFilteredListeners().get(listener).get(filter).size());
        assertEquals(filtered, filteredListeners.getFilteredListeners().get(listener).get(filter).get(0));
    }

    @Test
    public void it_adds_a_listener_with_a_filter_when_one_has_been_registered() {
        MessageFilter filter = getFilter("this-name-is-important");
        Message message1 = new Message("this-name-is-important", new Object(), "client", new MessageExtras(new JsonObject()));
        Message message2 = new Message("this-name-is-not-important", new Object(), "client", new MessageExtras(new JsonObject()));

        MockListener listener1 = new MockListener();
        MockListener listener2 = new MockListener();
        FilteredListeners filteredListeners = new FilteredListeners();

        ChannelBase.MessageListener filtered1 = filteredListeners.addFilteredListener(filter, listener1);
        ChannelBase.MessageListener filtered2 = filteredListeners.addFilteredListener(filter, listener2);
        filtered1.onMessage(message1);
        filtered1.onMessage(message2);
        filtered2.onMessage(message1);
        filtered2.onMessage(message2);

        assertEquals(1, listener1.calls.size());
        assertEquals(message1, listener1.calls.get(0));
        assertEquals(1, listener2.calls.size());
        assertEquals(message1, listener2.calls.get(0));

        assertEquals(2, filteredListeners.getFilteredListeners().size());
        assertEquals(1, filteredListeners.getFilteredListeners().get(listener1).size());
        assertEquals(1, filteredListeners.getFilteredListeners().get(listener1).get(filter).size());
        assertEquals(filtered1, filteredListeners.getFilteredListeners().get(listener1).get(filter).get(0));

        assertEquals(1, filteredListeners.getFilteredListeners().get(listener2).size());
        assertEquals(1, filteredListeners.getFilteredListeners().get(listener2).get(filter).size());
        assertEquals(filtered2, filteredListeners.getFilteredListeners().get(listener2).get(filter).get(0));
    }

    @Test
    public void it_adds_the_same_listener_with_a_different_filter() {
        MessageFilter filter = getFilter("this-name-is-important");
        MessageFilter filter2 = getFilter("this-name-is-also-important");
        Message message1 = new Message("this-name-is-important", new Object(), "client", new MessageExtras(new JsonObject()));
        Message message2 = new Message("this-name-is-also-important", new Object(), "client", new MessageExtras(new JsonObject()));

        MockListener listener1 = new MockListener();
        FilteredListeners filteredListeners = new FilteredListeners();

        ChannelBase.MessageListener filtered1 = filteredListeners.addFilteredListener(filter, listener1);
        ChannelBase.MessageListener filtered2 = filteredListeners.addFilteredListener(filter2, listener1);
        filtered1.onMessage(message1);
        filtered1.onMessage(message2);
        filtered2.onMessage(message1);
        filtered2.onMessage(message2);

        assertEquals(2, listener1.calls.size());
        assertEquals(message1, listener1.calls.get(0));
        assertEquals(message2, listener1.calls.get(1));

        assertEquals(1, filteredListeners.getFilteredListeners().size());
        assertEquals(2, filteredListeners.getFilteredListeners().get(listener1).size());
        assertEquals(1, filteredListeners.getFilteredListeners().get(listener1).get(filter).size());
        assertEquals(filtered1, filteredListeners.getFilteredListeners().get(listener1).get(filter).get(0));
        assertEquals(1, filteredListeners.getFilteredListeners().get(listener1).get(filter2).size());
        assertEquals(filtered2, filteredListeners.getFilteredListeners().get(listener1).get(filter2).get(0));
    }

    @Test
    public void it_adds_the_same_listener_with_the_same_filter() {
        MessageFilter filter = getFilter("this-name-is-important");
        Message message1 = new Message("this-name-is-important", new Object(), "client", new MessageExtras(new JsonObject()));
        Message message2 = new Message("this-name-is-not-important", new Object(), "client", new MessageExtras(new JsonObject()));

        MockListener listener1 = new MockListener();
        FilteredListeners filteredListeners = new FilteredListeners();

        ChannelBase.MessageListener filtered1 = filteredListeners.addFilteredListener(filter, listener1);
        ChannelBase.MessageListener filtered2 = filteredListeners.addFilteredListener(filter, listener1);
        filtered1.onMessage(message1);
        filtered2.onMessage(message2);
        filtered2.onMessage(message1);
        filtered2.onMessage(message2);

        assertEquals(2, listener1.calls.size());
        assertEquals(message1, listener1.calls.get(0));
        assertEquals(message1, listener1.calls.get(1));

        assertEquals(1, filteredListeners.getFilteredListeners().size());
        assertTrue(filteredListeners.getFilteredListeners().containsKey(listener1));
        assertEquals(1, filteredListeners.getFilteredListeners().get(listener1).size());
        assertTrue(filteredListeners.getFilteredListeners().get(listener1).containsKey(filter));
        assertEquals(2, filteredListeners.getFilteredListeners().get(listener1).get(filter).size());
        assertEquals(filtered1, filteredListeners.getFilteredListeners().get(listener1).get(filter).get(0));
        assertEquals(filtered2, filteredListeners.getFilteredListeners().get(listener1).get(filter).get(1));
    }

    @Test
    public void it_removes_all_listeners_for_filter() {
        MessageFilter filterToBeRemoved = getFilter("this-name-is-important");
        MessageFilter filterToBeKept = getFilter("this-name-is-not-important");

        FilteredListeners filteredListeners = new FilteredListeners();
        MockListener mockListener1 = new MockListener();
        MockListener mockListener2 = new MockListener();
        MockListener mockListener3 = new MockListener();
        MockListener mockListener4 = new MockListener();
        MockListener mockListener5 = new MockListener();

        // Filtered 1 and 2 are on the "wrong" filter, so shouldn't be touched
        ChannelBase.MessageListener filtered1 = filteredListeners.addFilteredListener(filterToBeKept, mockListener1);
        ChannelBase.MessageListener filtered2 = filteredListeners.addFilteredListener(filterToBeKept, mockListener2);

        // Filtered 3-5 are on the same listener, but 5 is on the filter to be kept. So we should lose 3 and 4, but 5 will remain.
        ChannelBase.MessageListener filtered3 = filteredListeners.addFilteredListener(filterToBeRemoved, mockListener3);
        ChannelBase.MessageListener filtered4 = filteredListeners.addFilteredListener(filterToBeRemoved, mockListener3);
        ChannelBase.MessageListener filtered5 = filteredListeners.addFilteredListener(filterToBeKept, mockListener4);

        // Filtered 6 is on the filter to be removed, and is the only filter. So the listener should be removed entirely.
        ChannelBase.MessageListener filtered6 = filteredListeners.addFilteredListener(filterToBeRemoved, mockListener5);

        ArrayList<ChannelBase.MessageListener> removed = filteredListeners.removeFilteredListener(filterToBeRemoved, null);

        // Check we have expected filters removed
        assertEquals(3, removed.size());
        assertTrue(removed.contains(filtered3));
        assertTrue(removed.contains(filtered4));
        assertTrue(removed.contains(filtered6));

        // Check what we have left
        assertEquals(3, filteredListeners.getFilteredListeners().size());
        assertEquals(filtered1, filteredListeners.getFilteredListeners().get(mockListener1).get(filterToBeKept).get(0));
        assertEquals(filtered2, filteredListeners.getFilteredListeners().get(mockListener2).get(filterToBeKept).get(0));
        assertEquals(filtered5, filteredListeners.getFilteredListeners().get(mockListener4).get(filterToBeKept).get(0));
    }

    @Test
    public void it_removes_all_listeners_for_listener() {
        MessageFilter filter1 = getFilter("this-name-is-important");
        MessageFilter filter2 = getFilter("this-name-is-not-important");

        FilteredListeners filteredListeners = new FilteredListeners();
        MockListener mockListener1 = new MockListener();
        MockListener mockListener2 = new MockListener();
        MockListener mockListener3 = new MockListener();
        MockListener mockListener4 = new MockListener();

        // Filtered 1 and 2 are on the "wrong" listener, so shouldn't be touched
        ChannelBase.MessageListener filtered1 = filteredListeners.addFilteredListener(filter2, mockListener1);
        ChannelBase.MessageListener filtered2 = filteredListeners.addFilteredListener(filter2, mockListener2);

        // Filtered 3-5 are on the same listener, so should all go.
        ChannelBase.MessageListener filtered3 = filteredListeners.addFilteredListener(filter1, mockListener3);
        ChannelBase.MessageListener filtered4 = filteredListeners.addFilteredListener(filter1, mockListener3);
        ChannelBase.MessageListener filtered5 = filteredListeners.addFilteredListener(filter2, mockListener3);

        // Filtered 6 are on the "wrong" listener
        ChannelBase.MessageListener filtered6 = filteredListeners.addFilteredListener(filter1, mockListener4);

        ArrayList<ChannelBase.MessageListener> removed = filteredListeners.removeFilteredListener(null, mockListener3);

        // Check we have expected filters removed
        assertEquals(3, removed.size());
        assertTrue(removed.contains(filtered3));
        assertTrue(removed.contains(filtered4));
        assertTrue(removed.contains(filtered5));

        // Check what we have left
        assertEquals(3, filteredListeners.getFilteredListeners().size());
        assertEquals(filtered1, filteredListeners.getFilteredListeners().get(mockListener1).get(filter2).get(0));
        assertEquals(filtered2, filteredListeners.getFilteredListeners().get(mockListener2).get(filter2).get(0));
        assertEquals(filtered6, filteredListeners.getFilteredListeners().get(mockListener4).get(filter1).get(0));
    }

    @Test
    public void it_removes_all_listeners_for_listener_and_filter() {
        MessageFilter filterToBeRemoved = getFilter("this-name-is-important");
        MessageFilter filterToBeKept = getFilter("this-name-is-not-important");

        FilteredListeners filteredListeners = new FilteredListeners();
        MockListener mockListener1 = new MockListener();
        MockListener mockListener2 = new MockListener();
        MockListener mockListener3 = new MockListener();
        MockListener mockListener4 = new MockListener();

        // Filtered 1 and 2 are on the "wrong" listener, so shouldn't be touched
        ChannelBase.MessageListener filtered1 = filteredListeners.addFilteredListener(filterToBeKept, mockListener1);
        ChannelBase.MessageListener filtered2 = filteredListeners.addFilteredListener(filterToBeKept, mockListener2);

        // Filtered 3-5 are on the same listener, but, only 3 and 4 should be removed as they are on the right filter
        ChannelBase.MessageListener filtered3 = filteredListeners.addFilteredListener(filterToBeRemoved, mockListener3);
        ChannelBase.MessageListener filtered4 = filteredListeners.addFilteredListener(filterToBeRemoved, mockListener3);
        ChannelBase.MessageListener filtered5 = filteredListeners.addFilteredListener(filterToBeKept, mockListener3);

        // Filtered 6 are on the "wrong" listener
        ChannelBase.MessageListener filtered6 = filteredListeners.addFilteredListener(filterToBeRemoved, mockListener4);

        ArrayList<ChannelBase.MessageListener> removed = filteredListeners.removeFilteredListener(filterToBeRemoved, mockListener3);

        // Check we have expected filters removed
        assertEquals(2, removed.size());
        assertTrue(removed.contains(filtered3));
        assertTrue(removed.contains(filtered4));

        // Check what we have left
        assertEquals(4, filteredListeners.getFilteredListeners().size());
        assertEquals(filtered1, filteredListeners.getFilteredListeners().get(mockListener1).get(filterToBeKept).get(0));
        assertEquals(filtered2, filteredListeners.getFilteredListeners().get(mockListener2).get(filterToBeKept).get(0));
        assertEquals(filtered6, filteredListeners.getFilteredListeners().get(mockListener4).get(filterToBeRemoved).get(0));
        assertEquals(1, filteredListeners.getFilteredListeners().get(mockListener3).size());
        assertEquals(1, filteredListeners.getFilteredListeners().get(mockListener3).get(filterToBeKept).size());
        assertEquals(filtered5, filteredListeners.getFilteredListeners().get(mockListener3).get(filterToBeKept).get(0));
    }

    @Test
    public void it_removes_all_listeners_for_listener_and_filter_and_removes_listener_completely() {
        MessageFilter filterToBeRemoved = getFilter("this-name-is-important");
        MessageFilter filterToBeKept = getFilter("this-name-is-not-important");

        FilteredListeners filteredListeners = new FilteredListeners();
        MockListener mockListener1 = new MockListener();
        MockListener mockListener2 = new MockListener();
        MockListener mockListener3 = new MockListener();
        MockListener mockListener4 = new MockListener();

        // Filtered 1 and 2 are on the "wrong" listener, so shouldn't be touched
        ChannelBase.MessageListener filtered1 = filteredListeners.addFilteredListener(filterToBeKept, mockListener1);
        ChannelBase.MessageListener filtered2 = filteredListeners.addFilteredListener(filterToBeKept, mockListener2);

        // Filtered 3 and 4 are on the same listener and should be removed
        ChannelBase.MessageListener filtered3 = filteredListeners.addFilteredListener(filterToBeRemoved, mockListener3);
        ChannelBase.MessageListener filtered4 = filteredListeners.addFilteredListener(filterToBeRemoved, mockListener3);

        // Filtered 5 are on the "wrong" listener
        ChannelBase.MessageListener filtered5 = filteredListeners.addFilteredListener(filterToBeRemoved, mockListener4);

        ArrayList<ChannelBase.MessageListener> removed = filteredListeners.removeFilteredListener(filterToBeRemoved, mockListener3);

        // Check we have expected filters removed
        assertEquals(2, removed.size());
        assertTrue(removed.contains(filtered3));
        assertTrue(removed.contains(filtered4));

        // Check what we have left
        assertEquals(3, filteredListeners.getFilteredListeners().size());
        assertEquals(filtered1, filteredListeners.getFilteredListeners().get(mockListener1).get(filterToBeKept).get(0));
        assertEquals(filtered2, filteredListeners.getFilteredListeners().get(mockListener2).get(filterToBeKept).get(0));
        assertEquals(filtered5, filteredListeners.getFilteredListeners().get(mockListener4).get(filterToBeRemoved).get(0));
        assertFalse(filteredListeners.getFilteredListeners().containsKey(mockListener3));
    }

    @Test
    public void it_handles_removing_non_existent_listener() {
        FilteredListeners filteredListeners = new FilteredListeners();
        MockListener mockListener1 = new MockListener();

        ArrayList<ChannelBase.MessageListener> removed = filteredListeners.removeFilteredListener(null, mockListener1);
        assertTrue(removed.isEmpty());
    }

    @Test
    public void it_clears_listeners() {
        MessageFilter filterToBeRemoved = getFilter("this-name-is-important");
        MessageFilter filterToBeKept = getFilter("this-name-is-not-important");

        FilteredListeners filteredListeners = new FilteredListeners();
        MockListener mockListener1 = new MockListener();
        MockListener mockListener2 = new MockListener();
        MockListener mockListener3 = new MockListener();
        MockListener mockListener4 = new MockListener();

        filteredListeners.addFilteredListener(filterToBeKept, mockListener1);
        filteredListeners.addFilteredListener(filterToBeKept, mockListener2);
        filteredListeners.addFilteredListener(filterToBeRemoved, mockListener3);
        filteredListeners.addFilteredListener(filterToBeRemoved, mockListener3);
        filteredListeners.addFilteredListener(filterToBeRemoved, mockListener4);

        filteredListeners.clear();
        assertTrue(filteredListeners.getFilteredListeners().isEmpty());
    }
}
