package io.ably.lib.network;

public class NotConnectedException extends RuntimeException {
    public NotConnectedException(Throwable cause) {
        super(cause);
    }
}
