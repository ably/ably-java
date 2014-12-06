package io.ably.realtime;

import io.ably.http.HttpUtils;
import io.ably.http.PaginatedQuery;
import io.ably.http.Http.BodyHandler;
import io.ably.transport.ConnectionManager;
import io.ably.transport.ConnectionManager.QueuedMessage;
import io.ably.types.AblyException;
import io.ably.types.ChannelOptions;
import io.ably.types.ErrorInfo;
import io.ably.types.Message;
import io.ably.types.MessageSerializer;
import io.ably.types.PaginatedResult;
import io.ably.types.Param;
import io.ably.types.PresenceMessage;
import io.ably.types.ProtocolMessage;
import io.ably.types.ProtocolMessage.Action;
import io.ably.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A class representing a Channel belonging to this application.
 * The Channel instance allows messages to be published and
 * received, and controls the lifecycle of this instance's
 * attachment to the channel.
 *
 */
public class Channel {

	/************************************
	 * ChannelState and state management
	 ************************************/

	/**
	 * Channel states. See Ably Realtime API documentation for more details.
	 */
	public enum ChannelState {
		initialised,
		attaching,
		attached,
		detaching,
		detached,
		failed
	}

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

	/**
	 * An interface whereby a client may be notified of state changes for a channel.
	 */
	public interface ChannelStateListener {
		public void onChannelStateChanged(ChannelState state, ErrorInfo reason);
	}

	/**
	 * A collection of listeners to be notified of state changes for this channel.
	 */
	public StateMulticaster stateListeners = new StateMulticaster();

	public static class StateMulticaster extends io.ably.util.Multicaster<ChannelStateListener> implements ChannelStateListener {
		@Override
		public void onChannelStateChanged(ChannelState state, ErrorInfo reason) {
			for(ChannelStateListener member : members)
				try {
					member.onChannelStateChanged(state, reason);
				} catch(Throwable t) {}
		}
	}

