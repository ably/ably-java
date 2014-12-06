package io.ably.types;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;
import org.msgpack.MessagePack;

/**
 * ProtocolSerializer: internal
 * A utility class that performs conversions from protocol-encoded
 * frames to and from ProtocolMessages.
 */
public class ProtocolSerializer {

	public static ProtocolMessage readMsgpack(byte[] packed) throws AblyException {
		try {
			return msgpack.read(packed, ProtocolMessage.class);
		} catch (IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	public static ProtocolMessage readJSON(String jsonText) throws AblyException {
		try {
			return ProtocolMessage.fromJSON(new JSONObject(jsonText));
		} catch (JSONException e) {
			throw AblyException.fromThrowable(e);
		}
	}

	public static ProtocolMessage readJSON(byte[] jsonBytes) throws AblyException {
		return readJSON(new String(jsonBytes));
	}

	public static String toJSON(ProtocolMessage message) throws AblyException {
		return ProtocolMessage.asJSON(message).toString();
	}

	public static byte[] toMsgpack(ProtocolMessage message) throws AblyException {
		try {
			return msgpack.write(message);
		} catch (IOException ioe) {
			throw AblyException.fromIOException(ioe);
		}
	}

	private static final MessagePack msgpack = new MessagePack();
}
