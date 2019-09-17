package io.ably.lib.types;

public interface AblyCodec {
	Object decode(Object payload, EncodingDecodingContext encodingContext);
	Object encode(Object payload, EncodingDecodingContext decodingContext);
}
