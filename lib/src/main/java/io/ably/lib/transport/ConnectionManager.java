package io.ably.lib.transport;

import io.ably.lib.debug.DebugOptions;
import io.ably.lib.debug.DebugOptions.RawProtocolListener;
import io.ably.lib.http.HttpHelpers;
import io.ably.lib.realtime.*;
import io.ably.lib.transport.ITransport.ConnectListener;
import io.ably.lib.transport.ITransport.TransportParams;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ConnectionDetails;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.ProtocolMessage;
import io.ably.lib.types.ProtocolMessage.Action;
import io.ably.lib.types.ProtocolSerializer;
import io.ably.lib.util.Log;
import io.ably.lib.transport.NetworkConnectivity.NetworkConnectivityListener;
import io.ably.lib.platform.Platform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;


public class ConnectionManager implements ConnectListener {

	private static final String TAG = ConnectionManager.class.getName();
	private static final String INTERNET_CHECK_URL = "http://internet-up.ably-realtime.com/is-the-internet-up.txt";
	private static final String INTERNET_CHECK_OK = "yes";

	/***********************************
	 * default errors
	 ***********************************/

	static ErrorInfo REASON_CLOSED = new ErrorInfo("Connection closed by client", 200, 10000);
	static ErrorInfo REASON_DISCONNECTED = new ErrorInfo("Connection temporarily unavailable", 503, 80003);
	static ErrorInfo REASON_SUSPENDED = new ErrorInfo("Connection unavailable", 503, 80002);
	static ErrorInfo REASON_FAILED = new ErrorInfo("Connection failed", 503, 80000);
	static ErrorInfo REASON_REFUSED = new ErrorInfo("Access refused", 401, 40100);
	static ErrorInfo REASON_TOO_BIG = new ErrorInfo("Connection closed; message too large", 400, 40000);
	static ErrorInfo REASON_NEVER_CONNECTED = new ErrorInfo("Unable to establish connection", 503, 80002);
	static ErrorInfo REASON_TIMEDOUT = new ErrorInfo("Unable to establish connection", 503, 80014);

	/***********************************
	 * a class encapsulating information
	 * associated with a state change
	 * request or notification
	 ***********************************/

	public static class StateIndication {
		final ConnectionState state;
		final ErrorInfo reason;
		final String fallback;
		final String currentHost;

		public StateIndication(ConnectionState state, ErrorInfo reason) {
			this(state, reason, null, null);
		}

		public StateIndication(ConnectionState state, ErrorInfo reason, String fallback, String currentHost) {
			this.state = state;
			this.reason = reason;
			this.fallback = fallback;
			this.currentHost = currentHost;
		}
	}

	/*************************************
	 * a class encapsulating state machine
	 * information for a given state
	 *************************************/

	public static class StateInfo {
		public final ConnectionState state;
		public final ErrorInfo defaultErrorInfo;
		public final boolean queueEvents;
		public final boolean sendEvents;

		final boolean terminal;
		final boolean retry;
		final long timeout;
		String host;

		StateInfo(ConnectionState state, boolean queueEvents, boolean sendEvents, boolean terminal, boolean retry, long timeout, ErrorInfo defaultErrorInfo) {
			this.state = state;
			this.queueEvents = queueEvents;
			this.sendEvents = sendEvents;
			this.terminal = terminal;
			this.retry = retry;
			this.timeout = timeout;
			this.defaultErrorInfo = defaultErrorInfo;
		}
	}

	/*************************************
	 * a class that listens for state change
	 * events for in-place authorization
	 *************************************/
	public class ConnectionWaiter implements ConnectionStateListener {

		/**
		 * Public API
		 */
		public ConnectionWaiter() {
			connection.on(this);
		}

		/**
		 * Wait for a state change notification
		 */
		public synchronized ErrorInfo waitForChange() {
			Log.d(TAG, "ConnectionWaiter.waitFor()");
			if (change == null) {
				try { wait(); } catch(InterruptedException e) {}
			}
			Log.d(TAG, "ConnectionWaiter.waitFor done: state=" + state + ")");
			ErrorInfo reason = change.reason;
			change = null;
			return reason;
		}

		/**
		 * ConnectionStateListener interface
		 */
		@Override
		public void onConnectionStateChanged(ConnectionStateListener.ConnectionStateChange state) {
			synchronized(this) {
				change = state;
				notify();
			}
		}

		/**
		 * Internal
		 */
		private ConnectionStateListener.ConnectionStateChange change;
	}

	/***********************
	 * all state information
	 ***********************/

	@SuppressWarnings("serial")
	public static final HashMap<ConnectionState, StateInfo> states = new HashMap<ConnectionState, StateInfo>() {{
		put(ConnectionState.initialized, new StateInfo(ConnectionState.initialized, true, false, false, false, 0, null));
		put(ConnectionState.connecting, new StateInfo(ConnectionState.connecting, true, false, false, false, Defaults.TIMEOUT_CONNECT, null));
		put(ConnectionState.connected, new StateInfo(ConnectionState.connected, false, true, false, false, 0, null));
		put(ConnectionState.disconnected, new StateInfo(ConnectionState.disconnected, true, false, false, true, Defaults.TIMEOUT_DISCONNECT, REASON_DISCONNECTED));
		put(ConnectionState.suspended, new StateInfo(ConnectionState.suspended, false, false, false, true, Defaults.connectionStateTtl, REASON_SUSPENDED));
		put(ConnectionState.closing, new StateInfo(ConnectionState.closing, false, false, false, false, Defaults.TIMEOUT_CONNECT, REASON_CLOSED));
		put(ConnectionState.closed, new StateInfo(ConnectionState.closed, false, false, true, false, 0, REASON_CLOSED));
		put(ConnectionState.failed, new StateInfo(ConnectionState.failed, false, false, true, false, 0, REASON_FAILED));
	}};

