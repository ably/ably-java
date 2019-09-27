package io.ably.lib.types;

public interface AblyCodec {
	Object decode(Object delta, EncodingDecodingContext encodingContext) throws Exception;
	EncodingResult encode(Object payload, EncodingDecodingContext decodingContext);
}
