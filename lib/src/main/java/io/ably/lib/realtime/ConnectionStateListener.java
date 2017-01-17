package io.ably.lib.realtime;

import io.ably.lib.types.ErrorInfo;

public interface ConnectionStateListener {

	public void onConnectionStateChanged(ConnectionStateListener.ConnectionStateChange state);

	public static class ConnectionStateChange {
		public final ConnectionEvent event;
		public final ConnectionState previous;
		public final ConnectionState current;
		public final long retryIn;
		public final ErrorInfo reason;
	
		public ConnectionStateChange(ConnectionState previous, ConnectionState current, long retryIn, ErrorInfo reason) {
			this.event = current.getConnectionEvent();
			this.previous = previous;
			this.current = current;
			this.retryIn = retryIn;
			this.reason = reason;
		}

		/* private constructor for UPDATE event */
		private ConnectionStateChange(ErrorInfo reason) {
			this.event = ConnectionEvent.update;
			this.current = this.previous = ConnectionState.connected;
			this.retryIn = 0;
			this.reason = reason;
		}

		/* construct UPDATE event */
		public static ConnectionStateChange createUpdateEvent(ErrorInfo reason) {
			return new ConnectionStateChange(reason);
		}
	}

	static class Multicaster extends io.ably.lib.util.Multicaster<ConnectionStateListener> implements ConnectionStateListener {
		@Override
		public void onConnectionStateChanged(ConnectionStateChange state) {
			for(ConnectionStateListener member : members)
				try {
					member.onConnectionStateChanged(state);
				} catch(Throwable t) {}
		}
	}

	static class Filter implements ConnectionStateListener {
		@Override
		public void onConnectionStateChanged(ConnectionStateChange change) {
			if(change.current == state)
				listener.onConnectionStateChanged(change);
		}
		Filter(ConnectionState state, ConnectionStateListener listener) { this.state = state; this.listener = listener; }
		ConnectionState state;
		ConnectionStateListener listener;
	}
}