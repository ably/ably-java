package io.ably.lib.types;

/**
 * (RTL15) Channel#properties attribute is a ChannelProperties object representing properties of the channel state
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

    /**
     * Contains the last @channelSerial@ received in any @MESSAGE@, @PRESENCE@, or @ATTACHED@ @ProtocolMesage@ on the channel,
     * see spec #RTL15b
     * is updated whenever a ProtocolMessage with either MESSAGE, PRESENCE, or ATTACHED actions is received on a channel,
     * and is set to the TR4c channelSerial of that ProtocolMessage, if and only if that field (ProtocolMessage.channelSerial) is populated.
     * <p>
     * Spec: CP2b
     */
    public String channelSerial;

    public ChannelProperties() {}
}
