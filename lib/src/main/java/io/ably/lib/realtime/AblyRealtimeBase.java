package io.ably.lib.realtime;

import java.util.Iterator;
import java.util.Map;

import io.ably.lib.platform.PlatformBase;
import io.ably.lib.push.PushBase;
import io.ably.lib.rest.AblyBase;
import io.ably.lib.rest.RestChannelBase;
import io.ably.lib.transport.ConnectionManager;
import io.ably.lib.types.AblyChannel;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.Channels;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.ProtocolMessage;
import io.ably.lib.util.InternalMap;
import io.ably.lib.util.Log;
import io.ably.lib.util.PlatformAgentProvider;

/**
 * AblyRealtime
 * The top-level class to be instanced for the Ably Realtime library.
 *
 * This class implements {@link AutoCloseable} so you can use it in
 * try-with-resources constructs and have the JDK close it for you.
 */
public abstract class AblyRealtimeBase<
    PushType extends PushBase,
    PlatformType extends PlatformBase,
    ChannelType extends AblyChannel
    > extends AblyBase<PushType, PlatformType, ChannelType> implements AutoCloseable {

    /**
     * The {@link Connection} object for this instance.
     */
    public final Connection connection;

    public final Channels<ChannelType> channels;

    /**
     * Instance the Ably library using a key only.
     * This is simply a convenience constructor for the
     * simplest case of instancing the library with a key
     * for basic authentication and no other options.
     * @param key String key (obtained from application dashboard)
     * @throws AblyException
     */
    public AblyRealtimeBase(String key, PlatformAgentProvider platformAgentProvider) throws AblyException {
        this(new ClientOptions(key), platformAgentProvider);
    }

    /**
     * Instance the Ably library with the given options.
     * @param options see {@link io.ably.lib.types.ClientOptions} for options
     * @throws AblyException
     */
    public AblyRealtimeBase(ClientOptions options, PlatformAgentProvider platformAgentProvider) throws AblyException {
        super(options, platformAgentProvider);
        final InternalChannels channels = new InternalChannels();
        this.channels = (Channels<ChannelType>) channels;
        connection = new Connection(this, channels, platformAgentProvider);

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

    protected abstract RealtimeChannelBase createChannel(AblyRealtimeBase<PushType, PlatformType, ChannelType> ablyRealtime, String channelName, ChannelOptions channelOptions) throws AblyException;

    protected RestChannelBase createChannel(AblyBase ablyBase, String channelName, ChannelOptions channelOptions) throws AblyException {
        // This method is here only due to the incremental refactoring work, AblyRealtime should never want to create
        // an Ably REST channel. After extracting AblyRestBase from AblyBase this method should be removed.
        return null;
    }

    private class InternalChannels extends InternalMap<String, RealtimeChannelBase> implements Channels<RealtimeChannelBase>, ConnectionManager.Channels {
        /**
         * Get the named channel; if it does not already exist,
         * create it with default options.
         * @param channelName the name of the channel
         * @return the channel
         */
        @Override
        public RealtimeChannelBase get(String channelName) {
            try {
                return get(channelName, null);
            } catch (AblyException e) { return null; }
        }

        @Override
        public RealtimeChannelBase get(final String channelName, final ChannelOptions channelOptions) throws AblyException {
            // We're not using computeIfAbsent because that requires Java 1.8.
            // Hence there's the slight inefficiency of creating newChannel when it may not be
            // needed because there is an existingChannel.
            final RealtimeChannelBase newChannel = createChannel(AblyRealtimeBase.this, channelName, channelOptions);
            final RealtimeChannelBase existingChannel = map.putIfAbsent(channelName, newChannel);

            if (existingChannel != null) {
                if (channelOptions != null) {
                    if (existingChannel.shouldReattachToSetOptions(channelOptions)) {
                        throw AblyException.fromErrorInfo(new ErrorInfo("Channels.get() cannot be used to set channel options that would cause the channel to reattach. Please, use Channel.setOptions() instead.", 40000, 400));
                    }
                    existingChannel.setOptions(channelOptions);
                }
                return existingChannel;
            }

            return newChannel;
        }

        @Override
        public void release(String channelName) {
            RealtimeChannelBase channel = map.remove(channelName);
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
            RealtimeChannelBase channel;
            synchronized(this) { channel = (RealtimeChannelBase) channels.get(channelName); }
            if(channel == null) {
                Log.e(TAG, "Received channel message for non-existent channel");
                return;
            }
            channel.onChannelMessage(msg);
        }

        @Override
        public void suspendAll(ErrorInfo error, boolean notifyStateChange) {
            for(Iterator<Map.Entry<String, RealtimeChannelBase>> it = map.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, RealtimeChannelBase> entry = it.next();
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

    private static final String TAG = AblyRealtimeBase.class.getName();
}
