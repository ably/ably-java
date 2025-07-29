package io.ably.lib.objects;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.types.*;
import io.ably.lib.util.Log;
import org.jetbrains.annotations.NotNull;

public class Adapter implements LiveObjectsAdapter {
    private final AblyRealtime ably;
    private static final String TAG = LiveObjectsAdapter.class.getName();

    public Adapter(@NotNull AblyRealtime ably) {
        this.ably = ably;
    }

    @Override
    public void setChannelSerial(@NotNull String channelName, @NotNull String channelSerial) {
        if (ably.channels.containsKey(channelName)) {
            ably.channels.get(channelName).properties.channelSerial = channelSerial;
        } else {
            Log.e(TAG, "setChannelSerial(): channel not found: " + channelName);
        }
    }

    @Override
    public void send(@NotNull ProtocolMessage msg, @NotNull CompletionListener listener) throws AblyException {
        // Always queue LiveObjects messages to ensure reliable state synchronization and proper acknowledgment
        ably.connection.connectionManager.send(msg, true, listener);
    }

    @Override
    public int maxMessageSizeLimit() {
        return ably.connection.connectionManager.maxMessageSize;
    }

    @Override
    public ChannelMode[] getChannelModes(@NotNull String channelName) {
        if (ably.channels.containsKey(channelName)) {
            // RTO2a - channel.modes is only populated on channel attachment, so use it only if it is set
            ChannelMode[] modes = ably.channels.get(channelName).getModes();
            if (modes != null) {
                return modes;
            }
            // RTO2b - otherwise as a best effort use user provided channel options
            ChannelOptions options = ably.channels.get(channelName).getOptions();
            if (options != null && options.hasModes()) {
                return options.modes;
            }
            return null;
        }
        Log.e(TAG, "getChannelMode(): channel not found: " + channelName);
        return null;
    }

    @Override
    public ChannelState getChannelState(@NotNull String channelName) {
        if (ably.channels.containsKey(channelName)) {
            return ably.channels.get(channelName).state;
        }
        Log.e(TAG, "getChannelState(): channel not found: " + channelName);
        return null;
    }

    @Override
    public @NotNull ClientOptions getClientOptions() {
        return ably.options;
    }
}
