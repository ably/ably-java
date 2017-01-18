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
	boolean primaryHostIsDefault;
	private final String defaultHost;
	private final String[] fallbackHosts;
	private final boolean fallbackHostsIsDefault;
	private final boolean fallbackHostsUseDefault;

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
	 * an Http for a rest connection. The case where the Hosts object is used
	 * by an Http that is being used by a ConnectionManager goes through this
	 * code, but the results are ignored because ConnectionManager then calls
	 * setHost() and fallback is not used.
	 */
	public Hosts(String primaryHost, String defaultHost, ClientOptions options) throws AblyException {
		this.defaultHost = defaultHost;
		if (primaryHost != null) {
			setHost(primaryHost);
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
			setHost(options.environment + "-" + defaultHost);
		} else {
			setHost(defaultHost);
		}
		fallbackHostsUseDefault = options.fallbackHostsUseDefault;
		if (options.fallbackHosts == null) {
			fallbackHosts = Arrays.copyOf(Defaults.HOST_FALLBACKS, Defaults.HOST_FALLBACKS.length);
			fallbackHostsIsDefault = true;
		} else {
			/* RSC15a: use ClientOptions#fallbackHosts if set */
			fallbackHosts = Arrays.copyOf(options.fallbackHosts, options.fallbackHosts.length);
			fallbackHostsIsDefault = false;
			if (options.fallbackHostsUseDefault) {
				/* TO3k7: It is never valid to configure fallbackHost and set
				 * fallbackHostsUseDefault to true */
				throw AblyException.fromErrorInfo(new ErrorInfo("cannot set both fallbackHosts and fallbackHostsUseDefault options", 40000, 400));
			}
		}
		/* RSC15a: shuffle the fallback hosts. */
		Collections.shuffle(Arrays.asList(fallbackHosts));
	}

	/**
	 * set primary hostname
	 *
	 * This gets called when the Hosts object is being used by an Http that is
	 * the http connection for a ConnectionManager.
	 */
	public void setHost(String primaryHost) {
		this.primaryHost = primaryHost;
		primaryHostIsDefault = primaryHost.equalsIgnoreCase(defaultHost);
	}

	/**
	 * Get primary host name
	 */
	public String getHost() {
		return primaryHost;
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
		} else {
			/* Onto next fallback. */
			idx = Arrays.asList(fallbackHosts).indexOf(lastHost);
			if (idx < 0)
				return null;
			++idx;
		}
		if (idx >= fallbackHosts.length)
			return null;
		return fallbackHosts[idx];
	}
}

