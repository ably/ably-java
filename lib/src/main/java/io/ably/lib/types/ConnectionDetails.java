package io.ably.lib.types;

import java.io.IOException;

import io.ably.lib.transport.Defaults;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessageUnpacker;

public class ConnectionDetails {
	public String clientId;
	public String connectionKey;
	public String serverId;
	public Long maxMessageSize;
	public Long maxInboundRate;
	public Long maxFrameSize;
	public Long maxIdleInterval;

	ConnectionDetails() {
		maxIdleInterval = Defaults.maxIdleInterval;
	}

	ConnectionDetails readMsgpack(MessageUnpacker unpacker) throws IOException {
		int fieldCount = unpacker.unpackMapHeader();
		for(int i = 0; i < fieldCount; i++) {
			String fieldName = unpacker.unpackString().intern();
			MessageFormat fieldFormat = unpacker.getNextFormat();
			if(fieldFormat.equals(MessageFormat.NIL)) { unpacker.unpackNil(); continue; }

			if(fieldName == "clientId") {
				clientId = unpacker.unpackString();
			} else if(fieldName == "connectionKey") {
				connectionKey = unpacker.unpackString();
			} else if(fieldName == "serverId") {
				serverId = unpacker.unpackString();
			} else if(fieldName == "maxMessageSize") {
				maxMessageSize = unpacker.unpackLong();
			} else if(fieldName == "maxInboundRate") {
				maxInboundRate = unpacker.unpackLong();
			} else if(fieldName == "maxFrameSize") {
				maxFrameSize = unpacker.unpackLong();
			} else if(fieldName == "maxIdleInterval") {
				maxIdleInterval = unpacker.unpackLong();
			} else {
				System.out.println("Unexpected field: " + fieldName);
				unpacker.skipValue();
			}
		}
		return this;
	}

	static ConnectionDetails fromMsgpack(MessageUnpacker unpacker) throws IOException {
		return (new ConnectionDetails()).readMsgpack(unpacker);
	}
}