	long maxIdleInterval = Defaults.maxIdleInterval;
	long connectionStateTtl = Defaults.connectionStateTtl;

	public ErrorInfo getStateErrorInfo() {
		return stateError != null ? stateError : state.defaultErrorInfo;
	}

	public boolean isActive() {
		return state.queueEvents || state.sendEvents;
	}

	/***********************
	 * constructor
	 ***********************/

	public ConnectionManager(final AblyRealtime ably, Connection connection) {
		this.ably = ably;
		this.options = ably.options;
		this.connection = connection;
		queuedMessages = new ArrayList<QueuedMessage>();
		pendingMessages = new PendingMessageQueue();
		state = states.get(ConnectionState.initialized);
		String transportClass = Defaults.TRANSPORT;
		try {
			this.hosts = new Hosts(options.realtimeHost, Defaults.HOST_REALTIME, options);
			/* debug options */
			if(options instanceof DebugOptions)
				protocolListener = ((DebugOptions)options).protocolListener;

			factory = ((ITransport.Factory)Class.forName(transportClass).newInstance());
		} catch(Exception e) {
			String msg = "Unable to instance factory class";
			Log.e(getClass().getName(), msg, e);
			throw new RuntimeException(msg, e);
		}
		synchronized(this) {
			setSuspendTime();
		}
	}

	/*********************
	 * host management
	 *********************/

	/* This is only here for the benefit of ConnectionManagerTest. */
	public String getHost() {
		return lastUsedHost;
	}

	/*********************
	 * state management
	 *********************/

	public void connect() {
		boolean connectionExist = state.state == ConnectionState.connected;
		boolean connectionAttemptInProgress = (requestedState != null && requestedState.state == ConnectionState.connecting) ||
				state.state == ConnectionState.connecting;

		if(!connectionExist && !connectionAttemptInProgress) {
			startup(); // Start thread if not already started.
			requestState(ConnectionState.connecting);
		}
	}

	public void close() {
		requestState(ConnectionState.closing);
	}

	public synchronized StateInfo getConnectionState() {
		return state;
	}

	/**
	 * Set the state the given state
	 * @param newState
	 * @return changed
	 */
	private boolean setState(StateIndication newState) {
		ConnectionStateListener.ConnectionStateChange change;
		StateInfo newStateInfo = states.get(newState.state);
		synchronized(this) {
			if(newState.state == state.state) {
				Log.v(TAG, "setState(): unchanged " + newState.state);
				return false;
			}
			ErrorInfo reason = newState.reason;
			if(reason == null) {
				reason = newStateInfo.defaultErrorInfo;
			}
			Log.v(TAG, "setState(): setting " + newState.state + "; reason " + reason);
			change = new ConnectionStateListener.ConnectionStateChange(state.state, newState.state, newStateInfo.timeout, reason);
			newStateInfo.host = newState.currentHost;
			state = newStateInfo;
			stateError = reason;

			if(change.current != change.previous) {
				/* any state change clears pending reauth flag */
				pendingReauth = false;
			}
			if(state.terminal) {
				clearTransport();
				shutdown();
			}
		}

		/* broadcast state change */
		connection.onConnectionStateChange(change);

		/* if now connected, send queued messages, etc */
		if(state.sendEvents) {
			sendQueuedMessages();
			for(Channel channel : ably.channels.values()) {
				channel.setConnected();
			}
		} else { 
			if(!state.queueEvents) {
				failQueuedMessages(state.defaultErrorInfo);
			}
			for(Channel channel : ably.channels.values()) {
				switch (state.state) {
					case disconnected:
						/* (RTL3e) If the connection state enters the
						 * DISCONNECTED state, it will have no effect on the
						 * channel states. */
						break;
					case failed:
						/* (RTL3a) If the connection state enters the FAILED
						 * state, then an ATTACHING or ATTACHED channel state
						 * will transition to FAILED, set the
						 * Channel#errorReason and emit the error event. */
						channel.setConnectionFailed(change.reason);
						break;
					case closed:
						/* (RTL3b) If the connection state enters the CLOSED
						 * state, then an ATTACHING or ATTACHED channel state
						 * will transition to DETACHED. */
						channel.setConnectionClosed(state.defaultErrorInfo);
						break;
					case suspended:
						/* (RTL3c) If the connection state enters the SUSPENDED
						 * state, then an ATTACHING or ATTACHED channel state
						 * will transition to SUSPENDED. */
						channel.setSuspended(state.defaultErrorInfo, true);
						break;
				}
			}
		}
		return true;
	}

	public void requestState(ConnectionState state) {
		requestState(new StateIndication(state, null));
	}

	public synchronized void requestState(StateIndication state) {
		Log.v(TAG, "requestState(): requesting " + state.state + "; id = " + connection.key);
		requestedState = state;
		notify();
	}

