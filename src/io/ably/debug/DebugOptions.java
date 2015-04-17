package io.ably.debug;

import io.ably.types.AblyException;
import io.ably.types.ClientOptions;

public class DebugOptions extends ClientOptions {
	public RawProtocolListener protocolListener;
	public DebugOptions(String key) throws AblyException { super(key); }
}
