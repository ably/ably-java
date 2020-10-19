package io.ably.lib.realtime;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.ably.lib.rest.AblyRest;
import io.ably.lib.transport.ConnectionManager;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.ProtocolMessage;
import io.ably.lib.types.ReadOnlyMap;
import io.ably.lib.util.InternalMap;
import io.ably.lib.util.Log;

/**
 * AblyRealtime
 * The top-level class to be instanced for the Ably Realtime library.
 *
 * This class implements {@link AutoCloseable} so you can use it in
 * try-with-resources constructs and have the JDK close it for you.
 */
public class AblyRealtime extends AblyRest implements AutoCloseable {

    /**
     * The {@link Connection} object for this instance.
     */
    public final Connection connection;

    public final Channels channels;

    /**
     * Instance the Ably library using a key only.
     * This is simply a convenience constructor for the
     * simplest case of instancing the library with a key
     * for basic authentication and no other options.
     * @param key String key (obtained from application dashboard)
     * @throws AblyException
     */
    public AblyRealtime(String key) throws AblyException {
        this(new ClientOptions(key));
    }

    /**
     * Instance the Ably library with the given options.
     * @param options see {@link io.ably.lib.types.ClientOptions} for options
     * @throws AblyException
     */
    public AblyRealtime(ClientOptions options) throws AblyException {
        super(options);
        final InternalChannels channels = new InternalChannels();
        this.channels = channels;
        connection = new Connection(this, channels);

        /* remove all channels when the connection is closed, to avoid stalled state */
        connection.on(ConnectionEvent.closed, new ConnectionStateListener() {
            @Override
            public void onConnectionStateChanged(ConnectionStateListener.ConnectionStateChange state) {
                channels.clear();
            }
        });

        if(options.autoConnect) connection.connect();
    }

    /**
     * Initiate a connection.
     * {@link Connection#connect}.
     */
    public void connect() {
        connection.connect();
    }

    /**
     * Close this instance. This closes the connection.
     * The connection can be re-opened by calling
     * {@link Connection#connect}.
     */
    @Override
    public void close() {
        connection.close();
    }

    /**
     * Authentication token has changed.
     */
    @Override
    protected void onAuthUpdated(String token, boolean waitForResponse) throws AblyException {
        connection.connectionManager.onAuthUpdated(token, waitForResponse);
    }

    /**
     * Authentication error occurred
     */
    protected void onAuthError(ErrorInfo errorInfo) {
        connection.connectionManager.onAuthError(errorInfo);
    }

    /**
     * A collection of Channels associated with this Ably Realtime instance.
     */
    public interface Channels extends ReadOnlyMap<String, Channel> {
        /**
         * Get the named channel; if it does not already exist,
         * create it with default options.
         * @param channelName the name of the channel
         * @return the channel
         */
        Channel get(String channelName);

        /**
         * Get the named channel and set the given options, creating it
         * if it does not already exist.
         * @param channelName the name of the channel
         * @param channelOptions the options to set (null to clear options on an existing channel)
         * @return the channel
         * @throws AblyException
         */
        Channel get(String channelName, ChannelOptions channelOptions) throws AblyException;

        /**
         * Remove this channel from this AblyRealtime instance. This detaches from the channel
         * and releases all other resources associated with the channel in this client.
         * This silently does nothing if the channel does not already exist.
         * @param channelName the name of the channel
         */
        void release(String channelName);
    }

    private class InternalChannels extends InternalMap<String, Channel> implements Channels, ConnectionManager.Channels {
        private InternalChannels() {
            super(new ConcurrentHashMap<String, Channel>());
        }

        /**
         * Get the named channel; if it does not already exist,
         * create it with default options.
         * @param channelName the name of the channel
         * @return the channel
         */
        @Override
        public Channel get(String channelName) {
            try {
                return get(channelName, null);
            } catch (AblyException e) { return null; }
        }

        @Override
        public Channel get(String channelName, ChannelOptions channelOptions) throws AblyException {
            Channel channel = map.get(channelName);
            if (channel != null) {
                if (channelOptions != null) {
                    if (channel.shouldReattachToSetOptions(channelOptions)) {
                        throw AblyException.fromErrorInfo(new ErrorInfo("Channels.get() cannot be used to set channel options that would cause the channel to reattach. Please, use Channel.setOptions() instead.", 40000, 400));
                    }
                    channel.setOptions(channelOptions);
                }
                return channel;
            }

            channel = new Channel(AblyRealtime.this, channelName, channelOptions);
            map.put(channelName, channel);
            return channel;
        }

        @Override
        public void release(String channelName) {
            Channel channel = map.remove(channelName);
            if(channel != null) {
                try {
                    channel.detach();
                } catch (AblyException e) {
                    Log.e(TAG, "Unexpected exception detaching channel; channelName = " + channelName, e);
                }
            }
        }

        @Override
        public void onMessage(ProtocolMessage msg) {
            String channelName = msg.channel;
            Channel channel;
            synchronized(this) { channel = channels.get(channelName); }
            if(channel == null) {
                Log.e(TAG, "Received channel message for non-existent channel");
                return;
            }
            channel.onChannelMessage(msg);
        }

        @Override
        public void suspendAll(ErrorInfo error, boolean notifyStateChange) {
            for(Iterator<Map.Entry<String, Channel>> it = map.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Channel> entry = it.next();
                entry.getValue().setSuspended(error, notifyStateChange);
            }
        }

        private void clear() {
            map.clear();
        }
    }

    /********************
     * internal
     ********************/

    private static final String TAG = AblyRealtime.class.getName();
}
