package io.ably.core.realtime;

import io.ably.core.types.AblyException;
import io.ably.core.types.ChannelOptions;
import io.ably.core.push.PushChannel;

public class Channel extends RealtimeChannelBase {
    /**
     * The push instance for this channel.
     */
    public final PushChannel push;

    Channel(AblyRealtimeBase ably, String name, ChannelOptions options) throws AblyException {
        super(ably, name, options);
        this.push = new PushChannel(name, ably);
    }
}
