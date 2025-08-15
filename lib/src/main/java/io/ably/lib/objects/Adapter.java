package io.ably.lib.objects;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.ChannelBase;
import io.ably.lib.transport.ConnectionManager;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.util.Log;
import org.jetbrains.annotations.NotNull;

public class Adapter implements ObjectsAdapter {
    private final AblyRealtime ably;
    private static final String TAG = ObjectsAdapter.class.getName();

    public Adapter(@NotNull AblyRealtime ably) {
        this.ably = ably;
    }

    @Override
    public @NotNull ClientOptions getClientOptions() {
        return ably.options;
    }

    @Override
    public @NotNull ConnectionManager getConnectionManager() {
        return ably.connection.connectionManager;
    }

    @Override
    public long getTime() throws AblyException {
        return ably.time();
    }

    @Override
    public @NotNull ChannelBase getChannel(@NotNull String channelName) throws AblyException {
        if (ably.channels.containsKey(channelName)) {
            return ably.channels.get(channelName);
        } else {
            Log.e(TAG, "attachChannel(): channel not found: " + channelName);
            ErrorInfo errorInfo = new ErrorInfo("Channel not found: " + channelName, 404);
            throw AblyException.fromErrorInfo(errorInfo);
        }
    }
}
