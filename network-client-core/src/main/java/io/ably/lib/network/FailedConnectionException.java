package io.ably.lib.network;

public class FailedConnectionException extends RuntimeException {
    public FailedConnectionException(Throwable cause) {
        super(cause);
    }
}
