package io.ably.lib.types;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.ably.lib.util.Serialisation;
import org.msgpack.core.MessagePacker;
import org.msgpack.value.Value;
import org.msgpack.value.ValueFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;

public final class DeltaExtras {
	private static final String TAG = DeltaExtras.class.getName();

	public static final String FORMAT_VCDIFF = "vcdiff";

	private final String format;
	private final String from;

	public DeltaExtras(final String format, final String from) {
		if (null == format) {
			throw new IllegalArgumentException("format cannot be null.");
		}
		if (null == from) {
			throw new IllegalArgumentException("from cannot be null.");
		}

		this.format = format;
		this.from = from;
	}

	/**
	 * The delta format. As at API version 1.2, only {@link DeltaExtras.FORMAT_VCDIFF} is supported.
	 * Will never return null.
	 */
	public String getFormat() {
		return format;
	}

	/**
	 * The id of the message the delta was generated from.
	 * Will never return null.
	 */
	public String getFrom() {
		return from;
	}

	/* package private */ void writeMsgpack(MessagePacker packer) throws IOException {
		packer.packMapHeader(2);

		packer.packString("format");
		packer.packString(format);

		packer.packString("from");
		packer.packString(from);
	}

	/* package private */ static DeltaExtras fromMessagePackMap(final Map<Value, Value> map) throws IOException {
		final Value format = map.get(ValueFactory.newString("format"));
		final Value from = map.get(ValueFactory.newString("from"));
		return new DeltaExtras(format.asStringValue().asString(), from.asStringValue().asString());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		DeltaExtras that = (DeltaExtras) o;
		return format.equals(that.format) &&
				from.equals(that.from);
	}

	@Override
	public int hashCode() {
		return Objects.hash(format, from);
	}

	public static class Serializer implements JsonSerializer<DeltaExtras> {
		@Override
		public JsonElement serialize(final DeltaExtras src, final Type typeOfSrc, final JsonSerializationContext context) {
			final JsonObject json = new JsonObject();
			final Gson gson = Serialisation.gson;
			json.addProperty("format", src.getFormat());
			json.addProperty("from", src.getFrom());
			return json;
		}
	}
}
