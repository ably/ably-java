package io.ably.lib.types;

import java.io.IOException;
import java.lang.reflect.Type;

import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import io.ably.lib.util.Log;

/**
 * A class representing an individual message to be sent or received
 * via the Ably Realtime service.
 */
public class Message extends BaseMessage {

	/**
	 * The event name, if available
	 */
	public String name;

	/**
	 * Default constructor
	 */
	public Message() {}

	/**
	 * Construct a message from event name and data 
	 * @param name
	 * @param data
	 */
	public Message(String name, Object data) {
		this(name, data, null);
	}

	/**
	 * Generic constructor
	 * @param name
	 * @param data
	 */
	public Message(String name, Object data, String clientId) {
		this.name = name;
		this.clientId = clientId;
		this.data = data;
	}

	/**
	 * Generate a String summary of this Message
	 * @return string
	 */
	public String toString() {
		StringBuilder result = new StringBuilder("[Message");
		super.getDetails(result);
		if(name != null)
			result.append(" name=").append(name);
		result.append(']');
		return result.toString();
	}

	void writeMsgpack(MessagePacker packer) throws IOException {
		int fieldCount = super.countFields();
		if(name != null) ++fieldCount;
		packer.packMapHeader(fieldCount);
		super.writeFields(packer);
		if(name != null) {
			packer.packString("name");
			packer.packString(name);
		}
	}

	Message readMsgpack(MessageUnpacker unpacker) throws IOException {
		int fieldCount = unpacker.unpackMapHeader();
		for(int i = 0; i < fieldCount; i++) {
			String fieldName = unpacker.unpackString().intern();
			MessageFormat fieldFormat = unpacker.getNextFormat();
			if(fieldFormat.equals(MessageFormat.NIL)) { unpacker.unpackNil(); continue; }

			if(super.readField(unpacker, fieldName, fieldFormat)) { continue; }
			if(fieldName.equals("name")) {
				name = unpacker.unpackString();
			} else {
				Log.v(TAG, "Unexpected field: " + fieldName);
				unpacker.skipValue();
			}
		}
		return this;
	}

	static Message fromMsgpack(MessageUnpacker unpacker) throws IOException {
		return (new Message()).readMsgpack(unpacker);
	}

	public static class Serializer extends BaseMessage.Serializer implements JsonSerializer<Message> {
		@Override
		public JsonElement serialize(Message message, Type typeOfMessage, JsonSerializationContext ctx) {
			JsonObject json = (JsonObject)super.serialize(message, typeOfMessage, ctx);
			if(message.name != null) json.addProperty("name", message.name);
			return json;
		}
	}

	private static final String TAG = Message.class.getName();
}
