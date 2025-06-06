package io.ably.lib.objects;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ProtocolMessage;
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
}
