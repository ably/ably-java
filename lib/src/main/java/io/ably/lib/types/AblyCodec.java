package io.ably.lib.types;

import java.io.IOException;

public interface AblyCodec {
	Object decode(Object delta, EncodingDecodingContext encodingContext) throws IOException;
	Object encode(Object payload, EncodingDecodingContext decodingContext);
}
