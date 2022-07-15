package io.ably.core.rest;

import io.ably.core.types.AblyException;
import io.ably.core.types.ChannelOptions;

public class Channel extends RestChannelBase {
    Channel(AblyBase ably, String name, ChannelOptions options) throws AblyException {
        super(ably, name, options);
    }
}
