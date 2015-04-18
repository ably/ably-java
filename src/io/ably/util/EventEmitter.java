package io.ably.util;

/**
 * An interface exposing the ability to register listeners for a class of events
 * @author paddy
 *
 * @param <Event> an Enum containing the event names that listeners may be registered for
 * @param <Listener> the interface type of the listener
 */
public interface EventEmitter<Event, Listener> {

	/**
	 * Register the given listener for all events
	 * @param listener
	 */
	public void on(Listener listener);

	/**
	 * Remove a previously registered listener
	 * @param listener
	 */
	public void off(Listener listener);

	/**
	 * Register the given listener for a specific event
	 * @param listener
	 */
	public void on(Event event, Listener listener);

	/**
	 * Remove a previously registered event-specific listener
	 * @param listener
	 * @param event
	 */
	public void off(Event event, Listener listener);
}