	synchronized void notifyState(ITransport transport, StateIndication state) {
		if(this.transport != transport) {
			Log.v(TAG, "notifyState: notification received for superseded transport");
			return;
		}
		/* if this transition signifies the end of the transport, clear the transport */
		if(states.get(state.state).terminal) {
			this.transport = null;
		}
		notifyState(state);
	}

	synchronized void notifyState(StateIndication state) {
		Log.v(TAG, "notifyState(): notifying " + state.state + "; id = " + connection.key);
		if (Thread.currentThread() == mgrThread) {
			handleStateChange(state);
		}
		else {
			indicatedState = state;
			notify();
		}
	}

	public void ping(final CompletionListener listener) {
		if(state.state != ConnectionState.connected) {
			if(listener != null)
				listener.onError(new ErrorInfo("Unable to ping service; not connected", 40000, 400));
			return;
		}
		if(listener != null) {
			Runnable waiter = new Runnable() {
				public void run() {
					boolean pending;
					synchronized(heartbeatWaiters) {
						pending = heartbeatWaiters.contains(this);
						if(pending)
							try { heartbeatWaiters.wait(HEARTBEAT_TIMEOUT); } catch(InterruptedException ie) {}

						pending = heartbeatWaiters.remove(this);
					}
					if(pending)
						listener.onError(new ErrorInfo("Timed out waiting for heartbeat response", 50000, 500));
					else
						listener.onSuccess();
				}
			};
			synchronized(heartbeatWaiters) {
				heartbeatWaiters.add(waiter);
				(new Thread(waiter)).start();
			}
		}
		try {
			send(new ProtocolMessage(ProtocolMessage.Action.heartbeat), false, null);
		} catch (AblyException e) {
			if(listener != null)
				listener.onError(e.errorInfo);
		}
	}

	/**
	 * (RTC8) For a realtime client, Auth.authorize instructs the library to
	 * obtain a token using the provided tokenParams and authOptions and upgrade
	 * the current connection to use that token; or if not currently connected,
	 * to connect with the token.
	 */
	public void onAuthUpdated(String token, boolean waitForResponse) throws AblyException {
		ConnectionWaiter waiter = new ConnectionWaiter();
		if (state.state == ConnectionState.connected) {
			/* (RTC8a) If the connection is in the CONNECTED state and
			 * auth.authorize is called or Ably requests a re-authentication
			 * (see RTN22), the client must obtain a new token, then send an
			 * AUTH ProtocolMessage to Ably with an auth attribute
			 * containing an AuthDetails object with the token string. */
			try {
				ProtocolMessage msg = new ProtocolMessage(ProtocolMessage.Action.auth);
				msg.auth = new ProtocolMessage.AuthDetails(token);
				send(msg, false, null);
			} catch (AblyException e) {
				/* The send failed. Close the transport; if a subsequent
				 * reconnect succeeds, it will be with the new token. */
				Log.v(TAG, "onAuthUpdated: closing transport after send failure");
				transport.close(/*sendDisconnect=*/false);
			}
		} else {
			if (state.state == ConnectionState.connecting) {
				/* Close the connecting transport. */
				Log.v(TAG, "onAuthUpdated: closing connecting transport");
				transport.close(/*sendDisconnect=*/false);
			}
			/* Start a new connection attempt. */
			connect();
		}

		if(!waitForResponse)
			return;

		/* Wait for a state transition into anything other than connecting or
		 * disconnected. Note that this includes the case that the connection
		 * was already connected, and the AUTH message prompted the server to
		 * send another connected message. */
		for (;;) {
			ErrorInfo reason = waiter.waitForChange();
			switch (state.state) {
				case connected:
					Log.v(TAG, "onAuthUpdated: got connected");
					return;
				case connecting:
				case disconnected:
					continue;
				default:
					/* suspended/closed/error: throw the error. */
					Log.v(TAG, "onAuthUpdated: throwing exception");
					throw AblyException.fromErrorInfo(reason);
			}
		}
	}

	/**
	 * Called when where was an error during authentication attempt
	 *
	 * @param errorInfo Error associated with unsuccessful authentication
	 */
	public void onAuthError(ErrorInfo errorInfo) {
		Log.i(TAG, String.format("onAuthError: (%d) %s", errorInfo.code, errorInfo.message));
		switch (state.state) {
			case connecting:
				ITransport transport = this.transport;
				if (transport != null)
					/* onTransportUnavailable will send state change event and set transport to null */
					onTransportUnavailable(transport, null, errorInfo);
				break;

			case connected:
				/* stay connected but notify of authentication error */
				connection.emitUpdate(errorInfo);
				break;

			default:
				break;
		}
	}

	/***************************************
	 * transport events/notifications
	 ***************************************/

