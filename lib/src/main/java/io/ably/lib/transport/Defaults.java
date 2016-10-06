package io.ably.lib.transport;

import io.ably.lib.types.ClientOptions;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Defaults {
	public static final int PROTOCOL_VERSION        = 1;
	static final List<String> HOST_FALLBACKS        = Arrays.asList("A.ably-realtime.com", "B.ably-realtime.com", "C.ably-realtime.com", "D.ably-realtime.com", "E.ably-realtime.com");
	public static final String HOST_REST            = "rest.ably.io";
	public static final String HOST_REALTIME        = "realtime.ably.io";
	public static final int PORT                    = 80;
	public static final int TLS_PORT                = 443;
	public static final int TIMEOUT_CONNECT         = 15000;
	public static final int TIMEOUT_DISCONNECT      = 30000;
	public static final int TIMEOUT_SUSPEND         = 120000;
	/* TO313 */
	public static final int TIMEOUT_HTTP_OPEN = 4000;
	/* TO314 */
	public static final int TIMEOUT_HTTP_REQUEST = 15000;
	/* DF1b */
	public static final long realtimeRequestTimeout = 10000L;
	/* CD2h (but no default in the spec) */
	public static final long maxIdleInterval = 20000L;


	public static final String[] TRANSPORTS         = new String[]{"web_socket"};
	public static final String TRANSPORT = "io.ably.lib.transport.WebSocketTransport$Factory";
	public static final int HTTP_MAX_RETRY_COUNT    = 3;

	static {
		Collections.shuffle(HOST_FALLBACKS);
	}

	public static int getPort(ClientOptions options) {
		return options.tls
			? ((options.tlsPort != 0) ? options.tlsPort : Defaults.TLS_PORT)
			: ((options.port != 0) ? options.port : Defaults.PORT);
	}
}
