package io.ably.transport;

import io.ably.types.Options;

public class Defaults {
	public static final int protocolVersion     = 1;
	public static final String[] FALLBACK_HOSTS = new String[] {"A.ably-realtime.com", "B.ably-realtime.com", "C.ably-realtime.com", "D.ably-realtime.com", "E.ably-realtime.com"};
	public static final String HOST             = "rest.ably.io";
	public static final String WS_HOST          = "realtime.ably.io";
	public static final int PORT                = 80;
	public static final int TLS_PORT            = 443;
	public static final int connectTimeout      = 15000;
	public static final int disconnectTimeout   = 10000;
	public static final int suspendedTimeout    = 60000;
	public static final int cometRecvTimeout    = 90000;
	public static final int cometSendTimeout    = 10000;
	public static final String[] transports     = new String[]{"web_socket", "comet"};
	public static final String transport        = "io.ably.transport.WebSocketTransport$Factory";

	public static String getHost(Options options) {
		String host;
		host = options.host;
		if(host == null)
			host = Defaults.HOST;
		return host;
	}

	public static String getHost(Options options, String host, boolean ws) {
		if(host == null) {
			host = options.host;
			if(host == null)
				host = Defaults.HOST;
		}

		if(ws) {
			if(host == options.host && options.wsHost != null)
				host = options.wsHost;
			else if(host == Defaults.HOST)
				host = Defaults.WS_HOST;
		}
		return host;
	}

	public static int getPort(Options options) {
		return options.tls
			? ((options.tlsPort != 0) ? options.tlsPort : Defaults.TLS_PORT)
			: ((options.port != 0) ? options.port : Defaults.PORT);
	}

	public static String[] getFallbackHosts(Options options) {
		return (options.host == null) ? Defaults.FALLBACK_HOSTS : null;
	}
}
