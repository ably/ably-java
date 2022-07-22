package io.ably.lib.realtime;

import io.ably.lib.platform.JavaPlatform;
import io.ably.lib.push.Push;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.util.JavaPlatformAgentProvider;

public class AblyRealtime extends AblyRealtimeBase<Push, JavaPlatform, Channel> {
    public AblyRealtime(String key) throws AblyException {
        super(key, new JavaPlatformAgentProvider());
    }

    public AblyRealtime(ClientOptions options) throws AblyException {
        super(options, new JavaPlatformAgentProvider());
    }

    @Override
    protected RealtimeChannelBase createChannel(AblyRealtimeBase ablyRealtime, String channelName, ChannelOptions channelOptions) throws AblyException {
        return new Channel(ablyRealtime, channelName, channelOptions);
    }

    @Override
    protected JavaPlatform createPlatform() {
        return new JavaPlatform();
    }

    @Override
    protected Push createPush() {
        return new Push(this);
    }

}
