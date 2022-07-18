package io.ably.lib.realtime;

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
