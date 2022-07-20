package io.ably.lib.util;

import java.util.Arrays;
import java.util.HashMap;

import io.ably.lib.rest.Auth;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ProxyOptions;

public class ObjectCopyUtil {

    public static ClientOptions copy(ClientOptions options) {
        ClientOptions copyOptions = new ClientOptions();
        copyOptions.clientId = options.clientId;
        copyOptions.logLevel = options.logLevel;
        copyOptions.logHandler = options.logHandler;
        copyOptions.tls = options.tls;
        copyOptions.restHost = options.restHost;
        copyOptions.realtimeHost = options.realtimeHost;
        copyOptions.port = options.port;
        copyOptions.tlsPort = options.tlsPort;
        copyOptions.autoConnect = options.autoConnect;
        copyOptions.useBinaryProtocol = options.useBinaryProtocol;
        copyOptions.queueMessages = options.queueMessages;
        copyOptions.echoMessages = options.echoMessages;
        copyOptions.recover = options.recover;
        copyOptions.idempotentRestPublishing = options.idempotentRestPublishing;
        copyOptions.httpOpenTimeout = options.httpOpenTimeout;
        copyOptions.httpRequestTimeout = options.httpRequestTimeout;
        copyOptions.httpMaxRetryCount = options.httpMaxRetryCount;
        copyOptions.realtimeRequestTimeout = options.realtimeRequestTimeout;
        copyOptions.fallbackHostsUseDefault = options.fallbackHostsUseDefault;
        copyOptions.fallbackRetryTimeout = options.fallbackRetryTimeout;
        copyOptions.defaultTokenParams = options.defaultTokenParams.copy();
        copyOptions.channelRetryTimeout = options.channelRetryTimeout;
        copyOptions.asyncHttpThreadpoolSize = options.asyncHttpThreadpoolSize;
        copyOptions.pushFullWait = options.pushFullWait;
        copyOptions.localStorage = options.localStorage;
        copyOptions.addRequestIds = options.addRequestIds;
        copyOptions.environment = options.environment;

        //params from AuthOptions
        copyOptions.authCallback = options.authCallback;
        copyOptions.authUrl = options.authUrl;
        copyOptions.authMethod = options.authMethod;
        copyOptions.key = options.key;
        copyOptions.token = options.token;
        copyOptions.queryTime = options.queryTime;
        copyOptions.useTokenAuth = options.useTokenAuth;

        if (options.headers != null) {
            copyOptions.headers = new HashMap<>(options.headers);
        }

        if (options.agents != null) {
            copyOptions.agents = new HashMap<>(options.agents);
        }

        if (options.authParams != null) {
            copyOptions.authParams = Arrays.copyOf(options.authParams, options.authParams.length);
        }

        if (options.authHeaders != null) {
            copyOptions.authHeaders = Arrays.copyOf(options.authHeaders, options.authHeaders.length);
        }

        if (options.transportParams != null) {
            copyOptions.transportParams = Arrays.copyOf(options.transportParams, options.transportParams.length);
        }

        if (options.fallbackHosts != null) {
            copyOptions.fallbackHosts = Arrays.copyOf(options.fallbackHosts, options.fallbackHosts.length);
        }

        if (options.proxy != null) {
            ProxyOptions po = new ProxyOptions();
            po.host = options.proxy.host;
            po.port = options.proxy.port;
            po.username = options.proxy.username;
            po.password = options.proxy.password;
            po.nonProxyHosts = options.proxy.nonProxyHosts;
            po.prefAuthType = options.proxy.prefAuthType; //maybe deep copy this
            copyOptions.proxy = po;
        }

        if (options.tokenDetails != null) {
            Auth.TokenDetails tokenDetails = new Auth.TokenDetails();
            tokenDetails.token = options.tokenDetails.token;
            tokenDetails.expires = options.tokenDetails.expires;
            tokenDetails.issued = options.tokenDetails.issued;
            tokenDetails.capability = options.tokenDetails.capability;
            tokenDetails.clientId = options.tokenDetails.clientId;
            copyOptions.tokenDetails = tokenDetails;
        }

        return copyOptions;
    }
}
