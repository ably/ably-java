package io.ably.lib.realtime;

/**
 * Connection states. See Ably Realtime API documentation for more details.
 */
public enum ConnectionState {
    initialized(ConnectionEvent.initialized),
    connecting(ConnectionEvent.connecting),
    connected(ConnectionEvent.connected),
    disconnected(ConnectionEvent.disconnected),
    suspended(ConnectionEvent.suspended),
    closing(ConnectionEvent.closing),
    closed(ConnectionEvent.closed),
    failed(ConnectionEvent.failed);

    final private ConnectionEvent event;
    ConnectionState(ConnectionEvent event) {
        this.event = event;
    }
    public ConnectionEvent getConnectionEvent() {
        return event;
    }
}
