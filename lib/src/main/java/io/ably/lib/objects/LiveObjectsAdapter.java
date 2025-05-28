package io.ably.lib.objects;

import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ProtocolMessage;
import org.jetbrains.annotations.NotNull;

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
    void send(ProtocolMessage msg, CompletionListener listener) throws AblyException;

    /**
     * Sets the channel serial for a specific channel.
     * @param channelName the name of the channel for which to set the serial
     * @param channelSerial the serial to set for the channel
     */
    void setChannelSerial(@NotNull String channelName, @NotNull String channelSerial);
}

