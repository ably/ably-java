package io.ably.lib.plugins;

import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ProtocolMessage;

/**
 * The PluginConnectionAdapter interface defines a contract for managing real-time communication
 * between plugins and the Ably Realtime system. Implementations of this interface are responsible
 * for sending protocol messages to their intended recipients, optionally queuing events, and
 * notifying listeners of the operation's outcome.
 */
public interface PluginConnectionAdapter {

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
}
