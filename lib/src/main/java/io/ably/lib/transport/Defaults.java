package io.ably.lib.transport;

import io.ably.lib.BuildConfig;
import io.ably.lib.types.ClientOptions;

import java.text.DecimalFormat;

public class Defaults {
	/* versions */
	public static final float ABLY_VERSION_NUMBER   = 1.0f;
	public static final String ABLY_VERSION         = new DecimalFormat("0.0").format(ABLY_VERSION_NUMBER);
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
	public static int TIMEOUT_DISCONNECT            = 15000;
	public static int TIMEOUT_CHANNEL_RETRY			= 15000;

	/* TO313 */
	public static int TIMEOUT_HTTP_OPEN = 4000;
	/* TO314 */
	public static int TIMEOUT_HTTP_REQUEST = 15000;
	/* DF1b */
	public static long realtimeRequestTimeout = 10000L;
	/* TO3l10 */
	public static long fallbackRetryTimeout = 10*60*1000L;
	/* CD2h (but no default in the spec) */
	public static long maxIdleInterval = 20000L;
	/* DF1a */
	public static long connectionStateTtl = 60000L;

	public static final ITransport.Factory TRANSPORT = new WebSocketTransport.Factory();
	public static final int HTTP_MAX_RETRY_COUNT    = 3;
	public static final int HTTP_ASYNC_THREADPOOL_SIZE = 64;

	public static int getPort(ClientOptions options) {
		return options.tls
			? ((options.tlsPort != 0) ? options.tlsPort : Defaults.TLS_PORT)
			: ((options.port != 0) ? options.port : Defaults.PORT);
	}
}
