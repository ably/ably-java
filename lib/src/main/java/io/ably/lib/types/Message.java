package io.ably.lib.types;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;

import com.google.gson.*;
import io.ably.lib.util.Serialisation;
import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import io.ably.lib.util.Log;
import io.ably.lib.util.Serialisation;

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
	 * Extras, if available
	 */
	public MessageExtras extras;

	/**
	 * Default constructor
	 */
	public Message() {
	}

	/**
	 * Construct a message from event name and data
	 * @param name
	 * @param data
	 */
	public Message(String name, Object data) {
		this(name, data, null, null);
	}


	public Message(String name, Object data, String clientId) {
		this(name, data, clientId, null);
	}

	public Message(String name, Object data, MessageExtras extras) {
		this(name, data, null, extras);
	}

	/**
	 * Generic constructor
	 * @param name
	 * @param data
	 * @param clientId
	 * @param extras
	 */
	public Message(String name, Object data, String clientId, MessageExtras extras) {
		this.name = name;
		this.clientId = clientId;
		this.data = data;
		this.extras = extras;
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
		if(extras != null) ++fieldCount;
		packer.packMapHeader(fieldCount);
		super.writeFields(packer);
		if(name != null) {
			packer.packString("name");
			packer.packString(name);
		}
		if(extras != null) {
			packer.packString("extras");
			extras.writeMsgpack(packer);
		}
	}

	Message readMsgpack(MessageUnpacker unpacker) throws IOException {
		int fieldCount = unpacker.unpackMapHeader();
		for(int i = 0; i < fieldCount; i++) {
			String fieldName = unpacker.unpackString().intern();
			MessageFormat fieldFormat = unpacker.getNextFormat();
			if(fieldFormat.equals(MessageFormat.NIL)) {
				unpacker.unpackNil();
				continue;
			}

			if(super.readField(unpacker, fieldName, fieldFormat)) {
				continue;
			}
			if(fieldName.equals("name")) {
				name = unpacker.unpackString();
			} else if (fieldName == "extras") {
				extras = MessageExtras.fromMsgpack(unpacker);
			} else {
				Log.v(TAG, "Unexpected field: " + fieldName);
				unpacker.skipValue();
			}
		}
		return this;
	}

	/**
	 * A specification for a collection of messages to be sent using the batch API
	 * @author paddy
	 */
	public static class Batch {
		public String[] channels;
		public Message[] messages;

		public Batch(String channel, Message[] messages) {
			if(channel == null || channel.isEmpty()) throw new IllegalArgumentException("A Batch spec cannot have an empty set of channels");
			if(messages == null || messages.length == 0) throw new IllegalArgumentException("A Batch spec cannot have an empty set of messages");
			this.channels = new String[]{channel};
			this.messages = messages;
		}

		public Batch(String[] channels, Message[] messages) {
			if(channels == null || channels.length == 0) throw new IllegalArgumentException("A Batch spec cannot have an empty set of channels");
			if(messages == null || messages.length == 0) throw new IllegalArgumentException("A Batch spec cannot have an empty set of messages");
			this.channels = channels;
			this.messages = messages;
		}

		public Batch(Collection<String> channels, Collection<Message> messages) {
			this(channels.toArray(new String[channels.size()]), messages.toArray(new Message[messages.size()]));
		}

		public void writeMsgpack(MessagePacker packer) throws IOException {
			packer.packMapHeader(2);
			packer.packString("channels");
			packer.packArrayHeader(channels.length);
			for(String ch : channels) packer.packString(ch);
			packer.packString("messages");
			MessageSerializer.writeMsgpackArray(messages, packer);
		}
	}

	static Message fromMsgpack(MessageUnpacker unpacker) throws IOException {
		return (new Message()).readMsgpack(unpacker);
	}

	/**
	 * Refer Spec TM3 <br>
	 * An alternative constructor that take an Message-JSON object and a channelOptions (optional), and return a Message
	 * @param messageJson
	 * @param channelOptions
	 * @return
	 * @throws MessageDecodeException
	 */
	public static Message fromEncoded(JsonObject messageJson, ChannelOptions channelOptions) throws MessageDecodeException {
		try {
			Message message = Serialisation.gson.fromJson(messageJson, Message.class);
			message.decode(channelOptions);
			return message;
		} catch(Exception e) {
			Log.e(Message.class.getName(), e.getMessage(), e);
			throw MessageDecodeException.fromDescription(e.getMessage());
		}
	}

	/**
	 * Refer Spec TM3 <br>
	 * An alternative constructor that takes a Stringified Message-JSON and a channelOptions (optional), and return a Message
	 * @param messageJson
	 * @param channelOptions
	 * @return
	 * @throws MessageDecodeException
	 */
	public static Message fromEncoded(String messageJson, ChannelOptions channelOptions) throws MessageDecodeException {
		try {
			JsonObject jsonObject = Serialisation.gson.fromJson(messageJson, JsonObject.class);
			return fromEncoded(jsonObject.getAsJsonObject(), channelOptions);
		} catch(Exception e) {
			Log.e(Message.class.getName(), e.getMessage(), e);
			throw MessageDecodeException.fromDescription(e.getMessage());
		}
	}

	/**
	 * Refer Spec TM3 <br>
	 * An alternative constructor that takes a Messages JsonArray and a channelOptions (optional), and return array of Messages.
	 * @param messageArray
	 * @param channelOptions
	 * @return
	 * @throws MessageDecodeException
	 */
	public static Message[] fromEncodedArray(JsonArray messageArray, ChannelOptions channelOptions) throws MessageDecodeException {
		try {
			Message[] messages = new Message[messageArray.size()];
			for(int index = 0; index < messageArray.size(); index++) {
				JsonElement jsonElement = messageArray.get(index);
				if(!jsonElement.isJsonObject()) {
					throw new JsonParseException("Not all JSON elements are of type JSON Object.");
				}
				messages[index] = fromEncoded(jsonElement.getAsJsonObject(), channelOptions);
			}
			return messages;
		} catch(Exception e) {
			e.printStackTrace();
			throw MessageDecodeException.fromDescription(e.getMessage());
		}
	}

	/**
	 *
	 * @param messagesArray
	 * @param channelOptions
	 * @return
	 * @throws MessageDecodeException
	 */
	public static Message[] fromEncodedArray(String messagesArray, ChannelOptions channelOptions) throws MessageDecodeException {
		try {
			JsonArray jsonArray = Serialisation.gson.fromJson(messagesArray, JsonArray.class);
			return fromEncodedArray(jsonArray, channelOptions);
		} catch(Exception e) {
			e.printStackTrace();
			throw MessageDecodeException.fromDescription(e.getMessage());
		}
	}

	public static class Serializer extends BaseMessage.Serializer implements JsonSerializer<Message> {
		@Override
		public JsonElement serialize(Message message, Type typeOfMessage, JsonSerializationContext ctx) {
			JsonObject json = (JsonObject) super.serialize(message, typeOfMessage, ctx);
			if(message.name != null) json.addProperty("name", message.name);
			if(message.extras != null) json.add("extras", message.extras.toJsonElement());
			return json;
		}
	}

	private static final String TAG = Message.class.getName();
}
