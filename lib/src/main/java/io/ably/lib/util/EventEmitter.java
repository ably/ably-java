package io.ably.lib.util;

/**
 * Class for emitting events consisting only of single enum. Simpler version of EventPairEmitter
 */
abstract public class EventEmitter<Event, Listener> extends EventPairEmitter<Event, Object, Listener> {
	public synchronized void emit(Event event, Object... args) {
		emitPair(event, null, args);
	}
}
