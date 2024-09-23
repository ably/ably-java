package io.ably.lib.network;

import java.nio.ByteBuffer;

public interface WebSocketListener {
    void onOpen();
    void onMessage(ByteBuffer blob);
    void onMessage(String string);
    void onWebsocketPing();
    void onClose(int code, String reason);
    void onError(Throwable throwable);
    void onOldJavaVersionDetected(Throwable throwable);
}
