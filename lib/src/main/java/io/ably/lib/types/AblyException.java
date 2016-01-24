package io.ably.lib.types;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;

/**
 * An exception type encapsulating an Ably error code
 */
public class AblyException extends Exception {
	private static final long serialVersionUID = -3804072091596832634L;
	public ErrorInfo errorInfo;


	/**
	 * Constructor for use where there is an ErrorInfo available
	 */
	AblyException(Throwable throwable, ErrorInfo reason) {
		super(throwable);
		this.errorInfo = reason;
	}

	public static AblyException fromErrorInfo(ErrorInfo errorInfo) {
		return new AblyException(
				new Exception(errorInfo.message),
				errorInfo);
	}

	/**
	 * Get an exception from a throwable occurring locally
	 * @param t
	 * @return
	 */
	public static AblyException fromThrowable(Throwable t) {
		if(t instanceof AblyException)
			return (AblyException)t;
		if(t instanceof ConnectException || t instanceof UnknownHostException || t instanceof NoRouteToHostException)
			return new HostFailedException(t, ErrorInfo.fromThrowable(t));

		return new AblyException(t, ErrorInfo.fromThrowable(t));
	}



	public static class HostFailedException extends AblyException {
		private static final long serialVersionUID = 1L;

		HostFailedException(Throwable throwable, ErrorInfo reason) {
			super(throwable, reason);
		}
	}
}