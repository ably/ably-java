package io.ably.lib.util;

import io.ably.lib.types.ClientOptions;

public class ObjectCopyUtil {

    public static ClientOptions copy(ClientOptions options) {
        ClientOptions copyOptions = new ClientOptions();
        copyOptions.clientId = options.clientId;
        copyOptions.logLevel = options.logLevel;
        copyOptions.logHandler = options.logHandler;
        copyOptions.tls = options.tls;
        copyOptions.headers = options.headers; //maybe deep copy this
        copyOptions.restHost = options.restHost;
        copyOptions.realtimeHost = options.realtimeHost;
        copyOptions.port = options.port;
        copyOptions.tlsPort = options.tlsPort;
        copyOptions.autoConnect = options.autoConnect;
        copyOptions.useBinaryProtocol = options.useBinaryProtocol;
        copyOptions.queueMessages = options.queueMessages;
        copyOptions.echoMessages = options.echoMessages;
        copyOptions.recover = options.recover;
        copyOptions.proxy = options.proxy; //maybe deep copy this
        copyOptions.idempotentRestPublishing = options.idempotentRestPublishing;
        copyOptions.httpOpenTimeout = options.httpOpenTimeout;
        copyOptions.httpRequestTimeout = options.httpRequestTimeout;
        copyOptions.httpMaxRetryCount = options.httpMaxRetryCount;
        copyOptions.realtimeRequestTimeout = options.realtimeRequestTimeout;
        copyOptions.fallbackHosts = options.fallbackHosts; //maybe deep copy this
        copyOptions.fallbackHostsUseDefault = options.fallbackHostsUseDefault;
        copyOptions.fallbackRetryTimeout = options.fallbackRetryTimeout;
        copyOptions.defaultTokenParams = options.defaultTokenParams; //maybe deep copy this
        copyOptions.channelRetryTimeout = options.channelRetryTimeout;
        copyOptions.transportParams = options.transportParams; //maybe deep copy this
        copyOptions.asyncHttpThreadpoolSize = options.asyncHttpThreadpoolSize;
        copyOptions.pushFullWait = options.pushFullWait;
        copyOptions.localStorage = options.localStorage;
        copyOptions.addRequestIds = options.addRequestIds;
        copyOptions.agents = options.agents; //maybe deep copy this

        return copyOptions;
    }
}
