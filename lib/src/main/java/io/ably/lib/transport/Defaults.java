package io.ably.lib.transport;

import io.ably.lib.BuildConfig;
import io.ably.lib.types.ClientOptions;

public class Defaults {
	/* versions */
	public static final String ABLY_VERSION         = "0.9";
	public static final String ABLY_LIB_VERSION     = String.format("%s-%s", BuildConfig.LIBRARY_NAME, BuildConfig.VERSION);

	/* params */
	public static final String ABLY_VERSION_PARAM   = "v";
	public static final String ABLY_LIB_PARAM       = "lib";

	/* Headers */
	public static final String ABLY_VERSION_HEADER  = "X-Ably-Version";
	public static final String ABLY_LIB_HEADER      = "X-Ably-Lib";

	/* Hosts */
	public static final String[] HOST_FALLBACKS     = { "A.ably-realtime.com", "B.ably-realtime.com", "C.ably-realtime.com", "D.ably-realtime.com", "E.ably-realtime.com" };
	public static final String HOST_REST            = "rest.ably.io";
	public static final String HOST_REALTIME        = "realtime.ably.io";
	public static final int PORT                    = 80;
	public static final int TLS_PORT                = 443;

	/* Timeouts */
	public static int TIMEOUT_CONNECT               = 15000;
	public static int TIMEOUT_DISCONNECT            = 30000;
	public static int TIMEOUT_SUSPEND               = 120000;
	public static int TIMEOUT_CHANNEL_RETRY			= 15000;

	/* TO313 */
	public static int TIMEOUT_HTTP_OPEN = 4000;
	/* TO314 */
	public static int TIMEOUT_HTTP_REQUEST = 15000;
	/* DF1b */
	public static long realtimeRequestTimeout = 10000L;
	/* CD2h (but no default in the spec) */
	public static long maxIdleInterval = 20000L;

	public static final String[] TRANSPORTS         = new String[]{"web_socket"};
	public static String TRANSPORT = "io.ably.lib.transport.WebSocketTransport$Factory";
	public static final int HTTP_MAX_RETRY_COUNT    = 3;

	public static int getPort(ClientOptions options) {
		return options.tls
			? ((options.tlsPort != 0) ? options.tlsPort : Defaults.TLS_PORT)
			: ((options.port != 0) ? options.port : Defaults.PORT);
	}
}
