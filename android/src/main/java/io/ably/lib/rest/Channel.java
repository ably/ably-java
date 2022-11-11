package io.ably.lib.rest;

import io.ably.lib.platform.AndroidPlatform;
import io.ably.lib.push.Push;
import io.ably.lib.push.PushChannel;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;

public class Channel extends RestChannelBase {
    /**
     * A {@link PushChannel} object.
     * <p>
     * Spec: RSH4
     */
    public final PushChannel push;

    public Channel(AblyBase<Push, AndroidPlatform, Channel> ably, String name, ChannelOptions options) throws AblyException {
        super(ably, name, options);
        this.push = new PushChannel(name, ably);
    }
}
