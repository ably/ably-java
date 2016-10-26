package io.ably.lib.types;

import java.io.IOException;
import java.lang.reflect.Type;

import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import io.ably.lib.util.Log;

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
		heartbeat,
		ack,
		nack,
		connect,
		connected,
		disconnect,
		disconnected,
		close,
		closed,
		error,
		attach,
		attached,
		detach,
		detached,
		presence,
		message,
		sync,
		auth;

		public int getValue() { return ordinal(); }
		public static Action findByValue(int value) { return values()[value]; }
	}

	public enum Flag {
		has_presence,
		has_backlog;

		public int getValue() { return ordinal(); }
		public static Flag findByValue(int value) { return values()[value]; }
	}

	public static boolean mergeTo(ProtocolMessage dest, ProtocolMessage src) {
		boolean result = false;
		Action action;
		if(dest.channel == src.channel) {
			if((action = dest.action) == src.action) {
				switch(action) {
				case message: {
						Message[] srcMessages = src.messages;
						Message[] destMessages = dest.messages;
						Message[] mergedMessages = dest.messages = new Message[destMessages.length + srcMessages.length];
						System.arraycopy(destMessages, 0, mergedMessages, 0, destMessages.length);
						System.arraycopy(srcMessages, 0, mergedMessages, destMessages.length, srcMessages.length);
						result = true;
					}
					break;					
				case presence: {
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
		return (msg.action == Action.message || msg.action == Action.presence);
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
	public Long connectionSerial;
	public Long msgSerial;
	public long timestamp;
	public Message[] messages;
	public PresenceMessage[] presence;
	public ConnectionDetails connectionDetails;
	public AuthDetails auth;

	void writeMsgpack(MessagePacker packer) throws IOException {
		int fieldCount = 1; //action
		if(channel != null) ++fieldCount;
		if(msgSerial != null) ++fieldCount;
		if(messages != null) ++fieldCount;
		if(presence != null) ++fieldCount;
		if(auth != null) ++fieldCount;
		packer.packMapHeader(fieldCount);
		packer.packString("action");
		packer.packInt(action.getValue());
		if(channel != null) {
			packer.packString("channel");
			packer.packString(channel);
		}
		if(msgSerial != null) {
			packer.packString("msgSerial");
			packer.packLong(msgSerial.longValue());
		}
		if(messages != null) {
			packer.packString("messages");
			MessageSerializer.writeMsgpackArray(messages, packer);
		}
		if(presence != null) {
			packer.packString("presence");
			PresenceSerializer.writeMsgpackArray(presence, packer);
		}
		if(auth != null) {
			packer.packString("auth");
			auth.writeMsgpack(packer);
		}
	}

	ProtocolMessage readMsgpack(MessageUnpacker unpacker) throws IOException {
		int fieldCount = unpacker.unpackMapHeader();
		for(int i = 0; i < fieldCount; i++) {
			String fieldName = unpacker.unpackString().intern();
			MessageFormat fieldFormat = unpacker.getNextFormat();
			if(fieldFormat.equals(MessageFormat.NIL)) { unpacker.unpackNil(); continue; }

			if(fieldName == "action") {
				action = Action.findByValue(unpacker.unpackInt());
			} else if(fieldName == "flags") {
				flags = unpacker.unpackInt();
			} else if(fieldName == "count") {
				count = unpacker.unpackInt();
			} else if(fieldName == "error") {
				error = ErrorInfo.fromMsgpack(unpacker);
			} else if(fieldName == "id") {
				id = unpacker.unpackString();
			} else if(fieldName == "channel") {
				channel = unpacker.unpackString();
			} else if(fieldName == "channelSerial") {
				channelSerial = unpacker.unpackString();
			} else if(fieldName == "connectionId") {
				connectionId = unpacker.unpackString();
			} else if(fieldName == "connectionSerial") {
				connectionSerial = Long.valueOf(unpacker.unpackLong());
			} else if(fieldName == "msgSerial") {
				msgSerial = Long.valueOf(unpacker.unpackLong());
			} else if(fieldName == "timestamp") {
				timestamp = unpacker.unpackLong();
			} else if(fieldName == "messages") {
				messages = MessageSerializer.readMsgpackArray(unpacker);
			} else if(fieldName == "presence") {
				presence = PresenceSerializer.readMsgpackArray(unpacker);
			} else if(fieldName == "connectionDetails") {
				connectionDetails = ConnectionDetails.fromMsgpack(unpacker);
			} else if(fieldName == "auth") {
				auth = AuthDetails.fromMsgpack(unpacker);
			} else {
				Log.v(TAG, "Unexpected field: " + fieldName);
				unpacker.skipValue();
			}
		}
		return this;
	}

	static ProtocolMessage fromMsgpack(MessageUnpacker unpacker) throws IOException {
		return (new ProtocolMessage()).readMsgpack(unpacker);
	}

	public static class ActionSerializer implements JsonSerializer<Action>, JsonDeserializer<Action> {
		@Override
		public Action deserialize(JsonElement json, Type t, JsonDeserializationContext ctx)
				throws JsonParseException {
			return Action.findByValue(json.getAsInt());
		}

		@Override
		public JsonElement serialize(Action action, Type t, JsonSerializationContext ctx) {
			return new JsonPrimitive(action.getValue());
		}		
	}

	public static class AuthDetails {
		public String accessToken;

		private AuthDetails() { }
		public AuthDetails(String s) { accessToken = s; }

		AuthDetails readMsgpack(MessageUnpacker unpacker) throws IOException {
			int fieldCount = unpacker.unpackMapHeader();
			for(int i = 0; i < fieldCount; i++) {
				String fieldName = unpacker.unpackString().intern();
				MessageFormat fieldFormat = unpacker.getNextFormat();
				if(fieldFormat.equals(MessageFormat.NIL)) { unpacker.unpackNil(); continue; }

				if(fieldName == "accessToken") {
					accessToken = unpacker.unpackString();
				} else {
					System.out.println("Unexpected field: " + fieldName);
					unpacker.skipValue();
				}
			}
			return this;
		}

		static AuthDetails fromMsgpack(MessageUnpacker unpacker) throws IOException {
			return (new AuthDetails()).readMsgpack(unpacker);
		}

		void writeMsgpack(MessagePacker packer) throws IOException {
			int fieldCount = 0;
			if(accessToken != null) ++fieldCount;
			packer.packMapHeader(fieldCount);
			if(accessToken != null) {
				packer.packString("accessToken");
				packer.packString(accessToken);
			}
		}
	}

	private static final String TAG = ProtocolMessage.class.getName();
}
