package io.ably.transport;

import io.ably.types.ClientOptions;

public class Defaults {
	public static final int protocolVersion     = 1;
	public static final String[] FALLBACK_HOSTS = new String[] {"A.ably-realtime.com", "B.ably-realtime.com", "C.ably-realtime.com", "D.ably-realtime.com", "E.ably-realtime.com"};
	public static final String REST_HOST        = "rest.ably.io";
	public static final String REALTIME_HOST    = "realtime.ably.io";
	public static final int PORT                = 80;
	public static final int TLS_PORT            = 443;
	public static final int connectTimeout      = 15000;
	public static final int disconnectTimeout   = 30000;
	public static final int suspendedTimeout    = 120000;
	public static final int cometRecvTimeout    = 90000;
	public static final int cometSendTimeout    = 10000;
	public static final String[] transports     = new String[]{"web_socket"};
	public static final String transport        = "io.ably.transport.WebSocketTransport$Factory";

	public static String getHost(ClientOptions options) {
		String host;
		host = options.restHost;
		if(host == null)
			host = Defaults.REST_HOST;
		return host;
	}

	public static String getHost(ClientOptions options, String host, boolean ws) {
		if(host == null) {
			host = options.restHost;
			if(host == null)
				host = Defaults.REST_HOST;
		}

		if(ws) {
			if(host.equals(options.restHost) && options.realtimeHost != null)
				host = options.realtimeHost;
			else if(host.equals(Defaults.REST_HOST))
				host = Defaults.REALTIME_HOST;
		}
		return host;
	}

	public static int getPort(ClientOptions options) {
		return options.tls
			? ((options.tlsPort != 0) ? options.tlsPort : Defaults.TLS_PORT)
			: ((options.port != 0) ? options.port : Defaults.PORT);
	}

	public static String[] getFallbackHosts(ClientOptions options) {
		return (options.restHost == null) ? Defaults.FALLBACK_HOSTS : null;
	}
}
