package io.ably.types;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonGenerator;

/**
 * A class representing an individual presence update to be sent or received
 * via the Ably Realtime service.
 */
@JsonInclude(Include.NON_DEFAULT)
@JsonIgnoreProperties(ignoreUnknown=true)
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
	}

	public Action action;

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

	protected void serializeFields(JsonGenerator generator) throws IOException {
		generator.writeNumberField("action", action.getValue());
		super.serializeFields(generator);
	}

	/* force initialisation of PresenceSerializer */
	static { new PresenceSerializer(); }
}
