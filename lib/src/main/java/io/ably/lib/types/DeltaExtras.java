package io.ably.lib.types;

import io.ably.lib.util.Log;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;

public class DeltaExtras {
	private static final String TAG = DeltaExtras.class.getName();

	public String format;
	public String from;

	void writeMsgpack(MessagePacker packer) throws IOException {
		packer.packMapHeader(2);

		packer.packString("format");
		packer.packString(this.format);

		packer.packString("from");
		packer.packString(this.from);
	}

	private DeltaExtras readMsgpack(MessageUnpacker unpacker) throws IOException {
		int fieldCount = unpacker.unpackMapHeader();
		for(int i = 0; i < fieldCount; i++) {
			String fieldName = unpacker.unpackString();
			MessageFormat fieldFormat = unpacker.getNextFormat();
			if(fieldFormat.equals(MessageFormat.NIL)) {
				unpacker.unpackNil();
				continue;
			}

			if(fieldName.equals("format")) {
				this.format = unpacker.unpackString();
			} else if (fieldName.equals("from")) {
				this.from = unpacker.unpackString();
			} else {
				Log.v(TAG, "Unexpected field: " + fieldName);
				unpacker.skipValue();
			}
		}
		return this;
	}

	static DeltaExtras fromMsgpack(MessageUnpacker unpacker) throws IOException {
		return (new DeltaExtras()).readMsgpack(unpacker);
	}
}
