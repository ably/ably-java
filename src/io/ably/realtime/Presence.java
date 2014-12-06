package io.ably.realtime;

import io.ably.http.Http.BodyHandler;
import io.ably.http.HttpUtils;
import io.ably.http.PaginatedQuery;
import io.ably.realtime.Channel.ChannelState;
import io.ably.transport.ConnectionManager;
import io.ably.types.AblyException;
import io.ably.types.ErrorInfo;
import io.ably.types.PaginatedResult;
import io.ably.types.Param;
import io.ably.types.PresenceMessage;
import io.ably.types.PresenceSerializer;
import io.ably.types.ProtocolMessage;
import io.ably.util.Log;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A class that provides access to presence operations and state for the
 * associated Channel.
 */
public class Presence {

	/************************************
	 * subscriptions and PresenceListener
	 ************************************/

	/**
	 * Get the presence state for this Channel.
	 * @return: the current present members.
	 * @throws AblyException 
	 */
	public synchronized PresenceMessage[] get() {
		return presence.values().toArray(new PresenceMessage[presence.size()]);
	}

	/**
	 * An interface allowing a listener to be notified of arrival of presence messages
	 */
	public interface PresenceListener {
		public void onPresenceMessage(PresenceMessage[] messages);
	}

	/**
	 * Subscribe to presence events on the associated Channel. This implicitly
	 * attaches the Channel if it is not already attached.
	 * @param listener: the listener to me notified on arrival of presence messages.
	 * @throws AblyException
	 */
	public void subscribe(PresenceListener listener) throws AblyException {
		listeners.add(listener);
		channel.attach();
	}

	/**
	 * Unsubscribe a previously subscribed presence listener for this channel.
	 * @param listener: the previously subscribed listener.
	 */
	public void unsubscribe(PresenceListener listener) {
		listeners.remove(listener);
	}

	/***
	 * internal
	 *
	 */
	void setPresence(PresenceMessage[] messages, boolean broadcast) {
		synchronized(this) {
			for(PresenceMessage update : messages) {
				switch(update.action) {
				case ENTER:
				case UPDATE:
					presence.put(update.clientId, update);
					break;
				case LEAVE:
					presence.remove(update.clientId);
					break;
				}
			}
		}
		if(broadcast)
			broadcastPresence(messages);
	}
 
	private void broadcastPresence(PresenceMessage[] messages) {
		this.listeners.onPresenceMessage(messages);
	}

	private final HashMap<String, PresenceMessage> presence = new HashMap<String, PresenceMessage>();

	private final Multicaster listeners = new Multicaster();

	private static class Multicaster extends io.ably.util.Multicaster<PresenceListener> implements PresenceListener {
		@Override
		public void onPresenceMessage(PresenceMessage[] messages) {
			for(PresenceListener member : members)
				try {
					member.onPresenceMessage(messages);
				} catch(Throwable t) {}
		}
	}

	/************************************
	 * enter/leave and pending messages
	 ************************************/

	/**
	 * Enter this client into this channel. This client will be added to the presence set
	 * and presence subscribers will see an enter message for this client.
	 * @param data: optional data (eg a status message) for this member.
	 * See {@link io.ably.types.Data} for the supported data types.
	 * @param listener: a listener to be notified on completion of the operation.
	 * @throws AblyException
	 */
	public void enter(Object data, CompletionListener listener) throws AblyException {
		enterClient(clientId, data, listener);
	}

	/**
	 * Update the presence data for this client. If the client is not already a member of
	 * the presence set it will be added, and presence subscribers will see an enter or
	 * update message for this client.
	 * @param data: optional data (eg a status message) for this member.
	 * See {@link io.ably.types.Data} for the supported data types.
	 * @param listener: a listener to be notified on completion of the operation.
	 * @throws AblyException
	 */
	public void update(Object data, CompletionListener listener) throws AblyException {
		updateClient(clientId, data, listener);
	}

