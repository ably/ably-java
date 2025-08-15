package io.ably.lib.realtime;

import io.ably.lib.objects.ObjectsPlugin;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import org.jetbrains.annotations.Nullable;

public class Channel extends ChannelBase {
    Channel(AblyRealtime ably, String name, ChannelOptions options, @Nullable ObjectsPlugin objectsPlugin) throws AblyException {
        super(ably, name, options, objectsPlugin);
    }

    public interface MessageListener extends ChannelBase.MessageListener {}
}
