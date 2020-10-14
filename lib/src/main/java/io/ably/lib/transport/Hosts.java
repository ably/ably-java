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
        if (primaryHost != null) {
            setPrimaryHost(primaryHost);
            if (options.environment != null) {
                /* TO3k2: It is never valid to provide both a restHost and environment value
                 * TO3k3: It is never valid to provide both a realtimeHost and environment value */
                throw AblyException.fromErrorInfo(new ErrorInfo("cannot set both restHost/realtimeHost and environment options", 40000, 400));
            }
        } else if (options.environment != null && !options.environment.equalsIgnoreCase("production")) {
            /* RSC11: If ClientOptions.environment is set and is not
             * "production", then the primary hostname is set to the default
             * hostname with the environment setting used as a prefix.
             * Note that this does not happen if there is an explicit setting
             * of ClientOptions.restHost or ClientOptions.realtimeHost (as
             * appropriate). The spec is not clear on which one should take
             * precedence. */
            setPrimaryHost(options.environment + "-" + defaultHost);
        } else {
            setPrimaryHost(defaultHost);
        }

        if (options.fallbackHostsUseDefault && options.fallbackHosts != null) {
            /* TO3k7: It is never valid to configure fallbackHost and set
             * fallbackHostsUseDefault to true */
            throw AblyException.fromErrorInfo(new ErrorInfo("cannot set both fallbackHosts and fallbackHostsUseDefault options", 40000, 400));
        }
        fallbackHostsUseDefault = options.fallbackHostsUseDefault;
        if (primaryHostIsDefault) {
            if (options.fallbackHosts == null && options.port == 0 && options.tlsPort == 0) {
                if (!fallbackHostsUseDefault && options.environment != null && !options.environment.equalsIgnoreCase("production")) {
                    /* RSC15g2: If ClientOptions#environment is set to a value other than "production"
                     * and ClientOptions#fallbackHosts is not set, use the environment fallback hosts */
                    fallbackHosts = Defaults.getEnvironmentFallbackHosts(options.environment);
                    fallbackHostsIsDefault = false;
                } else {
                    fallbackHosts = Defaults.HOST_FALLBACKS.clone();
                    fallbackHostsIsDefault = true;
                }
            } else {
                fallbackHosts = (options.fallbackHosts == null) ? new String[] {} : options.fallbackHosts.clone();
                fallbackHostsIsDefault = false;
            }
        } else {
            if (fallbackHostsUseDefault && options.port == 0 && options.tlsPort == 0) {
                fallbackHosts = Defaults.HOST_FALLBACKS.clone();
                fallbackHostsIsDefault = true;
            } else {
                fallbackHosts = (options.fallbackHosts == null) ? new String[] {} : options.fallbackHosts.clone();
                fallbackHostsIsDefault = false;
            }
        }

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
