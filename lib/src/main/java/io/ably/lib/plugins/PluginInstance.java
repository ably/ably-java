package io.ably.lib.plugins;

import io.ably.lib.types.ProtocolMessage;
import org.jetbrains.annotations.NotNull;

/**
 * The ProtocolMessageHandler interface defines a contract for handling protocol messages.
 * Implementations of this interface are responsible for processing incoming protocol messages
 * and performing the necessary actions based on the message content.
 */
public interface PluginInstance {
    /**
     * Handles a protocol message.
     * This method is invoked whenever a protocol message is received, allowing the implementation
     * to process the message and take appropriate actions.
     *
     * @param message the protocol message to handle.
     */
    void handle(@NotNull ProtocolMessage message);

    /**
     * Disposes of the plugin instance and all underlying resources.
     */
    void dispose();
}
