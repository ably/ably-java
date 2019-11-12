package io.ably.lib.realtime;

import io.ably.lib.http.BasePaginatedQuery;
import io.ably.lib.http.HttpCore;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.transport.ConnectionManager;
import io.ably.lib.transport.ConnectionManager.QueuedMessage;
import io.ably.lib.transport.Defaults;
import io.ably.lib.types.*;
import io.ably.lib.types.ProtocolMessage.Action;
import io.ably.lib.types.ProtocolMessage.Flag;
import io.ably.lib.util.EventEmitter;
import io.ably.lib.util.Log;

import java.util.*;


/**
 * A class representing a Channel belonging to this application.
 * The Channel instance allows messages to be published and
 * received, and controls the lifecycle of this instance's
 * attachment to the channel.
 *
 */
public abstract class ChannelBase extends EventEmitter<ChannelEvent, ChannelStateListener> {

	/************************************
	 * ChannelState and state management
	 ************************************/

	/**
	 * The name of this channel.
	 */
	public final String name;

	/**
	 * The {@link Presence} object for this channel. This controls this client's
	 * presence on the channel and may also be used to obtain presence information
	 * and change events for other members of the channel.
	 */
	public final Presence presence;

	/**
	 * The current channel state.
	 */
	public ChannelState state;

	/**
	 * Error information associated with a failed channel state.
	 */
	public ErrorInfo reason;

	/**
	 * Properties of Channel
	 */
	public ChannelProperties properties = new ChannelProperties();

	/***
	 * internal
	 *
	 */
	private void setState(ChannelState newState, ErrorInfo reason) {
		setState(newState, reason, false, true);
	}
	private void setState(ChannelState newState, ErrorInfo reason, boolean resumed) {
		setState(newState, reason, resumed, true);
	}
	private void setState(ChannelState newState, ErrorInfo reason, boolean resumed, boolean notifyStateChange) {
		Log.v(TAG, "setState(): channel = " + name + "; setting " + newState);
		ChannelStateListener.ChannelStateChange stateChange;
		synchronized(this) {
			stateChange = new ChannelStateListener.ChannelStateChange(newState, this.state, reason, resumed);
			this.state = stateChange.current;
			this.reason = stateChange.reason;
		}

		if(notifyStateChange) {
			/* broadcast state change */
			emit(newState, stateChange);
		}
	}

	/************************************
	 * attach / detach
	 ************************************/

	/**
	 * Attach to this channel.
	 * This call initiates the attach request, and the response
	 * is indicated asynchronously in the resulting state change.
	 * attach() is called implicitly when publishing or subscribing
	 * on this channel, so it is not usually necessary for a client
	 * to call attach() explicitly.
	 * @throws AblyException
	 */
	public void attach() throws AblyException {
		attach(null);
	}

	/**
	 * Attach to this channel.
	 * This call initiates the attach request, and the response
	 * is indicated asynchronously in the resulting state change.
	 * attach() is called implicitly when publishing or subscribing
	 * on this channel, so it is not usually necessary for a client
	 * to call attach() explicitly.
	 *
	 * @param listener When the channel is attached successfully or the attach fails and
	 * the ErrorInfo error is passed as an argument to the callback
	 * @throws AblyException
	 */
	public void attach(CompletionListener listener) throws  AblyException {
		this.attach(false, listener);
	}

	private void attach(boolean forceReattach, CompletionListener listener) {
		clearAttachTimers();
		attachWithTimeout(forceReattach, listener);
	}

	private void attachImpl(final boolean forceReattach, final CompletionListener listener) throws AblyException {
		Log.v(TAG, "attach(); channel = " + name);
		if(!forceReattach) {
			/* check preconditions */
			switch(state) {
				case attaching:
					if(listener != null) {
						on(new ChannelStateCompletionListener(listener, ChannelState.attached, ChannelState.failed));
					}
					return;
				case attached:
					callCompletionListenerSuccess(listener);
					return;
				default:
			}
		}
		ConnectionManager connectionManager = ably.connection.connectionManager;
		if(!connectionManager.isActive()) {
			throw AblyException.fromErrorInfo(connectionManager.getStateErrorInfo());
		}

		/* send attach request and pending state */
		Log.v(TAG, "attach(); channel = " + name + "; sending ATTACH request");
		ProtocolMessage attachMessage = new ProtocolMessage(Action.attach, this.name);
		if(this.options != null) {
			if(!this.options.params.isEmpty()) {
				attachMessage.params = this.options.params;
			}
			if(!this.options.modes.isEmpty()) {
				attachMessage.encodeModesToFlags(this.options.modes);
			}
		}
		if(this.decodeFailureRecoveryInProgress) {
			attachMessage.channelSerial = this.lastPayloadProtocolMessageChannelSerial;
		}
		try {
			if (listener != null) {
				on(new ChannelStateCompletionListener(listener, ChannelState.attached, ChannelState.failed));
			}

			setState(ChannelState.attaching, null);
			connectionManager.send(attachMessage, true, null);
		} catch(AblyException e) {
			throw e;
		}
	}