	/**
	 * React on message from the transport
	 * @param transport transport instance or null to bypass transport correctness check (for testing)
	 * @param message
	 * @throws AblyException
	 */
	public void onMessage(ITransport transport, ProtocolMessage message) throws AblyException {
		if (transport != null && this.transport != transport) {
			return;
		}
		if (Log.level <= Log.VERBOSE) {
			Log.v(TAG, "onMessage() (transport = " + transport + "): " + message.action + ": " + new String(ProtocolSerializer.writeJSON(message)));
		}
		try {
			if(protocolListener != null) {
				protocolListener.onRawMessageRecv(message);
			}
			switch(message.action) {
				case heartbeat:
					onHeartbeat(message);
					break;
				case error:
					ErrorInfo reason = message.error;
					if(reason == null)
						Log.e(TAG, "onMessage(): ERROR message received (no error detail)");
					else
						Log.e(TAG, "onMessage(): ERROR message received; message = " + reason.message + "; code = " + reason.code);

			/* an error message may signify an error state in a channel, or in the connection */
					if(message.channel != null)
						onChannelMessage(message);
					else
						onError(message);
					break;
				case connected:
					onConnected(message);
					break;
				case disconnect:
				case disconnected:
					onDisconnected(message);
					break;
				case closed:
					onClosed(message);
					break;
				case ack:
					onAck(message);
					break;
				case nack:
					onNack(message);
					break;
				case auth:
					synchronized (this) {
						pendingReauth = true;
						notify();
					}
					break;
				default:
					onChannelMessage(message);
			}
		}
		catch(Exception e) {
			// Prevent any non-AblyException to be thrown
			throw AblyException.fromThrowable(e);
		}
	}

	private void onChannelMessage(ProtocolMessage message) {
		if(message.connectionSerial != null) {
			connection.serial = message.connectionSerial.longValue();
			if (connection.key != null)
				connection.recoveryKey = connection.key + ":" + message.connectionSerial;
		}
		ably.channels.onChannelMessage(transport, message);
	}

	private synchronized void onConnected(ProtocolMessage message) {
		/* Set the http host to try and ensure that realtime and rest use the
		 * same region:
		 *  - if we're on the default realtime host, set http to the default
		 *    rest host
		 *  - otherwise (the realtime host has been overridden or has fallen
		 *    back), set http to the same as realtime.
		 */
		if (pendingConnect.host == options.realtimeHost) {
			ably.httpCore.setPreferredHost(options.restHost);
		} else {
			ably.httpCore.setPreferredHost(pendingConnect.host);
		}

		/* if the returned connection id differs from
		 * the existing connection id, then this means
		 * we need to suspend all existing attachments to
		 * the old connection.
		 * If realtime did not reply with an error, it
		 * signifies that this was a result of an earlier
		 * connection being invalidated due to being stale.
		 *
		 * Suspend all channels attached to the previous id;
		 * this will be reattached in setConnection() */
		ErrorInfo error = message.error;
		if(connection.id != null && !message.connectionId.equals(connection.id)) {
			/* we need to suspend the original connection */
			if(error == null) {
				error = REASON_SUSPENDED;
			}
			ably.channels.suspendAll(error, false);
		}

		/* set the new connection id */
		ConnectionDetails connectionDetails = message.connectionDetails;
		connection.key = connectionDetails.connectionKey;
		if (!message.connectionId.equals(connection.id)) {
			/* The connection id has changed. Reset the message serial and the
			 * pending message queue (which fails the messages currently in
			 * there). */
			pendingMessages.reset(msgSerial,
					new ErrorInfo("Connection resume failed", 500, 50000));
			msgSerial = 0;
		}
		connection.id = message.connectionId;
		if(message.connectionSerial != null) {
			connection.serial = message.connectionSerial.longValue();
			if (connection.key != null)
				connection.recoveryKey = connection.key + ":" + message.connectionSerial;
		}

		/* Get any parameters from connectionDetails. */
		maxIdleInterval = connectionDetails.maxIdleInterval;
		connectionStateTtl = connectionDetails.connectionStateTtl;

		/* set the clientId resolved from token, if any */
		String clientId = connectionDetails.clientId;
		try {
			ably.auth.setClientId(clientId);
		} catch (AblyException e) {
			notifyState(transport, new StateIndication(ConnectionState.failed, e.errorInfo));
		}

		/* indicated connected state */
		setSuspendTime();
		notifyState(new StateIndication(ConnectionState.connected, error));
	}

	private synchronized void onDisconnected(ProtocolMessage message) {
		onTransportUnavailable(transport, null, message.error);
	}

	private synchronized void onClosed(ProtocolMessage message) {
		if(message.error != null) {
			this.onError(message);
		} else {
			connection.key = null;
			notifyState(new StateIndication(ConnectionState.closed, null));
		}
	}

	private synchronized void onError(ProtocolMessage message) {
		connection.key = null;
		ConnectionState destinationState = isFatalError(message.error) ? ConnectionState.failed : ConnectionState.disconnected;
		notifyState(transport, new StateIndication(destinationState, message.error));
	}

	private void onAck(ProtocolMessage message) {
		pendingMessages.ack(message.msgSerial, message.count, message.error);
	}

	private void onNack(ProtocolMessage message) {
		pendingMessages.nack(message.msgSerial, message.count, message.error);
	}

	private void onHeartbeat(ProtocolMessage message) {
		synchronized(heartbeatWaiters) {
			heartbeatWaiters.clear();
			heartbeatWaiters.notifyAll();
		}
	}

	/******************************
	 * ConnectionManager lifecycle
	 ******************************/

	private void startup() {
		startThread();
		startConnectivityListener();
	}

	private void shutdown() {
		stopConnectivityListener();
		stopThread();
	}

