package io.ably.lib.objects;

import io.ably.lib.types.ProtocolMessage;
import org.jetbrains.annotations.NotNull;

/**
 * The LiveObjectsPlugin interface provides a mechanism for managing and interacting with
 * live data objects in a real-time environment. It allows for the retrieval, disposal, and
 * management of LiveObjects instances associated with specific channel names.
 */
public interface LiveObjectsPlugin {

    /**
     * Retrieves an instance of LiveObjects associated with the specified channel name.
     * This method ensures that a LiveObjects instance is available for the given channel,
     * creating one if it does not already exist.
     *
     * @param channelName the name of the channel for which the LiveObjects instance is to be retrieved.
     * @return the LiveObjects instance associated with the specified channel name.
     */
    @NotNull
    LiveObjects getInstance(@NotNull String channelName);

    /**
     * Handles a protocol message.
     * This method is invoked whenever a protocol message is received, allowing the implementation
     * to process the message and take appropriate actions.
     *
     * @param message the protocol message to handle.
     */
    void handle(@NotNull ProtocolMessage message);

    /**
     * Disposes of the LiveObjects instance associated with the specified channel name.
     * This method removes the LiveObjects instance for the given channel, releasing any
     * resources associated with it.
     *
     * @param channelName the name of the channel whose LiveObjects instance is to be removed.
     */
    void dispose(@NotNull String channelName);

    /**
     * Disposes of the plugin instance and all underlying resources.
     */
    void dispose();
}
