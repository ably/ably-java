package io.ably.types;

import java.io.IOException;

import org.json.JSONObject;
import org.msgpack.MessagePackable;
import org.msgpack.packer.Packer;
import org.msgpack.type.ValueType;
import org.msgpack.unpacker.Unpacker;

public class ConnectionDetails implements MessagePackable {
	public String clientId;
	public String connectionKey;
	public Long maxMessageSize;
	public Long maxInboundRate;
	public Long maxFrameSize;

	public static ConnectionDetails readJSON(JSONObject json) {
		ConnectionDetails result = new ConnectionDetails();
		if(json != null) {
			if(json.has("clientId"))
				result.clientId = json.optString("clientId");
			if(json.has("connectionKey"))
				result.connectionKey = json.optString("connectionKey");
			if(json.has("maxMessageSize"))
				result.maxMessageSize = Long.valueOf(json.optLong("maxMessageSize"));
			if(json.has("maxInboundRate"))
				result.maxInboundRate = Long.valueOf(json.optLong("maxInboundRate"));
			if(json.has("maxFrameSize"))
				result.maxFrameSize = Long.valueOf(json.optLong("maxFrameSize"));
		}
		return result;
	}

	@Override
	public void writeTo(Packer pk) throws IOException {
		/* unused */
	}

	@Override
	public void readFrom(Unpacker unpacker) throws IOException {
		int fieldCount = unpacker.readMapBegin();
		for(int i = 0; i < fieldCount; i++) {
			String fieldName = unpacker.readString().intern();
			ValueType fieldType = unpacker.getNextType();
			if(fieldType.equals(ValueType.NIL)) { unpacker.readNil(); continue; }

			if(fieldName == "clientId") {
				clientId = unpacker.readString();
			} else if(fieldName == "connectionKey") {
				connectionKey = unpacker.readString();
			} else if(fieldName == "maxMessageSize") {
				maxMessageSize = unpacker.readLong();
			} else if(fieldName == "maxInboundRate") {
				maxInboundRate = unpacker.readLong();
			} else if(fieldName == "maxFrameSize") {
				maxFrameSize = unpacker.readLong();
			} else {
				System.out.println("Unexpected field: " + fieldName);
				unpacker.skip();
			}
		}
		unpacker.readMapEnd(true);
	}
}
