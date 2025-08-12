package io.ably.lib.objects;

import io.ably.lib.realtime.ChannelBase;
import io.ably.lib.transport.ConnectionManager;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NotNull;

public interface LiveObjectsAdapter {
    /**
     * Retrieves the client options configured for the Ably client.
     * Used to access client configuration parameters such as echoMessages setting
     * that affect the behavior of LiveObjects operations.
     *
     * @return the client options containing configuration parameters
     */
    @NotNull ClientOptions getClientOptions();

    /**
     * Retrieves the connection manager for handling connection state and operations.
     * Used to check connection status, obtain error information, and manage
     * message transmission across the Ably connection.
     *
     * @return the connection manager instance
     */
    @NotNull ConnectionManager getConnectionManager();

    /**
     * Retrieves the current time in milliseconds from the Ably server.
     * Spec: RTO16
     */
    @Blocking
    long getTime() throws AblyException;

    /**
     * Retrieves the channel instance for the specified channel name.
     * If the channel does not exist, an AblyException is thrown.
     *
     * @param channelName the name of the channel to retrieve
     * @return the ChannelBase instance for the specified channel
     * @throws AblyException if the channel is not found or cannot be retrieved
     */
    @NotNull ChannelBase getChannel(@NotNull String channelName) throws AblyException;
}

