package io.ably.lib.objects;

import io.ably.lib.realtime.ChannelState;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelMode;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ProtocolMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface LiveObjectsAdapter {
    /**
     * Sends a protocol message to its intended recipient.
     * This method transmits a protocol message, allowing for queuing events if necessary,
     * and notifies the provided listener upon the success or failure of the send operation.
     *
     * @param msg the protocol message to send.
     * @param listener a listener to be notified of the success or failure of the send operation.
     * @throws AblyException if an error occurs during the send operation.
     */
    void send(@NotNull ProtocolMessage msg, @NotNull CompletionListener listener) throws AblyException;

    /**
     * Sets the channel serial for a specific channel.
     * @param channelName the name of the channel for which to set the serial
     * @param channelSerial the serial to set for the channel
     */
    void setChannelSerial(@NotNull String channelName, @NotNull String channelSerial);

    /**
     * Retrieves the maximum message size allowed for the messages.
     * This method returns the maximum size in bytes that a message can have.
     *
     * @return the maximum message size limit in bytes.
     */
    int maxMessageSizeLimit();

    /**
     * Retrieves the channel modes for a specific channel.
     * This method returns the modes that are set for the specified channel.
     *
     * @param channelName the name of the channel for which to retrieve the modes
     * @return the array of channel modes for the specified channel, or null if the channel is not found
     * Spec: RTO2a, RTO2b
     */
    @Nullable ChannelMode[] getChannelModes(@NotNull String channelName);

    /**
     * Retrieves the current state of a specific channel.
     * This method returns the state of the specified channel, which indicates its connection status.
     *
     * @param channelName the name of the channel for which to retrieve the state
     * @return the current state of the specified channel, or null if the channel is not found
     */
    @Nullable ChannelState getChannelState(@NotNull String channelName);

    /**
     * Retrieves the client options configured for the Ably client.
     * Used to access client configuration parameters such as echoMessages setting
     * that affect the behavior of LiveObjects operations.
     *
     * @return the client options containing configuration parameters
     */
    @NotNull ClientOptions getClientOptions();
}

