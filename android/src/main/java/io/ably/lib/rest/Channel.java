package io.ably.lib.rest;

import io.ably.lib.push.PushChannel;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;

public class Channel extends ChannelBase {
	/**
	 * The push instance for this channel.
	 */
	public final PushChannel push;

	Channel(AblyBase ably, String name, ChannelOptions options) throws AblyException {
		super(ably, name, options);
		this.push = new PushChannel(this, (AblyRest)ably);
	}
}