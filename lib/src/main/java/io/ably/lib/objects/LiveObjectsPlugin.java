package io.ably.lib.objects;

/**
 * The LiveObjectsPlugin interface provides a mechanism to retrieve instances of LiveObjects
 * associated with specific channel names. This allows for interaction with live data objects
 * in a real-time environment.
 */
public interface LiveObjectsPlugin {

    /**
     * Retrieves an instance of LiveObjects associated with the specified channel name.
     *
     * @param channelName the name of the channel for which the LiveObjects instance is to be retrieved.
     * @return the LiveObjects instance associated with the specified channel name.
     */
    LiveObjects getInstance(String channelName);


    /**
     * Disposes of the LiveObjects instance associated with the specified channel name.
     *
     * @param channelName the name of the channel whose LiveObjects instance is to be removed.
     */
    void dispose(String channelName);
}
