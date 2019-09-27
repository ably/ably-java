package io.ably.lib.types;

public class EncodingDecodingContext extends BaseEncodingDecodingContext {
	public final String encoding;

	EncodingDecodingContext(ChannelOptions options, String encoding, Object baseEncodedPreviousPayload){
		super(options, baseEncodedPreviousPayload);
		this.encoding = encoding;
	}
}
