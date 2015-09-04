package io.ably.types;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonGenerator;

/**
 * A class representing an individual message to be sent or received
 * via the Ably Realtime service.
 */
@JsonInclude(Include.NON_DEFAULT)
@JsonIgnoreProperties(ignoreUnknown=true)
public class Message extends BaseMessage {

	/**
	 * The event name, if available
	 */
	public String name;

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

	protected void serializeFields(JsonGenerator generator) throws IOException {
		if(name != null) generator.writeStringField("name", name);
		super.serializeFields(generator);
	}

	/* force initialisation of MessageSerializer */
	static { new MessageSerializer(); }
}
