package io.ably.lib.network;

public interface HttpEngine {
    HttpCall call(HttpRequest request);
    boolean isUsingProxy();
}
