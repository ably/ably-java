package io.ably.lib.realtime;

import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;

/**
 * Created by tcard on 3/2/17.
 */
public class Channel extends ChannelBase {
	Channel(AblyRealtime ably, String name, ChannelOptions options) throws AblyException {
        super(ably, name, options);
    }

    public interface MessageListener extends ChannelBase.MessageListener {}
}
