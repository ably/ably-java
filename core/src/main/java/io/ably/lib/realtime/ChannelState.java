package io.ably.lib.realtime;

/**
 * Channel states. See Ably Realtime API documentation for more details.
 */
public enum ChannelState {
    initialized(ChannelEvent.initialized),
    attaching(ChannelEvent.attaching),
    attached(ChannelEvent.attached),
    detaching(ChannelEvent.detaching),
    detached(ChannelEvent.detached),
    failed(ChannelEvent.failed),
    suspended(ChannelEvent.suspended);

    final private ChannelEvent event;
    ChannelState(ChannelEvent event) {
        this.event = event;
    }
    public ChannelEvent getChannelEvent() {
        return event;
    }
}
