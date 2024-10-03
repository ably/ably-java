package io.ably.lib.network;

public interface WebSocketEngine {
    WebSocketClient create(String url, WebSocketListener listener);
    boolean isPingListenerSupported();
}
