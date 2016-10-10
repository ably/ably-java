package io.ably.lib.transport;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 * Object to encapsulate primary host name and shuffled fallback host names.
 */
public class Hosts {
	private String primaryHost;
	boolean primaryHostIsDefault;
	private final String defaultHost;
	private final String[] fallbackHosts;

	/**
	 * Create Hosts object
	 *
	 * @param primaryHost the primary hostname
	 * @param defaultHost the default hostname that the primary hostname must
	 *        match for fallback to occur
	 */
	public Hosts(String primaryHost, String defaultHost) {
		this.defaultHost = defaultHost;
		setHost(primaryHost);
		fallbackHosts = Arrays.copyOf(Defaults.HOST_FALLBACKS, Defaults.HOST_FALLBACKS.length);
		/* RSC15a: shuffle the fallback hosts. */
		Collections.shuffle(Arrays.asList(fallbackHosts));
	}

	/**
	 * set primary hostname
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
			/* RSC15b: only use fallback if the hostname has not been overridden. */
			if (!primaryHostIsDefault)
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

