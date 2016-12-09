package io.ably.lib.realtime;

import io.ably.lib.types.Event;

/**
 * Channel states. See Ably Realtime API documentation for more details.
 */
public enum ChannelState implements Event {
	initialized,
	attaching,
	attached,
	detaching,
	detached,
	failed,
	suspended
}
