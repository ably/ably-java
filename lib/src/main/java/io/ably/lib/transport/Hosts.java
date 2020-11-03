package io.ably.lib.transport;

import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;

import java.util.Arrays;
import java.util.Collections;


/**
 * Object to encapsulate primary host name and shuffled fallback host names.
 */
public class Hosts {
    private String primaryHost;
    private String prefHost;
    private long prefHostExpiry;
    boolean primaryHostIsDefault;
    private final String defaultHost;
    private final String[] fallbackHosts;
    private final boolean fallbackHostsIsDefault;
    private final boolean fallbackHostsUseDefault;
    private final long fallbackRetryTimeout;


    /**
     * Create Hosts object
     *
     * @param primaryHost the primary hostname, null if not configured
     * @param defaultHost the default hostname that the primary hostname must
     *        match for fallback to occur
     * @param options ClientOptions to get environment and fallbackHosts from
     *
     * The fallback and environment processing here is used when the Hosts
     * object is used by a ConnectionManager (for a realtime connection) or by
     * an HttpCore for a rest connection. The case where the Hosts object is used
     * by an HttpCore that is being used by a ConnectionManager goes through this
     * code, but the results are ignored because ConnectionManager then calls
     * setHost() and fallback is not used.
     */
    public Hosts(String primaryHost, String defaultHost, ClientOptions options) throws AblyException {
        this.defaultHost = defaultHost;
        this.fallbackHostsUseDefault = options.fallbackHostsUseDefault;
        boolean hasCustomPrimaryHost = primaryHost != null && !primaryHost.equalsIgnoreCase(defaultHost);
        String[] tempFallbackHosts = options.fallbackHosts;
        if (options.fallbackHostsUseDefault) {
            if (options.fallbackHosts != null) {
                throw AblyException.fromErrorInfo(new ErrorInfo("fallbackHosts and fallbackHostsUseDefault cannot both be set", 40000, 400));
            }
            if (options.port != 0 || options.tlsPort != 0) {
                throw AblyException.fromErrorInfo(new ErrorInfo("fallbackHostsUseDefault cannot be set when port or tlsPort are set", 40000, 400));
            }
            tempFallbackHosts = Defaults.HOST_FALLBACKS;
        }

        boolean isProduction = options.environment == null || options.environment.isEmpty() || "production".equalsIgnoreCase(options.environment);

        if (!hasCustomPrimaryHost && tempFallbackHosts == null && options.port == 0 && options.tlsPort == 0) {
            tempFallbackHosts = isProduction ? Defaults.HOST_FALLBACKS : Defaults.getEnvironmentFallbackHosts(options.environment);
        }

        if (hasCustomPrimaryHost) {
            setPrimaryHost(primaryHost);
            if (options.environment != null) {
                /* TO3k2: It is never valid to provide both a restHost and environment value
                 * TO3k3: It is never valid to provide both a realtimeHost and environment value */
                throw AblyException.fromErrorInfo(new ErrorInfo("cannot set both restHost/realtimeHost and environment options", 40000, 400));
            }
        } else {
            setPrimaryHost(isProduction ? defaultHost : options.environment + "-" + defaultHost);
        }

        fallbackHostsIsDefault = Arrays.equals(Defaults.HOST_FALLBACKS, tempFallbackHosts);
        fallbackHosts = tempFallbackHosts == null ? new String[] {} : tempFallbackHosts.clone();
        /* RSC15a: shuffle the fallback hosts. */
        Collections.shuffle(Arrays.asList(fallbackHosts));
        fallbackRetryTimeout = options.fallbackRetryTimeout;
    }

    /**
     * set primary hostname
     */
    private void setPrimaryHost(String primaryHost) {
        this.primaryHost = primaryHost;
        primaryHostIsDefault = primaryHost.equalsIgnoreCase(defaultHost);
    }

    /**
     * set preferred hostname, which might not be the primary
     */
    public void setPreferredHost(String prefHost, boolean temporary) {
        if(prefHost.equals(this.prefHost)) {
            /* a successful request against a fallback; don't update the expiry time */
            return;
        }
        if(prefHost.equals(this.primaryHost)) {
            /* a successful request against the primary host; reset */
            clearPreferredHost();
        } else {
            this.prefHost = prefHost;
            this.prefHostExpiry = temporary ? System.currentTimeMillis() + fallbackRetryTimeout : 0;
        }
    }

    private void clearPreferredHost() {
        this.prefHost = null;
        this.prefHostExpiry = 0;
    }

    /**
     * Get primary host name
     */
    public String getPrimaryHost() {
        return primaryHost;
    }

    /**
     * Get preferred host name (taking into account any affinity to a fallback: see RSC15f)
     */
    public String getPreferredHost() {
        checkPreferredHostExpiry();
        return (prefHost == null) ? primaryHost : prefHost;
    }

    private String checkPreferredHostExpiry() {
        /* reset if expired */
        if(prefHostExpiry > 0 && prefHostExpiry <= System.currentTimeMillis()) {
            prefHostExpiry = 0;
            prefHost = null;
        }
        return prefHost;
    }

    /**
     * Get next fallback host if any
     *
     * @param lastHost
     * @return Successor host that can be used as a fallback.
     * null, if there is no successor fallback available.
     */
    public String getFallback(String lastHost) {
        if (fallbackHosts == null)
            return null;
        int idx;
        if (lastHost.equals(primaryHost)) {
            /* RSC15b, RTN17b: only use fallback if the hostname has not been overridden
             * or if ClientOptions#fallbackHostsUseDefault is true
             * or if ClientOptions#fallbackHosts was provided. */
            if (!primaryHostIsDefault && !fallbackHostsUseDefault && fallbackHostsIsDefault)
                return null;
            idx = 0;
        } else if(lastHost.equals(checkPreferredHostExpiry())) {
            /* RSC15f: there was a failure on an unexpired, cached fallback; so try again using the primary */
            clearPreferredHost();
            return primaryHost;
        } else {
            /* Onto next fallback. */
            idx = Arrays.asList(fallbackHosts).indexOf(lastHost);
            if (idx < 0) {
                return null;
            }
            ++idx;
        }
        if (idx >= fallbackHosts.length) {
            return null;
        }
        return fallbackHosts[idx];
    }

    public int fallbackHostsRemaining(String candidateHost) {
        if(fallbackHosts == null) {
            return 0;
        }
        if(candidateHost.equals(primaryHost) || candidateHost.equals(prefHost)) {
            return fallbackHosts.length;
        }
        return fallbackHosts.length - Arrays.asList(fallbackHosts).indexOf(candidateHost) - 1;
    }
}
