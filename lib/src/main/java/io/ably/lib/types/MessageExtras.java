package io.ably.lib.types;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.ably.lib.util.Serialisation;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.ImmutableMapValue;
import org.msgpack.value.ImmutableValue;
import org.msgpack.value.Value;
import org.msgpack.value.ValueFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;

public final class MessageExtras {
	private static final String TAG = MessageExtras.class.getName();

	private final DeltaExtras delta;
	private final JsonObject raw;

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

	private MessageExtras(final JsonObject raw, final DeltaExtras delta) {
		this.raw = raw;
		this.delta = delta;
	}

	public DeltaExtras getDelta() {
		return delta;
	}

	/* package private */ JsonObject getRaw() {
		return raw;
	}

	/* package private */ void writeMsgpack(MessagePacker packer) throws IOException {
		if (null == raw) {
			// raw is null, so delta is not null
			packer.packMapHeader(1);
			packer.packString("delta");
			this.delta.writeMsgpack(packer);
		} else {
			// raw is not null, so delta can be ignored
			Serialisation.gsonToMsgpack(raw, packer);
		}
	}

	/* package private */ static MessageExtras fromMsgpack(MessageUnpacker unpacker) throws IOException {
		DeltaExtras delta = null;

		final ImmutableValue value = unpacker.unpackValue();
		if (value instanceof ImmutableMapValue) {
			final Map<Value, Value> map = ((ImmutableMapValue) value).map();
			final Value deltaValue = map.get(ValueFactory.newString("delta"));
			if (null != deltaValue) {
				if (!(deltaValue instanceof ImmutableMapValue)) {
					// There's a delta key but the value at that key is not a map.
					throw new IOException("The delta extras unpacked to the wrong type \"" + deltaValue.getClass() + "\" when expected a map.");
				}
				final Map<Value, Value> deltaMap = ((ImmutableMapValue)deltaValue).map();
				delta = DeltaExtras.fromMessagePackMap(deltaMap);
			}
		}

		final JsonElement element = Serialisation.msgpackToGson(value);
		if (!(element instanceof JsonObject)) {
			// The root thing that we unpacked was not a map.
			throw new IOException("The extras unpacked to the wrong type \"" + element.getClass() + "\" when expected a JsonObject.");
		}
		final JsonObject raw = (JsonObject)element;

		return new MessageExtras(raw, delta);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		MessageExtras that = (MessageExtras) o;
		return (null == raw) ?
				Objects.equals(delta, that.delta) :
				Objects.equals(raw, that.raw);
	}

	@Override
	public int hashCode() {
		return (null == raw) ? Objects.hashCode(delta) : Objects.hashCode(raw);
	}

	@Override
	public String toString() {
		return "MessageExtras{" +
				"delta=" + delta +
				", raw=" + raw +
				'}';
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
