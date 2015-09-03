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

import io.ably.util.Serialisation;

/**
 * A message sent and received over the Realtime protocol.
 * A ProtocolMessage always relates to a single channel only, but
 * can contain multiple individual Messages or PresenceMessages.
 * ProtocolMessages are serially numbered on a connection.
 * See the Ably client library developer documentation for further
 * details on the members of a ProtocolMessage.
 */
public class ProtocolMessage {
	public enum Action {
		HEARTBEAT,
		ACK,
		NACK,
		CONNECT,
		CONNECTED,
		DISCONNECT,
		DISCONNECTED,
		CLOSE,
		CLOSED,
		ERROR,
		ATTACH,
		ATTACHED,
		DETACH,
		DETACHED,
		PRESENCE,
		MESSAGE,
		SYNC;

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

	public enum Flag {
		HAS_PRESENCE,
		HAS_BACKLOG;

		public int getValue() { return ordinal(); }
		public static Flag findByValue(int value) { return values()[value]; }
	}

	public static ProtocolMessage fromJSON(String packed) throws AblyException {
		try {
			return Serialisation.jsonObjectMapper.readValue(packed, ProtocolMessage.class);
		} catch (IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	public static ProtocolMessage fromMsgpack(byte[] packed) throws AblyException {
		try {
			return Serialisation.msgpackObjectMapper.readValue(packed, ProtocolMessage.class);
		} catch (IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	public static byte[] asJSON(ProtocolMessage message) throws AblyException {
		try {
			return Serialisation.jsonObjectMapper.writeValueAsBytes(message);
		} catch (IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	public static byte[] asMsgpack(ProtocolMessage message) throws AblyException {
		try {
			return Serialisation.msgpackObjectMapper.writeValueAsBytes(message);
		} catch (IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	public static boolean mergeTo(ProtocolMessage dest, ProtocolMessage src) {
		boolean result = false;
		Action action;
		if(dest.channel == src.channel) {
			if((action = dest.action) == src.action) {
				switch(action) {
				case MESSAGE: {
						Message[] srcMessages = src.messages;
						Message[] destMessages = dest.messages;
						Message[] mergedMessages = dest.messages = new Message[destMessages.length + srcMessages.length];
						System.arraycopy(destMessages, 0, mergedMessages, 0, destMessages.length);
						System.arraycopy(srcMessages, 0, mergedMessages, destMessages.length, srcMessages.length);
						result = true;
					}
					break;					
				case PRESENCE: {
						PresenceMessage[] srcMessages = src.presence;
						PresenceMessage[] destMessages = dest.presence;
						PresenceMessage[] mergedMessages = dest.presence = new PresenceMessage[destMessages.length + srcMessages.length];
						System.arraycopy(mergedMessages, 0, destMessages, 0, destMessages.length);
						System.arraycopy(mergedMessages, destMessages.length, srcMessages, 0, srcMessages.length);
						result = true;
					}
					break;
				default:
				}
			}
		}
		return result;
	}

	public static boolean ackRequired(ProtocolMessage msg) {
		return (msg.action == Action.MESSAGE || msg.action == Action.PRESENCE);
	}

	public ProtocolMessage() {}

	public ProtocolMessage(Action action) {
		this.action = action;
	}

	public ProtocolMessage(Action action, String channel) {
		this.action = action;
		this.channel = channel;
	}

	public Action action;
	public int flags;
	public int count;
	public ErrorInfo error;
	public String id;
	public String channel;
	public String channelSerial;
	public String connectionId;
	public String connectionKey;
	public Long connectionSerial;
	public Long msgSerial;
	public long timestamp;
	public Message[] messages;
	public PresenceMessage[] presence;
	public ConnectionDetails connectionDetails;

	static {
		SimpleModule protocolModule = new SimpleModule("ProtocolMessage", new Version(1, 0, 0, null, null, null));
		protocolModule.addSerializer(Action.class, new Action.Serializer());
		protocolModule.addDeserializer(Action.class, new Action.Deserializer());
		Serialisation.msgpackObjectMapper.registerModule(protocolModule);
		Serialisation.jsonObjectMapper.registerModule(protocolModule);
	}
}
