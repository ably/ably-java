package io.ably.lib.realtime;

/**
 * Channel states. See Ably Realtime API documentation for more details.
 */
public enum ChannelState {
	initialised,
	attaching,
	attached,
	detaching,
	detached,
	failed
}