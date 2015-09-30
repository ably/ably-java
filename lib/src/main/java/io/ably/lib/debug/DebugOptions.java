package io.ably.lib.debug;

import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;

public class DebugOptions extends ClientOptions {
	public RawProtocolListener protocolListener;
	public DebugOptions(String key) throws AblyException { super(key); }
}