	/**
	 * Leave this client from this channel. This client will be removed from the presence
	 * set and presence subscribers will see a leave message for this client.
	 * @param data: optional data (eg a status message) for this member.
	 * See {@link io.ably.types.Data} for the supported data types.
	 * @param listener: a listener to be notified on completion of the operation.
	 * @throws AblyException
	 */
	public void leave(CompletionListener listener) throws AblyException {
		leaveClient(clientId, listener);
	}

	/**
	 * Enter a specified client into this channel. The given client will be added to the
	 * presence set and presence subscribers will see a corresponding presence message.
	 * This method is provided to support connections (eg connections from application
	 * server instances) that act on behalf of multiple clientIds. In order to be able to
	 * enter the channel with this method, the client library must have been instanced
	 * either with a key, or with a token bound to the wildcard clientId.
	 * @param clientId: the id of the client.
	 * @param data: optional data (eg a status message) for this member.
	 * See {@link io.ably.types.Data} for the supported data types.
	 * @param listener: a listener to be notified on completion of the operation.
	 * @throws AblyException
	 */
	public void enterClient(String clientId, Object data, CompletionListener listener) throws AblyException {
		Log.v(TAG, "enterClient(); channel = " + channel.name + "; clientId = " + clientId);
		updatePresence(new PresenceMessage(PresenceMessage.Action.ENTER, clientId, data), listener);
	}

	/**
	 * Update the presence data for a specified client into this channel.
	 * If the client is not already a member of the presence set it will be added, and
	 * presence subscribers will see an enter or update message for this client.
	 * As for #enterClient above, the connection must be authenticated in a way that
	 * enables it to represent an arbitrary clientId.
	 * @param clientId: the id of the client.
	 * @param data: optional data (eg a status message) for this member.
	 * See {@link io.ably.types.Data} for the supported data types.
	 * @param listener: a listener to be notified on completion of the operation.
	 * @throws AblyException
	 */
	public void updateClient(String clientId, Object data, CompletionListener listener) throws AblyException {
		Log.v(TAG, "updateClient(); channel = " + channel.name + "; clientId = " + clientId);
		updatePresence(new PresenceMessage(PresenceMessage.Action.UPDATE, clientId, data), listener);
	}

	/**
	 * Leave a given client from this channel. This client will be removed from the
	 * presence set and presence subscribers will see a leave message for this client.
	 * @param clientId: the id of the client.
	 * @param listener: a listener to be notified on completion of the operation.
	 * @throws AblyException
	 */
	public void leaveClient(String clientId, CompletionListener listener) throws AblyException {
		Log.v(TAG, "leaveClient(); channel = " + channel.name + "; clientId = " + clientId);
		updatePresence(new PresenceMessage(PresenceMessage.Action.LEAVE, clientId), listener);
	}

	/**
	 * Update the presence for this channel with a given PresenceMessage update.
	 * The connection must be authenticated in a way that enables it to represent
	 * the clientId in the message.
	 * @param msg: the presence message
	 * @param listener: a listener to be notified on completion of the operation.
	 * @throws AblyException
	 */
	public void updatePresence(PresenceMessage msg, CompletionListener listener) throws AblyException {
		Log.v(TAG, "update(); channel = " + channel.name + "; clientId = " + clientId);
		if (msg.clientId == null) {
			msg.clientId = clientId;
		}

		if(msg.clientId == null)
			throw new AblyException("Unable to enter presence channel without clientId", 400, 91000);

		msg.encode(null);
		synchronized(channel) {
			switch(channel.state) {
			case initialised:
				channel.attach();
			case attaching:
				QueuedPresence queued = new QueuedPresence(msg, listener);
				pendingPresence.put(msg.clientId, queued);
				break;
			case attached:
				ProtocolMessage message = new ProtocolMessage(ProtocolMessage.Action.PRESENCE, channel.name);
				message.presence = new PresenceMessage[] { msg };
				AblyRealtime ably = channel.ably;
				ConnectionManager connectionManager = ably.connection.connectionManager;
				connectionManager.send(message, ably.options.queueMessages, listener);
				break;
			default:
				throw new AblyException("Unable to enter presence channel in detached or failed state", 400, 91001);
			}
		}
	}

