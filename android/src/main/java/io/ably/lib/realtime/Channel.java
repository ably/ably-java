package io.ably.lib.realtime;

import io.ably.lib.types.AblyException;
import io.ably.lib.types.ChannelOptions;
import io.ably.lib.push.PushChannel;

public class Channel extends ChannelBase {
	/**
	 * The push instance for this channel.
	 */
	public final PushChannel push;

	Channel(AblyRealtime ably, String name, ChannelOptions options) throws AblyException {
		super(ably, name, options);
		this.push = ((io.ably.lib.rest.AblyRest) ably).channels.get(name, options).push;
	}

	/**
	 * An interface whereby a client maybe notified of messages changes on a channel.
	 */
	public interface MessageListener extends ChannelBase.MessageListener {}
}
