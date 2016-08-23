package io.ably.lib.transport;

import io.ably.lib.debug.DebugOptions;
import io.ably.lib.debug.RawProtocolListener;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.realtime.Connection;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.realtime.ConnectionStateListener;
import io.ably.lib.transport.ITransport.ConnectListener;
import io.ably.lib.transport.ITransport.TransportParams;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Param;
import io.ably.lib.types.ProtocolMessage;
import io.ably.lib.types.ProtocolMessage.Action;
import io.ably.lib.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;


public class ConnectionManager implements Runnable, ConnectListener {

	private static final String TAG = ConnectionManager.class.getName();
	private static final String INTERNET_CHECK_URL = "http://internet-up.ably-realtime.com/is-the-internet-up.txt";
	private static final String INTERNET_CHECK_OK = "yes";

	/***********************************
	 * default errors
	 ***********************************/

	static ErrorInfo REASON_CLOSED = new ErrorInfo("Connection closed by client", 10000);
	static ErrorInfo REASON_DISCONNECTED = new ErrorInfo("Connection temporarily unavailable", 80003);
	static ErrorInfo REASON_SUSPENDED = new ErrorInfo("Connection unavailable", 80002);
	static ErrorInfo REASON_FAILED = new ErrorInfo("Connection failed", 80000);
	static ErrorInfo REASON_REFUSED = new ErrorInfo("Access refused", 40100);
	static ErrorInfo REASON_TOO_BIG = new ErrorInfo("Connection closed; message too large", 40000);
	static ErrorInfo REASON_NEVER_CONNECTED = new ErrorInfo("Unable to establish connection", 80002);
	static ErrorInfo REASON_TIMEDOUT = new ErrorInfo("Unable to establish connection", 80014);

	/***********************************
	 * a class encapsulating information
	 * associated with a state change
	 * request or notification
	 ***********************************/

	public static class StateIndication {
		final ConnectionState state;
		final ErrorInfo reason;
		final boolean fallback;
		final String currentHost;

		public StateIndication(ConnectionState state, ErrorInfo reason) {
			this(state, reason, false, null);
		}

