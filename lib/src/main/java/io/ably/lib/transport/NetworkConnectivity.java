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
		boolean wasEmpty = listeners.isEmpty();
		listeners.add(listener);
		if(wasEmpty) {
			onNonempty();
		}
	}

	public void removeListener(NetworkConnectivityListener listener) {
		listeners.remove(listener);
		if(listeners.isEmpty()) {
			onEmpty();
		}
	}

	protected void notifyNetworkAvailable() {
		for(NetworkConnectivityListener listener: listeners) {
			listener.onNetworkAvailable();
		}
	}

	protected void notifyNetworkUnavailable(ErrorInfo reason) {
		for(NetworkConnectivityListener listener: listeners) {
			listener.onNetworkUnavailable(reason);
		}
	}

	protected boolean isEmpty() {
		return listeners.isEmpty();
	}

	protected void onEmpty() {}

	protected void onNonempty() {}

	protected Set<NetworkConnectivityListener> listeners = new HashSet<NetworkConnectivityListener>();

	public static class DefaultNetworkConnectivity extends NetworkConnectivity {}

	public static class DelegatedNetworkConnectivity extends NetworkConnectivity implements NetworkConnectivityListener {
		@Override
		public void onNetworkAvailable() {
			notifyNetworkAvailable();
		}
		@Override
		public void onNetworkUnavailable(ErrorInfo reason) {
			notifyNetworkUnavailable(reason);
		}
	}
}
