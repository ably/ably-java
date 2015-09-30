package io.ably.lib.debug;

import io.ably.lib.types.ProtocolMessage;

public interface RawProtocolListener {
	public void onRawMessage(ProtocolMessage message);
}
