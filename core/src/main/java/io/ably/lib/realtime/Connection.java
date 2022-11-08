package io.ably.lib.realtime;

import io.ably.lib.realtime.ConnectionStateListener.ConnectionStateChange;
import io.ably.lib.transport.ConnectionManager;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.util.EventEmitter;
import io.ably.lib.util.Log;
import io.ably.lib.util.PlatformAgentProvider;

/**
 * A class representing the connection associated with an AblyRealtime instance.
 * The Connection object exposes the lifecycle and parameters of the realtime connection.
 */
public class Connection extends EventEmitter<ConnectionEvent, ConnectionStateListener> {

    /**
     * The current state of this Connection.
     */
    public ConnectionState state;

    /**
     * Error information associated with a connection failure.
     */
    public ErrorInfo reason;

    /**
     * The assigned connection key.
     */
    public String key;

    /**
     * A public identifier for this connection, used to identify
     * this member in presence events and message ids.
     */
    public String id;

    /**
     * Causes the library to re-attempt connection, if it was previously explicitly
     * closed by the user, or was closed as a result of an unrecoverable error.
     */
    public void connect() {
        connectionManager.connect();
    }

    /**
     * Send a heartbeat message to the Ably service and await a response.
     *
     * @param listener a listener to be notified of the outcome of this message.
     */
    public void ping(CompletionListener listener) {
        connectionManager.ping(listener);
    }

    /**
     * Causes the connection to close, entering the closed state, from any state except
     * the failed state. Once closed, the library will not attempt to re-establish the
     * connection without a call to {@link #connect}.
     */
    public void close() {
        key = null;
        connectionManager.close();
    }

    /*****************
     * internal
     *****************/

    Connection(AblyRealtimeBase ably, ConnectionManager.Channels channels, PlatformAgentProvider platformAgentProvider) throws AblyException {
        this.ably = ably;
        this.state = ConnectionState.initialized;
        this.connectionManager = new ConnectionManager(ably, this, channels, platformAgentProvider);
    }

    public void onConnectionStateChange(ConnectionStateChange stateChange) {
        state = stateChange.current;
        reason = stateChange.reason;
        emit(state, stateChange);
    }

    @Override
    protected void apply(ConnectionStateListener listener, ConnectionEvent event, Object... args) {
        try {
            listener.onConnectionStateChanged((ConnectionStateChange) args[0]);
        } catch (Throwable t) {
            Log.e(TAG, "Unexpected exception calling ConnectionStateListener", t);
        }
    }

    public void emitUpdate(ErrorInfo errorInfo) {
        if (state == ConnectionState.connected)
            emit(ConnectionEvent.update, ConnectionStateListener.ConnectionStateChange.createUpdateEvent(errorInfo));
    }

    /**
     * Spec: RTN16g
     *
     * @return a json string which incorporates the @connectionKey@, the current @msgSerial@,
     * and a collection of pairs of channel @name@ and current @channelSerial@ for every currently attached channel.
     */
    public String createRecoveryKey() {
        if (key == null || connectionManager == null || connectionManager.getConnectionState() == null ||
            connectionManager.getConnectionState().state == ConnectionState.closed ||
            connectionManager.getConnectionState().state == ConnectionState.closing ||
            connectionManager.getConnectionState().state == ConnectionState.failed ||
            connectionManager.getConnectionState().state == ConnectionState.suspended
        ) {
            //RTN16h
            return null;
        }

        ConnectionRecoveryKey recoveryKey = new ConnectionRecoveryKey(key, connectionManager.msgSerial);

        for (Object channel : ably.channels.values()) {
            if (channel instanceof RealtimeChannelBase) {
                RealtimeChannelBase rcb = (RealtimeChannelBase) channel;
                recoveryKey.addSerial(rcb.name, rcb.properties.channelSerial);
            }
        }

        return recoveryKey.asJson();
    }

    @Deprecated
    public void emit(ConnectionState state, ConnectionStateChange stateChange) {
        super.emit(state.getConnectionEvent(), stateChange);
    }

    @Deprecated
    public void on(ConnectionState state, ConnectionStateListener listener) {
        super.on(state.getConnectionEvent(), listener);
    }

    @Deprecated
    public void once(ConnectionState state, ConnectionStateListener listener) {
        super.once(state.getConnectionEvent(), listener);
    }

    private static final String TAG = Connection.class.getName();
    final AblyRealtimeBase ably;
    public final ConnectionManager connectionManager;
}
