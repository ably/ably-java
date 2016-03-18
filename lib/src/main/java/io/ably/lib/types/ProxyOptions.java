package io.ably.lib.types;

import io.ably.lib.http.HttpAuth;

public class ProxyOptions {
	public String host;
	public int port;
	public String username;
	public String password;
	public String[] nonProxyHosts;
	public HttpAuth.Type prefAuthType = HttpAuth.Type.BASIC;
}
