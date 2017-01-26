package io.ably.lib.realtime;

/**
 * (RTL15) Channel#properties attribute is a ChannelProperties object representing properties of the channel state
 */
public class ChannelProperties {
	/**
	 * A message identifier indicating the time of attachment to the channel;
	 * used when recovering a message history to mesh exactly with messages
	 * received on this channel subsequent to attachment.
	 */
	public String attachSerial;

	public ChannelProperties() {}
}
