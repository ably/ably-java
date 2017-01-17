package io.ably.lib.types;

/**
 * Special AblyException for message decoding problems
 */
public class MessageDecodeException extends AblyException {
	private static final long serialVersionUID = 1L;

	private MessageDecodeException(Throwable e, String description) {
        super(e, new ErrorInfo(description, 91200));
    }

    public static MessageDecodeException fromDescription(String description) {
        return new MessageDecodeException(
                new Exception(description),
                description);
    }
}
