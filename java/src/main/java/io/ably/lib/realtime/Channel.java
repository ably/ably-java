package io.ably.lib.realtime;

import io.ably.lib.objects.LiveObjectsPlugin;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;

public class Channel extends ChannelBase {
    Channel(AblyRealtime ably, String name, ChannelOptions options, LiveObjectsPlugin liveObjectsPlugin) throws AblyException {
        super(ably, name, options, liveObjectsPlugin);
    }

    public interface MessageListener extends ChannelBase.MessageListener {}
}