	private void startThread() {
		boolean creating = false;
		synchronized(this) {
			if(mgrThread == null) {
				mgrThread = new CMThread();
				state = states.get(ConnectionState.initialized);
				creating = true;
			}
		}
		if(creating) {
			synchronized(mgrThread) {
				mgrThread.start();
				try { mgrThread.wait(); } catch(InterruptedException ie) {}
			}
		}
	}

	private void stopThread() {
		if(mgrThread != null) {
			mgrThread.setExiting();
			mgrThread = null;
		}
	}

	private void handleStateRequest() {
		boolean handled = false;
		switch(requestedState.state) {
		case disconnected:
			if(transport != null) {
				transport.close(false);
				handled = true;
			}
			break;
		case failed:
			if(transport != null) {
				transport.abort(requestedState.reason);
				handled = true;
			}
			break;
		case closed:
			/* if already failed, don't transition to closed */
			if(state.state == ConnectionState.failed) {
				handled = true;
				break;
			}

			if(transport != null) {
				transport.close(state.state == ConnectionState.connected);
				handled = true;
			}
			break;
		case connecting:
			if(!connectImpl(requestedState)) {
				indicatedState = new StateIndication(ConnectionState.failed, new ErrorInfo("Connection failed; no host available", 404, 80000), null, requestedState.currentHost);
			}

			handled = true;
			break;
		case closing:
			closeImpl(requestedState);
			handled = true;
		default:
		}
		if(!handled) {
			/* the transport wasn't there, so we just transition directly */
			indicatedState = requestedState;
		}
		requestedState = null;
	}

	private boolean checkConnectionStale() {
		if(lastActivity == 0) {
			return false;
		}
		long now = System.currentTimeMillis();
		long intervalSinceLastActivity = now - lastActivity;
		if(intervalSinceLastActivity > (maxIdleInterval + connectionStateTtl)) {
			/* RTN15g1, RTN15g2 Force a new connection if the previous one is stale;
			 * Clearing connection.key will ensure that we don't attempt to resume;
			 * leaving the original connection.id will mean that we notice at
			 * connection time that the connectionId has changed */
			if(connection.key != null) {
				Log.v(TAG, "Clearing stale connection key to suppress resume");
				connection.key = null;
				connection.recoveryKey = null;
			}
			return true;
		}
		return false;
	}

	private void handleStateChange(StateIndication stateChange) {
		/* if we have had a disconnected state indication
		 * from the transport then we have to decide whether
		 * to transition to closed, disconnected to suspended depending
		 * on when we last had a successful connection */
		if(stateChange.state == ConnectionState.disconnected) {
			if(checkConnectionStale()) {
				requestState(ConnectionState.suspended);
				return;
			}
			switch(state.state) {
			case connecting:
				stateChange = checkSuspend(stateChange);
				pendingConnect = null;
				break;
			case closing:
				/* this becomes a close event; if the indication we received
				 * has a non-default disconnection reason, use that */
				ErrorInfo closeReason = (stateChange.reason == REASON_DISCONNECTED) ? REASON_CLOSED : stateChange.reason;
				stateChange = new StateIndication(ConnectionState.closed, closeReason);
				break;
			case closed:
			case failed:
				/* terminal states */
				stateChange = null;
				break;
			case connected:
				setSuspendTime();
				/* we were connected, so retry immediately */
				if(!suppressRetry) {
					requestState(ConnectionState.connecting);
				}
				break;
			case suspended:
				/* Don't allow a second disconnected to make the state come out of suspended. */
				Log.v(TAG, "handleStateChange: not moving out of suspended");
				stateChange = null;
				break;
			default:
				break;
			}
		}
		if(stateChange != null) {
			boolean changed = setState(stateChange);
			if (!changed && stateChange.state == ConnectionState.connected) {
				/* connected is special case because we want to deliver reauth notifications to listeners as an update */
				connection.emitUpdate(null);
			}
		}
	}

	private void setSuspendTime() {
		suspendTime = (System.currentTimeMillis() + connectionStateTtl);
	}

	private StateIndication checkSuspend(StateIndication stateChange) {
		/* We got here when a connection attempt failed and we need to check to
		 * see whether we should go into disconnected or suspended state.
		 * There are three options:
		 * - First check to see whether or not internet connectivity is ok;
		 *   if so we'll trigger a new connect attempt with a fallback host.
		 * - we're entering disconnected and will schedule a retry after the
		 *   reconnect timer;
		 * - the suspend timer has expired, so we're going into suspended state.
		 */

		if(pendingConnect != null && (stateChange.reason == null || stateChange.reason.statusCode >= 500)) {
			if (checkConnectivity()) {
				/* we will try a fallback host */
				String hostFallback = hosts.getFallback(pendingConnect.host);
				if (hostFallback != null) {
					Log.v(TAG, "checkSuspend: fallback to " + hostFallback);
					requestState(new StateIndication(ConnectionState.connecting, null, hostFallback, pendingConnect.host));
					/* returning null ensures we stay in the connecting state */
					return null;
				}
			}
		}
		Log.v(TAG, "checkSuspend: not falling back");
		boolean suspendMode = System.currentTimeMillis() > suspendTime;
		ConnectionState expiredState = suspendMode ? ConnectionState.suspended : ConnectionState.disconnected;
		return new StateIndication(expiredState, stateChange.reason);
	}

