package io.ably.lib.realtime;

import io.ably.lib.types.ErrorInfo;

/**
 * An interface whereby a client may be notified of state changes for a connection.
 */
public interface ConnectionStateListener {

    /**
     * Called when connection state changes.
     * <p>
     * This callback is triggered on background thread.
     *
     * @param state information about the new state. Check {@link ConnectionState ConnectionState} - for all states available.
     */
    void onConnectionStateChanged(ConnectionStateListener.ConnectionStateChange state);

    class ConnectionStateChange {
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

    class Multicaster extends io.ably.lib.util.Multicaster<ConnectionStateListener> implements ConnectionStateListener {
        @Override
        public void onConnectionStateChanged(ConnectionStateChange state) {
            for (final ConnectionStateListener member : getMembers())
                try {
                    member.onConnectionStateChanged(state);
                } catch(Throwable t) {}
        }
    }

    class Filter implements ConnectionStateListener {
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
