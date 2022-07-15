package io.ably.core.realtime;

import io.ably.core.platform.JavaPlatform;
import io.ably.core.push.Push;
import io.ably.core.types.AblyException;
import io.ably.core.types.ChannelOptions;
import io.ably.core.types.ClientOptions;
import io.ably.core.util.JavaPlatformAgentProvider;

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
