package io.ably.lib.network;

public class OkHttpEngine implements HttpEngine {

    @Override
    public HttpCall call(HttpRequest request) {
        return null;
    }

    @Override
    public boolean isUsingProxy() {
        return false;
    }
}
