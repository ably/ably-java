package io.ably.lib.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * An interface exposing the ability to register listeners for a class of events. Event is characterized
 * by two enums
 *
 * @author paddy
 *
 * @param <Event> first enum in the pair
 * @param <SecondaryEvent> secondary enum, if not null forms event type along with Event
 * @param <Listener> the interface type of the listener
 */
public abstract class EventPairEmitter<Event, SecondaryEvent, Listener> {

	/**
	 * Remove all registered listeners irrespective of type
	 */
	public synchronized void off() {
		listeners.clear();
		filters.clear();
	}

	/**
	 * Register the given listener for all events
	 * @param listener
	 */
	public synchronized void on(Listener listener) {
		if(!listeners.contains(listener))
			listeners.add(listener);
	}

	/**
	 * Register the given listener for a single occurrence of any event
	 * @param listener
	 */
	public synchronized void once(Listener listener) {
		filters.put(listener, new Filter(null, null, listener, true));
	}

	/**
	 * Remove a previously registered listener irrespective of type
	 * @param listener
	 */
	public synchronized void off(Listener listener) {
		listeners.remove(listener);
		filters.remove(listener);
	}

	/**
	 * Register the given listener for a specific event
	 * @param listener
	 */
	public synchronized void on(Event event, SecondaryEvent event2, Listener listener) {
		filters.put(listener, new Filter(event, event2, listener, false));
	}

	/**
	 * Register the given listener for a single occurrence of a specific event
	 * @param listener
	 */
	public synchronized void once(Event event, SecondaryEvent event2, Listener listener) {
		filters.put(listener, new Filter(event, event2, listener, true));
	}

	/**
	 * Remove a previously registered event-specific listener
	 * @param listener
	 * @param event
	 */
	public synchronized void off(Event event, SecondaryEvent event2, Listener listener) {
		Filter filter = filters.get(listener);
		if(filter != null && filter.event == event && filter.event2 == event2)
			filters.remove(listener);
	}

	/**
	 * Simple cases for events characterized by a single enum only
	 */
	public synchronized void on(Event event, Listener listener) {
		on(event, null, listener);
	}

	public synchronized void once(Event event, Listener listener) {
		once(event, null, listener);
	}

	public synchronized void off(Event event, Listener listener) {
		off(event, null, listener);
	}

	/**
	 * Emit the given event (broadcasting to registered listeners)
	 * @param event the Event
	 * @param args the arguments to pass to listeners
	 */
	public synchronized void emitPair(Event event, SecondaryEvent event2, Object... args) {
		for (int i = listeners.size() - 1; i >= 0; i--) {
			apply(listeners.get(i), args);
		}

		for(Iterator<Map.Entry<Listener, Filter>> it = filters.entrySet().iterator(); it.hasNext(); )
			if(it.next().getValue().apply(event, event2, args))
				it.remove();
	}

	protected abstract void apply(Listener listener, Object... args);

	protected class Filter {
		Filter(Event event, SecondaryEvent event2, Listener listener, boolean once) { this.event = event; this.event2 = event2; this.listener = listener; this.once = once; }
		private Event event;
		private SecondaryEvent event2;
		private Listener listener;
		private boolean once;
		protected boolean apply(Event event, SecondaryEvent event2, Object... args) {
			if((this.event == event || this.event == null) &&
					(this.event2 == event2 || this.event2 == null)) {
				EventPairEmitter.this.apply(listener, args);
				return once;
			} else {
				return false;
			}
		}
	}

	private Map<Listener, Filter> filters = new HashMap<Listener, Filter>();
	private List<Listener> listeners = new ArrayList<Listener>();
}
