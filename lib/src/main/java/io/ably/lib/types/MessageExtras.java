package io.ably.lib.types;

import com.google.gson.JsonElement;
import io.ably.lib.util.Log;
import io.ably.lib.util.Serialisation;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;

public final class MessageExtras {
	private static final String TAG = MessageExtras.class.getName();
	
	// TODO what about the 1.1 push extension? (TM2i)

	private final DeltaExtras delta; // may be null
	
	public MessageExtras(final DeltaExtras delta) {
		this.delta = delta;
	}
	
	public DeltaExtras getDelta() {
		return delta;
	}

	/* package private */ JsonElement toJsonElement() {
		return Serialisation.gson.toJsonTree(this);
	}

	/* package private */ void writeMsgpack(MessagePacker packer) throws IOException {
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

	/* package private */ static MessageExtras fromMsgpack(MessageUnpacker unpacker) throws IOException {
		final int fieldCount = unpacker.unpackMapHeader();
		DeltaExtras delta = null;
		for(int i = 0; i < fieldCount; i++) {
			String fieldName = unpacker.unpackString();
			MessageFormat fieldFormat = unpacker.getNextFormat();
			if(fieldFormat.equals(MessageFormat.NIL)) {
				unpacker.unpackNil();
				continue;
			}

			if(fieldName.equals("delta")) {
				delta = DeltaExtras.fromMsgpack(unpacker);
			} else {
				Log.w(TAG, "Unexpected field: " + fieldName);
				unpacker.skipValue();
			}
		}
		
		return new MessageExtras(delta);
	}
}
