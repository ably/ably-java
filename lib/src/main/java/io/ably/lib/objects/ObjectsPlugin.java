package io.ably.lib.objects;

import io.ably.lib.realtime.ChannelState;
import io.ably.lib.types.ProtocolMessage;
import org.jetbrains.annotations.NotNull;

/**
 * The ObjectsPlugin interface provides a mechanism for managing and interacting with
 * live data objects in a real-time environment. It allows for the retrieval, disposal, and
 * management of Objects instances associated with specific channel names.
 */
public interface ObjectsPlugin {

    /**
     * Retrieves an instance of RealtimeObjects associated with the specified channel name.
     * This method ensures that a RealtimeObjects instance is available for the given channel,
     * creating one if it does not already exist.
     *
     * @param channelName the name of the channel for which the RealtimeObjects instance is to be retrieved.
     * @return the RealtimeObjects instance associated with the specified channel name.
     */
    @NotNull
    RealtimeObjects getInstance(@NotNull String channelName);

    /**
     * Handles a protocol message.
     * This method is invoked whenever a protocol message is received, allowing the implementation
     * to process the message and take appropriate actions.
     *
     * @param message the protocol message to handle.
     */
    void handle(@NotNull ProtocolMessage message);

    /**
     * Handles state changes for a specific channel.
     * This method is invoked whenever a channel's state changes, allowing the implementation
     * to update the RealtimeObjects instances accordingly based on the new state and presence of objects.
     *
     * @param channelName the name of the channel whose state has changed.
     * @param state the new state of the channel.
     * @param hasObjects flag indicates whether the channel has any associated live objects.
     */
    void handleStateChange(@NotNull String channelName, @NotNull ChannelState state, boolean hasObjects);

    /**
     * Disposes of the RealtimeObjects instance associated with the specified channel name.
     * This method removes the RealtimeObjects instance for the given channel, releasing any
     * resources associated with it.
     * This is invoked when ablyRealtimeClient.channels.release(channelName) is called
     *
     * @param channelName the name of the channel whose RealtimeObjects instance is to be removed.
     */
    void dispose(@NotNull String channelName);

    /**
     * Disposes of the plugin instance and all underlying resources.
     * This is invoked when ablyRealtimeClient.close() is called
     */
    void dispose();
}
