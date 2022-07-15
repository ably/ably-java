package io.ably.core.types;

import io.ably.core.http.HttpAuth;

public class ProxyOptions {
    public String host;
    public int port;
    public String username;
    public String password;
    public String[] nonProxyHosts;
    public HttpAuth.Type prefAuthType = HttpAuth.Type.BASIC;
}
