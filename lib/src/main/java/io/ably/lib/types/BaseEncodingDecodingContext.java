package io.ably.lib.types;

abstract class BaseEncodingDecodingContext {
	public final ChannelOptions channelOptions;
	/**
	 * The payload of the previous message encoded the same way as when it was received by Ably Realtime.
	 * I.e. all encodings added by Ably Realtime have been decoded.
	 */
	public Object baseEncodedPreviousPayload;

	BaseEncodingDecodingContext(ChannelOptions options){
		this(options, null);
	}

	BaseEncodingDecodingContext(ChannelOptions options, Object baseEncodedPreviousPayload) {
		this.channelOptions = options;
		this.baseEncodedPreviousPayload = baseEncodedPreviousPayload;
	}
}