	private void tryWait(long timeout) {
		if(requestedState == null && indicatedState == null && !pendingReauth) {
			try {
				if(timeout == 0) {
					wait();
				} else {
					wait(timeout);
				}
			} catch (InterruptedException e) {}
		}
	}

	class CMThread extends Thread {

		private boolean exiting = false;
		private void setExiting() {
			exiting = true;
		}

		public void run() {
			ConnectionManager cm = ConnectionManager.this;
			while(!exiting) {
				/*
				 * Until we're commited to exit we:
				 * - get a state change;
				 * - enact that change
				 */
				StateIndication stateChange = null;

				/* Hold the lock until we obtain a state change */
				synchronized(cm) {
					/* if we're initialising, then tell the starting thread that
					 * we're ready to receive events */
					if (state.state == ConnectionState.initialized) {
						synchronized(this) {
							notify();
						}
					}

					while(!exiting && stateChange == null) {
						/* wait for a state change event or for expiry of the current state */
						tryWait(state.timeout);

						/* if during the wait some action was requested, handle it */
						if (requestedState != null) {
							handleStateRequest();
							continue;
						}

						/* if during the wait we were told that a transition
						 * needs to be enacted, handle that (outside the lock) */
						if (indicatedState != null) {
							stateChange = indicatedState;
							indicatedState = null;
							break;
						}

						/* if our state wants us to retry on timer expiry, do that */
						if (state.retry && !suppressRetry) {
							requestState(ConnectionState.connecting);
							continue;
						}

						if (pendingReauth) {
							handleReauth();
							break;
						}

						/* no indicated state or requested action, so the timer
						 * expired while we were in the connecting/closing state */
						stateChange = checkSuspend(new StateIndication(ConnectionState.disconnected, REASON_TIMEDOUT));
					}
				}
				if(exiting) { break; }

				/* Enact the change without the lock */
				if (stateChange != null) {
					handleStateChange(stateChange);
				}
			}
		}
	}

	private void handleReauth() {
		pendingReauth = false;

		if (state.state == ConnectionState.connected) {
			Log.v(TAG, "Server initiated reauth");

			ErrorInfo errorInfo = null;

			/*
			 * It is a server initiated reauth, it is issued while previous token is still valid for ~30 seconds,
			 * we have to clear cached token and get a new one
			 */
			try {
				ably.auth.renew();
			} catch (AblyException e) {
				errorInfo = e.errorInfo;
			}

			/* report error in UPDATE event */
			if (state.state == ConnectionState.connected && errorInfo != null) {
				connection.emitUpdate(errorInfo);
			}
		}
	}

	@Override
	public void onTransportAvailable(ITransport transport, TransportParams params) {
		if (this.transport != transport) {
			/* This is from a transport that we have already abandoned. */
			Log.v(TAG, "onTransportAvailable: ignoring connection event from superseded transport");
			return;
		}
		if(protocolListener != null) {
			protocolListener.onRawConnect(transport.getURL());
		}
	}

	@Override
	public synchronized void onTransportUnavailable(ITransport transport, TransportParams params, ErrorInfo reason) {
		if (this.transport != transport) {
			/* This is from a transport that we have already abandoned. */
			Log.v(TAG, "onTransportUnavailable: ignoring disconnection event from superseded transport");
			return;
		}
		ably.auth.onAuthError(reason);
		notifyState(new StateIndication(ConnectionState.disconnected, reason, null, transport.getHost()));
		this.transport = null;
	}

	private class ConnectParams extends TransportParams {
		ConnectParams(ClientOptions options) {
			this.options = options;
			this.connectionKey = connection.key;
			this.connectionSerial = String.valueOf(connection.serial);
			this.port = Defaults.getPort(options);
		}
	}

	private boolean connectImpl(StateIndication request) {
		/* determine the parameters of this connection attempt, and
		 * instance the transport.
		 * First, choose the transport. (Right now there's only one.)
		 * Second, choose the host. ConnectParams will use the default
		 * (or requested) host, unless fallback!=null, in which case
		 * checkSuspend has already chosen a fallback host at random */

		String host = request.fallback;
		if (host == null) {
			host = hosts.getPreferredHost();
		}
		checkConnectionStale();
		pendingConnect = new ConnectParams(options);
		pendingConnect.host = host;
		lastUsedHost = host;

		/* enter the connecting state */
		notifyState(request);

		/* try the connection */
		ITransport transport;
		try {
			transport = factory.getTransport(pendingConnect, this);
		} catch(Exception e) {
			String msg = "Unable to instance transport class";
			Log.e(getClass().getName(), msg, e);
			throw new RuntimeException(msg, e);
		}
		ITransport oldTransport;
		synchronized(this) {
			oldTransport = this.transport;
			this.transport = transport;
		}
		if (oldTransport != null)
			oldTransport.abort(REASON_TIMEDOUT);
		transport.connect(this);
		return true;
	}

	private void closeImpl(StateIndication request) {
		boolean isConnected = state.state == ConnectionState.connected;

		/* enter the closing state */
		notifyState(request);

		/* close or abort transport */
		if(transport != null) {
			if(isConnected) {
				/* send a close message on the transport, if connected */
				try {
					Log.v(TAG, "Requesting connection close");
					transport.send(new ProtocolMessage(Action.close));
				} catch (AblyException e) {
					transport.abort(e.errorInfo);
				}
			} else {
				/* just close the transport */
				Log.v(TAG, "Aborting incomplete transport due to close()");
				transport.close(false);
			}
			transport = null;
		}
		notifyState(new StateIndication(ConnectionState.closed, null));
	}

