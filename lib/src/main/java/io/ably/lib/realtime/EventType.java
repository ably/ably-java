package io.ably.lib.realtime;

/**
 * Event type for listeners
 */
public enum EventType {
	stateChange,	/* state of Ably object changed */
	update			/* update without state change */
}
