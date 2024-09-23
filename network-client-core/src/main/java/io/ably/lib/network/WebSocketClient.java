package io.ably.lib.network;

public interface WebSocketClient {

    void connect();

    /**
     * Sends the closing handshake. May be sent in response to any other handshake.
     */
    void close();

    /**
     * Sends the closing handshake. May be sent in response to any other handshake.
     *
     * @param code    the closing code
     * @param reason the closing message
     */
    void close(int code, String reason);

    /**
     * This will close the connection immediately without a proper close handshake. The code and the
     * message therefore won't be transferred over the wire also they will be forwarded to `onClose`.
     *
     * @param code    the closing code
     * @param reason the closing message
     **/
    void cancel(int code, String reason);

    void send(byte[] message);

    void send(String message);

}
