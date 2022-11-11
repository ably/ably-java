package io.ably.lib.types;


/**
 * A collection of Channels associated with an Ably instance.
 */
public interface Channels<Channel extends AblyChannel> extends ReadOnlyMap<String, Channel> {
    /**
     * Get the named channel; if it does not already exist,
     * create it with default options.
     *
     * @param channelName the name of the channel
     * @return the channel
     */
    Channel get(String channelName);

    /**
     * Get the named channel and set the given options, creating it
     * if it does not already exist.
     *
     * @param channelName    the name of the channel
     * @param channelOptions the options to set (null to clear options on an existing channel)
     * @return the channel
     * @throws AblyException
     */
    Channel get(String channelName, ChannelOptions channelOptions) throws AblyException;

    /**
     * Remove this channel from this Ably instance. This detaches from the channel
     * and releases all other resources associated with the channel in this client.
     * This silently does nothing if the channel does not already exist.
     *
     * @param channelName the name of the channel
     */
    void release(String channelName);

    int size();

    Iterable<Channel> values();
}
