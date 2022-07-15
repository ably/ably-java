package io.ably.core.debug;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;

import io.ably.core.http.HttpCore;
import io.ably.core.transport.ITransport;
import io.ably.core.types.AblyException;
import io.ably.core.types.ClientOptions;
import io.ably.core.types.ProtocolMessage;

public class DebugOptions extends ClientOptions {
    public interface RawProtocolListener {
        void onRawConnectRequested(String url);
        void onRawConnect(String url);
        void onRawMessageSend(ProtocolMessage message);
        void onRawMessageRecv(ProtocolMessage message);
    }

    public interface RawHttpListener {
        HttpCore.Response onRawHttpRequest(String id, HttpURLConnection conn, String method, String authHeader, Map<String, List<String>> requestHeaders, HttpCore.RequestBody requestBody);
        void onRawHttpResponse(String id, String method, HttpCore.Response response);
        void onRawHttpException(String id, String method, Throwable t);
    }

    public DebugOptions() { super(); pushFullWait = true; }

    public DebugOptions(String key) throws AblyException { super(key); pushFullWait = true; }

    public RawProtocolListener protocolListener;
    public RawHttpListener httpListener;
    public ITransport.Factory transportFactory;
}