	/**
	 * Detach from this channel.
	 * This call initiates the detach request, and the response
	 * is indicated asynchronously in the resulting state change.
	 * @throws AblyException
	 */
	public void detach() throws AblyException {
		detach(null);
	}

	/**
	 * Detach from this channel.
	 * This call initiates the detach request, and the response
	 * is indicated asynchronously in the resulting state change.
	 * @throws AblyException
	 */
	public void detach(CompletionListener listener) throws AblyException {
		clearAttachTimers();
		detachWithTimeout(listener);
	}

	private void detachImpl(CompletionListener listener) throws AblyException {
		Log.v(TAG, "detach(); channel = " + name);
		/* check preconditions */
		switch(state) {
			case initialized:
			case detached: {
				callCompletionListenerSuccess(listener);
				return;
			}
			case detaching:
				if (listener != null) {
					on(new ChannelStateCompletionListener(listener, ChannelState.detached, ChannelState.failed));
				}
				return;
			default:
		}
		ConnectionManager connectionManager = ably.connection.connectionManager;
		if(!connectionManager.isActive())
			throw AblyException.fromErrorInfo(connectionManager.getStateErrorInfo());

		/* send detach request */
		ProtocolMessage detachMessage = new ProtocolMessage(Action.detach, this.name);
		try {
			if (listener != null) {
				on(new ChannelStateCompletionListener(listener, ChannelState.detached, ChannelState.failed));
			}

			setState(ChannelState.detaching, null);
			connectionManager.send(detachMessage, true, null);
		} catch(AblyException e) {
			throw e;
		}
	}

	public void sync() throws AblyException {
		Log.v(TAG, "sync(); channel = " + name);
		/* check preconditions */
		switch(state) {
			case initialized:
			case detaching:
			case detached:
				throw AblyException.fromErrorInfo(new ErrorInfo("Unable to sync to channel; not attached", 40000));
			default:
		}
		ConnectionManager connectionManager = ably.connection.connectionManager;
		if(!connectionManager.isActive())
			throw AblyException.fromErrorInfo(connectionManager.getStateErrorInfo());

		/* send sync request */
		ProtocolMessage syncMessage = new ProtocolMessage(Action.sync, this.name);
		syncMessage.channelSerial = syncChannelSerial;
		connectionManager.send(syncMessage, true, null);
	}

	/***
	 * internal
	 *
	 */
	private static void callCompletionListenerSuccess(CompletionListener listener) {
		if(listener != null) {
			try {
				listener.onSuccess();
			} catch(Throwable t) {
				Log.e(TAG, "Unexpected exception calling CompletionListener", t);
			}
		}
	}

	private static void callCompletionListenerError(CompletionListener listener, ErrorInfo err) {
		if(listener != null) {
			try {
				listener.onError(err);
			} catch(Throwable t) {
				Log.e(TAG, "Unexpected exception calling CompletionListener", t);
			}
		}
	}

	private void setAttached(ProtocolMessage message) {
		clearAttachTimers();
		boolean resumed = message.hasFlag(Flag.resumed);
		Log.v(TAG, "setAttached(); channel = " + name + ", resumed = " + resumed);
		properties.attachSerial = message.channelSerial;
		params = message.params;
		modes = message.decodeModesFromFlags();
		if(state == ChannelState.attached) {
			Log.v(TAG, String.format("Server initiated attach for channel %s", name));
			/* emit UPDATE event according to RTL12 */
			emitUpdate(null, resumed);
		} else {
			setState(ChannelState.attached, message.error, resumed);
			sendQueuedMessages();
			presence.setAttached(message.hasFlag(Flag.has_presence));
		}
	}

	private void setDetached(ErrorInfo reason) {
		clearAttachTimers();
		Log.v(TAG, "setDetached(); channel = " + name);
		presence.setDetached(reason);
		setState(ChannelState.detached, reason);
		failQueuedMessages(reason);
	}

	private void setFailed(ErrorInfo reason) {
		clearAttachTimers();
		Log.v(TAG, "setFailed(); channel = " + name);
		presence.setDetached(reason);
		setState(ChannelState.failed, reason);
		failQueuedMessages(reason);
	}

