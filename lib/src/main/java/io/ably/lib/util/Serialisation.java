package io.ably.lib.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePack.PackerConfig;
import org.msgpack.core.MessagePack.UnpackerConfig;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

import io.ably.lib.http.Http;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Message;
import io.ably.lib.types.PresenceMessage;
import io.ably.lib.types.ProtocolMessage;

public class Serialisation {
	public static final JsonParser gsonParser;
	public static final GsonBuilder gsonBuilder;
	public static final Gson gson;

	public static final PackerConfig msgpackPackerConfig;
	public static final UnpackerConfig msgpackUnpackerConfig;

	static {
		gsonParser = new JsonParser();
		gsonBuilder = new GsonBuilder();
		gsonBuilder.registerTypeAdapter(Message.class, new Message.Serializer());
		gsonBuilder.registerTypeAdapter(PresenceMessage.class, new PresenceMessage.Serializer());
		gsonBuilder.registerTypeAdapter(PresenceMessage.Action.class, new PresenceMessage.ActionSerializer());
		gsonBuilder.registerTypeAdapter(ProtocolMessage.Action.class, new ProtocolMessage.ActionSerializer());
		gson = gsonBuilder.create();

		msgpackPackerConfig = Platform.name.equals("android") ?
				new PackerConfig().withSmallStringOptimizationThreshold(Integer.MAX_VALUE) :
				MessagePack.DEFAULT_PACKER_CONFIG;

		msgpackUnpackerConfig = MessagePack.DEFAULT_UNPACKER_CONFIG;
	}

	public static byte[] gsonToMsgpack(JsonElement json) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			MessagePacker packer = msgpackPackerConfig.newPacker(out);
			gsonToMsgpack(json, packer);
			packer.flush();
			return out.toByteArray();
		} catch(IOException e) { return null; }
	}

	public static JsonElement msgpackToGson(byte[] bytes) {
		MessageUnpacker unpacker = msgpackUnpackerConfig.newUnpacker(bytes);
		try {
			return msgpackToGson(unpacker.unpackValue());
		} catch (IOException e) {
			return null;
		}
	}

	public static class HttpResponseHandler<T extends JsonElement> implements Http.ResponseHandler<T> {
		@Override
		public T handleResponse(Http.Response response, ErrorInfo error) throws AblyException {
			if (error != null) {
				throw AblyException.fromErrorInfo(error);
			}
			if ("application/json".equals(response.contentType)) {
				return (T)jsonBytesToGson(response.body);
			} else if("application/x-msgpack".equals(response.contentType)) {
				return (T)msgpackToGson(response.body);
			} else {
				throw AblyException.fromThrowable(new Exception("unknown content type " + response.contentType));
			}
		}
	}

	public static JsonElement jsonBytesToGson(byte[] bytes) {
		try {
			return gson.fromJson(new String(bytes, "UTF-8"), JsonElement.class);
		} catch (UnsupportedEncodingException e) {
			return null;
		}
	}

	private static void gsonToMsgpack(JsonElement json, MessagePacker packer) {
		if (json.isJsonArray()) {
			gsonToMsgpack((JsonArray)json, packer);
		} else if (json.isJsonObject()) {
			gsonToMsgpack((JsonObject)json, packer);
		} else if (json.isJsonNull()) {
			gsonToMsgpack((JsonNull)json, packer);
		} else if (json.isJsonPrimitive()) {
			gsonToMsgpack((JsonPrimitive)json, packer);
		} else {
			throw new RuntimeException("unreachable");
		}
	}

	private static void gsonToMsgpack(JsonArray array, MessagePacker packer) {
		try {
			packer.packArrayHeader(array.size());
			for (JsonElement elem : array) {
				gsonToMsgpack(elem, packer);
			}
		} catch(IOException e) {}
	}

	private static void gsonToMsgpack(JsonObject object, MessagePacker packer) {
		try {
			Set<Map.Entry<String, JsonElement>> entries = object.entrySet();
			packer.packMapHeader(entries.size());
			for (Map.Entry<String, JsonElement> entry : entries) {
				packer.packString(entry.getKey());
				gsonToMsgpack(entry.getValue(), packer);
			}
		} catch(IOException e) {}
	}

	private static void gsonToMsgpack(JsonNull n, MessagePacker packer) {
		try {
			packer.packNil();
		} catch(IOException e) {}
	}

	private static void gsonToMsgpack(JsonPrimitive primitive, MessagePacker packer) {
		try {
			if (primitive.isBoolean()) {
				packer.packBoolean(primitive.getAsBoolean());
			} else if (primitive.isNumber()) {
				Number number = primitive.getAsNumber();
				if (number instanceof BigDecimal || number instanceof Double) {
					packer.packDouble(number.doubleValue());
				} else if (number instanceof Float) {
					packer.packFloat(number.floatValue());
				} else if (number instanceof BigInteger || number instanceof Long) {
					packer.packLong(number.longValue());
				} else if (number instanceof Integer) {
					packer.packInt(number.intValue());
				} else if (number instanceof Short) {
					packer.packShort(number.shortValue());
				} else if (number instanceof Byte) {
					packer.packByte(number.byteValue());
				} else {
					packer.packString(primitive.getAsString());
				}
			} else {
				packer.packString(primitive.getAsString());
			}
		} catch(IOException e) {}
	}

	private static JsonElement msgpackToGson(Value value) {
		switch (value.getValueType()) {
			case NIL:
				return JsonNull.INSTANCE;
			case BOOLEAN:
				return new JsonPrimitive(value.asBooleanValue().getBoolean());
			case INTEGER:
				return new JsonPrimitive(value.asIntegerValue().asLong());
			case FLOAT:
				return new JsonPrimitive(value.asFloatValue().toDouble());
			case STRING:
				return new JsonPrimitive(value.asStringValue().asString());
			case BINARY:
				return new JsonPrimitive(Base64Coder.encodeToString(value.asBinaryValue().asByteArray()));
			case ARRAY:
				JsonArray array = new JsonArray();
				for (Value element : value.asArrayValue()) {
					array.add(msgpackToGson(element));
				}
				return array;
			case MAP:
				JsonObject object = new JsonObject();
				for (Map.Entry<Value, Value> entry : value.asMapValue().entrySet()) {
					object.add(
							entry.getKey().asStringValue().asString(),
							msgpackToGson(entry.getValue())
					);
				}
				return object;
			case EXTENSION:
				return null;
			default:
				return null;
		}
	}
}
