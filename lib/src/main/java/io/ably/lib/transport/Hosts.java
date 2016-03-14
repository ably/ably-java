package io.ably.lib.transport;

import java.util.List;

/**
 * Created by gokhanbarisaker on 2/1/16.
 */
public class Hosts {
	private static final List<String> FALLBACKS = Defaults.HOST_FALLBACKS;
	private static final String REST_PROD_HOST = Defaults.HOST_REST;
	private static final String REALTIME_PROD_HOST = Defaults.HOST_REALTIME;

	private Hosts() { /* Restrict new instance creation */ }

	/**
	 * Provides fallback host alternative for given host
	 *
	 * @param host
	 * @return Successor host that can be used as a fallback
	 */
	public static String getFallback(String host) {
		int size = FALLBACKS.size();
		int indexCurrent = FALLBACKS.indexOf(host);
		int indexNext = indexCurrent + 1;

		if (indexNext >= size) {
			return null;
		}

		return FALLBACKS.get(indexNext);
	}

	/**
	 * Checks if given host is a fallback, or not.
	 *
	 * @param host
	 * @return true, if the given host is a fallback. Otherwise, false.
	 */
	public static boolean isFallback(String host) {
		return FALLBACKS.indexOf(host) >= 0;
	}

	/**
	 * <p>
	 * Determines whether given rest host is qualified for a retry against a fallback host, or not.
	 * </p>
	 * <p>
	 * Spec: RSC15b
	 * </p>
	 *
	 * @param host
	 * @return true, if the given host is qualified for a retry against a fallback host. Otherwise, false.
	 */
	public static boolean isRestFallbackSupported(String host) {
		return host.equals(REST_PROD_HOST);
	}

	/**
	 * <p>
	 * Determines whether given realtime host is qualified for a retry against a fallback host, or not.
	 * </p>
	 * <p>
	 * Spec: RTN17b
	 * </p>
	 *
	 * @param host
	 * @return true, if given host is qualified for a retry against a fallback host. Otherwise, false.
	 */
	public static boolean isRealtimeFallbackSupported(String host) {
		return host.equals(REALTIME_PROD_HOST);
	}
}
