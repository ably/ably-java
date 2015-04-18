package io.ably.realtime;

/**
 * Connection states. See Ably Realtime API documentation for more details.
 */
public enum ConnectionState {
	initialized,
	connecting,
	connected,
	disconnected,
	suspended,
	closing,
	closed,
	failed
}