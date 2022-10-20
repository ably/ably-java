package io.ably.lib.realtime;

import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.push.PushChannel;

public class Channel extends RealtimeChannelBase {
    /**
     * A {@link PushChannel} object.
     * <p>
     * Spec: RSH4
     */
    public final PushChannel push;

    Channel(AblyRealtimeBase ably, String name, ChannelOptions options) throws AblyException {
        super(ably, name, options);
        this.push = new PushChannel(name, ably);
    }
}
