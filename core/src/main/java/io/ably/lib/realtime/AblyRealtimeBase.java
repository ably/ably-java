package io.ably.lib.realtime;

import java.util.Iterator;
import java.util.Map;

import io.ably.lib.platform.Platform;
import io.ably.lib.push.PushBase;
import io.ably.lib.rest.AblyBase;
import io.ably.lib.rest.Auth;
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
 * A client that extends the functionality of the {@link AblyBase} and provides additional realtime-specific features.
 *
 * This class implements {@link AutoCloseable} so you can use it in
 * try-with-resources constructs and have the JDK close it for you.
 */
public abstract class AblyRealtimeBase<
    PushType extends PushBase,
    PlatformType extends Platform,
    ChannelType extends AblyChannel
    > extends AblyBase<PushType, PlatformType, ChannelType> implements AutoCloseable {

    /**
     * The {@link Connection} object for this instance.
     * <p>
     * Spec: RTC2
     */
    public final Connection connection;

    /**
     * A {@link Channels} object.
     * <p>
     * Spec: RTC3, RTS1
     */
    public final Channels<ChannelType> channels;

    /**
     * Constructs a Realtime client object using an Ably API key or token string.
     * <p>
     * Spec: RSC1
     * @param key The Ably API key or token string used to validate the client.
     * @throws AblyException
     */
    public AblyRealtimeBase(String key, PlatformAgentProvider platformAgentProvider) throws AblyException {
        this(new ClientOptions(key), platformAgentProvider);
    }

    /**
     * Constructs a RealtimeClient object using an Ably {@link ClientOptions} object.
     * <p>
     * Spec: RSC1
     * @param options A {@link ClientOptions} object.
     * @param platformAgentProvider for providing the platform specific part of the agent header
     * @throws AblyException
     */
    public AblyRealtimeBase(final ClientOptions options, PlatformAgentProvider platformAgentProvider) throws AblyException {
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

        if (options.recover != null) {
            ConnectionRecoveryKey recoveryKey = ConnectionRecoveryKey.fromJson(options.recover);
            if (recoveryKey == null) {
                Log.d(TAG, "Recovery key initialization failed!");
                connection.connectionManager.msgSerial = 0; //RTN16f
            } else {
                connection.connectionManager.msgSerial = recoveryKey.getMsgSerial(); //RTN16f

                for (Map.Entry<String, String> serial : recoveryKey.getSerials().entrySet()) {
                    //RTN16j
                    RealtimeChannelBase channel = channels.get(serial.getKey());
                    if (channel != null) {
                        channel.properties.channelSerial = serial.getValue(); //RTN16i
                    }
                }
            }

            connection.on(ConnectionEvent.connected, new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange state) {
                    options.recover = null; //RTN16k
                }
            });
        }

        if(options.autoConnect) connection.connect();
    }

    /**
     * Calls {@link Connection#connect} and causes the connection to open,
     * entering the connecting state. Explicitly calling connect() is unnecessary
     * unless the {@link ClientOptions#autoConnect} property is disabled.
     * <p>
     * Spec: RTN11
     */
    public void connect() {
        connection.connect();
    }

    /**
     * Calls {@link Connection#close} and causes the connection to close, entering the closing state.
     * Once closed, the library will not attempt to re-establish the connection
     * without an explicit call to {@link Connection#connect}.
     * <p>
     * Spec: RTN12
     */
    @Override
    public void close() {
        try {
            super.close(); // throws checked exception
        } catch (final Exception exception) {
            // Soften to Log, rather than throw.
            // This is because our close() method has never declared that it throws a checked exception.
            // Which is confusing, given AutoCloseable declares that it does.
            // TODO captured in https://github.com/ably/ably-java/issues/806
            // It's also because this particular piece of resource cleanup, focussed on thread pool resources used by
            // our REST code in the base class, is being introduced in an SDK patch release for version 1.2.
            Log.e(TAG, "There was an exception releasing client instance base resources.", exception);
        }

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
     * Authentication token has changed. Async version
     */
    protected void onAuthUpdatedAsync(String token, Auth.AuthUpdateResult authUpdateResult)  {
        connection.connectionManager.onAuthUpdatedAsync(token,authUpdateResult);
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
        throw new IllegalStateException("Rest channel should not be created from the Ably Realtime instance");
    }

    private class InternalChannels extends InternalMap<String, RealtimeChannelBase> implements Channels<RealtimeChannelBase>, ConnectionManager.Channels {
        /**
         * Creates a new {@link Channel} object, with the specified {@link ChannelOptions}, or returns the existing channel object.
         * <p>
         * Spec: RSN3c, RTS3c
         * @param channelName The channel name.
         * @return A {@link RealtimeChannelBase} object.
         */
        @Override
        public RealtimeChannelBase get(String channelName) {
            try {
                return get(channelName, null);
            } catch (AblyException e) { return null; }
        }

        /**
         * Creates a new {@link Channel} object, with the specified {@link ChannelOptions}, or returns the existing channel object.
         * <p>
         * Spec: RSN3c, RTS3c
         * @param channelName The channel name.
         * @param channelOptions A {@link ChannelOptions} object.
         * @return A {@link RealtimeChannelBase} object.
         * @throws AblyException
         */
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

        /**
         * Releases a {@link Channel} object, deleting it, and enabling it to be garbage collected.
         * It also removes any listeners associated with the channel.
         * To release a channel, the {@link ChannelState} must be INITIALIZED, DETACHED, or FAILED.
         * <p>
         * Spec: RSN4, RTS4
         * @param channelName The channel name.
         */
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

        /**
         * By spec RTN15c6, RTN15c7
         */
        @Override
        public void reAttach() {
            for (Map.Entry<String, RealtimeChannelBase> channelEntry : map.entrySet()) {
                RealtimeChannelBase channel = channelEntry.getValue();
                if (channel.state == ChannelState.attaching || channel.state == ChannelState.attached || channel.state == ChannelState.suspended) {
                    Log.d(TAG, "reAttach(); channel = " + channel.name);
                    channel.state = ChannelState.attaching;
                    channel.attach(true, null);
                }
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
