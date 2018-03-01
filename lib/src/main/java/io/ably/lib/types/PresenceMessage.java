package io.ably.lib.types;

import java.io.IOException;
import java.lang.reflect.Type;

import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import io.ably.lib.util.Log;

/**
 * A class representing an individual presence update to be sent or received
 * via the Ably Realtime service.
 */
public class PresenceMessage extends BaseMessage implements Cloneable {

	/**
	 * Presence Action: the event signified by a PresenceMessage
	 */
	public enum Action {
		absent,
		present,
		enter,
		leave,
		update;

		public int getValue() { return ordinal(); }
		public static Action findByValue(int value) { return values()[value]; }
	}

	public Action action;

	/**
	 * Default constructor
	 */
	public PresenceMessage() {}

	/**
	 * Construct a PresenceMessage from an Action and clientId
	 * @param action
	 * @param clientId
	 */
	public PresenceMessage(Action action, String clientId) {
		this(action, clientId, null);
	}

	/**
	 * Generic constructor
	 * @param action
	 * @param clientId
	 * @param data
	 */
	public PresenceMessage(Action action, String clientId, Object data) {
		this.action = action;
		this.clientId = clientId;
		this.data = data;
	}

	/**
	 * Generate a String summary of this PresenceMessage
	 * @return string
	 */
	public String toString() {
		StringBuilder result = new StringBuilder("[PresenceMessage");
		super.getDetails(result);
		result.append(" action=").append(action.name());
		result.append(']');
		return result.toString();
	}

	@Override
	public Object clone() {
		PresenceMessage result = new PresenceMessage();
		result.id = id;
		result.timestamp = timestamp;
		result.clientId = clientId;
		result.connectionId = connectionId;
		result.encoding = encoding;
		result.data = data;
		result.action = action;
		return result;
	}

	void writeMsgpack(MessagePacker packer) throws IOException {
		int fieldCount = super.countFields();
		++fieldCount;
		packer.packMapHeader(fieldCount);
		super.writeFields(packer);
		packer.packString("action");
		packer.packInt(action.getValue());
	}

	PresenceMessage readMsgpack(MessageUnpacker unpacker) throws IOException {
		int fieldCount = unpacker.unpackMapHeader();
		for(int i = 0; i < fieldCount; i++) {
			String fieldName = unpacker.unpackString().intern();
			MessageFormat fieldFormat = unpacker.getNextFormat();
			if(fieldFormat.equals(MessageFormat.NIL)) { unpacker.unpackNil(); continue; }

			if(super.readField(unpacker, fieldName, fieldFormat)) { continue; }
			if(fieldName.equals("action")) {
				action = Action.findByValue(unpacker.unpackInt());
			} else {
				Log.v(TAG, "Unexpected field: " + fieldName);
				unpacker.skipValue();
			}
		}
		return this;
	}

	static PresenceMessage fromMsgpack(MessageUnpacker unpacker) throws IOException {
		return (new PresenceMessage()).readMsgpack(unpacker);
	}

	public static class ActionSerializer implements JsonDeserializer<Action> {
		@Override
		public Action deserialize(JsonElement json, Type t, JsonDeserializationContext ctx)
				throws JsonParseException {
			return Action.findByValue(json.getAsInt());
		}
	}

	public static class Serializer extends BaseMessage.Serializer implements JsonSerializer<PresenceMessage> {
		@Override
		public JsonElement serialize(PresenceMessage message, Type typeOfMessage, JsonSerializationContext ctx) {
			JsonObject json = (JsonObject)super.serialize(message, typeOfMessage, ctx);
			if(message.action != null) json.addProperty("action", message.action.getValue());
			return json;
		}		
	}

	/**
	 * Get the member key for the PresenceMessage.
	 * @return
	 */
	public String memberKey() {
		return connectionId + ':' + clientId;
	}

	private static final String TAG = PresenceMessage.class.getName();
}
