package io.ably.lib.types;

/**
 * Special AblyException for message decoding problems
 */
public class MessageDecodeException extends AblyException {
	private static final long serialVersionUID = 1L;

	private MessageDecodeException(Throwable e, ErrorInfo errorInfo) {
		super(e, errorInfo);
	}

	public static MessageDecodeException fromDescription(String description) {
		return new MessageDecodeException(
			new Exception(description),
			new ErrorInfo(description, 91200));
	}

	public static MessageDecodeException fromThrowableAndErrorInfo(Throwable e, ErrorInfo errorInfo) {
		return new MessageDecodeException(e, errorInfo);
	}
}