	private void clearTransport() {
		if(transport != null) {
			transport.close(false);
			transport = null;
		}
	}

	/**
	 * Determine whether or not the client has connection to the network
	 * without reference to a specific ably host. This is to determine whether
	 * it is better to try a fallback host, or keep retrying with the default
	 * host.
	 * @return boolean, true if network is available
	 */
	protected boolean checkConnectivity() {
		try {
			return HttpHelpers.getUrlString(ably.httpCore, INTERNET_CHECK_URL).contains(INTERNET_CHECK_OK);
		} catch(AblyException e) {
			return false;
		}
	}

	protected void setLastActivity(long lastActivityTime) {
		this.lastActivity = lastActivityTime;
	}

	/******************
	 * event queueing
	 ******************/

	public static class QueuedMessage {
		public final ProtocolMessage msg;
		public CompletionListener listener;
		private boolean isMerged;
		public QueuedMessage(ProtocolMessage msg, CompletionListener listener) {
			this.msg = msg;
			this.listener = listener;
		}
	}

	public void send(ProtocolMessage msg, boolean queueEvents, CompletionListener listener) throws AblyException {
		StateInfo state;
		synchronized(this) {
			state = this.state;
			if(state.sendEvents) {
				sendImpl(msg, listener);
				return;
			}
			if(state.queueEvents && queueEvents) {
				int queueSize = queuedMessages.size();
				if(queueSize > 0) {
					QueuedMessage lastQueued = queuedMessages.get(queueSize - 1);
					ProtocolMessage lastMessage = lastQueued.msg;
					if(ProtocolMessage.mergeTo(lastMessage, msg)) {
						if(!lastQueued.isMerged) {
							lastQueued.listener = new CompletionListener.Multicaster(lastQueued.listener);
							lastQueued.isMerged = true;
						}
						((CompletionListener.Multicaster)lastQueued.listener).add(listener);
						return;
					}
				}
				queuedMessages.add(new QueuedMessage(msg, listener));
				return;
			}
		}
		throw AblyException.fromErrorInfo(state.defaultErrorInfo);
	}

	private void sendImpl(ProtocolMessage message, CompletionListener listener) throws AblyException {
		if(transport == null) {
			Log.v(TAG, "sendImpl(): Discarding message; transport unavailable");
			return;
		}
		if(ProtocolMessage.ackRequired(message)) {
			message.msgSerial = msgSerial++;
			pendingMessages.push(new QueuedMessage(message, listener));
		}
		if(protocolListener != null) {
			protocolListener.onRawMessageSend(message);
		}
		transport.send(message);
	}

	private void sendImpl(QueuedMessage msg) throws AblyException {
		if(transport == null) {
			Log.v(TAG, "sendImpl(): Discarding message; transport unavailable");
			return;
		}
		ProtocolMessage message = msg.msg;
		if(ProtocolMessage.ackRequired(message)) {
			message.msgSerial = msgSerial++;
			pendingMessages.push(msg);
		}
		if(protocolListener != null) {
			protocolListener.onRawMessageSend(message);
		}
		transport.send(message);
	}

	private void sendQueuedMessages() {
		synchronized(this) {
			while(queuedMessages.size() > 0) {
				try {
					sendImpl(queuedMessages.get(0));
				} catch (AblyException e) {
					Log.e(TAG, "sendQueuedMessages(): Unexpected error sending queued messages", e);
				} finally {
					queuedMessages.remove(0);
				}
			}
		}
	}

	private void failQueuedMessages(ErrorInfo reason) {
		synchronized(this) {
			for (QueuedMessage queued: queuedMessages) {
				if (queued.listener != null) {
					try {
						queued.listener.onError(reason);
					} catch (Throwable t) {
						Log.e(TAG, "failQueuedMessages(): Unexpected error calling listener", t);
					}
				}
			}
			queuedMessages.clear();
		}
	}

	private class PendingMessageQueue {
		private long startSerial = 0L;
		private ArrayList<QueuedMessage> queue = new ArrayList<QueuedMessage>();

		public synchronized void push(QueuedMessage msg) {
			queue.add(msg);
		}

		public void ack(long msgSerial, int count, ErrorInfo reason) {
			QueuedMessage[] ackMessages = null, nackMessages = null;
			synchronized(this) {
				if(msgSerial < startSerial) {
					/* this is an error condition and shouldn't happen but
					 * we can handle it gracefully by only processing the
					 * relevant portion of the response */
					count -= (int)(startSerial - msgSerial);
					if(count < 0)
						count = 0;
					msgSerial = startSerial;
				}
				if(msgSerial > startSerial) {
					/* this counts as a nack of the messages earlier than serial,
					 * as well as an ack */
					int nCount = (int)(msgSerial - startSerial);
					List<QueuedMessage> nackList = queue.subList(0, nCount);
					nackMessages = nackList.toArray(new QueuedMessage[nCount]);
					nackList.clear();
					startSerial = msgSerial;
				}
				if(msgSerial == startSerial) {
					List<QueuedMessage> ackList = queue.subList(0, count);
					ackMessages = ackList.toArray(new QueuedMessage[count]);
					ackList.clear();
					startSerial += count;
				}
			}
			if(nackMessages != null) {
				if(reason == null)
					reason = new ErrorInfo("Unknown error", 500, 50000);
				for(QueuedMessage msg : nackMessages) {
					try {
						if(msg.listener != null)
							msg.listener.onError(reason);
					} catch(Throwable t) {
						Log.e(TAG, "ack(): listener exception", t);
					}
				}
			}
			if(ackMessages != null) {
				for(QueuedMessage msg : ackMessages) {
					try {
						if(msg.listener != null)
							msg.listener.onSuccess();
					} catch(Throwable t) {
						Log.e(TAG, "ack(): listener exception", t);
					}
				}
			}
		}

