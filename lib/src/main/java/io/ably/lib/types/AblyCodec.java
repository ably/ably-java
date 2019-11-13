package io.ably.lib.types;

public interface AblyCodec {
	Object decode(Object delta, EncodingDecodingContext encodingContext) throws AblyException;
	EncodingResult encode(Object payload, EncodingDecodingContext decodingContext);
}
