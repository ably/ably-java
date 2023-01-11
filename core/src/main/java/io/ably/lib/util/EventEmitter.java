package io.ably.lib.util;

import java.util.ArrayList;
import java.util.List;

/**
 * A generic interface for event registration and delivery used in a number of the types in the Realtime client library.
 * For example, the {@link io.ably.lib.realtime.Connection} object emits events for connection state using the EventEmitter pattern.
 *
 * @param <Event> an Enum containing the event names that listeners may be registered for
 * @param <Listener> the interface type of the listener
 */
public abstract class EventEmitter<Event, Listener> {

    /**
     * Deregisters all registrations, for all events and listeners.
     * <p>
     * Spec: RTE5
     */
    public synchronized void off() {
        listeners.clear();
        filters.clear();
    }

    /**
     * Registers the provided listener for all events.
     * If on() is called more than once with the same listener,
     * the listener is added multiple times to its listener registry.
     * Therefore, as an example, assuming the same listener is registered twice using on(),
     * and an event is emitted once, the listener would be invoked twice.
     * <p>
     * Spec: RTE4
     *
     * @param listener The event listener.
     * <p>
     * This listener is invoked on a background thread.
     */
    public synchronized void on(Listener listener) {
        listeners.add(listener);
    }

    /**
     * Registers the provided listener for the first event that is emitted.
     * If once() is called more than once with the same listener,
     * the listener is added multiple times to its listener registry.
     * Therefore, as an example, assuming the same listener is registered twice using once(),
     * and an event is emitted once, the listener would be invoked twice.
     * However, all subsequent events emitted would not invoke the listener as once() ensures that each registration is only invoked once.
     * <p>
     * Spec: RTE4
     *
     * @param listener The event listener.
     * <p>
     * This listener is invoked on a background thread.
     */
    public synchronized void once(Listener listener) {
        filters.add(new Filter(null, listener, true));
    }

    /**
     * Deregisters the specified listener.
     * Removes all registrations matching the given listener, regardless of whether they are associated with an event or not.
     * <p>
     * Spec: RTE5
     * @param listener The event listener.
     */
    public synchronized void off(Listener listener) {
        listeners.remove(listener);
        filters.removeIf(pair -> pair.listener == listener);
    }

    /**
     * Registers the provided listener for the specified event.
     * If on() is called more than once with the same listener and event,
     * the listener is added multiple times to its listener registry.
     * Therefore, as an example, assuming the same listener is registered twice using on(),
     * and an event is emitted once, the listener would be invoked twice.
     * <p>
     * Spec: RTE4
     *
     * @param event The named event to listen for.
     * @param listener The event listener.
     * <p>
     * This listener is invoked on a background thread.
     */
    public synchronized void on(Event event, Listener listener) {
        filters.add(new Filter(event, listener, false));
    }

    /**
     * Registers the provided listener for the first occurrence of a single named event specified as the Event argument.
     * If once() is called more than once with the same listener, the listener is added multiple times to its listener registry.
     * Therefore, as an example, assuming the same listener is registered twice using once(), and an event is emitted once,
     * the listener would be invoked twice.
     * However, all subsequent events emitted would not invoke the listener as once() ensures that each registration is only invoked once.
     * <p>
     * Spec: RTE4
     *
     * @param listener The event listener.
     * @param event The named event to listen for.
     * <p>
     * This listener is invoked on a background thread.
     */
    public synchronized void once(Event event, Listener listener) {
        filters.add(new Filter(event, listener, true));
    }

    /**
     * Removes all registrations that match both the specified listener and the specified event.
     * <p>
     * Spec: RTE5
     * @param listener The event listener.
     * @param event The named event.
     */
    public synchronized void off(Event event, Listener listener) {
        filters.removeIf(filter -> filter.listener == listener && filter.event != null && filter.event == event);
    }

    /**
     * Emits an event, calling registered listeners with the given event name and any other given arguments.
     * If an exception is raised in any of the listeners,
     * the exception is caught by the EventEmitter and the exception is logged to the Ably logger.
     * <p>
     * Spec: RTE5
     *
     * @param event The named event.
     * @param args the arguments to pass to listeners
     */
    public synchronized void emit(Event event, Object... args) {
        /*
         * The set of listeners called by emit must not change over the course of the emit
         * Refer RTE6a part of Spec for more details.
         * To address this issue, we clone the listeners before calling emit.
         */
        List<Listener> clonedListeners = new ArrayList<>(listeners);

        for (Listener listener : clonedListeners) {
            apply(listener, event, args);
        }

        List<Filter> clonedFilters = new ArrayList<>(filters);
        for (Filter entry: clonedFilters) {
            if (entry.apply(event, args)) {
                filters.remove(entry);
            }
        }
    }

    protected abstract void apply(Listener listener, Event event, Object... args);

    /**
     * Used to determine whether or not a listener is interested
     */
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

    List<Filter> filters = new ArrayList<Filter>();
    List<Listener> listeners = new ArrayList<Listener>();
}
