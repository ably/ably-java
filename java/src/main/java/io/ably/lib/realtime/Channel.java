package io.ably.lib.realtime;

import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;

public class Channel extends ChannelBase {
    Channel(AblyRealtime ably, String name, ChannelOptions options) throws AblyException {
        super(ably, name, options);
    }

    public interface MessageListener extends ChannelBase.MessageListener {}
}