		public synchronized void nack(long serial, int count, ErrorInfo reason) {
			QueuedMessage[] nackMessages = null;
			synchronized(this) {
				if(serial != startSerial) {
					/* this is an error condition and shouldn't happen but
					 * we can handle it gracefully by only processing the
					 * relevant portion of the response */
					count -= (int)(startSerial - serial);
					serial = startSerial;
				}
				List<QueuedMessage> nackList = queue.subList(0, count);
				nackMessages = nackList.toArray(new QueuedMessage[count]);
				nackList.clear();
				startSerial += count;
			}
			if(nackMessages != null) {
				if(reason == null)
					reason = new ErrorInfo("Unknown error", 500, 50000);
				for(QueuedMessage msg : nackMessages) {
					try {
						if(msg.listener != null)
							msg.listener.onError(reason);
					} catch(Throwable t) {
						Log.e(TAG, "nack(): listener exception", t);
					}
				}
			}
		}

		/**
		 * reset the pending message queue, failing any currently pending messages.
		 * Used when a resume fails and we get a different connection id.
		 * @param oldMsgSerial the next message serial number for the old
		 *		connection, and thus one more than the highest message serial
		 *		in the queue.
		 */
		public synchronized void reset(long oldMsgSerial, ErrorInfo err) {
			nack(startSerial, (int)(oldMsgSerial - startSerial), err);
			startSerial = 0;
		}

	}

	/***********************
	 * Network connectivity
	 **********************/

	private class CMConnectivityListener implements NetworkConnectivityListener {

		@Override
		public void onNetworkAvailable() {
			Log.i(TAG, "onNetworkAvailable()");
			ConnectionManager cm = ConnectionManager.this;
			ConnectionState currentState = cm.state.state;
			if(currentState == ConnectionState.disconnected || currentState == ConnectionState.suspended) {
				Log.i(TAG, "onNetworkAvailable(): initiating reconnect");
				cm.connect();
			}
		}

		@Override
		public void onNetworkUnavailable(ErrorInfo reason) {
			Log.i(TAG, "onNetworkAvailable(); reason = " + reason.toString());
			ConnectionManager cm = ConnectionManager.this;
			ConnectionState currentState = cm.state.state;
			if(currentState == ConnectionState.connected || currentState == ConnectionState.connecting) {
				Log.i(TAG, "onNetworkAvailable(): closing connected transport");
				cm.requestState(new StateIndication(ConnectionState.disconnected, reason));
			}
		}
	}

	private void startConnectivityListener() {
		connectivityListener = new CMConnectivityListener();
		ably.platform.getNetworkConnectivity().addListener(connectivityListener);
	}

	private void stopConnectivityListener() {
		ably.platform.getNetworkConnectivity().removeListener(connectivityListener);
		connectivityListener = null;
	}

	/*******************
	 * for tests only
	 ******************/

	void disconnectAndSuppressRetries() {
		requestState(ConnectionState.disconnected);
		suppressRetry = true;
	}

	/*******************
	 * internal
	 ******************/

	private boolean isFatalError(ErrorInfo err) {
		if(err.code != 0) {
			/* token errors are assumed to be recoverable */
			if((err.code >= 40140) && (err.code < 40150)) { return false; }
			/* 400 codes assumed to be fatal */
			if((err.code >= 40000) && (err.code < 50000)) { return true; }
		}
		/* otherwise, use statusCode */
		if(err.statusCode != 0 && err.statusCode < 500) { return true; }
		return false;
	}

	/*******************
	 * private members
	 ******************/

	final AblyRealtime ably;
	private final ClientOptions options;
	private final Connection connection;
	private final ITransport.Factory factory;
	private final List<QueuedMessage> queuedMessages;
	private final PendingMessageQueue pendingMessages;
	private final HashSet<Object> heartbeatWaiters = new HashSet<Object>();
	private final Hosts hosts;

	private CMThread mgrThread;
	private StateInfo state;
	private ErrorInfo stateError;
	private StateIndication indicatedState, requestedState;
	private ConnectParams pendingConnect;
	private boolean pendingReauth;
	private boolean suppressRetry; /* for tests only; modified via reflection */
	private ITransport transport;
	private long suspendTime;
	private long msgSerial;
	private long lastActivity;
	private CMConnectivityListener connectivityListener;

	/* for debug/test only */
	private RawProtocolListener protocolListener;
	private String lastUsedHost;

	private static final long HEARTBEAT_TIMEOUT = 5000L;
}
