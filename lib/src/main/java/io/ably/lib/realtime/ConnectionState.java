package io.ably.lib.realtime;

import io.ably.lib.types.Event;

/**
 * Connection states. See Ably Realtime API documentation for more details.
 */
public enum ConnectionState implements Event {
	initialized,
	connecting,
	connected,
	disconnected,
	suspended,
	closing,
	closed,
	failed
}