	/* Timer for attach operation */
	private Timer attachTimer;

	/* Timer for reattaching if attach failed */
	private Timer reattachTimer;

	/**
	 * Cancel attach/reattach timers
	 */
	synchronized private void clearAttachTimers() {
		Timer[] timers = new Timer[]{attachTimer, reattachTimer};
		attachTimer = reattachTimer = null;
		for (Timer t: timers) {
			if (t != null) {
				t.cancel();
				t.purge();
			}
		}
	}

	private void attachWithTimeout(final CompletionListener listener) throws AblyException {
		this.attachWithTimeout(false, listener);
	}

	/**
	 * Attach channel, if not attached within timeout set state to suspended and
	 * set up timer to reattach it later
	 */
	synchronized private void attachWithTimeout(final boolean forceReattach, final CompletionListener listener) {
		Timer currentAttachTimer;
		try {
			currentAttachTimer = new Timer();
		} catch(Throwable t) {
			/* an exception instancing the timer can arise because the runtime is exiting */
			callCompletionListenerError(listener, ErrorInfo.fromThrowable(t));
			return;
		}
		attachTimer = currentAttachTimer;

		try {
			attachImpl(forceReattach, new CompletionListener() {
				@Override
				public void onSuccess() {
					clearAttachTimers();
					callCompletionListenerSuccess(listener);
				}

				@Override
				public void onError(ErrorInfo reason) {
					clearAttachTimers();
					callCompletionListenerError(listener, reason);
				}
			});
		} catch(AblyException e) {
			attachTimer = null;
			callCompletionListenerError(listener, e.errorInfo);
		}

		if(attachTimer == null) {
			/* operation has already succeeded or failed, no need to set the timer */
			return;
		}

		final Timer inProgressTimer = currentAttachTimer;
		attachTimer.schedule(
				new TimerTask() {
					@Override
					public void run() {
						String errorMessage = String.format("Attach timed out for channel %s", name);
						Log.v(TAG, errorMessage);
						synchronized (ChannelBase.this) {
							if(attachTimer != inProgressTimer) {
								return;
							}
							attachTimer = null;
							if(state == ChannelState.attaching) {
								setSuspended(new ErrorInfo(errorMessage, 91200), true);
								reattachAfterTimeout();
							}
						}
					}
				}, Defaults.realtimeRequestTimeout);
	}

