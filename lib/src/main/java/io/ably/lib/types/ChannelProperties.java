package io.ably.lib.types;

/**
 * Describes the properties of the channel state.
 */
public class ChannelProperties {
    /**
     * Starts unset when a channel is instantiated, then updated with the channelSerial
     * from each {@link io.ably.lib.realtime.ChannelState#attached} event that matches the channel.
     * Used as the value for {@link io.ably.lib.realtime.Channel#history}.
     * <p>
     * Spec: CP2a
     */
    public String attachSerial;

    public ChannelProperties() {}
}
