package io.ably.lib.realtime;

/**
 * Channel states. See Ably Realtime API documentation for more details.
 */
public enum ChannelState {
	initialized,
	attaching,
	attached,
	detaching,
	detached,
	failed,
	suspended
}
