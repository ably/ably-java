package io.ably.lib.types;

import java.util.Map;

public class InternalEncodingDecodingContext extends BaseEncodingDecodingContext {

	public final Map<String, AblyCodec> userProvidedCodecs;

	public InternalEncodingDecodingContext(ChannelOptions options, Map<String, AblyCodec> userProvidedCodecs) {
		super(options);
		this.userProvidedCodecs = userProvidedCodecs;
	}
}
