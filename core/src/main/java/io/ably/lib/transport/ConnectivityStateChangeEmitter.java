package io.ably.lib.transport;

import io.ably.lib.util.EventEmitter;

/**
 * Created by gokhanbarisaker on 3/31/16.
 */
public abstract class ConnectivityStateChangeEmitter extends EventEmitter<Boolean, ConnectivityStateChangeEmitter.ConnectivityStateChangeListener> {
	public interface ConnectivityStateChangeListener {
		void onConnectivityStateChanged(boolean connected);
	}
}
