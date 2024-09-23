package io.ably.lib.network;

public interface HttpCall {
    HttpResponse execute();
    void cancel();
}
