package io.ably.lib.objects;

import io.ably.lib.plugins.PluginConnectionAdapter;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ProtocolMessage;
import io.ably.lib.util.Log;
import org.jetbrains.annotations.NotNull;

public interface LiveObjectsAdapter extends PluginConnectionAdapter {
    void setChannelSerial(@NotNull String channelName, @NotNull String channelSerial);

    class Adapter implements LiveObjectsAdapter {
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
        public void send(ProtocolMessage msg, CompletionListener listener) throws AblyException {
            ably.connection.connectionManager.send(msg, true, listener);
        }
    }
}
