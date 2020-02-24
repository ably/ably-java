package io.ably.lib.types;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
		/* Channel attach state flags */
		has_presence(0),
		has_backlog(1),
		resumed(2),

		/* Channel mode flags */
		presence(16),
		publish(17),
		subscribe(18),
		presence_subscribe(19);

		private int mask;
		Flag(int offset) { this.mask = 1 << offset; }
		public int getMask() { return this.mask; }
		public static Flag[] getModes() { return new Flag[] { presence, publish, subscribe, presence_subscribe }; };
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
	public ChannelParams params;

	public boolean hasFlag(Flag flag) {
		return (this.flags & flag.getMask()) == flag.getMask();
	}

	public void setFlag(Flag flag) {
		this.flags |= flag.getMask();
	}

	public void encodeModesToFlags(ChannelModes modes) {
		for (ChannelMode mode : modes) {
			this.setFlag(Flag.valueOf(mode.name()));
		}
	}

	public ChannelModes decodeModesFromFlags() {
		ChannelModes result = new ChannelModes();
		for (Flag mode : Flag.getModes()) {
			if (this.hasFlag(mode)) {
				result.add(ChannelMode.valueOf(mode.name()));
			}
		}
		return result;
	}

	void writeMsgpack(MessagePacker packer) throws IOException {
		int fieldCount = 1; //action
		if(channel != null) ++fieldCount;
		if(msgSerial != null) ++fieldCount;
		if(messages != null) ++fieldCount;
		if(presence != null) ++fieldCount;
		if(auth != null) ++fieldCount;
		if(flags != 0) ++fieldCount;
		if(params != null) ++fieldCount;
		if(channelSerial != null) ++fieldCount;
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
		if(flags != 0) {
			packer.packString("flags");
			packer.packInt(flags);
		}
		if(params != null) {
			packer.packString("params");
			params.writeMsgpack(packer);
		}
		if(channelSerial != null) {
			packer.packString("channelSerial");
			packer.packString(channelSerial);
		}
	}

	ProtocolMessage readMsgpack(MessageUnpacker unpacker) throws IOException {
		int fieldCount = unpacker.unpackMapHeader();
		for(int i = 0; i < fieldCount; i++) {
			String fieldName = unpacker.unpackString().intern();
			MessageFormat fieldFormat = unpacker.getNextFormat();
			if(fieldFormat.equals(MessageFormat.NIL)) { unpacker.unpackNil(); continue; }

			switch(fieldName) {
				case "action":
					action = Action.findByValue(unpacker.unpackInt());
					break;
				case "flags":
					flags = unpacker.unpackInt();
					break;
				case "count":
					count = unpacker.unpackInt();
					break;
				case "error":
					error = ErrorInfo.fromMsgpack(unpacker);
					break;
				case "id":
					id = unpacker.unpackString();
					break;
				case "channel":
					channel = unpacker.unpackString();
					break;
				case "channelSerial":
					channelSerial = unpacker.unpackString();
					break;
				case "connectionId":
					connectionId = unpacker.unpackString();
					break;
				case "connectionSerial":
					connectionSerial = Long.valueOf(unpacker.unpackLong());
					break;
				case "msgSerial":
					msgSerial = Long.valueOf(unpacker.unpackLong());
					break;
				case "timestamp":
					timestamp = unpacker.unpackLong();
					break;
				case "messages":
					messages = MessageSerializer.readMsgpackArray(unpacker);
					break;
				case "presence":
					presence = PresenceSerializer.readMsgpackArray(unpacker);
					break;
				case "connectionDetails":
					connectionDetails = ConnectionDetails.fromMsgpack(unpacker);
					break;
				case "auth":
					auth = AuthDetails.fromMsgpack(unpacker);
					break;
				case "connectionKey":
					/* deprecated; ignore */
					unpacker.unpackString();
					break;
				case "params":
					params = ChannelParams.fromMsgpack(unpacker);
					break;
				default:
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

				switch(fieldName) {
					case "accessToken":
						accessToken = unpacker.unpackString();
						break;
					default:
						Log.v(TAG, "Unexpected field: " + fieldName);
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
