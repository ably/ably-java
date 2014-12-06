package io.ably.realtime;

import io.ably.types.ErrorInfo;

public interface ConnectionStateListener {
	public void onConnectionStateChanged(ConnectionStateListener.ConnectionStateChange state);

	public static class ConnectionStateChange {
		public final Connection.ConnectionState previous;
		public final Connection.ConnectionState current;
		public final long retryIn;
		public final ErrorInfo reason;
	
		public ConnectionStateChange(Connection.ConnectionState previous, Connection.ConnectionState current, long retryIn, ErrorInfo reason) {
			this.previous = previous;
			this.current = current;
			this.retryIn = retryIn;
			this.reason = reason;
		}
	}

	public static class Multicaster extends io.ably.util.Multicaster<ConnectionStateListener> implements ConnectionStateListener {
		@Override
		public void onConnectionStateChanged(ConnectionStateListener.ConnectionStateChange state) {
			for(ConnectionStateListener member : members)
				try {
					member.onConnectionStateChanged(state);
				} catch(Throwable t) {}
		}
	}
}