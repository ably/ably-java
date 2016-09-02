package io.ably.lib.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An interface exposing the ability to register listeners for a class of events
 * @author paddy
 *
 * @param <Event> an Enum containing the event names that listeners may be registered for
 * @param <Listener> the interface type of the listener
 */
public abstract class EventEmitter<Event, Listener> {

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
		filters.put(listener, new Filter(null, listener, true));
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
	public synchronized void on(Event event, Listener listener) {
		filters.put(listener, new Filter(event, listener, false));
	}

	/**
	 * Register the given listener for a single occurrence of a specific event
	 * @param listener
	 */
	public synchronized void once(Event event, Listener listener) {
		filters.put(listener, new Filter(event, listener, true));
	}

	/**
	 * Remove a previously registered event-specific listener
	 * @param listener
	 * @param event
	 */
	public synchronized void off(Event event, Listener listener) {
		Filter filter = filters.get(listener);
		if(filter != null && filter.event == event)
			filters.remove(listener);
	}

	/**
	 * Emit the given event (broadcasting to registered listeners)
	 * @param event the Event
	 * @param args the arguments to pass to listeners
	 */
	public synchronized void emit(Event event, Object... args) {
		for (int i = listeners.size() - 1; i >= 0; i--) {
			apply(listeners.get(i), event, args);
		}

		for(Iterator<Map.Entry<Listener, Filter>> it = filters.entrySet().iterator(); it.hasNext(); )
			if(it.next().getValue().apply(event, args))
				it.remove();
	}

	protected abstract void apply(Listener listener, Event event, Object... args);

	protected class Filter {
		Filter(Event event, Listener listener, boolean once) { this.event = event; this.listener = listener; this.once = once; }
		private Event event;
		private Listener listener;
		private boolean once;
		protected boolean apply(Event event, Object... args) {
			if(this.event == event || this.event == null) {
				EventEmitter.this.apply(listener, event, args);
				return once;
			}
			return false;
		}
	}

	Map<Listener, Filter> filters = new HashMap<Listener, Filter>();
	List<Listener> listeners = new ArrayList<Listener>();
}
