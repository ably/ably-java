package io.ably.lib.types;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.ably.lib.util.Log;
import io.ably.lib.util.Serialisation;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.lang.reflect.Type;

public final class MessageExtras {
	private static final String TAG = MessageExtras.class.getName();

	/**
	 * Only intended for use with a MessageExtras instance received from Ably's servers (inbound).
	 * May be null if raw is null.
	 * Must be null if raw is not null.
	 */
	private final DeltaExtras delta;

	/**
	 * Only intended for use with MessageExtras instances created to be sent to Ably's servers (outbound).
	 * May be null if delta is null.
	 * Must be null if delta is not null.
	 */
	private final JsonObject raw; // may be null

	/**
	 * Creates a MessageExtras instance to be sent as extra with a Message to Ably's servers.
	 *
	 * @see <a href="https://www.ably.io/documentation/general/push/publish#channel-broadcast-example">Channel-based push notification example</a>
	 *
	 * @since 1.2.1
	 */
	public MessageExtras(final JsonObject raw) {
		this.raw = raw;
		delta = null;
	}

	/**
	 * @since 1.2.0
	 */
	public MessageExtras(final DeltaExtras delta) {
		this.delta = delta;
		raw = null;
	}

	public DeltaExtras getDelta() {
		return delta;
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

	public static class Serializer implements JsonSerializer<MessageExtras> {
		@Override
		public JsonElement serialize(final MessageExtras src, final Type typeOfSrc, final JsonSerializationContext context) {
			return (null != src.raw) ? src.raw : wrapDelta(src.getDelta());
		}

		private JsonObject wrapDelta(final DeltaExtras delta) {
			final JsonObject json = new JsonObject();
			json.add("delta", Serialisation.gson.toJsonTree(delta));
			return json;
		}
	}
}
