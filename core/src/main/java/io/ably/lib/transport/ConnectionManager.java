package io.ably.lib.transport;

import io.ably.lib.debug.DebugOptions;
import io.ably.lib.debug.DebugOptions.RawProtocolListener;
import io.ably.lib.http.HttpHelpers;
import io.ably.lib.realtime.AblyRealtimeBase;
import io.ably.lib.realtime.RealtimeChannelBase;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.realtime.Connection;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.realtime.ConnectionStateListener;
import io.ably.lib.realtime.ConnectionStateListener.ConnectionStateChange;
import io.ably.lib.transport.ITransport.ConnectListener;
import io.ably.lib.transport.ITransport.TransportParams;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ConnectionDetails;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.ProtocolMessage;
import io.ably.lib.types.ProtocolSerializer;
import io.ably.lib.util.Log;
import io.ably.lib.transport.NetworkConnectivity.NetworkConnectivityListener;
import io.ably.lib.util.PlatformAgentProvider;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ConnectionManager implements ConnectListener {

    /**************************************************************
     * ConnectionManager
     *
     * This class is responsible for coordinating all actions that
     * relate to transports and connection state.
     *
     * It comprises two principal parts:
     * - An action queue, and a thread that performs those actions.
     *   Actions comprise connection state change requests, plus other
     *   actions that arise from transport state indications. An
     *   action Handler thread runs, except during idle times when
     *   there is no current or pending connection activity, that
     *   performs queued actions.
     *
     * - A state machine that represents the current connection state,
     *   and the possible transitions between states.
     **************************************************************/

    private static final String TAG = ConnectionManager.class.getName();
    private static final String INTERNET_CHECK_URL = "https://internet-up.ably-realtime.com/is-the-internet-up.txt";
    private static final String INTERNET_CHECK_OK = "yes";

    /***********************************
     * default errors
     ***********************************/

    static ErrorInfo REASON_CLOSED = new ErrorInfo("Can't attach when not in an active state", 200, 10000);
    static ErrorInfo REASON_DISCONNECTED = new ErrorInfo("Connection temporarily unavailable", 503, 80003);
    static ErrorInfo REASON_SUSPENDED = new ErrorInfo("Connection unavailable", 503, 80002);
    static ErrorInfo REASON_FAILED = new ErrorInfo("Connection failed", 400, 80000);
    static ErrorInfo REASON_REFUSED = new ErrorInfo("Access refused", 401, 40100);
    static ErrorInfo REASON_TOO_BIG = new ErrorInfo("Connection closed; message too large", 400, 40000);

    /**
     * Methods on the channels map owned by the {@link AblyRealtimeBase} instance
     * which the {@link ConnectionManager} needs access to.
     */
    public interface Channels {
        void onMessage(ProtocolMessage msg);
        void suspendAll(ErrorInfo error, boolean notifyStateChange);
        void reAttach();
        Iterable<RealtimeChannelBase> values();
    }

    /***********************************
     * a class encapsulating information
     * associated with a currentState change
     * request or notification
     ***********************************/

    public static class StateIndication {
        final ConnectionState state;
        final ErrorInfo reason;
        final String fallback;
        final String currentHost;

        StateIndication(ConnectionState state) {
            this(state, null);
        }

        public StateIndication(ConnectionState state, ErrorInfo reason) {
            this(state, reason, null, null);
        }

        StateIndication(ConnectionState state, ErrorInfo reason, String fallback, String currentHost) {
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

    public abstract class State {
        public final ConnectionState state;
        public final ErrorInfo defaultErrorInfo;
        public final boolean queueEvents;
        public final boolean sendEvents;

        final boolean terminal;
        public final long timeout;

        State(ConnectionState state, boolean queueEvents, boolean sendEvents, boolean terminal, long timeout, ErrorInfo defaultErrorInfo) {
            this.state = state;
            this.queueEvents = queueEvents;
            this.sendEvents = sendEvents;
            this.terminal = terminal;
            this.timeout = timeout;
            this.defaultErrorInfo = defaultErrorInfo;
        }

        /**
         * Called on the current state to determine the response to a
         * give state change request.
         * @param target: the state change request or event
         * @return StateIndication result: the determined response to
         * the request with the required state transition, if any. A
         * null result indicates that there is no resulting transition.
         */
        abstract StateIndication validateTransition(StateIndication target);

        /**
         * Called when the timeout occurs for the current state.
         * @return StateIndication result: the determined response to
         * the timeout with the required state transition, if any. A
         * null result indicates that there is no resulting transition.
         */
        StateIndication onTimeout() {
            return null;
        }

        /**
         * Perform a transition to this state.
         * @param stateIndication: the transition request that triggered this transition
         * @param change: the change event corresponding to this transition.
         */
        void enact(StateIndication stateIndication, ConnectionStateChange change) {
            if(change != null) {
                /* if now connected, send queued messages, etc */
                if(sendEvents) {
                    sendQueuedMessages();
                } else if(!queueEvents) {
                    failQueuedMessages(stateIndication.reason);
                }
                for(final RealtimeChannelBase channel : channels.values()) {
                    enactForChannel(stateIndication, change, channel);
                }
            }
        }

        /**
         * Perform a transition to this state for a given channel.
         * @param stateIndication: the transition request that triggered this transition
         * @param change: the change event corresponding to this transition.
         * @param channel: the channel
         */
        void enactForChannel(StateIndication stateIndication, ConnectionStateChange change, RealtimeChannelBase channel) {}
    }

    /**************************************************
     * Initialized: the initial state
     **************************************************/

    class Initialized extends State {
        Initialized() {
            super(ConnectionState.initialized, true, false, false, 0, null);
        }

        @Override
        StateIndication validateTransition(StateIndication target) {
            /* we can transition to any other state, other than ourselves */
            if(target.state == this.state) {
                return null;
            }
            return target;
        }
    }

    /**************************************************
     * Connecting: a connection attempt is in progress
     **************************************************/

    class Connecting extends State {
        Connecting() {
            super(ConnectionState.connecting, true, false, false, Defaults.TIMEOUT_CONNECT, null);
        }

        @Override
        StateIndication onTimeout() {
            return checkSuspended(null);
        }

        @Override
        StateIndication validateTransition(StateIndication target) {
            /* we can transition to any other state */
            return target;
        }

        @Override
        void enact(StateIndication stateIndication, ConnectionStateChange change) {
            super.enact(stateIndication, change);
            connectImpl(stateIndication);
        }
    }

    /**************************************************
     * Connected: a connection is established
     **************************************************/

    class Connected extends State {
        Connected() {
            super(ConnectionState.connected, false, true, false, 0, null);
        }

        @Override
        StateIndication validateTransition(StateIndication target) {
            if(target.state == this.state) {
                /* RTN24: no currentState change, so no transition, required, but there will be an update event;
                 * connected is special case because we want to deliver reauth notifications to listeners as an update */
                addAction(new UpdateAction(null));
                return null;
            }

            /* we can transition to any other state */
            return target;
        }

        @Override
        void enactForChannel(StateIndication stateIndication, ConnectionStateChange change, RealtimeChannelBase channel) {
            channel.setConnected();
        }
    }

    /**************************************************
     * Disconnected: no connection is established, but
     * a reconnection attempt will be made on timer
     * expiry, anticipating preservation of connection
     * state on reconnection
     **************************************************/

    class Disconnected extends State {
        Disconnected() {
            super(ConnectionState.disconnected, true, false, false, Defaults.TIMEOUT_DISCONNECT, REASON_DISCONNECTED);
        }

        @Override
        StateIndication validateTransition(StateIndication target) {
            /* we can't transition to ourselves */
            if(target.state == this.state) {
                return null;
            }
            /* a closing event will transition directly to closed */
            if(target.state == ConnectionState.closing) {
                return new StateIndication(ConnectionState.closed);
            }
            /* otherwise, the transition is valid */
            return target;
        }

        @Override
        StateIndication onTimeout() {
            return new StateIndication(ConnectionState.connecting);
        }

        @Override
        void enactForChannel(StateIndication stateIndication, ConnectionStateChange change, RealtimeChannelBase channel) {
            /* (RTL3e) If the connection currentState enters the
             * DISCONNECTED currentState, it will have no effect on the
             * channel states. */
        }

        @Override
        void enact(StateIndication stateIndication, ConnectionStateChange change) {
            super.enact(stateIndication, change);
            clearTransport();
            if(change.previous == ConnectionState.connected) {
                setSuspendTime();
                /* we were connected, so retry immediately */
                if(!suppressRetry) {
                    requestState(ConnectionState.connecting);
                }
            }
        }
    }

    /**************************************************
     * Suspended: no connection is established. A
     * reconnection attempt will be made on timer expiry
     * but there will be no continuity of connection
     * state on reconnection
     **************************************************/

    class Suspended extends State {
        Suspended() {
            super(ConnectionState.suspended, false, false, false, Defaults.connectionStateTtl, REASON_SUSPENDED);
        }

        @Override
        StateIndication validateTransition(StateIndication target) {
            /* we can't transition to ourselves */
            if(target.state == this.state) {
                return null;
            }
            /* a closing event will transition directly to closed */
            if(target.state == ConnectionState.closing) {
                return new StateIndication(ConnectionState.closed);
            }
            /* otherwise, the transition is valid */
            return target;
        }

        @Override
        StateIndication onTimeout() {
            return new StateIndication(ConnectionState.connecting);
        }

        @Override
        void enactForChannel(StateIndication stateIndication, ConnectionStateChange change, RealtimeChannelBase channel) {
            /* (RTL3c) If the connection currentState enters the SUSPENDED
             * currentState, then an ATTACHING or ATTACHED channel currentState
             * will transition to SUSPENDED. */
            channel.setSuspended(defaultErrorInfo, true);
        }
    }

    /**************************************************
     * Closing: a close sequence is in progress
     **************************************************/

    class Closing extends State {
        Closing() {
            super(ConnectionState.closing, false, false, false, Defaults.TIMEOUT_CONNECT, REASON_CLOSED);
        }

        @Override
        StateIndication validateTransition(StateIndication target) {
            /* we can't transition to ourselves */
            if(target.state == this.state) {
                return null;
            }
            /* any disconnection event will transition directly to closed */
            if(target.state == ConnectionState.disconnected || target.state == ConnectionState.suspended) {
                return new StateIndication(ConnectionState.closed);
            }
            /* otherwise, the transition is valid */
            return target;
        }

        @Override
        StateIndication onTimeout() {
            return new StateIndication(ConnectionState.closed);
        }

        @Override
        void enact(StateIndication stateIndication, ConnectionStateChange change) {
            super.enact(stateIndication, change);
            boolean closed = closeImpl();
            if(closed) {
                addAction(new AsynchronousStateChangeAction(ConnectionState.closed));
            }
        }
    }

    /**************************************************
     * Closed: the connection is closed, and no
     * reconnection attempt will be made unless
     * explicitly requested
     **************************************************/

    class Closed extends State {
        Closed() {
            super(ConnectionState.closed, false, false, true, 0, REASON_CLOSED);
        }

        @Override
        StateIndication validateTransition(StateIndication target) {
            /* we only leave the closed state via a connection attempt */
            if(target.state == ConnectionState.connecting) {
                return target;
            }
            /* otherwise, the transition is not valid */
            return null;
        }

        @Override
        void enactForChannel(StateIndication stateIndication, ConnectionStateChange change, RealtimeChannelBase channel) {
            /* (RTL3b) If the connection currentState enters the CLOSED
             * currentState, then an ATTACHING or ATTACHED channel currentState
             * will transition to DETACHED. */
            channel.setConnectionClosed(REASON_CLOSED);
        }
    }

    /**************************************************
     * Failed: there is no connection, and there has
     * been an error, either in options validation or
     * as a response to a connection attempt, that
     * implies no new connection attempt will succeed.
     * No reconnection attempt will be made unless
     * explicitly requested
     **************************************************/

    class Failed extends State {
        Failed() {
            super(ConnectionState.failed, false, false, true, 0, REASON_FAILED);
        }

        @Override
        StateIndication validateTransition(StateIndication target) {
            /* we only leave the failed state via a connection attempt */
            if(target.state == ConnectionState.connecting) {
                return target;
            }
            /* otherwise, the transition is not valid */
            return null;
        }

        @Override
        void enactForChannel(StateIndication stateIndication, ConnectionStateChange change, RealtimeChannelBase channel) {
            /* (RTL3a) If the connection currentState enters the FAILED
             * currentState, then an ATTACHING or ATTACHED channel currentState
             * will transition to FAILED, set the
             * Channel#errorReason and emit the error event. */
            channel.setConnectionFailed(stateIndication.reason);
        }

        @Override
        void enact(StateIndication stateIndication, ConnectionStateChange change) {
            super.enact(stateIndication, change);
            clearTransport();
        }
    }

    public ErrorInfo getStateErrorInfo() {
        return stateError != null ? stateError : currentState.defaultErrorInfo;
    }

    public boolean isActive() {
        return currentState.queueEvents || currentState.sendEvents;
    }

    /**
     * Listens for connection state changes.
     *
     * The close() method must be called when the ConnectionWaiter is no longer needed.
     */
    private class ConnectionWaiter implements ConnectionStateListener {
        private ConnectionStateChange change;
        private boolean closed = false;

        /**
         * Create a ConnectionWaiter as a connection listener.
         */
        private ConnectionWaiter() {
            connection.on(this);
        }

        /**
         * Wait for a currentState change notification
         */
        private synchronized ErrorInfo waitForChange() {
            if (closed) {
                throw new IllegalStateException("Already closed.");
            }

            Log.d(TAG, "ConnectionWaiter.waitFor()");
            if (change == null) {
                try { wait(); } catch(InterruptedException e) {}
            }
            Log.d(TAG, "ConnectionWaiter.waitFor done: currentState=" + currentState + ")");
            ErrorInfo reason = change.reason;
            change = null;
            return reason;
        }

        /**
         * ConnectionStateListener interface
         */
        @Override
        public synchronized void onConnectionStateChanged(ConnectionStateChange state) {
            change = state;
            notify();
        }

        /**
         * Remove this ConnectionWaiter as a connection listener.
         */
        private void close() {
            // This method is explicitly not synchronized. There may be a case for this in the
            // future, however its addition is designed to be lightweight with minimal impact.
            if (closed) {
                return;
            }
            closed = true;
            connection.off(this);
        }
    }

    /***********************
     * Actions
     ***********************/

    /**
     * A class that encapsulates actions to perform by the ConnectionManager
     */
    private interface Action extends Runnable {}

    /**
     * An class that performs a state transition
     */
    private abstract class StateChangeAction {

        protected final ITransport transport;
        protected final StateIndication stateIndication;
        protected ConnectionStateChange change;

        StateChangeAction(ITransport transport, StateIndication stateIndication) {
            this.transport = transport;
            this.stateIndication = stateIndication;
        }

        /**
         * Make the change to the ConnectionManager currentState represented by this Action
         */
        protected void setState() {
            change = ConnectionManager.this.setState(transport, stateIndication);
        }

        protected void enactState() {
            if(change != null) {
                if(change.current != change.previous) {
                    /* broadcast currentState change */
                    connection.onConnectionStateChange(change);
                }

                /* implement the state change */
                states.get(stateIndication.state).enact(stateIndication, change);
                if(currentState.terminal) {
                    clearTransport();
                }
            }
        }
    }

    /**
     * An Action that enacts a state transition, making the ConnectionManager state change
     * synchronously. This is for instances such as any transition away from the connected
     * state, where the state is updated synchronously with the transport state change.
     * This ensures that there is no possibility of an attempt to send on the transport
     * after it has indicated that it is not available.
     */
    private class SynchronousStateChangeAction extends StateChangeAction implements Action {
        SynchronousStateChangeAction(ITransport transport, StateIndication stateIndication) {
            super(transport, stateIndication);
            setState();
        }

        @Override
        public void run() {
            enactState();
        }
    }

    /**
     * An Action that enacts a state transition, making the ConnectionManager state change
     * asynchronously. This applies to all transitions that are not transitions away from
     * the connected state.
     */
    private class AsynchronousStateChangeAction extends StateChangeAction implements Action{
        AsynchronousStateChangeAction(ConnectionState state) {
            super(null, new StateIndication(state, null));
        }

        AsynchronousStateChangeAction(ITransport transport, StateIndication stateIndication) {
            super(transport, stateIndication);
        }

        @Override
        public void run() {
            setState();
            enactState();
        }
    }

    /**
     * An Action that performs an inband reauthorisation
     */
    private class ReauthAction implements Action {
        @Override
        public void run() {
            handleReauth();
        }
    }

    /**
     * An Action that handles dissemination of update events arising from a
     * connected -> connected transition
     */
    private class UpdateAction implements Action {
        private final ErrorInfo reason;

        UpdateAction(ErrorInfo reason) {
            this.reason = reason;
        }

        @Override
        public void run() {
            connection.emitUpdate(reason);
        }
    }

    /**
     * A queue of Actions awaiting processing
     */
    private static class ActionQueue extends ArrayDeque<Action> {
        public synchronized boolean add(Action action) {
            return super.add(action);
        }

        public synchronized Action poll() {
            return super.poll();
        }

        public synchronized Action peek() {
            return super.peek();
        }

        public synchronized int size() {
            return super.size();
        }
    }

    /**
     * Append an action to the pending action queue
     * @param action: the action
     */
    private synchronized void addAction(Action action) {
        actionQueue.add(action);
        notifyAll();
    }

    /**
     * A handler that runs in a dedicated Thread that processes queued actions
     */
    class ActionHandler implements Runnable {

        public void run() {
            while(true) {
                /*
                 * Until we're committed to exit we:
                 * - wait for an action or timeout
                 * - given an action, perform the action asynchronously;
                 * - if a timeout, perform the timeout state transition
                 */

                /* Hold the lock until we obtain an action */
                synchronized(ConnectionManager.this) {
                    while(actionQueue.size() == 0) {
                        /* if we're in a terminal state, then this thread is done */
                        if(currentState.terminal) {
                            /* indicate that this thread is committed to die */
                            handlerThread = null;
                            stopConnectivityListener();
                            return;
                        }

                        /* wait for an action event or for expiry of the current currentState */
                        tryWait(currentState.timeout);

                        /* if during the wait some action was requested, handle it */
                        Action act = actionQueue.peek();
                        if (act != null) {
                            Log.d(TAG, "Wait ended by action: " + act.toString());
                            break;
                        }

                        /* if our currentState wants us to retry on timer expiry, do that */
                        if (!suppressRetry) {
                            StateIndication nextState = currentState.onTimeout();
                            if (nextState != null) {
                                requestState(nextState);
                            }
                        }
                    }
                }

                /* perform outstanding actions, without the ConnectionManager locked */
                Action deferredAction;
                while((deferredAction = actionQueue.poll()) != null) {
                    try {
                        deferredAction.run();
                    } catch(Exception e) {
                        Log.e(TAG, "Action invocation failed with exception: action = " + deferredAction.toString(), e);
                    }
                }
            }
        }
    }

    /***********************
     * ConnectionManager
     ***********************/

    public ConnectionManager(final AblyRealtimeBase ably, final Connection connection, final Channels channels, final PlatformAgentProvider platformAgentProvider) throws AblyException {
        this.ably = ably;
        this.connection = connection;
        this.channels = channels;
        this.platformAgentProvider = platformAgentProvider;

        ClientOptions options = ably.options;
        this.hosts = new Hosts(options.realtimeHost, Defaults.HOST_REALTIME, options);

        /* debug options */
        ITransport.Factory transportFactory = null;
        RawProtocolListener protocolListener = null;
        if(options instanceof DebugOptions) {
            protocolListener = ((DebugOptions) options).protocolListener;
            transportFactory = ((DebugOptions) options).transportFactory;
        }
        this.protocolListener = protocolListener;
        this.transportFactory = (transportFactory != null) ? transportFactory : Defaults.TRANSPORT;

        /* construct all states */
        states.put(ConnectionState.initialized, new Initialized());
        states.put(ConnectionState.connecting, new Connecting());
        states.put(ConnectionState.connected, new Connected());
        states.put(ConnectionState.disconnected, new Disconnected());
        states.put(ConnectionState.suspended, new Suspended());
        states.put(ConnectionState.closing, new Closing());
        states.put(ConnectionState.closed, new Closed());
        states.put(ConnectionState.failed, new Failed());

        currentState = states.get(ConnectionState.initialized);

        setSuspendTime();
    }

    /*********************
     * host management
     *********************/

    /* This is only here for the benefit of ConnectionManagerTest. */
    public String getHost() {
        return lastUsedHost;
    }

    /*********************
     * states API
     *********************/

    public synchronized State getConnectionState() {
        return currentState;
    }

    public synchronized void connect() {
        /* connect() is the only action that will bring the ConnectionManager out of a terminal currentState */
        if(currentState.terminal || currentState.state == ConnectionState.initialized) {
            startup();
        }
        requestState(ConnectionState.connecting);
    }

    public void close() {
        requestState(ConnectionState.closing);
    }

    public void requestState(ConnectionState state) {
        requestState(new StateIndication(state, null));
    }

    public void requestState(StateIndication state) {
        requestState(null, state);
    }

    private synchronized void requestState(ITransport transport, StateIndication stateIndication) {
        Log.v(TAG, "requestState(): requesting " + stateIndication.state + "; id = " + connection.id);
        addAction(new AsynchronousStateChangeAction(transport, stateIndication));
    }

    private synchronized ConnectionStateChange setState(ITransport transport, StateIndication stateIndication) {
        /* check validity of transport */
        if (transport != null && transport != this.transport) {
            Log.v(TAG, "setState: action received for superseded transport; discarding");
            return null;
        }

        /* check validity of transition */
        StateIndication validatedStateIndication = currentState.validateTransition(stateIndication);
        if (validatedStateIndication == null) {
            Log.v(TAG, "setState(): not transitioning; not a valid transition " + stateIndication.state);
            return null;
        }

        /* update currentState */
        ConnectionState newConnectionState = validatedStateIndication.state;
        State newState = states.get(newConnectionState);
        ErrorInfo reason = validatedStateIndication.reason;
        if (reason == null) {
            reason = newState.defaultErrorInfo;
        }
        Log.v(TAG, "setState(): setting " + newState.state + "; reason " + reason);
        ConnectionStateChange change = new ConnectionStateChange(currentState.state, newConnectionState, newState.timeout, reason);
        currentState = newState;
        stateError = reason;

        return change;
    }

    /*********************
     * ping API
     *********************/

    public void ping(final CompletionListener listener) {
        HeartbeatWaiter waiter = new HeartbeatWaiter(listener);
        if(currentState.state != ConnectionState.connected) {
            waiter.onError(new ErrorInfo("Unable to ping service; not connected", 40000, 400));
            return;
        }
        synchronized(heartbeatWaiters) {
            heartbeatWaiters.add(waiter);
            waiter.start();
        }
        try {
            send(new ProtocolMessage(ProtocolMessage.Action.heartbeat), false, null);
        } catch (AblyException e) {
            waiter.onError(e.errorInfo);
        }
    }

    /**
     * A thread that waits for completion of a ping
     */
    private class HeartbeatWaiter extends Thread {
        private final CompletionListener listener;

        HeartbeatWaiter(CompletionListener listener) {
            this.listener = listener;
        }

        private void onSuccess() {
            clear();
            if(listener != null) {
                listener.onSuccess();
            }
        }

        private void onError(ErrorInfo reason) {
            clear();
            if(listener != null) {
                listener.onError(reason);
            }
        }

        private boolean clear() {
            boolean pending = heartbeatWaiters.remove(this);
            if(pending) {
                interrupt();
            }
            return pending;
        }

        @Override
        public void run() {
            boolean pending;
            synchronized(heartbeatWaiters) {
                try {
                    heartbeatWaiters.wait(HEARTBEAT_TIMEOUT);
                } catch (InterruptedException ie) {
                }
                pending = clear();
            }
            if(pending) {
                onError(new ErrorInfo("Timed out waiting for heartbeat response", 50000, 500));
            } else {
                onSuccess();
            }
        }
    }

    /***************************************
     * auth event handling
     ***************************************/

    /**
     * (RTC8) For a realtime client, Auth.authorize instructs the library to
     * obtain a token using the provided tokenParams and authOptions and upgrade
     * the current connection to use that token; or if not currently connected,
     * to connect with the token.
     */
    public void onAuthUpdated(final String token, final boolean waitForResponse) throws AblyException {
        final ConnectionWaiter waiter = new ConnectionWaiter();
        try {
            switch(currentState.state) {
                case connected:
                    /* (RTC8a) If the connection is in the CONNECTED currentState and
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
                        transport.close();
                    }
                    break;

                case connecting:
                    /* Close the connecting transport. */
                    Log.v(TAG, "onAuthUpdated: closing connecting transport");
                    ErrorInfo disconnectError = new ErrorInfo("Aborting incomplete connection with superseded auth params", 503, 80003);
                    requestState(new StateIndication(ConnectionState.disconnected, disconnectError, null, null));
                    /* Start a new connection attempt. */
                    connect();
                    break;

                default:
                    /* Start a new connection attempt. */
                    connect();
                    break;
            }

            if(!waitForResponse) {
                return;
            }

            /* Wait for a currentState transition into anything other than connecting or
             * disconnected. Note that this includes the case that the connection
             * was already connected, and the AUTH message prompted the server to
             * send another connected message. */
            boolean waitingForConnected = true;
            while (waitingForConnected) {
                final ErrorInfo reason = waiter.waitForChange();
                final ConnectionState connectionState = currentState.state;
                switch (connectionState) {
                    case connected:
                        Log.v(TAG, "onAuthUpdated: got connected");
                        waitingForConnected = false;
                        break;

                    case connecting:
                    case disconnected:
                        Log.v(TAG, "onAuthUpdated: " + connectionState);
                        break;

                    default:
                        /* suspended/closed/error: throw the error. */
                        Log.v(TAG, "onAuthUpdated: throwing exception");
                        throw AblyException.fromErrorInfo(reason);
                }
            }
        } finally {
            waiter.close();
        }
    }

    /**
     * Called when where was an error during authentication attempt
     *
     * @param errorInfo Error associated with unsuccessful authentication
     */
    public void onAuthError(ErrorInfo errorInfo) {
        Log.i(TAG, String.format(Locale.ROOT, "onAuthError: (%d) %s", errorInfo.code, errorInfo.message));

        if(errorInfo.statusCode == 403) {
            ConnectionStateChange failedStateChange =
                new ConnectionStateChange(
                    connection.state,
                    ConnectionState.failed,
                    0,
                    errorInfo);

            this.connection.onConnectionStateChange(failedStateChange);
            return;
        }

        switch (currentState.state) {
            case connecting:
                ITransport transport = this.transport;
                if (transport != null)
                    /* request that the current transport is closed */
                    requestState(new StateIndication(ConnectionState.disconnected, errorInfo));
                break;

            case connected:
                /* stay connected but notify of authentication error */
                addAction(new UpdateAction(errorInfo));
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
                    if(reason == null) {
                        Log.e(TAG, "onMessage(): ERROR message received (no error detail)");
                    } else {
                        Log.e(TAG, "onMessage(): ERROR message received; message = " + reason.message + "; code = " + reason.code);
                    }

                    /* an error message may signify an error currentState in a channel, or in the connection */
                    if(message.channel != null) {
                        onChannelMessage(message);
                    } else {
                        onError(message);
                    }
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
                    addAction(new ReauthAction());
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
        channels.onMessage(message);
    }

    private synchronized void onConnected(ProtocolMessage message) {
        ErrorInfo error = message.error;

        if (message.action == ProtocolMessage.Action.connected && message.connectionId.equals(connection.id) && error == null) {
            //RTN15c6
            Log.d(TAG, "connection has reconnected and resumed successfully");
            connection.reason = null;
            channels.reAttach();
            requestState(new StateIndication(ConnectionState.connected, null));
        } else if (message.action == ProtocolMessage.Action.connected && !message.connectionId.equals(connection.id) && error != null) {
            //RTN15c7
            Log.d(TAG, "connection resume is invalid: " + error.message);
            connection.reason = error;
            msgSerial = 0;
            channels.reAttach();
            requestState(new StateIndication(ConnectionState.connected, error));
        } else if (!message.connectionId.equals(connection.id)) {
            Log.d(TAG, "connection resume failed: " + error.message);
            /* we need to suspend the original connection */
            error = REASON_SUSPENDED;
            channels.suspendAll(error, false);
            /* The connection id has changed. Reset the message serial and the
             * pending message queue (which fails the messages currently in
             * there). */
            pendingMessages.reset(msgSerial, new ErrorInfo("Connection resume failed", 500, 50000));
            msgSerial = 0;
        } else {
            Log.d(TAG, "connection has reconnected and resumed successfully");
        }

        connection.id = message.connectionId;
        ConnectionDetails connectionDetails = message.connectionDetails;
        /* Get any parameters from connectionDetails. */
        connection.key = connectionDetails.connectionKey; //RTN16d
        maxIdleInterval = connectionDetails.maxIdleInterval;
        connectionStateTtl = connectionDetails.connectionStateTtl;

        /* set the clientId resolved from token, if any */
        String clientId = connectionDetails.clientId;
        try {
            ably.auth.setClientId(clientId);
        } catch (AblyException e) {
            requestState(transport, new StateIndication(ConnectionState.failed, e.errorInfo));
            return;
        }

        /* indicated connected currentState */
        setSuspendTime();
        requestState(new StateIndication(ConnectionState.connected, error));
    }

    private synchronized void onDisconnected(ProtocolMessage message) {
        ErrorInfo reason = message.error;
        if(reason != null && isTokenError(reason)) {
            ably.auth.onAuthError(reason);
        }
        requestState(new StateIndication(ConnectionState.disconnected, reason));
    }

    private synchronized void onClosed(ProtocolMessage message) {
        if(message.error != null) {
            this.onError(message);
        } else {
            connection.key = null;
            requestState(new StateIndication(ConnectionState.closed, null));
        }
    }

    private synchronized void onError(ProtocolMessage message) {
        connection.key = null;
        ErrorInfo reason = message.error;
        if(isTokenError(reason)) {
            ably.auth.onAuthError(reason);
        }
        ConnectionState destinationState = isFatalError(reason) ? ConnectionState.failed : ConnectionState.disconnected;
        requestState(transport, new StateIndication(destinationState, reason));
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

    private synchronized void startup() {
        if(handlerThread == null) {
            (handlerThread = new Thread(new ActionHandler())).start();
            startConnectivityListener();
        }
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
            }
            return true;
        }
        return false;
    }

    private synchronized void setSuspendTime() {
        suspendTime = (System.currentTimeMillis() + connectionStateTtl);
    }

    /**
     * After a connection attempt failed, check to
     * see whether we should attempt to use a fallback.
     * @param reason
     * @return StateIndication if a fallback connection attempt is required, otherwise null
     */
    private StateIndication checkFallback(ErrorInfo reason) {
        if(pendingConnect != null && (reason == null || reason.statusCode >= 500)) {
            if (checkConnectivity()) {
                /* we will try a fallback host */
                String hostFallback = hosts.getFallback(pendingConnect.host);
                if (hostFallback != null) {
                    Log.v(TAG, "checkFallback: fallback to " + hostFallback);
                    return new StateIndication(ConnectionState.connecting, null, hostFallback, pendingConnect.host);
                }
            }
        }
        pendingConnect = null;
        return null;
    }

    private synchronized StateIndication checkSuspended(ErrorInfo reason) {
        long currentTime = System.currentTimeMillis();
        long timeToSuspend = suspendTime - currentTime;
        boolean suspendMode = timeToSuspend <= 0;
        Log.v(TAG, "checkSuspended: timeToSuspend = " + timeToSuspend + "ms; suspendMode = " + suspendMode);
        ConnectionState expiredState = suspendMode ? ConnectionState.suspended : ConnectionState.disconnected;
        return new StateIndication(expiredState, reason);
    }

    private void tryWait(long timeout) {
        try {
            if(timeout == 0) {
                wait();
            } else {
                wait(timeout);
            }
        } catch (InterruptedException e) {}
    }

    private void handleReauth() {
        if (currentState.state == ConnectionState.connected) {
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

            /* report connection currentState in UPDATE event */
            if (currentState.state == ConnectionState.connected) {
                connection.emitUpdate(errorInfo);
            }
        }
    }

    @Override
    public synchronized void onTransportAvailable(ITransport transport) {
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
    public synchronized void onTransportUnavailable(ITransport transport, ErrorInfo reason) {
        if (this.transport != transport) {
            /* This is from a transport that we have already abandoned. */
            Log.v(TAG, "onTransportUnavailable: ignoring disconnection event from superseded transport");
            return;
        }

        /* if this is a failure of a pending connection attempt, decide whether or not to attempt a fallback host */
        StateIndication fallbackAttempt = checkFallback(reason);
        if(fallbackAttempt != null) {
            requestState(fallbackAttempt);
            return;
        }

        StateIndication stateIndication = null;
        if(reason != null) {
            if(isFatalError(reason)) {
                Log.e(TAG, "onTransportUnavailable: unexpected transport error: " + reason.message);
                stateIndication = new StateIndication(ConnectionState.failed, reason);
            } else if(isTokenError(reason)) {
                ably.auth.onAuthError(reason);
            }
        }
        if(stateIndication == null) {
            stateIndication = checkSuspended(reason);
        }
        addAction(new SynchronousStateChangeAction(transport, stateIndication));
    }

    private class ConnectParams extends TransportParams {
        ConnectParams(ClientOptions options, PlatformAgentProvider platformAgentProvider) {
            super(options, platformAgentProvider);
            this.connectionKey = connection.key;
            this.port = Defaults.getPort(options);
        }
    }

    private void connectImpl(StateIndication request) {
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
        pendingConnect = new ConnectParams(ably.options, platformAgentProvider);
        pendingConnect.host = host;
        lastUsedHost = host;

        /* try the connection */
        ITransport transport;
        try {
            transport = transportFactory.getTransport(pendingConnect, this);
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
        if (oldTransport != null) {
            oldTransport.close();
        }
        transport.connect(this);
        if(protocolListener != null) {
            protocolListener.onRawConnectRequested(transport.getURL());
        }
    }

    /**
     * Close any existing transport
     * @return closed if true, otherwise awaiting closed indication
     */
    private boolean closeImpl() {
        if(transport == null) {
            return true;
        }

        /* if connected, send an explicit close message and await response */
        boolean isConnected = currentState.state == ConnectionState.connected;
        if(isConnected) {
            try {
                Log.v(TAG, "Requesting connection close");
                transport.send(new ProtocolMessage(ProtocolMessage.Action.close));
                return false;
            } catch (AblyException e) {
                /* we're closing, and the attempt to send the CLOSE message failed;
                 * continue, because we're not going to reinstate the transport
                 * just to send a CLOSE message */
            }
        }

        /* just close the transport */
        Log.v(TAG, "Closing incomplete transport");
        clearTransport();
        return true;
    }

    private void clearTransport() {
        if(transport != null) {
            transport.close();
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
        public final CompletionListener listener;
        public QueuedMessage(ProtocolMessage msg, CompletionListener listener) {
            this.msg = msg;
            this.listener = listener;
        }
    }

    public void send(ProtocolMessage msg, boolean queueEvents, CompletionListener listener) throws AblyException {
        State state;
        synchronized(this) {
            state = this.currentState;
            if(state.sendEvents) {
                sendImpl(msg, listener);
                return;
            }
            if(state.queueEvents && queueEvents) {
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

    /**
     * A class containing a queue of messages awaiting acknowledgement
     */
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
         * connection, and thus one more than the highest message serial
         * in the queue.
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
            ConnectionManager cm = ConnectionManager.this;
            ConnectionState currentState = cm.getConnectionState().state;
            Log.i(TAG, "onNetworkAvailable(): currentState = " + currentState.name());
            if(currentState == ConnectionState.disconnected || currentState == ConnectionState.suspended) {
                Log.i(TAG, "onNetworkAvailable(): initiating reconnect");
                cm.connect();
            }
        }

        @Override
        public void onNetworkUnavailable(ErrorInfo reason) {
            ConnectionManager cm = ConnectionManager.this;
            ConnectionState currentState = cm.getConnectionState().state;
            Log.i(TAG, "onNetworkUnavailable(); currentState = " + currentState.name() + "; reason = " + reason.toString());
            if(currentState == ConnectionState.connected || currentState == ConnectionState.connecting) {
                Log.i(TAG, "onNetworkUnavailable(): closing connected transport");
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
        if(transport != null) {
            transport.close();
        }
        suppressRetry = true;
    }

    /*******************
     * misc error handling
     ******************/

    private boolean isTokenError(ErrorInfo err) {
        return ((err.code >= 40140) && (err.code < 40150)) || (err.code == 80019 && err.statusCode == 401);
    }

    private boolean isFatalError(ErrorInfo err) {
        if(err.code != 0) {
            /* token errors are assumed to be recoverable */
            if(isTokenError(err)) { return false; }
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

    final AblyRealtimeBase ably;
    private final Channels channels;
    private final Connection connection;
    private final ITransport.Factory transportFactory;
    private final List<QueuedMessage> queuedMessages = new ArrayList<>();
    private final PendingMessageQueue pendingMessages = new PendingMessageQueue();
    private final HashSet<Object> heartbeatWaiters = new HashSet<Object>();
    private final ActionQueue actionQueue = new ActionQueue();
    private final Hosts hosts;
    private final PlatformAgentProvider platformAgentProvider;

    private Thread handlerThread;
    private final Map<ConnectionState, State> states = new HashMap<>();
    private State currentState;
    private ErrorInfo stateError;
    private ConnectParams pendingConnect;
    private boolean suppressRetry; /* for tests only; modified via reflection */
    private ITransport transport;
    private long suspendTime;
    public long msgSerial;
    private long lastActivity;
    private CMConnectivityListener connectivityListener;
    private long connectionStateTtl = Defaults.connectionStateTtl;
    long maxIdleInterval = Defaults.maxIdleInterval;

    /* for debug/test only */
    private final RawProtocolListener protocolListener;
    private String lastUsedHost;

    private static final long HEARTBEAT_TIMEOUT = 5000L;
}
