package io.ably.lib.types;

import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ChannelParams extends HashMap<String, String> {
	public ChannelParams() { }

	public ChannelParams(ChannelParams toCopy) {
		super(toCopy);
	}

	void writeMsgpack(MessagePacker packer) throws IOException {
		packer.packMapHeader(this.size());
		for (Map.Entry<String, String> entry : this.entrySet()) {
			packer.packString(entry.getKey());
			packer.packString(entry.getValue());
		}
	}

	private ChannelParams readMsgpack(MessageUnpacker unpacker) throws IOException {
		int fieldCount = unpacker.unpackMapHeader();
		for(int i = 0; i < fieldCount; i++) {
			String fieldName = unpacker.unpackString();
			MessageFormat fieldFormat = unpacker.getNextFormat();
			if(fieldFormat.equals(MessageFormat.NIL)) { unpacker.unpackNil(); continue; }
			this.put(fieldName, unpacker.unpackString());
		}
		return this;
	}

	static ChannelParams fromMsgpack(MessageUnpacker unpacker) throws IOException {
		return (new ChannelParams()).readMsgpack(unpacker);
	}
}
