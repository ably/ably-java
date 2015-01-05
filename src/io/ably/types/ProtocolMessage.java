package io.ably.types;

import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.msgpack.MessagePack;
import org.msgpack.MessagePackable;
import org.msgpack.packer.Packer;
import org.msgpack.type.ValueType;
import org.msgpack.unpacker.Unpacker;

/**
 * A message sent and received over the Realtime protocol.
 * A ProtocolMessage always relates to a single channel only, but
 * can contain multiple individual Messages or PresenceMessages.
 * ProtocolMessages are serially numbered on a connection.
 * See the Ably client library developer documentation for further
 * details on the members of a ProtocolMessage.
 */
public class ProtocolMessage implements MessagePackable {
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
	}

	public enum Flag {
		HAS_PRESENCE,
		HAS_BACKLOG;

		public int getValue() { return ordinal(); }
		public static Flag findByValue(int value) { return values()[value]; }
	}

	public static ProtocolMessage fromJSON(JSONObject json) {
		ProtocolMessage result = new ProtocolMessage();
		if(json != null) {
			result.action = Action.findByValue(json.optInt("action"));
			result.flags = json.optInt("flags");
			result.count = json.optInt("count");
			if(json.has("msgSerial"))
				result.msgSerial = Long.valueOf(json.optLong("msgSerial"));
			if(json.has("error"))
				result.error = new ErrorInfo(json.optJSONObject("error"));
			if(json.has("id"))
				result.id = json.optString("id");
			if(json.has("channel"))
				result.channel = json.optString("channel");
			if(json.has("channelSerial"))
				result.channelSerial = json.optString("channelSerial");
			if(json.has("connectionId"))
				result.connectionId = json.optString("connectionId");
			if(json.has("connectionSerial"))
				result.connectionSerial = Long.valueOf(json.optLong("connectionSerial"));
			result.timestamp = json.optLong("timestamp");
			if(json.has("messages"))
				result.messages = MessageSerializer.readJSON(json.optJSONArray("messages"));
			if(json.has("presence"))
				result.presence = PresenceSerializer.readJSON(json.optJSONArray("presence"));
		}
		return result;
	}

	public static ProtocolMessage fromMsgpack(byte[] packed) throws AblyException {
		try {
			return msgpack.read(packed, ProtocolMessage.class);
		} catch (IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	private JSONObject toJSON() throws AblyException {
		JSONObject json = new JSONObject();
		try {
			json.put("action", action.getValue());
			json.put("channel", channel);
			json.put("msgSerial", msgSerial);
			if(messages != null) json.put("messages", MessageSerializer.writeJSON(messages));
			if(presence != null) json.put("presence", PresenceSerializer.writeJSON(presence));

			return json;
		} catch(JSONException e) {
			throw new AblyException("Unexpected exception encoding message; err = " + e, 400, 40000);
		}
	}

	public static JSONObject asJSON(ProtocolMessage message) throws AblyException {
		return message.toJSON();
	}

	public static JSONArray asJSON(ProtocolMessage[] messages) throws AblyException {
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

	public static byte[] asMsgpack(ProtocolMessage message) throws AblyException {
		try {
			return msgpack.write(message);
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

	@Override
	public void writeTo(Packer packer) throws IOException {
		int fieldCount = 1; //action
		if(channel != null) ++fieldCount;
		if(msgSerial != null) ++fieldCount;
		if(messages != null) ++fieldCount;
		if(presence != null) ++fieldCount;
		packer.writeMapBegin(fieldCount);
		packer.write("action");
		packer.write(action.getValue());
		if(channel != null) {
			packer.write("channel");
			packer.write(channel);
		}
		if(msgSerial != null) {
			packer.write("msgSerial");
			packer.write(msgSerial.longValue());
		}
		if(messages != null) {
			packer.write("messages");
			packer.writeArrayBegin(messages.length);
			for(Message msg : messages)
				msg.writeTo(packer);
			packer.writeArrayEnd(true);
		}
		if(presence != null) {
			packer.write("presence");
			packer.writeArrayBegin(presence.length);
			for(PresenceMessage msg : presence)
				msg.writeTo(packer);
			packer.writeArrayEnd(true);
		}
		packer.writeMapEnd(true);
	}

	@Override
	public void readFrom(Unpacker unpacker) throws IOException {
		int fieldCount = unpacker.readMapBegin();
		for(int i = 0; i < fieldCount; i++) {
			String fieldName = unpacker.readString().intern();
			ValueType fieldType = unpacker.getNextType();
			if(fieldType.equals(ValueType.NIL)) { unpacker.readNil(); continue; }

			if(fieldName == "action") {
				action = Action.findByValue(unpacker.readInt());
			} else if(fieldName == "flags") {
				flags = unpacker.readInt();
			} else if(fieldName == "count") {
				count = unpacker.readInt();
			} else if(fieldName == "error") {
				error = unpacker.read(ErrorInfo.class);
			} else if(fieldName == "id") {
				id = unpacker.readString();
			} else if(fieldName == "channel") {
				channel = unpacker.readString();
			} else if(fieldName == "channelSerial") {
				channelSerial = unpacker.readString();
			} else if(fieldName == "memberId") {
				memberId = unpacker.readString();
			} else if(fieldName == "connectionId") {
				connectionId = unpacker.readString();
			} else if(fieldName == "connectionSerial") {
				connectionSerial = Long.valueOf(unpacker.readLong());
			} else if(fieldName == "msgSerial") {
				msgSerial = Long.valueOf(unpacker.readLong());
			} else if(fieldName == "timestamp") {
				timestamp = unpacker.readLong();
			} else if(fieldName == "messages") {
				messages = MessageSerializer.readMsgpack(unpacker);
			} else if(fieldName == "presence") {
				presence = PresenceSerializer.readMsgpack(unpacker);
			} else {
				System.out.println("Unexpected field: " + fieldName);
				unpacker.skip();
			}
		}
		unpacker.readMapEnd(true);
	}

	public Action action;
	public int flags;
	public int count;
	public ErrorInfo error;
	public String id;
	public String channel;
	public String channelSerial;
	public String memberId;
	public String connectionId;
	public Long connectionSerial;
	public Long msgSerial;
	public long timestamp;
	public Message[] messages;
	public PresenceMessage[] presence;

	private static final MessagePack msgpack = new MessagePack();;
}
