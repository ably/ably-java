package io.ably.lib.realtime;

import io.ably.lib.http.HttpUtils;
import io.ably.lib.http.PaginatedQuery;
import io.ably.lib.http.Http.BodyHandler;
import io.ably.lib.transport.ConnectionManager;
import io.ably.lib.transport.ConnectionManager.QueuedMessage;
import io.ably.lib.types.*;
import io.ably.lib.types.ProtocolMessage.Action;
import io.ably.lib.types.ProtocolMessage.Flag;
import io.ably.lib.util.EventEmitter;
import io.ably.lib.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;


/**
 * A class representing a Channel belonging to this application.
 * The Channel instance allows messages to be published and
 * received, and controls the lifecycle of this instance's
 * attachment to the channel.
 *
 */
public class Channel extends EventEmitter<ChannelState, ChannelStateListener> {

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
	 * A message identifier indicating the time of attachment to the channel;
	 * used when recovering a message history to mesh exactly with messages
	 * received on this channel subsequent to attachment.
	 */
	public String attachSerial;

	/***
	 * internal
	 *
	 */
	private void setState(ChannelState newState, ErrorInfo reason) {
		setState(newState, reason, false);
	}
	private void setState(ChannelState newState, ErrorInfo reason, boolean resumed) {
		Log.v(TAG, "setState(): channel = " + name + "; setting " + newState);
		ChannelStateListener.ChannelStateChange stateChange;
		synchronized(this) {
			stateChange = new ChannelStateListener.ChannelStateChange(newState, this.state, reason, resumed);
			this.state = stateChange.current;
			this.reason = stateChange.reason;
		}

		/* broadcast state change */
		emit(newState, stateChange);
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
	public void attach(final CompletionListener listener) throws AblyException {
		Log.v(TAG, "attach(); channel = " + name);
		/* check preconditions */
		switch(state) {
			case attaching:
				if (listener != null) {
					on(new ChannelStateCompletionListener(listener, ChannelState.attached, ChannelState.failed));
				}

				return;
			case attached:
				if (listener != null) {
					listener.onSuccess();
				}
				return;
			default:
		}
		ConnectionManager connectionManager = ably.connection.connectionManager;
		if(!connectionManager.isActive())
			throw AblyException.fromErrorInfo(connectionManager.getStateErrorInfo());

		/* send attach request and pending state */
		ProtocolMessage attachMessage = new ProtocolMessage(Action.attach, this.name);
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
		Log.v(TAG, "detach(); channel = " + name);
		/* check preconditions */
		switch(state) {
			case initialized:
			case detached: {
				if(listener != null) {
					listener.onSuccess();
				}
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
	private void setAttached(ProtocolMessage message) {
		boolean resumed = (message.flags & ( 1 << Flag.resumed.ordinal())) != 0;
		Log.v(TAG, "setAttached(); channel = " + name + ", resumed = " + resumed);
		attachSerial = message.channelSerial;
		setState(ChannelState.attached, message.error, resumed);
		sendQueuedMessages();
		if((message.flags & ( 1 << Flag.has_presence.ordinal())) > 0) {
			Log.v(TAG, "setAttached(); awaiting sync; channel = " + name);
			presence.awaitSync();
		}
		presence.setAttached();
	}

	private void setDetached(ErrorInfo reason) {
		Log.v(TAG, "setDetached(); channel = " + name);
		setState(ChannelState.detached, reason);
		failQueuedMessages(reason);
		presence.setDetached(reason);
	}

	private void setFailed(ErrorInfo reason) {
		Log.v(TAG, "setFailed(); channel = " + name);
		setState(ChannelState.failed, reason);
		failQueuedMessages(reason);
		presence.setDetached(reason);
	}

	/* State changes provoked by ConnectionManager state changes. */

	public void setConnected() {
		if(state == ChannelState.attached) {
			try {
				sync();
			} catch (AblyException e) {
				Log.e(TAG, "setConnected(): Unable to sync; channel = " + name, e);
			}
		}
	}

	/** (RTL3a) If the connection state enters the FAILED state, then an
	 * ATTACHING or ATTACHED channel state will transition to FAILED, set the
	 * Channel#errorReason and emit the error event.
	 * The Java library does not currently have functionality for an error
	 * event; it is just an error in the attached->failed state change. */
	public void setConnectionFailed(ErrorInfo reason) {
		if (state == ChannelState.attached || state == ChannelState.attaching)
			setFailed(reason);
	}

	/** (RTL3b) If the connection state enters the CLOSED state, then an
	 * ATTACHING or ATTACHED channel state will transition to DETACHED. */
	public void setConnectionClosed(ErrorInfo reason) {
		if (state == ChannelState.attached || state == ChannelState.attaching)
			setDetached(reason);
	}

	/** (RTL3c) If the connection state enters the SUSPENDED state, then an
	 * ATTACHING or ATTACHED channel state will transition to SUSPENDED.
	 * This also gets called when a connection enters CONNECTED but with a
	 * non-fatal error for a failed reconnect (RTN16e). */
	public void setSuspended(ErrorInfo reason) {
		if (state == ChannelState.attached || state == ChannelState.attaching) {
			Log.v(TAG, "setSuspended(); channel = " + name);
			setState(ChannelState.suspended, reason);
			failQueuedMessages(reason);		
			presence.setSuspended(reason);
		}
	}

	@Override
	protected void apply(ChannelStateListener listener, ChannelState state, Object... args) {
		listener.onChannelStateChanged((ChannelStateListener.ChannelStateChange)args[0]);
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
		for(int i = 0; i < messages.length; i++) {
			Message msg = messages[i];
			try {
				msg.decode(options);
			} catch (MessageDecodeException e) {
				Log.e(TAG, String.format("%s on channel %s", e.errorInfo.message, name));
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

		for (Message msg : message.messages) {
			this.listeners.onMessage(msg);
		}
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
				} catch(Throwable t) {}
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
	public void publish(Message[] messages, CompletionListener listener) throws AblyException {
		Log.v(TAG, "publish(Message[]); channel = " + this.name);
		for(Message message : messages) message.encode(options);
		ProtocolMessage msg = new ProtocolMessage(Action.message, this.name);
		msg.messages = messages;
		switch(state) {
		case initialized:
			attach();
		case attaching:
			/* queue the message for later send */
			queuedMessages.add(new QueuedMessage(msg, listener));
			break;
		case detaching:
		case detached:
		case failed:
		case suspended:
			throw AblyException.fromErrorInfo(new ErrorInfo("Unable to publish in detached, failed or suspended state", 400, 40000));
		case attached:
			ConnectionManager connectionManager = ably.connection.connectionManager;
			connectionManager.send(msg, ably.options.queueMessages, listener);
		}
	}

	/***
	 * internal
	 *
	 */
	private void sendQueuedMessages() {
		Log.v(TAG, "sendQueuedMessages()");
		boolean queueMessages = ably.options.queueMessages;
		ConnectionManager connectionManager = ably.connection.connectionManager;
		for(QueuedMessage msg : queuedMessages)
			try {
				connectionManager.send(msg.msg, queueMessages, msg.listener);
			} catch(AblyException e) {
				Log.e(TAG, "sendQueuedMessages(): Unexpected exception sending message", e);
				if(msg.listener != null)
					msg.listener.onError(e.errorInfo);
			}
		queuedMessages.clear();
	}

	private void failQueuedMessages(ErrorInfo reason) {
		Log.v(TAG, "failQueuedMessages()");
		for(QueuedMessage msg : queuedMessages)
			if(msg.listener != null)
				try {
					msg.listener.onError(reason);
				} catch(Throwable t) {
					Log.e(TAG, "failQueuedMessages(): Unexpected exception calling listener", t);
				}
		queuedMessages.clear();
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

					params.add(new Param(KEY_FROM_SERIAL, channel.attachSerial));
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
		params = replacePlaceholderParams(this, params);

		BodyHandler<Message> bodyHandler = MessageSerializer.getMessageResponseHandler(options);
		return new PaginatedQuery<>(ably.http, basePath + "/history", HttpUtils.defaultAcceptHeaders(ably.options.useBinaryProtocol), params, bodyHandler).get();
	}

	/************************************
	 * Channel options 
	 ************************************/

	public void setOptions(ChannelOptions options) throws AblyException {
		this.options = options;
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
				Channel.this.off(this);
				completionListener.onSuccess();
			}
			else if(stateChange.current.equals(failureState)) {
				Channel.this.off(this);
				completionListener.onError(reason);
			}
		}
	}

	Channel(AblyRealtime ably, String name) {
		Log.v(TAG, "RealtimeChannel(); channel = " + name);
		this.ably = ably;
		this.name = name;
		this.basePath = "/channels/" + HttpUtils.encodeURIComponent(name);
		this.presence = new Presence(this);
		state = ChannelState.initialized;
		queuedMessages = new ArrayList<QueuedMessage>();
	}

	void onChannelMessage(ProtocolMessage msg) {
		switch(msg.action) {
		case attached:
			setAttached(msg);
			break;
		case detach:
		case detached:
			setDetached((msg.error != null) ? msg.error : REASON_NOT_ATTACHED);
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

	private static final String TAG = Channel.class.getName();
	final AblyRealtime ably;
	final String basePath;
	ChannelOptions options;
	String syncChannelSerial;
}
