package io.ably.lib.rest;

import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;

/**
 * Created by tcard on 3/2/17.
 */
public class Channel extends ChannelBase {
	Channel(AblyRest ably, String name, ChannelOptions options) throws AblyException {
        super(ably, name, options);
    }
}
