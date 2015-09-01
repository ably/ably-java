package io.ably.types;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;
import org.msgpack.MessagePack;
import org.msgpack.MessagePackable;
import org.msgpack.packer.Packer;
import org.msgpack.type.ValueType;
import org.msgpack.unpacker.Unpacker;

/**
 * A class representing an individual message to be sent or received
 * via the Ably Realtime service.
 */
public class Message extends BaseMessage implements MessagePackable {

	/**
	 * The event name, if available
	 */
	public String name;

	/**
	 * Construct a message from a JSON-encoded response body.
	 * @param json: a JSONObject obtained by parsing the response text
	 * @return
	 */
	public static Message fromJSON(JSONObject json) {
		Message result = new Message();
		if(json != null) {
			result.readJSON(json);
			result.name = (String)json.opt("name");
		}
		return result;
	}

	/**
	 * Internal: obtain a JSONObject from a Message
	 * @return
	 * @throws AblyException
	 */
	JSONObject toJSON() throws AblyException {
		JSONObject json = super.toJSON();
		try {
			if(name != null) json.put("name", name);
			return json;
		} catch(JSONException e) {
			throw new AblyException("Unexpected exception encoding message; err = " + e, 400, 40000);
		}
	}

	/**
	 * Construct a message from a Msgpack-encoded response body.
	 * @param packed: the response data
	 * @return
	 */
	public static Message fromMsgpack(byte[] packed) throws AblyException {
		try {
			return msgpack.read(packed, Message.class);
		} catch (IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

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
		this(name, null, data);
	}

	/**
	 * Generic constructor
	 * @param name
	 * @param data
	 */
	public Message(String name, String clientId, Object data) {
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

	@Override
	public void readFrom(Unpacker unpacker) throws IOException {
		int fieldCount = unpacker.readMapBegin();
		for(int i = 0; i < fieldCount; i++) {
			String fieldName = unpacker.readString().intern();
			ValueType fieldType = unpacker.getNextType();
			if(fieldType.equals(ValueType.NIL)) { --fieldCount; unpacker.readNil(); continue; }
			if(super.readField(unpacker, fieldName, fieldType)) continue;
			if(fieldName == "name") {
				name = unpacker.readString();
			} else {
				System.out.println("Unexpected field: " + fieldName);
				unpacker.skip();
			}
		}
		unpacker.readMapEnd(true);
	}

	@Override
	public void writeTo(Packer packer) throws IOException {
		int fieldCount = countFields();
		if(name != null) ++fieldCount;
		packer.writeMapBegin(fieldCount);
		super.writeFields(packer);
		if(name != null) {
			packer.write("name");
			packer.write(name);
		}
		packer.writeMapEnd(true);
	}

	private static final MessagePack msgpack = new MessagePack();
}