	/**
	 * Must be called in suspended state. Wait for timeout specified in clientOptions, and then
	 * try to attach the channel
	 */
	synchronized private void reattachAfterTimeout() {
		Timer currentReattachTimer;
		try {
			currentReattachTimer = new Timer();
		} catch(Throwable t) {
			/* an exception instancing the timer can arise because the runtime is exiting */
			return;
		}
		reattachTimer = currentReattachTimer;

		final Timer inProgressTimer = currentReattachTimer;
		reattachTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				synchronized (ChannelBase.this) {
					if (inProgressTimer != reattachTimer) {
						return;
					}
					reattachTimer = null;
					if (state == ChannelState.suspended) {
						try {
							attachWithTimeout(null);
						} catch (AblyException e) {
							Log.e(TAG, "Reattach channel failed; channel = " + name, e);
						}
					}
				}
			}
		}, ably.options.channelRetryTimeout);
	}

	/**
	 * Try to detach the channel. If the server doesn't confirm the detach operation within realtime
	 * request timeout return channel to previous state
	 */
	synchronized private void detachWithTimeout(final CompletionListener listener) {
		final ChannelState originalState = state;
		Timer currentDetachTimer;
		try {
			currentDetachTimer = new Timer();
		} catch(Throwable t) {
			/* an exception instancing the timer can arise because the runtime is exiting */
			callCompletionListenerError(listener, ErrorInfo.fromThrowable(t));
			return;
		}
		attachTimer = currentDetachTimer;

		try {
			detachImpl(new CompletionListener() {
				@Override
				public void onSuccess() {
					clearAttachTimers();
					callCompletionListenerSuccess(listener);
				}

				@Override
				public void onError(ErrorInfo reason) {
					clearAttachTimers();
					callCompletionListenerError(listener, reason);
				}
			});
		} catch (AblyException e) {
			attachTimer = null;
		}

		if(attachTimer == null) {
			/* operation has already succeeded or failed, no need to set the timer */
			return;
		}

		final Timer inProgressTimer = currentDetachTimer;
		attachTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				synchronized (ChannelBase.this) {
					if (inProgressTimer != attachTimer) {
						return;
					}
					attachTimer = null;
					if (state == ChannelState.detaching) {
						ErrorInfo reason = new ErrorInfo("Detach operation timed out", 90007);
						callCompletionListenerError(listener, reason);
						setState(originalState, reason);
					}
				}
			}
		}, Defaults.realtimeRequestTimeout);
	}

	/* State changes provoked by ConnectionManager state changes. */

	public void setConnected() {
		if(state == ChannelState.attached) {
			try {
				sync();
			} catch (AblyException e) {
				Log.e(TAG, "setConnected(): Unable to sync; channel = " + name, e);
			}
		} else if (state == ChannelState.suspended) {
			/* (RTL3d) If the connection state enters the CONNECTED state, then
			 * a SUSPENDED channel will initiate an attach operation. If the
			 * attach operation for the channel times out and the channel
			 * returns to the SUSPENDED state (see #RTL4f)
			 */
			try {
				attachWithTimeout(null);
			} catch (AblyException e) {
				Log.e(TAG, "setConnected(): Unable to initiate attach; channel = " + name, e);
			}
		}
	}

	/** If the connection state enters the FAILED state, then an ATTACHING
	 * or ATTACHED channel state will transition to FAILED and set the
	 * Channel#errorReason
	 */
	public void setConnectionFailed(ErrorInfo reason) {
		clearAttachTimers();
		if (state == ChannelState.attached || state == ChannelState.attaching)
			setFailed(reason);
	}

	/** (RTL3b) If the connection state enters the CLOSED state, then an
	 * ATTACHING or ATTACHED channel state will transition to DETACHED. */
	public void setConnectionClosed(ErrorInfo reason) {
		clearAttachTimers();
		if (state == ChannelState.attached || state == ChannelState.attaching)
			setDetached(reason);
	}

	/** (RTL3c) If the connection state enters the SUSPENDED state, then an
	 * ATTACHING or ATTACHED channel state will transition to SUSPENDED.
	 * (RTN15c3) The client library should initiate an attach for channels
	 *  that are in the SUSPENDED state. For all channels in the ATTACHING
	 *  or ATTACHED state, the client library should fail any previously queued
	 *  messages for that channel and initiate a new attach.
	 * This also gets called when a connection enters CONNECTED but with a
	 * non-fatal error for a failed reconnect (RTN16e). */
	public synchronized void setSuspended(ErrorInfo reason, boolean notifyStateChange) {
		clearAttachTimers();
		if (state == ChannelState.attached || state == ChannelState.attaching) {
			Log.v(TAG, "setSuspended(); channel = " + name);
			presence.setSuspended(reason);
			setState(ChannelState.suspended, reason, false, notifyStateChange);
			failQueuedMessages(reason);
		}
	}

	@Override
	protected void apply(ChannelStateListener listener, ChannelEvent event, Object... args) {
		try {
			listener.onChannelStateChanged((ChannelStateListener.ChannelStateChange)args[0]);
		} catch (Throwable t) {
			Log.e(TAG, "Unexpected exception calling ChannelStateListener", t);
		}
	}

	static ErrorInfo REASON_NOT_ATTACHED = new ErrorInfo("Channel not attached", 400, 90001);

	/************************************
	 * subscriptions and MessageListener
	 ************************************/

	/**
	 * An interface whereby a client maybe notified of messages changes on a channel.
	 */
	public interface MessageListener {
		void onMessage(Message messages);
	}

	/**
	 * <p>
	 * Unsubscribe all subscribed listeners from this channel.
	 * </p>
	 * <p>
	 * Spec: RTL8a
	 * </p>
	 */
	public synchronized void unsubscribe() {
		Log.v(TAG, "unsubscribe(); channel = " + this.name);
		listeners.clear();
		eventListeners.clear();
	}

	/**
	 * Subscribe for messages on this channel. This implicitly attaches the channel if
	 * not already attached.
	 * @param listener: the MessageListener
	 * @throws AblyException
	 */
	public synchronized void subscribe(MessageListener listener) throws AblyException {
		Log.v(TAG, "subscribe(); channel = " + this.name);
		listeners.add(listener);
		attach();
	}

	/**
	 * Unsubscribe a previously subscribed listener from this channel.
	 * @param listener: the previously subscribed listener.
	 */
	public synchronized void unsubscribe(MessageListener listener) {
		Log.v(TAG, "unsubscribe(); channel = " + this.name);
		listeners.remove(listener);
		for (MessageMulticaster multicaster: eventListeners.values()) {
			multicaster.remove(listener);
		}
	}

	/**
	 * Subscribe for messages with a specific event name on this channel.
	 * This implicitly attaches the channel if not already attached.
	 * @param name: the event name
	 * @param listener: the MessageListener
	 * @throws AblyException
	 */
	public synchronized void subscribe(String name, MessageListener listener) throws AblyException {
		Log.v(TAG, "subscribe(); channel = " + this.name + "; event = " + name);
		subscribeImpl(name, listener);
		attach();
	}

	/**
	 * Unsubscribe a previously subscribed event listener from this channel.
	 * @param name: the event name
	 * @param listener: the previously subscribed listener.
	 */
	public synchronized void unsubscribe(String name, MessageListener listener) {
		Log.v(TAG, "unsubscribe(); channel = " + this.name + "; event = " + name);
		unsubscribeImpl(name, listener);
	}

	/**
	 * Subscribe for messages with an array of event names on this channel.
	 * This implicitly attaches the channel if not already attached.
	 * @param names: the event names
	 * @param listener: the MessageListener
	 * @throws AblyException
	 */
	public synchronized void subscribe(String[] names, MessageListener listener) throws AblyException {
		Log.v(TAG, "subscribe(); channel = " + this.name + "; (multiple events)");
		for(String name : names)
			subscribeImpl(name, listener);
		attach();
	}

	/**
	 * Unsubscribe a previously subscribed event listener from this channel.
	 * @param names: the event names
	 * @param listener: the previously subscribed listener.
	 */
	public synchronized void unsubscribe(String[] names, MessageListener listener) {
		Log.v(TAG, "unsubscribe(); channel = " + this.name + "; (multiple events)");
		for(String name : names)
			unsubscribeImpl(name, listener);
	}

	/***
	 * internal
	 *
	 */
	private void onMessage(ProtocolMessage message) {
		Log.v(TAG, "onMessage(); channel = " + name);
		Message[] messages = message.messages;
		Message firstMessage = messages[0];
		Message lastMessage = messages[messages.length - 1];

		if (firstMessage.extras != null && firstMessage.extras.delta != null && !firstMessage.extras.delta.from.equals(this.lastPayloadMessageId)) {
			Log.w(TAG, "Delta message decode failure - previous message not available.");
			this.startDecodeFailureRecovery();
			return;
		}

		for(int i = 0; i < messages.length; i++) {
			Message msg = messages[i];
			try {
				msg.decode(encodingDecodingContext);
			} catch (MessageDecodeException e) {
				Log.e(TAG, String.format("%s on channel %s", e.errorInfo.message, name));
				if (e.errorInfo.code == 40018) {
					this.startDecodeFailureRecovery();
					return;
				}
			}
			/* populate fields derived from protocol message */
			if(msg.connectionId == null) msg.connectionId = message.connectionId;
			if(msg.timestamp == 0) msg.timestamp = message.timestamp;
			if(msg.id == null) msg.id = message.id + ':' + i;
			/* broadcast */
			MessageMulticaster listeners = eventListeners.get(msg.name);
			if(listeners != null)
				listeners.onMessage(msg);
		}

		this.lastPayloadMessageId = lastMessage.id;
		this.lastPayloadProtocolMessageChannelSerial = message.channelSerial;

		for (Message msg : message.messages) {
			this.listeners.onMessage(msg);
		}
	}

	private void startDecodeFailureRecovery() {
		if (this.decodeFailureRecoveryInProgress) {
			return;
		}
		Log.w(TAG, "Starting decode failure recovery process");
		this.decodeFailureRecoveryInProgress = true;
		this.attach(true, new CompletionListener() {
			@Override
			public void onSuccess() {
				decodeFailureRecoveryInProgress = false;
			}

			@Override
			public void onError(ErrorInfo reason) {
				decodeFailureRecoveryInProgress = false;
			}
		});
	}

	private void onPresence(ProtocolMessage message, String syncChannelSerial) {
		Log.v(TAG, "onPresence(); channel = " + name + "; syncChannelSerial = " + syncChannelSerial);
		PresenceMessage[] messages = message.presence;
		for(int i = 0; i < messages.length; i++) {
			PresenceMessage msg = messages[i];
			try {
				msg.decode(options);
			} catch (MessageDecodeException e) {
				Log.e(TAG, String.format("%s on channel %s", e.errorInfo.message, name));
			}
			/* populate fields derived from protocol message */
			if(msg.connectionId == null) msg.connectionId = message.connectionId;
			if(msg.timestamp == 0) msg.timestamp = message.timestamp;
			if(msg.id == null) msg.id = message.id + ':' + i;
		}
		presence.setPresence(messages, true, syncChannelSerial);
	}

	private void onSync(ProtocolMessage message) {
		Log.v(TAG, "onSync(); channel = " + name);
		if(message.presence != null)
			onPresence(message, (syncChannelSerial = message.channelSerial));
	}

	private MessageMulticaster listeners = new MessageMulticaster();
	private HashMap<String, MessageMulticaster> eventListeners = new HashMap<String, MessageMulticaster>();

	private static class MessageMulticaster extends io.ably.lib.util.Multicaster<MessageListener> implements MessageListener {
		@Override
		public void onMessage(Message message) {
			for(MessageListener member : members)
				try {
					member.onMessage(message);
				} catch (Throwable t) {
					Log.e(TAG, "Unexpected exception calling listener", t);
				}
		}
	}

	private void subscribeImpl(String name, MessageListener listener) throws AblyException {
		MessageMulticaster listeners = eventListeners.get(name);
		if(listeners == null) {
			listeners = new MessageMulticaster();
			eventListeners.put(name, listeners);
		}
		listeners.add(listener);
	}

	private void unsubscribeImpl(String name, MessageListener listener) {
		MessageMulticaster listeners = eventListeners.get(name);
		if(listeners != null) {
			listeners.remove(listener);
			if(listeners.isEmpty())
				eventListeners.remove(name);
		}
	}

	/************************************
	 * publish and pending messages
	 ************************************/

	/**
	 * Publish a message on this channel. This implicitly attaches the channel if
	 * not already attached.
	 * @param name: the event name
	 * @param data: the message payload. See {@link io.ably.types.Data} for supported datatypes
	 * @throws AblyException
	 */
	public void publish(String name, Object data) throws AblyException {
		publish(name, data, null);
	}

	/**
	 * Publish a message on this channel. This implicitly attaches the channel if
	 * not already attached.
	 * @param message: the message
	 * @throws AblyException
	 */
	public void publish(Message message) throws AblyException {
		publish(message, null);
	}

	/**
	 * Publish an array of messages on this channel. This implicitly attaches the channel if
	 * not already attached.
	 * @param messages: the message
	 * @throws AblyException
	 */
	public void publish(Message[] messages) throws AblyException {
		publish(messages, null);
	}

	/**
	 * Publish a message on this channel. This implicitly attaches the channel if
	 * not already attached.
	 * @param name: the event name
	 * @param data: the message payload. See {@link io.ably.types.Data} for supported datatypes
	 * @param listener: a listener to be notified of the outcome of this message.
	 * @throws AblyException
	 */
	public void publish(String name, Object data, CompletionListener listener) throws AblyException {
		Log.v(TAG, "publish(String, Object); channel = " + this.name + "; event = " + name);
		publish(new Message[] {new Message(name, data)}, listener);
	}

	/**
	 * Publish a message on this channel. This implicitly attaches the channel if
	 * not already attached.
	 * @param message: the message
	 * @param listener: a listener to be notified of the outcome of this message.
	 * @throws AblyException
	 */
	public void publish(Message message, CompletionListener listener) throws AblyException {
		Log.v(TAG, "publish(Message); channel = " + this.name + "; event = " + message.name);
		publish(new Message[] {message}, listener);
	}

	/**
	 * Publish an array of messages on this channel. This implicitly attaches the channel if
	 * not already attached.
	 * @param messages: the message
	 * @param listener: a listener to be notified of the outcome of this message.
	 * @throws AblyException
	 */
	public synchronized void publish(Message[] messages, CompletionListener listener) throws AblyException {
		Log.v(TAG, "publish(Message[]); channel = " + this.name);
		ConnectionManager connectionManager = ably.connection.connectionManager;
		ConnectionManager.StateInfo connectionState = connectionManager.getConnectionState();
		boolean queueMessages = ably.options.queueMessages;
		if(!connectionManager.isActive() || (connectionState.queueEvents && !queueMessages)) {
			throw AblyException.fromErrorInfo(connectionState.defaultErrorInfo);
		}
		boolean connected = (connectionState.sendEvents);
		try {
			for(Message message : messages) {
				/* RTL6g3: check validity of any clientId;
				 * RTL6g4: be lenient with a null clientId if we're not connected */
				ably.auth.checkClientId(message, true, connected);
				message.encode(options);
			}
		} catch(AblyException e) {
			callCompletionListenerError(listener, e.errorInfo);
			return;
		}
		ProtocolMessage msg = new ProtocolMessage(Action.message, this.name);
		msg.messages = messages;
		switch(state) {
		case failed:
		case suspended:
			throw AblyException.fromErrorInfo(new ErrorInfo("Unable to publish in failed or suspended state", 400, 40000));
		default:
			connectionManager.send(msg, queueMessages, listener);
		}
	}

	/***
	 * internal
	 *
	 */

	private static class FailedMessage {
		QueuedMessage msg;
		ErrorInfo reason;
		FailedMessage(QueuedMessage msg, ErrorInfo reason) {
			this.msg = msg;
			this.reason = reason;
		}
	}

	private void sendQueuedMessages() {
		Log.v(TAG, "sendQueuedMessages()");
		ArrayList<FailedMessage> failedMessages = new ArrayList<>();
		synchronized (this) {
			boolean queueMessages = ably.options.queueMessages;
			ConnectionManager connectionManager = ably.connection.connectionManager;
			for (QueuedMessage msg : queuedMessages)
				try {
					connectionManager.send(msg.msg, queueMessages, msg.listener);
				} catch (AblyException e) {
					Log.e(TAG, "sendQueuedMessages(): Unexpected exception sending message", e);
					if (msg.listener != null)
						failedMessages.add(new FailedMessage(msg, e.errorInfo));
				}
			queuedMessages.clear();
		}

		/* Call completion callbacks for failed messages without holding the lock */
		for (FailedMessage failed: failedMessages) {
			callCompletionListenerError(failed.msg.listener, failed.reason);
		}
	}

	private void failQueuedMessages(ErrorInfo reason) {
		Log.v(TAG, "failQueuedMessages()");

		ArrayList<FailedMessage> failedMessages = new ArrayList<>();
		synchronized (this) {
			for (QueuedMessage msg: queuedMessages) {
				if (msg.listener != null)
					failedMessages.add(new FailedMessage(msg, reason));
			}
			queuedMessages.clear();
		}

		for(FailedMessage failed : failedMessages) {
			callCompletionListenerError(failed.msg.listener, failed.reason);
		}
	}

	static Param[] replacePlaceholderParams(Channel channel, Param[] placeholderParams) throws AblyException {
		if (placeholderParams == null) {
			return null;
		}

		HashSet<Param> params = new HashSet<>();

		Param param;
		for(int i = 0; i < placeholderParams.length; i++) {
			param = placeholderParams[i];

			if(KEY_UNTIL_ATTACH.equals(param.key)) {
				if("true".equalsIgnoreCase(param.value)) {
					if (channel.state != ChannelState.attached) {
						throw AblyException.fromErrorInfo(new ErrorInfo("option untilAttach requires the channel to be attached", 40000, 400));
					}

					params.add(new Param(KEY_FROM_SERIAL, channel.properties.attachSerial));
				}
				else if(!"false".equalsIgnoreCase(param.value)) {
					throw AblyException.fromErrorInfo(new ErrorInfo("option untilAttach is invalid. \"true\" or \"false\" expected", 40000, 400));
				}
			}
			else {
				/* Add non-placeholder param as is */
				params.add(param);
			}
		}

		return params.toArray(new Param[params.size()]);
	}


	private static final String KEY_UNTIL_ATTACH = "untilAttach";
	private static final String KEY_FROM_SERIAL = "fromSerial";
	private List<QueuedMessage> queuedMessages;

	/************************************
	 * Channel history
	 ************************************/

	/**
	 * Obtain recent history for this channel using the REST API.
	 * The history provided relqtes to all clients of this application,
	 * not just this instance.
	 * @param params: the request params. See the Ably REST API
	 * documentation for more details.
	 * @return: an array of Messgaes for this Channel.
	 * @throws AblyException
	 */
	public PaginatedResult<Message> history(Param[] params) throws AblyException {
		return historyImpl(params).sync();
	}

	public void historyAsync(Param[] params, Callback<AsyncPaginatedResult<Message>> callback) {
		historyImpl(params).async(callback);
	}

	private BasePaginatedQuery.ResultRequest<Message> historyImpl(Param[] params) {
		try {
			params = replacePlaceholderParams((Channel) this, params);
		} catch (AblyException e) {
			return new BasePaginatedQuery.ResultRequest.Failed<Message>(e);
		}

		HttpCore.BodyHandler<Message> bodyHandler = MessageSerializer.getMessageResponseHandler(options);
		return new BasePaginatedQuery<Message>(ably.http, basePath + "/history", HttpUtils.defaultAcceptHeaders(ably.options.useBinaryProtocol), params, bodyHandler).get();
	}

	/************************************
	 * Channel options
	 ************************************/

	public void setOptions(ChannelOptions options) throws AblyException {
		this.setOptions(options, null);
	}

	public void setOptions(ChannelOptions options, CompletionListener listener) throws AblyException {
		this.options = options;
		if(this.shouldReattachToSetOptions(options)) {
			this.attach(true, listener);
		} else {
			callCompletionListenerSuccess(listener);
		}
	}

	boolean shouldReattachToSetOptions(ChannelOptions options) {
		/* TODO: Check if the new options are different than the old ones */
		return
			(this.state == ChannelState.attached || this.state == ChannelState.attaching) &&
			(!options.modes.isEmpty() || !options.params.isEmpty());
	}

	public ChannelParams getParams() {
		return this.params;
	}

	public ChannelModes getModes() {
		return this.modes;
	}

	/************************************
	 * internal general
	 * @throws AblyException
	 ************************************/

	private class ChannelStateCompletionListener implements ChannelStateListener {
		private CompletionListener completionListener;
		private final ChannelState successState;
		private final ChannelState failureState;

		public ChannelStateCompletionListener(CompletionListener completionListener, ChannelState successState, ChannelState failureState) {
			this.completionListener = completionListener;
			this.successState = successState;
			this.failureState = failureState;
		}

		@Override
		public void onChannelStateChanged(ChannelStateListener.ChannelStateChange stateChange) {
			if(stateChange.current.equals(successState)) {
				ChannelBase.this.off(this);
				completionListener.onSuccess();
			}
			else if(stateChange.current.equals(failureState)) {
				ChannelBase.this.off(this);
				completionListener.onError(reason);
			}
		}
	}

	ChannelBase(AblyRealtime ably, String name, ChannelOptions options) throws AblyException {
		Log.v(TAG, "RealtimeChannel(); channel = " + name);
		this.ably = ably;
		this.name = name;
		this.basePath = "/channels/" + HttpUtils.encodeURIComponent(name);
		this.setOptions(options);
		this.presence = new Presence((Channel) this);
		state = ChannelState.initialized;
		queuedMessages = new ArrayList<QueuedMessage>();
		encodingDecodingContext = new InternalEncodingDecodingContext(options, ably.options.Codecs);
	}

	void onChannelMessage(ProtocolMessage msg) {
		switch(msg.action) {
		case attached:
			setAttached(msg);
			break;
		case detach:
		case detached:
			ChannelState oldState = state;
			switch(oldState) {
				case attached:
					/* Unexpected detach, reattach when possible */
					setDetached((msg.error != null) ? msg.error : REASON_NOT_ATTACHED);
					Log.v(TAG, String.format("Server initiated detach for channel %s; attempting reattach", name));
					try {
						attachWithTimeout(null);
					} catch (AblyException e) {
					/* Send message error */
						Log.e(TAG, "Attempting reattach threw exception", e);
						setDetached(e.errorInfo);
					}
					break;
				case attaching:
					/* RTL13b says we need to be suspended, but continue to retry */
					Log.v(TAG, String.format("Server initiated detach for channel %s whilst attaching; moving to suspended", name));
					setSuspended(msg.error, true);
					reattachAfterTimeout();
					break;
				case detaching:
					setDetached((msg.error != null) ? msg.error : REASON_NOT_ATTACHED);
					break;
				case detached:
				case suspended:
				case failed:
				default:
					/* do nothing */
					break;
			}
			break;
		case message:
			onMessage(msg);
			break;
		case presence:
			onPresence(msg, null);
			break;
		case sync:
			onSync(msg);
			break;
		case error:
			setFailed(msg.error);
			break;
		default:
			Log.e(TAG, "onChannelMessage(): Unexpected message action (" + msg.action + ")");
		}
	}

	/**
	 * Emits UPDATE event
	 * @param errorInfo
	 */
	void emitUpdate(ErrorInfo errorInfo, boolean resumed) {
		if(state == ChannelState.attached)
			emit(ChannelEvent.update, ChannelStateListener.ChannelStateChange.createUpdateEvent(errorInfo, resumed));
	}

	public void emit(ChannelState state, ChannelStateListener.ChannelStateChange channelStateChange) {
		super.emit(state.getChannelEvent(), channelStateChange);
	}

	public void on(ChannelState state, ChannelStateListener listener) {
		super.on(state.getChannelEvent(), listener);
	}

	public void once(ChannelState state, ChannelStateListener listener) {
		super.once(state.getChannelEvent(), listener);
	}

	private static final String TAG = Channel.class.getName();
	final AblyRealtime ably;
	final String basePath;
	ChannelOptions options;
	String syncChannelSerial;
	private final InternalEncodingDecodingContext encodingDecodingContext;
	private ChannelParams params;
	private ChannelModes modes;
	private String lastPayloadMessageId;
	private String lastPayloadProtocolMessageChannelSerial;
	private boolean decodeFailureRecoveryInProgress;
}
