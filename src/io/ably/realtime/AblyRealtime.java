package io.ably.realtime;

import java.util.HashMap;

import io.ably.rest.AblyRest;
import io.ably.transport.ITransport;
import io.ably.types.AblyException;
import io.ably.types.ChannelOptions;
import io.ably.types.Options;
import io.ably.types.ProtocolMessage;
import io.ably.util.Log;

/**
 * AblyRealtime
 * The top-level class to be instanced for the Ably Realtime library.
 */
public class AblyRealtime extends AblyRest {

	/**
	 * The {@link Connection} object for this instance.
	 */
	public final Connection connection;

	/**
	 * The {@link #Channels} associated with this instance.
	 */
	public Channels channels;

	/**
	 * Instance the Ably library using a key only.
	 * This is simply a convenience constructor for the
	 * simplest case of instancing the library with a key
	 * for basic authentication and no other options.
	 * @param key; String key (obtained from application dashboard)
	 * @throws AblyException
	 */
	public AblyRealtime(String key) throws AblyException {
		this(new Options(key));
	}

	/**
	 * Instance the Ably library with the given options.
	 * @param options: see {@link io.ably.types.Options} for options
	 * @throws AblyException
	 */
	public AblyRealtime(Options options) throws AblyException {
		super(options);
		channels = new Channels();
		(connection = new Connection(this)).connect();
	}

	/**
	 * Close this instance. This closes the connection.
	 * The connection can be re-opened by calling
	 * {@link Connection#connect}.
	 */
	public void close() {
		connection.close();
	}

	/**
	 * A collection of the Channels associated with this Realtime
	 * instance.
	 *
	 */
	@SuppressWarnings("serial")
	public class Channels extends HashMap<String, Channel> {
		/**
		 * Get the named channel; if it does not already exist,
		 * create it with default options.
		 * @param channelName the name of the channel
		 * @return the channel
		 */
		public Channel get(String channelName) {
			Channel channel = super.get(channelName);
			if(channel == null) {
				channel = new Channel(AblyRealtime.this, channelName);
				put(channelName, channel);
			}
			return channel;
		}

		/**
		 * Get the named channel and set the given options, creating it
		 * if it does not already exist.
		 * @param channelName the name of the channel
		 * @param channelOptions the options to set (null to clear options on an existing channel)
		 * @return the channel
		 * @throws AblyException
		 */
		public Channel get(String channelName, ChannelOptions channelOptions) throws AblyException {
			Channel channel = get(channelName);
			channel.setOptions(channelOptions);
			return channel;
		}

		/**
		 * Remove this channel from this AblyRealtime instance. This detaches from the channel
		 * and releases all other resources associated with the channel in this client.
		 * This silently does nothing if the channel does not already exist.
		 * @param channelName
		 */
		public void release(String channelName) {
			Channel channel = remove(channelName);
			if(channel != null) {
				try {
					channel.detach();
				} catch (AblyException e) {
					Log.e(TAG, "Unexpected exception detaching channel; channelName = " + channelName, e);
				}
			}
		}

		public void onChannelMessage(ITransport transport, ProtocolMessage msg) {
			String channelName = msg.channel;
			Channel channel;
			synchronized(this) { channel = channels.get(channelName); }
			if(channel == null) {
				Log.e(TAG, "Received channel message for non-existent channel");
				return;
			}
			channel.onChannelMessage(msg);
		}
	}

	/********************
	 * internal
	 ********************/

	private static final String TAG = AblyRealtime.class.getName();
}
