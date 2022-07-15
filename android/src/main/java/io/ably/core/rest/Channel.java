package io.ably.core.rest;

import io.ably.core.platform.AndroidPlatform;
import io.ably.core.push.Push;
import io.ably.core.push.PushChannel;
import io.ably.core.types.AblyException;
import io.ably.core.types.ChannelOptions;

public class Channel extends RestChannelBase {
    /**
     * The push instance for this channel.
     */
    public final PushChannel push;

    public Channel(AblyBase<Push, AndroidPlatform, Channel> ably, String name, ChannelOptions options) throws AblyException {
        super(ably, name, options);
        this.push = new PushChannel(name, ably);
    }
}