	/***
	 * internal
	 *
	 */
	private void setState(ChannelState newState, ErrorInfo reason) {
		Log.v(TAG, "setState(): channel = " + name + "; setting " + newState);
		synchronized(this) {
			this.state = newState;
			this.reason = reason;
		}

		/* broadcast state change */
		stateListeners.onChannelStateChanged(newState, reason);
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
		Log.v(TAG, "attach(); channel = " + name);
		/* check preconditions */
		switch(state) {
			case attaching:
			case attached:
				/* nothing to do */
				return;
			default:
		}
		ConnectionManager connectionManager = ably.connection.connectionManager;
		if(!connectionManager.isActive())
			throw new AblyException(connectionManager.getStateErrorInfo());

		/* send attach request and pending state */
		ProtocolMessage attachMessage = new ProtocolMessage(Action.ATTACH, this.name);
		try {
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
		Log.v(TAG, "detach(); channel = " + name);
		/* check preconditions */
		switch(state) {
			case initialised:
			case detaching:
			case detached:
				/* nothing to do */
				return;
			default:
		}
		ConnectionManager connectionManager = ably.connection.connectionManager;
		if(!connectionManager.isActive())
			throw new AblyException(connectionManager.getStateErrorInfo());

		/* send detach request */
		ProtocolMessage detachMessage = new ProtocolMessage(Action.DETACH, this.name);
		try {
			setState(ChannelState.detaching, null);
			connectionManager.send(detachMessage, true, null);
		} catch(AblyException e) {
			throw e;
		}
	}

	/***
	 * internal
	 *
	 */
	private void setAttached(ProtocolMessage message) {
		Log.v(TAG, "setAttached(); channel = " + name);
		attachSerial = message.channelSerial;
		setState(ChannelState.attached, message.error);
		sendQueuedMessages();
		presence.setAttached(message.presence);
	}

	private void setDetached(ProtocolMessage message) {
		Log.v(TAG, "setDetached(); channel = " + name);
		ErrorInfo reason = (message.error != null) ? message.error : REASON_NOT_ATTACHED;
		setState(ChannelState.detached, reason);
		failQueuedMessages(reason);
		presence.setDetached(reason);
	}

	private void setFailed(ProtocolMessage message) {
		Log.v(TAG, "setFailed(); channel = " + name);
		ErrorInfo reason = message.error;
		setState(ChannelState.failed, reason);
		failQueuedMessages(reason);
		presence.setDetached(reason);
	}

	public void setSuspended(ErrorInfo reason) {
		Log.v(TAG, "setSuspended(); channel = " + name);
		setState(ChannelState.detached, reason);
		failQueuedMessages(reason);		
		presence.setSuspended(reason);
	}

	static ErrorInfo REASON_NOT_ATTACHED = new ErrorInfo("Channel not attached", 400, 90001);

	/************************************
	 * subscriptions and MessageListener
	 ************************************/

	/**
	 * An interface whereby a client maybe notified of messages changes on a channel.
	 */
	public interface MessageListener {
		public void onMessage(Message[] messages);
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
			} catch(AblyException e) {
				Log.e(TAG, "Unexpected exception decrypting message", e);
			}
			/* populate fields derived from protocol message */
			if(msg.timestamp == 0) msg.timestamp = message.timestamp;
			if(msg.id == null) msg.id = message.id + ':' + i;
			/* broadcast */
			Message[] singleMessage = new Message[] {msg};
			MessageMulticaster listeners = eventListeners.get(msg.name);
			if(listeners != null)
				listeners.onMessage(singleMessage);
		}
		this.listeners.onMessage(message.messages);
	}

	private void onPresence(ProtocolMessage message) {
		Log.v(TAG, "onPresence(); channel = " + name);
		PresenceMessage[] messages = message.presence;
		for(int i = 0; i < messages.length; i++) {
			PresenceMessage msg = messages[i];
			try {
				msg.decode(options);
			} catch(AblyException e) {
				Log.e(TAG, "Unexpected exception decrypting message", e);
			}
			/* populate fields derived from protocol message */
			if(msg.timestamp == 0) msg.timestamp = message.timestamp;
			if(msg.id == null) msg.id = message.id + ':' + i;
		}
		presence.setPresence(messages, true);
	}

	private MessageMulticaster listeners = new MessageMulticaster();
	private HashMap<String, MessageMulticaster> eventListeners = new HashMap<String, MessageMulticaster>();

	private static class MessageMulticaster extends io.ably.util.Multicaster<MessageListener> implements MessageListener {
		@Override
		public void onMessage(Message[] messages) {
			for(MessageListener member : members)
				try {
					member.onMessage(messages);
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
	 * @param message: the message
	 * @param listener: a listener to be notified of the outcome of this message.
	 * @throws AblyException
	 */
	public void publish(Message[] messages, CompletionListener listener) throws AblyException {
		Log.v(TAG, "publish(Message[]); channel = " + this.name);
		for(Message message : messages) message.encode(options);
		ProtocolMessage msg = new ProtocolMessage(Action.MESSAGE, this.name);
		msg.messages = messages;
		switch(state) {
		case initialised:
		case attaching:
			/* queue the message for later send */
			queuedMessages.add(new QueuedMessage(msg, listener));
			break;
		case detaching:
		case detached:
		case failed:
			throw new AblyException(new ErrorInfo("Unable to publish in detached or failed state", 400, 40000));
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
		if(this.state == ChannelState.attached) {
			if(!Param.containsKey(params, "live")) {
				/* add the "attached=true" param to tell the system to look at the realtime history */
				Param attached = new Param("live", "true");
				if(params == null) params = new Param[]{ attached };
				else params = Param.push(params, attached);
			}
		}
		BodyHandler<Message> bodyHandler = MessageSerializer.getMessageResponseHandler(options);
		return new PaginatedQuery<Message>(ably.http, basePath + "/history", HttpUtils.defaultGetHeaders(ably.options.useBinaryProtocol), params, bodyHandler).get();
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

	Channel(AblyRealtime ably, String name) {
		Log.v(TAG, "RealtimeChannel(); channel = " + name);
		this.ably = ably;
		this.name = name;
		this.basePath = "/channels/" + HttpUtils.encodeURIComponent(name);
		this.presence = new Presence(this);
		state = ChannelState.initialised;
		queuedMessages = new ArrayList<QueuedMessage>();
	}
	
	void onChannelMessage(ProtocolMessage msg) {
		switch(msg.action) {
		case ATTACHED:
			setAttached(msg);
			break;
		case DETACHED:
			setDetached(msg);
			break;
		case MESSAGE:
			onMessage(msg);
			break;
		case PRESENCE:
			onPresence(msg);
			break;
		case ERROR:
			setFailed(msg);
			break;
		default:
			Log.e(TAG, "onChannelMessage(): Unexpected message action (" + msg.action + ")");
		}
	}

	private static final String TAG = Channel.class.getName();
	final AblyRealtime ably;
	final String basePath;
	ChannelOptions options;
}
