package io.ably.lib.transport;

import io.ably.lib.types.ErrorInfo;

import java.util.HashSet;
import java.util.Set;

public abstract class NetworkConnectivity {

	public interface NetworkConnectivityListener {
		public void onNetworkAvailable();
		public void onNetworkUnavailable(ErrorInfo reason);
	}

	public void addListener(NetworkConnectivityListener listener) {
		listeners.add(listener);
	}

	public void removeListener(NetworkConnectivityListener listener) {
		listeners.remove(listener);
	}

	protected static void notifyNetworkAvailable() {
		for(NetworkConnectivityListener listener: listeners) {
			listener.onNetworkAvailable();
		}
	}

	protected void notifyNetworkUnavailable(ErrorInfo reason) {
		for(NetworkConnectivityListener listener: listeners) {
			listener.onNetworkUnavailable(reason);
		}
	}

	protected static Set<NetworkConnectivityListener> listeners = new HashSet<NetworkConnectivityListener>();

	public static class DefaultNetworkConnectivity extends NetworkConnectivity {}
}
