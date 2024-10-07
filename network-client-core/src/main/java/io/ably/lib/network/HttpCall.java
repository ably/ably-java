package io.ably.lib.network;

/**
 * Cancelable Http request call
 * <p/>
 * Implementation should be thread-safe
 */
public interface HttpCall {
    /**
     * Synchronously execute Http request and return response from te server
     */
    HttpResponse execute();

    /**
     * Cancel pending Http request
     */
    void cancel();
}
