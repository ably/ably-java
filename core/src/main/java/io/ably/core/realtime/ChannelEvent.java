package io.ably.core.realtime;

/**
 * Channel event
 */
public enum ChannelEvent {
    initialized,
    attaching,
    attached,
    detaching,
    detached,
    failed,
    suspended,
    update
}
