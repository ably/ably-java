package io.ably.lib.types;

import java.util.Map;

public class EncodingDecodingContext {

	public final ChannelOptions channelOptions;
	/**
	 * The payload of the previous message encoded the same way as when it was received by Ably Realtime.
	 * I.e. all encodings added by Ably Realtime have been decoded.
	 */
	public Object baseEncodedPreviousPayload;
	public Map<String, AblyCodec> userProvidedCodecs;

	public EncodingDecodingContext(ChannelOptions options, Map<String, AblyCodec> userProvidedCodecs){
		baseEncodedPreviousPayload = null;
		channelOptions = options;
		this.userProvidedCodecs = userProvidedCodecs;
	}
}
