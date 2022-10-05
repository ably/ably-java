package io.ably.lib.types;

/**
 * (RTL15) Channel#properties attribute is a ChannelProperties object representing properties of the channel state
 */
public class ChannelProperties {
    /**
     * A message identifier indicating the time of attachment to the channel;
     * used when recovering a message history to mesh exactly with messages
     * received on this channel subsequent to attachment.
     * contains the last @channelSerial@ received in an @ATTACHED@ @ProtocolMessage@ for the channel, see spec #RTL15a
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
