package io.ably.types;

import java.io.IOException;

import org.json.JSONObject;
import org.msgpack.MessagePackable;
import org.msgpack.packer.Packer;
import org.msgpack.type.ValueType;
import org.msgpack.unpacker.Unpacker;

/**
 * An exception type encapsulating error information containing
 * an Ably-specific error code and generic status code.
 */
public class ErrorInfo implements MessagePackable {

	/**
	 * Ably error code (see ably-common/protocol/errors.json)
	 */
	public int code;

	/**
	 * HTTP Status Code corresponding to this error, where applicable
	 */
	public int statusCode;

	/**
	 * Additional message information, where available
	 */
	public String message;

	/**
	 * Full JSON of error, when obtained from JSON error response
	 */
	private JSONObject json;

	/**
	 * Public no-argument constructor for msgpack
	 */
	public ErrorInfo() {}

	/**
	 * Construct an ErrorInfo from message and code
	 * @param message
	 * @param code
	 */
	public ErrorInfo(String message, int code) {
		this.code = code;
		this.message = message;
	}

	/**
	 * Generic constructor
	 * @param message
	 * @param statusCode
	 * @param code
	 */
	public ErrorInfo(String message, int statusCode, int code) {
		this(message, code);
		this.statusCode = statusCode;
	}

	/**
	 * Construct an ErrorInfo from a JSON-encoded error response body
	 * or ProtocolMessage
	 * @param json
	 */
	public ErrorInfo(JSONObject json) {
		this((json.has("message") ? json.optString("message") : null), json.optInt("statusCode"), json.optInt("code"));
		this.json = json;
	}

	public String toString() {
		StringBuilder result = new StringBuilder("[ErrorInfo");
		if(message != null)
			result.append(" message = ").append(message);
		if(code > 0)
			result.append(" code = ").append(code);
		if(statusCode > 0)
			result.append(" statusCode = ").append(statusCode);
		result.append(']');
		return result.toString();
	}

	public JSONObject getRawJSON() {
		return json;
	}

	@Override
	public void writeTo(Packer packer) throws IOException {
		packer.writeMapBegin(3);
		packer.write("message");
		packer.write(message);
		packer.write("code");
		packer.write(code);
		packer.write("statusCode");
		packer.write(statusCode);
		packer.writeMapEnd(true);
	}

	@Override
	public void readFrom(Unpacker unpacker) throws IOException {
		int fieldCount = unpacker.readMapBegin();
		for(int i = 0; i < fieldCount; i++) {
			String fieldName = unpacker.readString().intern();
			ValueType fieldType = unpacker.getNextType();
			if(fieldType.equals(ValueType.NIL)) { unpacker.readNil(); continue; }

			if(fieldName == "message") {
				message = unpacker.readString();
			} else if(fieldName == "code") {
				code = unpacker.readInt();
			} else if(fieldName == "statusCode") {
				statusCode = unpacker.readInt();
			} else {
				System.out.println("Unexpected field: " + fieldName);
				unpacker.skip();
			}
		}
		unpacker.readMapEnd(true);
	}
}