package io.ably.debug;

import io.ably.types.AblyException;
import io.ably.types.Options;

public class DebugOptions extends Options {
	public RawProtocolListener protocolListener;
	public DebugOptions(String key) throws AblyException { super(key); }
}