	/************************************
	 * history
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
	public PaginatedResult<PresenceMessage> history(Param[] params) throws AblyException {
		if(channel.state == ChannelState.attached) {
			if(!Param.containsKey(params, "live")) {
				/* add the "attached=true" param to tell the system to look at the realtime history */
				Param attached = new Param("live", "true");
				if(params == null) params = new Param[]{ attached };
				else params = Param.push(params, attached);
			}
		}
		AblyRealtime ably = channel.ably;
		BodyHandler<PresenceMessage> bodyHandler = PresenceSerializer.getPresenceResponseHandler(channel.options);
		return new PaginatedQuery<PresenceMessage>(ably.http, channel.basePath + "/presence/history", HttpUtils.defaultGetHeaders(ably.options.useBinaryProtocol), params, bodyHandler).get();
	}

	/***
	 * internal
	 *
	 */
	private static class QueuedPresence {
		public PresenceMessage msg;
		public CompletionListener listener;
		public QueuedPresence(PresenceMessage msg, CompletionListener listener) { this.msg = msg; this.listener = listener; }
	}

	private final Map<String, QueuedPresence> pendingPresence = new HashMap<String, QueuedPresence>();

	private void sendQueuedMessages() {
		Log.v(TAG, "sendQueuedMessages()");
		AblyRealtime ably = channel.ably;
		boolean queueMessages = ably.options.queueMessages;
		ConnectionManager connectionManager = ably.connection.connectionManager;
		int count = pendingPresence.size();
		if(count == 0)
			return;

		ProtocolMessage message = new ProtocolMessage(ProtocolMessage.Action.PRESENCE, channel.name);
		Iterator<QueuedPresence> allQueued = pendingPresence.values().iterator();
		PresenceMessage[] presenceMessages = message.presence = new PresenceMessage[count];
		CompletionListener listener;

		if(count == 1) {
			QueuedPresence queued = allQueued.next();
			presenceMessages[0] = queued.msg;
			listener = queued.listener;
		} else {
			int idx = 0;
			CompletionListener.Multicaster mListener = new CompletionListener.Multicaster();
			while(allQueued.hasNext()) {
				QueuedPresence queued = allQueued.next();
				presenceMessages[idx++] = queued.msg;
				if(queued.listener != null)
					mListener.add(queued.listener);
			}
			listener = mListener.isEmpty() ? null : mListener;
		}
		try {
			connectionManager.send(message, queueMessages, listener);
		} catch(AblyException e) {
			Log.e(TAG, "sendQueuedMessages(): Unexpected exception sending message", e);
			if(listener != null)
				listener.onError(e.errorInfo);
		}
	}

	private void failQueuedMessages(ErrorInfo reason) {
		Log.v(TAG, "failQueuedMessages()");
		for(QueuedPresence msg : pendingPresence.values())
			if(msg.listener != null)
				try {
					msg.listener.onError(reason);
				} catch(Throwable t) {
					Log.e(TAG, "failQueuedMessages(): Unexpected exception calling listener", t);
				}			
			
	}

	/************************************
	 * attach / detach
	 ************************************/

	void setAttached(PresenceMessage[] messages) {
		sendQueuedMessages();
		if(messages != null)
			setPresence(messages, false);
	}

	void setDetached(ErrorInfo reason) {
		failQueuedMessages(reason);
	}

	void setSuspended(ErrorInfo reason) {
		failQueuedMessages(reason);
	}

	/************************************
	 * general
	 ************************************/

	Presence(Channel channel) {
		this.channel = channel;
		clientId = channel.ably.options.clientId;
	}

	private static final String TAG = Channel.class.getName();

	private Channel channel;
	private String clientId;
}
