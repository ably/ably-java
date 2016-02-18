package io.ably.lib.transport;

import java.util.List;

/**
 * Created by gokhanbarisaker on 2/1/16.
 */
public class Hosts {
    private static final List<String> FALLBACKS = Defaults.HOST_FALLBACKS;
    private static final String PATTERN_REST_PROD = "^rest.ably.io$";
    private static final String PATTERN_REALTIME_PROD = "^realtime.ably.io$";

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
        return FALLBACKS.get((indexCurrent + 1) % size);
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
        return host.matches(PATTERN_REST_PROD);
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
        return host.matches(PATTERN_REALTIME_PROD);
    }
}
