package io.ably.lib.transport;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by gokhanbarisaker on 4/1/16.
 */
public class ConnectivityStateChangeEmitterImpl extends ConnectivityStateChangeEmitter {
	private boolean connected;

	private final Runnable checkerRunnable = new Runnable() {
		@Override
		public void run() {
			/* Get current connectivity state */
			boolean connected = isConnected();

			/* Check if connectivity state changed by current state with previously saved state */
			if (connected != ConnectivityStateChangeEmitterImpl.this.connected) {
				/* Update current state */
				ConnectivityStateChangeEmitterImpl.this.connected = connected;

				emit(connected);
			}
		}
	};

	public ConnectivityStateChangeEmitterImpl() {
		connected = isConnected();

		ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
		scheduler.scheduleAtFixedRate(checkerRunnable, 2, 2, TimeUnit.SECONDS);
	}

	@Override
	protected void apply(ConnectivityStateChangeListener listener, Boolean connected, Object... args) {
		listener.onConnectivityStateChanged(connected);
	}

	public boolean isConnected() {
		try {
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			NetworkInterface interf;
			while (interfaces.hasMoreElements()) {
				interf = interfaces.nextElement();
				if (interf.isUp() && !interf.isLoopback())
					return true;
			}
		} catch (SocketException e) { /* Ignore */ }

		return false;
	}
}
