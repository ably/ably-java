package io.ably.lib.types;

import java.util.Map;

public class InternalEncodingDecodingContext extends BaseEncodingDecodingContext {

	public final Map<String, AblyCodec> Codecs;

	public InternalEncodingDecodingContext(ChannelOptions options, Map<String, AblyCodec> codecs) {
		super(options);
		this.Codecs = codecs;
	}
}
