package io.ably.types;

import io.ably.http.Http;
import io.ably.http.Http.RequestBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.msgpack.MessagePack;
import org.msgpack.MessagePackable;
import org.msgpack.annotation.Message;
import org.msgpack.annotation.OrdinalEnum;
import org.msgpack.packer.Packer;
import org.msgpack.template.ListTemplate;
import org.msgpack.template.Template;
import org.msgpack.type.ValueType;
import org.msgpack.unpacker.Unpacker;

/**
 * A class representing an individual presence update to be sent or received
 * via the Ably Realtime service.
 */
public class PresenceMessage extends BaseMessage implements MessagePackable, Cloneable {

	/**
	 * Presence Action: the event signified by a PresenceMessage
	 */
	@Message
	@OrdinalEnum
	public enum Action {
		ABSENT,
		PRESENT,
		ENTER,
		LEAVE,
		UPDATE;

		public int getValue() { return ordinal(); }
		public static Action findByValue(int value) { return values()[value]; }
	}

	public Action action;

	/**
	 * A unique member identifier, disambiguating situations where a given
	 * clientId is present on multiple connections simultaneously.
	 */
	public String memberId;

	/**
	 * Construct a PresenceMessage from a JSON-encoded response body
	 * @param json: the JSONObject obtained by parsing the body text
	 * @return
	 */
	public static PresenceMessage fromJSON(JSONObject json) {
		PresenceMessage result = new PresenceMessage();
		if(json != null) {
			result.readJSON(json);
			result.action = Action.findByValue(json.optInt("action"));
			result.memberId = (String)json.opt("memberId");
		}
		return result;
	}

	/**
	 * Construct an array of PresenceMessages from a JSON-encoded response body
	 * @param json: the JSONArray obtained by parsing the body text
	 * @return
	 */
	public static PresenceMessage[] fromJSON(JSONArray json) {
		int count = json.length();
		PresenceMessage[] result = new PresenceMessage[count];
		for(int i = 0; i < count; i++)
			result[i] = PresenceMessage.fromJSON(json.optJSONObject(i));
		return result;
	}

	/**
	 * Construct a PresenceMessage from a Msgpack-encoded response body
	 * @param packed: the Msgpack buffer body text
	 * @return
	 */
	public static PresenceMessage fromMsgpack(byte[] packed) throws AblyException {
		try {
			return (new MessagePack()).read(packed, PresenceMessage.class);
		} catch (IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	/**
	 * Internal: obtain a JSONObject from a PresenceMessage
	 * @return
	 * @throws AblyException
	 */
	JSONObject toJSON() throws AblyException {
		JSONObject json = super.toJSON();
		try {
			json.put("action", action.getValue());
			return json;
		} catch(JSONException e) {
			throw new AblyException("Unexpected exception encoding message; err = " + e, 400, 40000);
		}
	}

	/**
	 * Internal: obtain a JSON-encoded request body from a PresenceMessage
	 * @param message
	 * @return
	 * @throws AblyException
	 */
	public static RequestBody asJSONRequest(PresenceMessage message) throws AblyException {
		return new Http.JSONRequestBody(message.toJSON().toString());
	}

	/**
	 * Internal: obtain a JSONArray from an array of PresenceMessages
	 * @param messages
	 * @return
	 * @throws AblyException
	 */
	public static JSONArray asJSON(PresenceMessage[] messages) throws AblyException {
		JSONArray json;
		try {
			json = new JSONArray();
			for(int i = 0; i < messages.length; i++)
				json.put(i, messages[i].toJSON());

			return json;
		} catch (JSONException e) {
			throw AblyException.fromThrowable(e);
		}
	}

	/**
	 * Internal: obtain a JSON-encoded request body from an array of PresenceMessages
	 * @param messages
	 * @return
	 * @throws AblyException
	 */
	public static RequestBody asJSONRequest(PresenceMessage[] messages) throws AblyException {
		return new Http.JSONRequestBody(asJSON(messages).toString());
	}

	/**
	 * Internal: obtain a Msgpack representation of a single Message
	 * @param message
	 * @return
	 * @throws AblyException
	 */
	public static byte[] asMsgpack(Message message) throws AblyException {
		try {
			return msgpack.write(message);
		} catch (IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	/**
	 * Internal: obtain a Msgpack-encoded request body from a Message
	 * @param message
	 * @return
	 * @throws AblyException
	 */
	public static RequestBody asMsgpackRequest(PresenceMessage message) throws AblyException {
		return asMsgpackRequest(new PresenceMessage[] { message });
	}

	/**
	 * Internal: obtain a Msgpack-encoded request body from an array of Messages
	 * @param messages
	 * @return
	 * @throws AblyException
	 */
	public static RequestBody asMsgpackRequest(PresenceMessage[] messages) throws AblyException {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			Packer packer = msgpack.createPacker(out);
			listTmpl.write(packer, Arrays.asList(messages));
			return new Http.ByteArrayRequestBody(out.toByteArray());
		} catch(IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

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
		if(memberId != null)
			result.append(" memberId=").append(memberId);
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
			if(fieldName == "action") {
				action = Action.findByValue(unpacker.readInt());
			} else if(fieldName == "memberId") {
				memberId = unpacker.readString();
			} else {
				System.out.println("Unexpected field: " + fieldName);
				unpacker.skip();
			}
		}
		unpacker.readMapEnd(true);
	}

	@Override
	public void writeTo(Packer packer) throws IOException {
		int fieldCount = countFields() + 1; //action
		packer.writeMapBegin(fieldCount);
		super.writeFields(packer);
		packer.write("action");
		packer.write(action);
		packer.writeMapEnd(true);
	}

	@Override
	public Object clone() {
		PresenceMessage result = new PresenceMessage();
		result.id = id;
		result.timestamp = timestamp;
		result.clientId = clientId;
		result.encoding = encoding;
		result.data = data;
		result.action = action;
		result.memberId = memberId;
		return result;
	}

	private static final MessagePack msgpack = new MessagePack();
	private static final Template<List<PresenceMessage>> listTmpl = new ListTemplate<PresenceMessage>(msgpack.lookup(PresenceMessage.class));
}
