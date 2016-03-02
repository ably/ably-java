package io.ably.lib.realtime;

import io.ably.lib.http.Http.BodyHandler;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.http.PaginatedQuery;
import io.ably.lib.transport.ConnectionManager;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.Param;
import io.ably.lib.types.PresenceMessage;
import io.ably.lib.types.PresenceSerializer;
import io.ably.lib.types.ProtocolMessage;
import io.ably.lib.util.Log;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

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
	public synchronized PresenceMessage[] get()  {
		Collection<PresenceMessage> values = presence.values();
		return values.toArray(new PresenceMessage[values.size()]);
	}

	/**
	 * Get the presence state for this Channel, optionally waiting for sync to complete.
	 * @return: the current present members.
	 * @throws AblyException
	 */
	public synchronized PresenceMessage[] get(boolean wait) throws InterruptedException {
		Collection<PresenceMessage> values = presence.values(wait);
		return values.toArray(new PresenceMessage[values.size()]);
	}

	/**
	 * Get the presence state for a given clientId
	 * @param wait
	 * @return
	 * @throws InterruptedException
	 */
	public synchronized PresenceMessage[] get(String clientId, boolean wait) throws InterruptedException {
		Collection<PresenceMessage> values = presence.getClient(clientId, wait);
		return values.toArray(new PresenceMessage[values.size()]);
	}

	/**
	 * An interface allowing a listener to be notified of arrival of presence messages
	 */
	public interface PresenceListener {
		void onPresenceMessage(PresenceMessage messages);
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
		for (Multicaster multicaster: eventListeners.values()) {
			multicaster.remove(listener);
		}
	}

	/**
	 * Subscribe to presence events with a specific action on the associated Channel.
	 * This implicitly attaches the Channel if it is not already attached.
	 *
	 * @param action to be observed
	 * @param listener
	 * @throws AblyException
	 */
	public void subscribe(PresenceMessage.Action action, PresenceListener listener) throws AblyException {
		subscribeImpl(action, listener);
		channel.attach();
	}

	/**
	 * Unsubscribe a previously subscribed presence listener for this channel from specific action.
	 *
	 * @param action
	 * @param listener
	 */
	public void unsubscribe(PresenceMessage.Action action, PresenceListener listener) {
		unsubscribeImpl(action, listener);
	}

	/**
	 * Subscribe to presence events with specific actions on the associated Channel.
	 * This implicitly attaches the Channel if it is not already attached.
	 *
	 * @param actions to be observed
	 * @param listener
	 * @throws AblyException
	 */
	public void subscribe(EnumSet<PresenceMessage.Action> actions, PresenceListener listener) throws AblyException {
		for (PresenceMessage.Action action : actions) {
			subscribeImpl(action, listener);
		}
		channel.attach();
	}

	/**
	 * Unsubscribe a previously subscribed presence listener for this channel from specific actions.
	 *
	 * @param actions
	 * @param listener
	 */
	public void unsubscribe(EnumSet<PresenceMessage.Action> actions, PresenceListener listener) {
		for (PresenceMessage.Action action : actions) {
			unsubscribeImpl(action, listener);
		}
	}

	/**
	 * Unsubscribe all subscribed presence lisceners for this channel.
	 */
	public void unsubscribe() {
		listeners.clear();
		eventListeners.clear();
	}


	/***
	 * internal
	 *
	 */

	void setPresence(PresenceMessage[] messages, boolean broadcast, String syncChannelSerial) {
		Log.v(TAG, "setPresence(); channel = " + channel.name + "; broadcast = " + broadcast + "; syncChannelSerial = " + syncChannelSerial);
		String syncCursor = null;
		if(syncChannelSerial != null) {
			syncCursor = syncChannelSerial.substring(syncChannelSerial.indexOf(':'));
			if(syncCursor.length() > 1)
				presence.startSync();
		}
		for(PresenceMessage update : messages) {
			switch(update.action) {
			case enter:
			case update:
				update = (PresenceMessage)update.clone();
				update.action = PresenceMessage.Action.present;
			case present:
				broadcast &= presence.put(update);
				break;
			case leave:
				broadcast &= presence.remove(update);
				break;
			case absent:
			}
		}
		/* if this is the last message in a sequence of sync updates, end the sync */
		if(syncChannelSerial == null || syncCursor.length() <= 1)
			presence.endSync();

		if(broadcast)
			broadcastPresence(messages);
	}

	private void broadcastPresence(PresenceMessage[] messages) {
		for(PresenceMessage message : messages) {
			listeners.onPresenceMessage(message);

			Multicaster eventListener = eventListeners.get(message.action);
			if(eventListener != null)
				eventListener.onPresenceMessage(message);
		}
	}

	private final Multicaster listeners = new Multicaster();
	private final EnumMap<PresenceMessage.Action, Multicaster> eventListeners = new EnumMap<>(PresenceMessage.Action.class);

	private static class Multicaster extends io.ably.lib.util.Multicaster<PresenceListener> implements PresenceListener {
		@Override
		public void onPresenceMessage(PresenceMessage message) {
			for(PresenceListener member : members)
				try {
					member.onPresenceMessage(message);
				} catch(Throwable t) {}
		}
	}

	private void subscribeImpl(PresenceMessage.Action action, PresenceListener listener) {
		Multicaster listeners = eventListeners.get(action);
		if(listeners == null) {
			listeners = new Multicaster();
			eventListeners.put(action, listeners);
		}
		listeners.add(listener);
	}

	private void unsubscribeImpl(PresenceMessage.Action action, PresenceListener listener) {
		Multicaster listeners = eventListeners.get(action);
		if(listeners != null) {
			listeners.remove(listener);
			if(listeners.isEmpty()) {
				eventListeners.remove(action);
			}
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
	public void leave(Object data, CompletionListener listener) throws AblyException {
		leaveClient(clientId, data, listener);
	}

	/**
	 * Leave this client from this channel. This client will be removed from the presence
	 * set and presence subscribers will see a leave message for this client.
	 * @param listener: a listener to be notified on completion of the operation.
	 * @throws AblyException
	 */
	public void leave(CompletionListener listener) throws AblyException {
		leaveClient(clientId, null, listener);
	}

	/**
	 * Enter a specified client into this channel. The given clientId will be added to
	 * the presence set and presence subscribers will see a corresponding presence message
	 * with an empty data payload.
	 * This method is provided to support connections (eg connections from application
	 * server instances) that act on behalf of multiple clientIds. In order to be able to
	 * enter the channel with this method, the client library must have been instanced
	 * either with a key, or with a token bound to the wildcard clientId.
	 * @param clientId: the id of the client.
	 */
	public void enterClient(String clientId) throws AblyException {
		enterClient(clientId, null);
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
	 * @throws AblyException
	 */
	public void enterClient(String clientId, Object data) throws AblyException {
		enterClient(clientId, data, null);
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
	 * @param listener: a listener to be notified on completion of the operation.
	 * @throws AblyException
	 */
	public void enterClient(String clientId, Object data, CompletionListener listener) throws AblyException {
		Log.v(TAG, "enterClient(); channel = " + channel.name + "; clientId = " + clientId);
		updatePresence(new PresenceMessage(PresenceMessage.Action.enter, clientId, data), listener);
	}

	/**
	 * Update the presence data for a specified client into this channel.
	 * If the client is not already a member of the presence set it will be added,
	 * and presence subscribers will see a corresponding presence message
	 * with an empty data payload. As for #enterClient above, the connection
	 * must be authenticated in a way that enables it to represent an arbitrary clientId.
	 * @param clientId: the id of the client.
	 * @throws AblyException
	 */
	public void updateClient(String clientId) throws AblyException {
		updateClient(clientId, null);
	}

	/**
	 * Update the presence data for a specified client into this channel.
	 * If the client is not already a member of the presence set it will be added, and
	 * presence subscribers will see an enter or update message for this client.
	 * As for #enterClient above, the connection must be authenticated in a way that
	 * enables it to represent an arbitrary clientId.
	 * @param clientId: the id of the client.
	 * @param data: optional data (eg a status message) for this member.
	 * @throws AblyException
	 */
	public void updateClient(String clientId, Object data) throws AblyException {
		updateClient(clientId, data, null);
	}

	/**
	 * Update the presence data for a specified client into this channel.
	 * If the client is not already a member of the presence set it will be added, and
	 * presence subscribers will see an enter or update message for this client.
	 * As for #enterClient above, the connection must be authenticated in a way that
	 * enables it to represent an arbitrary clientId.
	 * @param clientId: the id of the client.
	 * @param data: optional data (eg a status message) for this member.
	 * @param listener: a listener to be notified on completion of the operation.
	 * @throws AblyException
	 */
	public void updateClient(String clientId, Object data, CompletionListener listener) throws AblyException {
		Log.v(TAG, "updateClient(); channel = " + channel.name + "; clientId = " + clientId);
		updatePresence(new PresenceMessage(PresenceMessage.Action.update, clientId, data), listener);
	}

	/**
	 * Leave a given client from this channel. This client will be removed from the
	 * presence set and presence subscribers will see a corresponding presence message
	 * with an empty data payload.
	 * @param clientId: the id of the client.
	 * @throws AblyException
	 */
	public void leaveClient(String clientId) throws AblyException {
		leaveClient(clientId, null);
	}

	/**
	 * Leave a given client from this channel. This client will be removed from the
	 * presence set and presence subscribers will see a leave message for this client.
	 * @param clientId: the id of the client.
	 * @param data: optional data (eg a status message) for this member.
	 * @throws AblyException
	 */
	public void leaveClient(String clientId, Object data) throws AblyException {
		leaveClient(clientId, data, null);
	}

	/**
	 * Leave a given client from this channel. This client will be removed from the
	 * presence set and presence subscribers will see a leave message for this client.
	 * @param clientId: the id of the client.
	 * @param data: optional data (eg a status message) for this member.
	 * @param listener: a listener to be notified on completion of the operation.
	 * @throws AblyException
	 */
	public void leaveClient(String clientId, Object data, CompletionListener listener) throws AblyException {
		Log.v(TAG, "leaveClient(); channel = " + channel.name + "; clientId = " + clientId);
		updatePresence(new PresenceMessage(PresenceMessage.Action.leave, clientId, data), listener);
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
			throw AblyException.fromErrorInfo(new ErrorInfo("Unable to enter presence channel without clientId", 400, 91000));

		msg.encode(null);
		synchronized(channel) {
			switch(channel.state) {
			case initialized:
				channel.attach();
			case attaching:
				QueuedPresence queued = new QueuedPresence(msg, listener);
				pendingPresence.put(msg.clientId, queued);
				break;
			case attached:
				ProtocolMessage message = new ProtocolMessage(ProtocolMessage.Action.presence, channel.name);
				message.presence = new PresenceMessage[] { msg };
				AblyRealtime ably = channel.ably;
				ConnectionManager connectionManager = ably.connection.connectionManager;
				connectionManager.send(message, ably.options.queueMessages, listener);
				break;
			default:
				throw AblyException.fromErrorInfo(new ErrorInfo("Unable to enter presence channel in detached or failed state", 400, 91001));
			}
		}
	}

	/************************************
	 * history
	 ************************************/

	/**
	 * Obtain recent history for this channel using the REST API.
	 * The history provided relates to all clients of this application,
	 * not just this instance.
	 * @param params: the request params. See the Ably REST API
	 * documentation for more details.
	 * @return: an array of Messgaes for this Channel.
	 * @throws AblyException
	 */
	public PaginatedResult<PresenceMessage> history(Param[] params) throws AblyException {
		AblyRealtime ably = channel.ably;
		BodyHandler<PresenceMessage> bodyHandler = PresenceSerializer.getPresenceResponseHandler(channel.options);
		return new PaginatedQuery<PresenceMessage>(ably.http, channel.basePath + "/presence/history", HttpUtils.defaultAcceptHeaders(ably.options.useBinaryProtocol), params, bodyHandler).get();
	}

	/**
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

		ProtocolMessage message = new ProtocolMessage(ProtocolMessage.Action.presence, channel.name);
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

	void setAttached() {
		sendQueuedMessages();
	}

	void setDetached(ErrorInfo reason) {
		failQueuedMessages(reason);
	}

	void setSuspended(ErrorInfo reason) {
		failQueuedMessages(reason);
	}

	/************************************
	 * sync
	 ************************************/

	void awaitSync() {
		presence.startSync();
	}

	/**
	 * A class encapsulating a map of the members of this presence channel,
	 * indexed by a String key that is a combination of connectionId and clientId.
	 * This map synchronises the membership of the presence set by handling
	 * sync messages from the service. Since sync messages can be out-of-order -
	 * eg an enter sync event being received after that member has in fact left -
	 * this map keeps "witness" entries, with absent Action, to remember the
	 * fact that a leave event has been seen for a member. These entries are
	 * cleared once the last set of updates of a sync sequence have been received.
	 *
	 */
	private class PresenceMap {
		/**
		 * Get the current presence state for a given member key
		 * @param key
		 * @return
		 */
		synchronized Collection<PresenceMessage> getClient(String clientId, boolean wait) throws InterruptedException {
			Collection<PresenceMessage> result = new HashSet<PresenceMessage>();
			for(Iterator<Map.Entry<String, PresenceMessage>> it = members.entrySet().iterator(); it.hasNext();) {
				PresenceMessage entry = it.next().getValue();
				if(entry.clientId.equals(clientId) && entry.action != PresenceMessage.Action.absent) {
					result.add(entry);
				}
			}
			return result;
		}

		/**
		 * Add or update the presence state for a member
		 * @param item
		 * @return true if the given message represents a change;
		 * false if the message is already superseded
		 */
		synchronized boolean put(PresenceMessage item) {
			String key = memberKey(item);
			/* we've seen this member, so do not remove it at the end of sync */
			if(residualMembers != null)
				residualMembers.remove(key);

			/* compare the timestamp of the new item with any existing member (or absent witness) */
			PresenceMessage existingItem = members.get(key);
			if(existingItem != null && item.timestamp < existingItem.timestamp) {
				/* no item supersedes a newer item with the same key */
				return false;
			}
			members.put(key, item);
			return true;
		}

		/**
		 * Get all members based on the current state (even if sync is in progress)
		 * @return
		 */
		synchronized Collection<PresenceMessage> values() {
			try { return values(false); } catch (InterruptedException e) { return null; }
		}

		/**
		 * Get all members, optionally waiting if a sync is in progress.
		 * @param wait
		 * @return
		 * @throws InterruptedException
		 */
		synchronized Collection<PresenceMessage> values(boolean wait) throws InterruptedException {
			if(wait) {
				while(syncInProgress) wait();
			}
			Set<PresenceMessage> result = new HashSet<PresenceMessage>();
			result.addAll(members.values());
			for(Iterator<PresenceMessage> it = result.iterator(); it.hasNext();) {
				PresenceMessage entry = it.next();
				if(entry.action == PresenceMessage.Action.absent) {
					it.remove();
				}
			}
			return result;
		}

		/**
		 * Remove a member.
		 * @param item
		 * @return
		 */
		synchronized boolean remove(PresenceMessage item) {
			String key = memberKey(item);
			PresenceMessage existingItem = members.remove(key);
			if(existingItem != null && existingItem.action == PresenceMessage.Action.absent)
				return false;
			return true;
		}

		/**
		 * Start a sync sequence.
		 * Note that this is called each time a sync message is received that is not
		 * the last.
		 */
		synchronized void startSync() {
			Log.v(TAG, "startSync(); channel = " + channel.name + "; syncInProgress = " + syncInProgress);
			/* we might be called multiple times while a sync is in progress */
			if(!syncInProgress) {
				residualMembers = new HashSet<String>(members.keySet());
				syncInProgress = true;
			}
		}

		/**
		 * Finish a sync sequence.
		 */
		synchronized void endSync() {
			Log.v(TAG, "endSync(); channel = " + channel.name + "; syncInProgress = " + syncInProgress);
			if(syncInProgress) {
				/* we can now strip out the absent members, as we have
				 * received all of the out-of-order sync messages */
				for(Iterator<Map.Entry<String, PresenceMessage>> it = members.entrySet().iterator(); it.hasNext();) {
					Map.Entry<String, PresenceMessage> entry = it.next();
					if(entry.getValue().action == PresenceMessage.Action.absent) {
						it.remove();
					}
				}
				/* any members that were present at the start of the sync,
				 * and have not been seen in sync, can be removed */
				for(Iterator<String> it = residualMembers.iterator(); it.hasNext();) {
					members.remove(it.next());
				}
				residualMembers = null;
	
				/* finish, notifying any waiters */
				syncInProgress = false;
			}
			notifyAll();
		}

		/**
		 * Get the member key for a given PresenceMessage.
		 * @param message
		 * @return
		 */
		private String memberKey(PresenceMessage message) {
			return message.connectionId + ':' + message.clientId;
		}

		private boolean syncInProgress;
		private Collection<String> residualMembers;
		private final HashMap<String, PresenceMessage> members = new HashMap<String, PresenceMessage>();
	}

	private final PresenceMap presence = new PresenceMap();

	/************************************
	 * general
	 ************************************/

	Presence(Channel channel) {
		this.channel = channel;
		this.clientId = channel.ably.options.clientId;
	}

	private static final String TAG = Channel.class.getName();

	private final Channel channel;
	private final String clientId;
}
