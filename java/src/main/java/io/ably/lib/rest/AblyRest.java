package io.ably.lib.rest;

import io.ably.lib.platform.JavaPlatform;
import io.ably.lib.push.Push;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.util.JavaPlatformAgentProvider;

public class AblyRest extends AblyBase<Push, JavaPlatform, Channel> {
    /**
     * Instance the Ably library using a key only.
     * This is simply a convenience constructor for the
     * simplest case of instancing the library with a key
     * for basic authentication and no other options.
     *
     * @param key; String key (obtained from application dashboard)
     * @throws AblyException
     */
    public AblyRest(String key) throws AblyException {
        super(key, new JavaPlatformAgentProvider());
    }

    /**
     * Instance the Ably library with the given options.
     *
     * @param options: see {@link io.ably.lib.types.ClientOptions} for options
     * @throws AblyException
     */
    public AblyRest(ClientOptions options) throws AblyException {
        super(options, new JavaPlatformAgentProvider());
    }

    @Override
    protected JavaPlatform createPlatform() {
        return new JavaPlatform();
    }

    @Override
    protected Push createPush() {
        return new Push(this);
    }

    @Override
    protected RestChannelBase createChannel(AblyBase ablyBase, String channelName, ChannelOptions channelOptions) throws AblyException {
        return new Channel(ablyBase, channelName, channelOptions);
    }
}
