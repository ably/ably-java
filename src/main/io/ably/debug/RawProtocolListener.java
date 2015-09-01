package io.ably.debug;

import io.ably.types.ProtocolMessage;

public interface RawProtocolListener {
	public void onRawMessage(ProtocolMessage message);
}