		public StateIndication(ConnectionState state, ErrorInfo reason, boolean fallback, String currentHost) {
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

		final boolean queueEvents;
		final boolean sendEvents;
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

	/***********************
	 * all state information
	 ***********************/

	@SuppressWarnings("serial")
	public static final HashMap<ConnectionState, StateInfo> states = new HashMap<ConnectionState, StateInfo>() {{
		put(ConnectionState.initialized, new StateInfo(ConnectionState.initialized, true, false, false, false, 0, null));
		put(ConnectionState.connecting, new StateInfo(ConnectionState.connecting, true, false, false, false, Defaults.TIMEOUT_CONNECT, null));
		put(ConnectionState.connected, new StateInfo(ConnectionState.connected, false, true, false, false, 0, null));
		put(ConnectionState.disconnected, new StateInfo(ConnectionState.disconnected, true, false, false, true, Defaults.TIMEOUT_DISCONNECT, REASON_DISCONNECTED));
		put(ConnectionState.suspended, new StateInfo(ConnectionState.suspended, false, false, false, true, Defaults.TIMEOUT_SUSPEND, REASON_SUSPENDED));
		put(ConnectionState.closing, new StateInfo(ConnectionState.closing, false, false, false, false, Defaults.TIMEOUT_CONNECT, REASON_CLOSED));
		put(ConnectionState.closed, new StateInfo(ConnectionState.closed, false, false, true, false, 0, REASON_CLOSED));
		put(ConnectionState.failed, new StateInfo(ConnectionState.failed, false, false, true, false, 0, REASON_FAILED));
	}};

	public ErrorInfo getStateErrorInfo() {
		return state.defaultErrorInfo;
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
		/* debug options */
		if(options instanceof DebugOptions)
			protocolListener = ((DebugOptions)options).protocolListener;

		try {
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

	public String getHost() {
		if(transport != null)
			return transport.getHost();

		return state.host;
	}
	
	/*********************
	 * state management
	 *********************/

	public void connect() {
		boolean connectionExist = state.state == ConnectionState.connected;
		boolean connectionAttemptAvailable = (requestedState != null && requestedState.state != ConnectionState.connecting) ||
				state.state == ConnectionState.connecting;

		if(!connectionExist && !connectionAttemptAvailable && startThread()) {
			requestState(ConnectionState.connecting);
		}
	}

	public void close() {
		requestState(ConnectionState.closing);
	}

	public synchronized StateInfo getConnectionState() {
		return state;
	}
	
	private void setState(StateIndication newState) {
		Log.v(TAG, "setState(): setting " + newState.state);
		ConnectionStateListener.ConnectionStateChange change;
		StateInfo newStateInfo = states.get(newState.state);
		synchronized(this) {
			ErrorInfo reason = newState.reason; if(reason == null) reason = newStateInfo.defaultErrorInfo;
			change = new ConnectionStateListener.ConnectionStateChange(state.state, newState.state, newStateInfo.timeout, reason);
			newStateInfo.host = newState.currentHost;
			state = newStateInfo;
		}

		/* broadcast state change */
		connection.onConnectionStateChange(change);

		/* if now connected, send queued messages, etc */
		if(state.sendEvents) {
			sendQueuedMessages();
			for(Channel channel : ably.channels.values())
				channel.setConnected();
		} else if(!state.queueEvents) {
			failQueuedMessages(state.defaultErrorInfo);
			for(Channel channel : ably.channels.values())
				channel.setSuspended(state.defaultErrorInfo);
		}
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
		if(this.transport == transport) {
			/* if this transition signifies the end of the transport, clear the transport */
			if(states.get(state.state).terminal)
				this.transport = null;
			notifyState(state);
		}
	}

	synchronized void notifyState(StateIndication state) {
		Log.v(TAG, "notifyState(): notifying " + state.state + "; id = " + connection.key);
		indicatedState = state;
		notify();
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

	/***************************************
	 * transport events/notifications
	 ***************************************/

	public void onMessage(ProtocolMessage message) throws AblyException {
		try {
			if(protocolListener != null)
				protocolListener.onRawMessage(message);
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
		if(message.connectionSerial != null)
			connection.serial = message.connectionSerial.longValue();
		ably.channels.onChannelMessage(transport, message);						
	}

	private synchronized void onConnected(ProtocolMessage message) {
		/* if there was a (non-fatal) connection error
		 * that invalidates an existing connection id, then
		 * remove all channels attached to the previous id */
		ErrorInfo error = message.error;
		if(error != null && !message.connectionId.equals(connection.id))
			ably.channels.suspendAll(error);

		/* set the new connection id */
		connection.key = message.connectionKey;
		connection.id = message.connectionId;
		if(message.connectionSerial != null)
			connection.serial = message.connectionSerial.longValue();
		msgSerial = 0;

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

	/**************************
	 * ConnectionManager thread
	 **************************/

	private boolean startThread() {
		boolean creating = false;
		synchronized(this) {
			if(mgrThread == null) {
				mgrThread = new Thread(this);
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

		return creating;
	}

	private void stopThread() {
		synchronized(this) {
			if(mgrThread != null) {
				mgrThread.interrupt();
				mgrThread = null;
			}
		}
	}

	private void handleStateRequest() {
		boolean handled = false;
		switch(requestedState.state) {
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
				indicatedState = new StateIndication(ConnectionState.failed, new ErrorInfo("Connection failed; no host available", 404, 80000), false, requestedState.currentHost);
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

	private void handleStateChange(StateIndication stateChange) {
		/* if we have had a disconnected state indication
		 * from the transport then we have to decide whether
		 * to transition to closed, disconnected to suspended depending
		 * on when we last had a successful connection */
		if(stateChange.state == ConnectionState.disconnected) {
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
				if(transport != null) {
					transport.close(false);
					transport = null;
				}
				stateChange = null;
				stopThread();
				break;
			case connected:
				/* we were connected, so retry immediately */
				requestState(ConnectionState.connecting);
				break;
			default:
				break;
			}
		}
		if(stateChange != null)
			setState(stateChange);
	}

	private void setSuspendTime() {
		suspendTime = (System.currentTimeMillis() + Defaults.TIMEOUT_SUSPEND);
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

		if(pendingConnect != null && stateChange.reason == null) {
			if (!Hosts.isFallback(pendingConnect.host)) {
				if (!checkConnectivity()) {
					return new StateIndication(ConnectionState.failed, new ErrorInfo("connection failed", 80000), false, pendingConnect.host);
				}
			}

			/* we will try a fallback host */
			requestState(new StateIndication(ConnectionState.connecting, null, true, pendingConnect.host));
			/* returning null ensures we stay in the connecting state */
			return null;
		}
		boolean suspendMode = System.currentTimeMillis() > suspendTime;
		ConnectionState expiredState = suspendMode ? ConnectionState.suspended : ConnectionState.disconnected;
		return new StateIndication(expiredState, stateChange.reason);
	}

	private void tryWait(long timeout) {
		if(requestedState == null && indicatedState == null)
			try {
				if(timeout == 0) wait();
				else wait(timeout);
			} catch (InterruptedException e) {}
	}

	public void run() {
		StateIndication stateChange;
		Thread thisThread = Thread.currentThread();
		while(!state.terminal) {
			stateChange = null;
			synchronized(this) {
				/* if we're initialising, then tell the starting thread that
				 * we're ready to receive events */
				if(state.state == ConnectionState.initialized) {
					synchronized(thisThread) {
						thisThread.notify();
					}
				}
	
				while(stateChange == null) {
					tryWait(state.timeout);
					/* if during the wait some action was requested, handle it */
					if(requestedState != null) {
						handleStateRequest();
						continue;
					}
	
					/* if during the wait we were told that a transition
					 * needs to be enacted, handle that (outside the lock) */
					if(indicatedState != null) {
						stateChange = indicatedState;
						indicatedState = null;
						break;
					}
	
					/* if our state wants us to retry on timer expiry, do that */
					if(state.retry) {
						requestState(ConnectionState.connecting);
						continue;
					}
	
					/* no indicated state or requested action, so the timer
					 * expired while we were in the connecting/closing state */
					stateChange = checkSuspend(new StateIndication(ConnectionState.disconnected, REASON_TIMEDOUT));
				}
			}
			if(stateChange != null)
				handleStateChange(stateChange);
		}
		synchronized(this) {
			if(mgrThread == thisThread)
				mgrThread = null;
		}
	}

	@Override
	public void onTransportAvailable(ITransport transport, TransportParams params) {
		this.transport = transport;
	}

	@Override
	public synchronized void onTransportUnavailable(ITransport transport, TransportParams params, ErrorInfo reason) {
		ably.auth.onAuthError(reason);
		notifyState(new StateIndication(ConnectionState.disconnected, reason, false, transport.getHost()));
		transport = null;
	}

	private class ConnectParams extends TransportParams {
		ConnectParams(ClientOptions options) {
			this.options = options;
			this.connectionKey = connection.key;
			this.connectionSerial = String.valueOf(connection.serial);
			this.host = options.realtimeHost;
			this.port = Defaults.getPort(options);
		}
	}

	private boolean connectImpl(StateIndication request) {
		/* determine the parameters of this connection attempt, and
		 * instance the transport.
		 * First, choose the transport. (Right now there's only one.)
		 * Second, choose the host. ConnectParams will use the default
		 * (or requested) host, unless fallback=true, in which case
		 * it will choose a fallback host at random */

		if(request.fallback) {
			String hostFallback = Hosts.isRealtimeFallbackSupported(options.realtimeHost)?(Hosts.getFallback(request.currentHost)):(null);

			if (hostFallback == null) {
				return false;
			}

			pendingConnect = new ConnectParams(options);
			pendingConnect.host = hostFallback;
			ably.http.setHost(hostFallback);
		}
		else {
			pendingConnect = new ConnectParams(options);
			pendingConnect.host = options.realtimeHost;
			ably.http.setHost(options.restHost);
		}

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
		transport.connect(this);

		return true;
	}

	private void closeImpl(StateIndication request) {
		/* enter the closing state */
		notifyState(request);

		/* send a close message on the transport, if any */
		if(transport != null) {
			try {
				transport.send(new ProtocolMessage(Action.close));
			} catch (AblyException e) {
				transport.abort(e.errorInfo);
			}
			return;
		}
		notifyState(new StateIndication(ConnectionState.closed, null));
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
			return ably.http.getUrlString(INTERNET_CHECK_URL).contains(INTERNET_CHECK_OK);
		} catch(AblyException e) {
			return false;
		}
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

	@SuppressWarnings("unused")
	private void sendImpl(ProtocolMessage message) throws AblyException {
		transport.send(message);
	}

	private void sendImpl(ProtocolMessage message, CompletionListener listener) throws AblyException {
		if(ProtocolMessage.ackRequired(message)) {
			message.msgSerial = msgSerial++;
			pendingMessages.push(new QueuedMessage(message, listener));
		}
		transport.send(message);
	}

	private void sendImpl(QueuedMessage msg) throws AblyException {
		ProtocolMessage message = msg.msg;
		if(ProtocolMessage.ackRequired(message)) {
			message.msgSerial = msgSerial++;
			pendingMessages.push(msg);
		}
		transport.send(message);
	}

	private void sendQueuedMessages() {
		synchronized(this) {
			while(queuedMessages.size() > 0) {
				try {
					sendImpl(queuedMessages.get(0));
					queuedMessages.remove(0);
				} catch (AblyException e) {
					Log.e(TAG, "sendQueuedMessages(): Unexpected error sending queued messages", e);
				}
			}
		}
	}

	private void failQueuedMessages(ErrorInfo reason) {
		synchronized(this) {
			for(QueuedMessage queued : queuedMessages) {
				try {
					if(queued.listener != null)
						queued.listener.onError(reason);
				} catch (Throwable t) {
					Log.e(TAG, "failQueuedMessages(): Unexpected error calling listener", t);
				}
			}
		}
	}

	private class PendingMessageQueue {
		private long startSerial = 0L;
		private ArrayList<QueuedMessage> queue = new ArrayList<QueuedMessage>();

		public PendingMessageQueue() {
			/* put startSerial to 0 every time the connection is closed */
			connection.on(ConnectionState.closed, new ConnectionStateListener() {
				@Override
				public void onConnectionStateChanged(ConnectionStateListener.ConnectionStateChange state) {
					startSerial = 0L;
				}
			});
		}

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
	}

	/***************************
	 * connect params management
	 ***************************/

	public Param[] getConnectParams() {
		return pendingConnect.getConnectParams(new Param[]{});
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

	private Thread mgrThread;
	final AblyRealtime ably;
	private final ClientOptions options;
	private final Connection connection;
	private final ITransport.Factory factory;
	private final List<QueuedMessage> queuedMessages;
	private final PendingMessageQueue pendingMessages;
	private final HashSet<Object> heartbeatWaiters = new HashSet<Object>();

	private StateInfo state;
	private StateIndication indicatedState, requestedState;
	private ConnectParams pendingConnect;
	private ITransport transport;
	private long suspendTime;
	private long msgSerial;

	/* for debug/test only */
	private RawProtocolListener protocolListener;

	private static final long HEARTBEAT_TIMEOUT = 5000L;
}
