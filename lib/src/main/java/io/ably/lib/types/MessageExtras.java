package io.ably.lib.types;

import com.google.gson.JsonElement;
import io.ably.lib.util.Log;
import io.ably.lib.util.Serialisation;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;

public class MessageExtras {
	private static final String TAG = MessageExtras.class.getName();

	public DeltaExtras delta;

	JsonElement asJsonElement() {
		return Serialisation.gson.toJsonTree(this);
	}

	void writeMsgpack(MessagePacker packer) throws IOException {
		int fieldCount = 0;
		if (this.delta != null) {
			fieldCount++;
		}
		packer.packMapHeader(fieldCount);
		if (this.delta != null) {
			packer.packString("delta");
			this.delta.writeMsgpack(packer);
		}
	}

	private MessageExtras readMsgpack(MessageUnpacker unpacker) throws IOException {
		int fieldCount = unpacker.unpackMapHeader();
		for(int i = 0; i < fieldCount; i++) {
			String fieldName = unpacker.unpackString();
			MessageFormat fieldFormat = unpacker.getNextFormat();
			if(fieldFormat.equals(MessageFormat.NIL)) {
				unpacker.unpackNil();
				continue;
			}

			if(fieldName.equals("delta")) {
				this.delta = DeltaExtras.fromMsgpack(unpacker);
			} else {
				Log.v(TAG, "Unexpected field: " + fieldName);
				unpacker.skipValue();
			}
		}
		return this;
	}

	static MessageExtras fromMsgpack(MessageUnpacker unpacker) throws IOException {
		return (new MessageExtras()).readMsgpack(unpacker);
	}
}
