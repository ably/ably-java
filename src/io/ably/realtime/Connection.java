package io.ably.realtime;

import io.ably.transport.ConnectionManager;
import io.ably.types.ErrorInfo;
import io.ably.util.EventEmitter;

/**
 * A class representing the connection associated with an AblyRealtime instance.
 * The Connection object exposes the lifecycle and parameters of the realtime connection.
 */
public class Connection implements EventEmitter<ConnectionState, ConnectionStateListener>, ConnectionStateListener {

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
	 * The serial number of the last message to be received on this connection.
	 */
	public long serial;

	/**
	 * Causes the library to re-attempt connection, if it was previously explicitly
	 * closed by the user, or was closed as a result of an unrecoverable error.
	 */
	public void connect() {
		connectionManager.requestState(ConnectionState.connecting);
	}

	/**
	 * Send a heartbeat message to the Ably service and await a response.
	 * @param listener: a listener to be notified of the outcome of this message.
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
		connectionManager.requestState(ConnectionState.closing);
	}

	/**
	 * Register the given listener for all connection state changes
	 * @param listener
	 */
	@Override
	public void on(ConnectionStateListener listener) {
		listeners.add(listener);
	}

	/**
	 * Remove a previously registered listener
	 * @param listener
	 */
	@Override
	public void off(ConnectionStateListener listener) {
		listeners.remove(listener);
	}

	/**
	 * Register the given listener for a specific connection state event
	 * @param state the connection state of interest
	 * @param listener
	 */
	@Override
	public void on(ConnectionState state, ConnectionStateListener listener) {
		on(new ConnectionStateListener.Filter(state, listener));
	}

	/**
	 * Remove a previously registered state-specific listener
	 * @param listener
	 * @param state
	 */
	@Override
	public void off(ConnectionState state, ConnectionStateListener listener) {
		if(listener instanceof ConnectionStateListener.Filter)
			off(((ConnectionStateListener.Filter)listener).listener);
	}

	/*****************
	 * internal
	 *****************/

	Connection(AblyRealtime ably) {
		this.ably = ably;
		this.state = ConnectionState.initialized;
		this.listeners = new ConnectionStateListener.Multicaster();
		this.connectionManager = new ConnectionManager(ably, this);
	}

	@Override
	public void onConnectionStateChanged(ConnectionStateListener.ConnectionStateChange stateChange) {
		state = stateChange.current;
		reason = stateChange.reason;
		listeners.onConnectionStateChanged(stateChange);
	}

	final AblyRealtime ably;
	private final ConnectionStateListener.Multicaster listeners;
	public final ConnectionManager connectionManager;

}
