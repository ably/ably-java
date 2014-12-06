package io.ably.types;

import java.io.IOException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;

import org.apache.http.StatusLine;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * An exception type encapsulating an Ably error code
 */
public class AblyException extends Exception {
	private static final long serialVersionUID = -3804072091596832634L;

	/**
	 * Constructor for use where the exception wasn't
	 * anticipated at a lower level
	 */
	public AblyException(Throwable cause) {
		super(cause);
		errorInfo = new ErrorInfo("Unexpected exception: " + cause.getLocalizedMessage(), 50000, 500);
	}

	/**
	 * Constructor for use where there is an ErrorInfo available
	 */
	public AblyException(ErrorInfo reason) {
		super(reason.message);
		this.errorInfo = reason;
	}

	/**
	 * Constructor for use where there is a specific reason code available
	 */
	public AblyException(String reason, int statusCode, int code) {
		super(reason);
		this.errorInfo = new ErrorInfo(reason, statusCode, code);
	}

	/**
	 * Get an exception from a response body with error details
	 * @param jsonText
	 * @return
	 * @throws AblyException
	 */
	public static AblyException fromJSON(String jsonText) throws AblyException {
		try {
			JSONObject json = (new JSONObject(jsonText)).optJSONObject("error");
			return new AblyException(new ErrorInfo(json));
		} catch (JSONException e) {
			throw new AblyException("Unexpected exception decoding server response: " + e, 500, 50000);
		}
	}

	/**
	 * Get an exception from a response body with error details as byte[]
	 * @param jsonBytes
	 * @return
	 */
	public static AblyException fromJSON(byte[] jsonBytes) {
		try {
			String jsonText = new String(jsonBytes);
			return AblyException.fromJSON(jsonText);
		} catch (AblyException e) {
			return e;
		}
	}

	/**
	 * Get an exception from an IOException occurring locally
	 * @param ioe
	 * @return
	 */
	public static AblyException fromIOException(IOException ioe) {
		if(ioe instanceof UnknownHostException
				|| ioe instanceof NoRouteToHostException)
			return new AblyException(ioe.getLocalizedMessage(), 404, 40400);
		return new AblyException(ioe.getLocalizedMessage(), 500, 50000);
	}

	/**
	 * Get an exception from an error response that does not contain
	 * a response body with error details
	 * @param statusLine
	 * @param statusCode
	 * @return
	 */
	public static AblyException fromResponseStatus(StatusLine statusLine, int statusCode) {
		return new AblyException(statusLine.getReasonPhrase(), statusCode, statusCode * 100);
	}

	/**
	 * Get an exception from a throwable occurring locally
	 * @param t
	 * @return
	 */
	public static AblyException fromThrowable(Throwable t) {
		if(t instanceof AblyException)
			return (AblyException)t;
		if(t instanceof IOException)
			return fromIOException((IOException)t);
		return new AblyException(t);
	}

	public ErrorInfo errorInfo;
}