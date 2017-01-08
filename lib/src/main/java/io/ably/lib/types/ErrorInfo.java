package io.ably.lib.types;

import org.msgpack.core.MessageFormat;
import org.msgpack.core.MessageUnpacker;

import io.ably.lib.util.Log;

import java.io.IOException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;

/**
 * An exception type encapsulating error information containing
 * an Ably-specific error code and generic status code.
 */
public class ErrorInfo {

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

	ErrorInfo readMsgpack(MessageUnpacker unpacker) throws IOException {
		int fieldCount = unpacker.unpackMapHeader();
		for(int i = 0; i < fieldCount; i++) {
			String fieldName = unpacker.unpackString().intern();
			MessageFormat fieldFormat = unpacker.getNextFormat();
			if(fieldFormat.equals(MessageFormat.NIL)) { unpacker.unpackNil(); continue; }

			if(fieldName == "message") {
				message = unpacker.unpackString();
			} else if(fieldName == "code") {
				code = unpacker.unpackInt();
			} else if(fieldName == "statusCode") {
				statusCode = unpacker.unpackInt();
			} else {
				Log.v(TAG, "Unexpected field: " + fieldName);
				unpacker.skipValue();
			}
		}
		return this;
	}

	static ErrorInfo fromMsgpack(MessageUnpacker unpacker) throws IOException {
		return (new ErrorInfo()).readMsgpack(unpacker);
	}

	public static ErrorInfo fromThrowable(Throwable throwable) {
		ErrorInfo errorInfo;
		if(throwable instanceof UnknownHostException
				|| throwable instanceof NoRouteToHostException) {
			errorInfo = new ErrorInfo(throwable.getLocalizedMessage(), 404, 40400);
		}
		else if(throwable instanceof IOException) {
			errorInfo = new ErrorInfo(throwable.getLocalizedMessage(), 500, 50000);
		}
		else {
			errorInfo = new ErrorInfo("Unexpected exception: " + throwable.getLocalizedMessage(), 50000, 500);
		}

		return errorInfo;
	}

	public static ErrorInfo fromResponseStatus(String statusLine, int statusCode) {
		return new ErrorInfo(statusLine, statusCode, statusCode * 100);
	}

	private static final String TAG = ErrorInfo.class.getName();
}