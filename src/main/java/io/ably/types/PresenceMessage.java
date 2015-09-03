package io.ably.types;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.ably.http.Http;
import io.ably.http.Http.RequestBody;
import io.ably.util.Serialisation;

/**
 * A class representing an individual presence update to be sent or received
 * via the Ably Realtime service.
 */
public class PresenceMessage extends BaseMessage implements Cloneable {

	/**
	 * Presence Action: the event signified by a PresenceMessage
	 */
	public enum Action {
		ABSENT,
		PRESENT,
		ENTER,
		LEAVE,
		UPDATE;

		public int getValue() { return ordinal(); }
		public static Action findByValue(int value) { return values()[value]; }

		public static class Serializer extends JsonSerializer<Action> {
			@Override
			public void serialize(Action action, JsonGenerator generator, SerializerProvider arg2)
					throws IOException, JsonProcessingException {

				generator.writeNumber(action.getValue());
			}
		}

		public static class Deserializer extends JsonDeserializer<Action> {
			@Override
			public Action deserialize(JsonParser parser, DeserializationContext deserContext)
					throws IOException, JsonProcessingException {

				return findByValue(parser.getIntValue());
			}
		}
	}

	public Action action;

	/**
	 * Construct a PresenceMessage from a Msgpack-encoded response body
	 * @param packed: the Msgpack buffer body text
	 * @return
	 */
	public static PresenceMessage fromJSON(String packed) throws AblyException {
		try {
			return Serialisation.jsonObjectMapper.readValue(packed, PresenceMessage.class);
		} catch (IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	/**
	 * Construct a PresenceMessage from a Msgpack-encoded response body
	 * @param packed: the Msgpack buffer body text
	 * @return
	 */
	public static PresenceMessage fromMsgpack(byte[] packed) throws AblyException {
		try {
			return Serialisation.msgpackObjectMapper.readValue(packed, PresenceMessage.class);
		} catch (IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	/**
	 * Internal: obtain a JSON representation of a single Message
	 * @param message
	 * @return
	 * @throws AblyException
	 */
	public static String asJSON(Message message) throws AblyException {
		try {
			return Serialisation.jsonObjectMapper.writeValueAsString(message);
		} catch (IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	/**
	 * Internal: obtain a Msgpack representation of a single Message
	 * @param message
	 * @return
	 * @throws AblyException
	 */
	public static byte[] asMsgpack(Message message) throws AblyException {
		try {
			return Serialisation.msgpackObjectMapper.writeValueAsBytes(message);
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
			return new Http.ByteArrayRequestBody(Serialisation.msgpackObjectMapper.writeValueAsBytes(messages));
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


	static {
		SimpleModule presenceModule = new SimpleModule("PresenceMessage", new Version(1, 0, 0, null, null, null));
		presenceModule.addSerializer(Action.class, new Action.Serializer());
		presenceModule.addDeserializer(Action.class, new Action.Deserializer());
		Serialisation.msgpackObjectMapper.registerModule(presenceModule);
	}
}
