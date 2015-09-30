package io.ably.lib.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A message sent and received over the Realtime protocol.
 * A ProtocolMessage always relates to a single channel only, but
 * can contain multiple individual Messages or PresenceMessages.
 * ProtocolMessages are serially numbered on a connection.
 * See the Ably client library developer documentation for further
 * details on the members of a ProtocolMessage.
 */
@JsonInclude(Include.NON_DEFAULT)
@JsonIgnoreProperties(ignoreUnknown=true)
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
	}

	public enum Flag {
		HAS_PRESENCE,
		HAS_BACKLOG;

		public int getValue() { return ordinal(); }
		public static Flag findByValue(int value) { return values()[value]; }
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
}
