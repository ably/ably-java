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

import java.util.*;

/**
 * A class that provides access to presence operations and state for the
 * associated Channel.
 */
public class Presence {

	/************************************
	 * subscriptions and PresenceListener
	 ************************************/

	/**
	 * String parameter names for get() call with Param... as an argument
	 */
	public final static String GET_WAITFORSYNC = "waitForSync";
	public final static String GET_CLIENTID = "clientId";
	public final static String GET_CONNECTIONID = "connectionId";

	/**
	 * Get the presence state for this channel. Take Param[] array as an argument.
	 * Implicitly attaches the channel. However, if the channel is in or moves to the FAILED
	 * state before the operation succeeds, it will result in an error
	 * @param params
	 * @return
	 * @throws AblyException
	 * @throws InterruptedException
	 */
	public synchronized PresenceMessage[] get(Param... params) throws AblyException {
		if (channel.state == ChannelState.failed) {
			throw AblyException.fromErrorInfo(new ErrorInfo("channel operation failed (invalid channel state)", 90001));
		}

		channel.attach();
		try {
			Collection<PresenceMessage> values = presence.get(params);
			return values.toArray(new PresenceMessage[values.size()]);
		} catch (InterruptedException e) {
			Log.v(TAG, String.format("Channel %s: get() operation interrupted", channel.name));
			throw AblyException.fromThrowable(e);
		}
	}

	/**
	 * Get the presence state for this Channel, optionally waiting for sync to complete.
	 * Implicitly attaches the Channel. However, if the channel is in or moves to the FAILED
	 * state before the operation succeeds, it will result in an error
	 * @return: the current present members.
	 * @throws AblyException
	 */
	public synchronized PresenceMessage[] get(boolean wait) throws AblyException {
		return get(new Param(GET_WAITFORSYNC, String.valueOf(wait)));
	}

	/**
	 * Get the presence state for a given clientId. Implicitly attaches the
	 * Channel. However, if the channel is in or moves to the FAILED
	 * state before the operation succeeds, it will result in an error
	 * @param wait
	 * @return
	 * @throws InterruptedException
	 * @throws AblyException
	 */
	public synchronized PresenceMessage[] get(String clientId, boolean wait) throws AblyException {
		return get(new Param(GET_WAITFORSYNC, String.valueOf(wait)), new Param(GET_CLIENTID, clientId));
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
	 * @param completionListener listener to be called on success/failure
	 * @throws AblyException
	 */
	public void subscribe(PresenceListener listener, CompletionListener completionListener) throws AblyException {
		implicitAttachOnSubscribe(completionListener);
		listeners.add(listener);
	}

	/**
	 * Same as above without completion listener
	 */
	public void subscribe(PresenceListener listener) throws AblyException {
		subscribe(listener, null);
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
	 * @param completionListener listener to be called on success/failure
	 * @throws AblyException
	 */
	public void subscribe(PresenceMessage.Action action, PresenceListener listener, CompletionListener completionListener) throws AblyException {
		implicitAttachOnSubscribe(completionListener);
		subscribeImpl(action, listener);
	}

	/**
	 * Same as above without completion listener
	 */
	public void subscribe(PresenceMessage.Action action, PresenceListener listener) throws AblyException {
		subscribe(action, listener, null);
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
	 * @param completionListener listener to be called on success/failure
	 * @throws AblyException
	 */
	public void subscribe(EnumSet<PresenceMessage.Action> actions, PresenceListener listener, CompletionListener completionListener) throws AblyException {
		implicitAttachOnSubscribe(completionListener);
		for (PresenceMessage.Action action : actions) {
			subscribeImpl(action, listener);
		}
	}

	/**
	 * Same as above without completion listener
	 */
	public void subscribe(EnumSet<PresenceMessage.Action> actions, PresenceListener listener) throws AblyException {
		subscribe(actions, listener, null);
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

	/**
	 * Implicitly attach channel on subscribe. Throw exception if channel is in failed state
	 * @param completionListener
	 * @throws AblyException
	 */
	private void implicitAttachOnSubscribe(CompletionListener completionListener) throws AblyException {
		if (channel.state == ChannelState.failed) {
			String erroString = String.format("Channel %s: subscribe in FAILED channel state", channel.name);
			Log.v(TAG, erroString);
			ErrorInfo errorInfo = new ErrorInfo(erroString, 90001);
			throw AblyException.fromErrorInfo(errorInfo);
		}
		channel.attach(completionListener);
	}

	/* End sync and emit leave messages for residual members */
	private void endSyncAndEmitLeaves() {
		currentSyncChannelSerial = null;
		List<PresenceMessage> residualMembers = presence.endSync();
		for (PresenceMessage member: residualMembers) {
			/*
			 * RTP19: ... The PresenceMessage published should contain the original attributes of the presence
			 * member with the action set to LEAVE, PresenceMessage#id set to null, and the timestamp set
			 * to the current time ...
			 */
			member.action = PresenceMessage.Action.leave;
			member.id = null;
			member.timestamp = System.currentTimeMillis();
		}
		broadcastPresence(residualMembers.toArray(new PresenceMessage[residualMembers.size()]));

		/**
		 * (RTP5c2) If a SYNC is initiated as part of the attach, then once the SYNC is complete,
		 * all members not present in the PresenceMap but present in the internal PresenceMap must
		 * be re-entered automatically by the client using the clientId and data attributes from
		 * each. The members re-entered automatically must be removed from the internal PresenceMap
		 * ensuring that members present on the channel are constructed from presence events sent
		 * from Ably since the channel became ATTACHED
		 */
		if (syncAsResultOfAttach) {
			syncAsResultOfAttach = false;
			for (PresenceMessage item: internalPresence.values()) {
				if (presence.put(item)) {
					/* Message is new to presence map, send it */
					final String clientId = item.clientId;
					try {
						PresenceMessage itemToSend = (PresenceMessage)item.clone();
						itemToSend.action = PresenceMessage.Action.enter;
						updatePresence(itemToSend, new CompletionListener() {
							@Override
							public void onSuccess() {
							}

							@Override
							public void onError(ErrorInfo reason) {
									/*
									 * (RTP5c3)  If any of the automatic ENTER presence messages published
									 * in RTP5c2 fail, then an UPDATE event should be emitted on the channel
									 * with resumed set to true and reason set to an ErrorInfo object with error
									 * code value 91004 and the error message string containing the message
									 * received from Ably (if applicable), the code received from Ably
									 * (if applicable) and the explicit or implicit client_id of the PresenceMessage
									 */
								String errorString = String.format("Cannot automatically re-enter %s on channel %s (%s)",
										clientId, channel.name, reason.message);
								Log.e(TAG, errorString);
								channel.emitUpdate(new ErrorInfo(errorString, 91004), true);
							}
						});
					} catch(AblyException e) {
						String errorString = String.format("Cannot automatically re-enter %s on channel %s (%s)",
								clientId, channel.name, e.errorInfo.message);
						Log.e(TAG, errorString);
						channel.emitUpdate(new ErrorInfo(errorString, 91004), true);
					}
				}
			}
			internalPresence.clear();
		}
	}

	void setPresence(PresenceMessage[] messages, boolean broadcast, String syncChannelSerial) {
		Log.v(TAG, "setPresence(); channel = " + channel.name + "; broadcast = " + broadcast + "; syncChannelSerial = " + syncChannelSerial);
		String syncCursor = null;
		if(syncChannelSerial != null) {
			int colonPos = syncChannelSerial.indexOf(':');
			String serial = colonPos >= 0 ? syncChannelSerial.substring(0, colonPos) : syncChannelSerial;
			/* Discard incomplete sync if serial has changed */
			if (presence.syncInProgress && currentSyncChannelSerial != null && !currentSyncChannelSerial.equals(serial))
				endSyncAndEmitLeaves();
			syncCursor = syncChannelSerial.substring(colonPos);
			if(syncCursor.length() > 1) {
				presence.startSync();
				currentSyncChannelSerial = serial;
			}
		}
		for(PresenceMessage update : messages) {
			boolean updateInternalPresence = update.connectionId.equals(channel.ably.connection.id);
			boolean broadcastThisUpdate = broadcast;
			PresenceMessage originalUpdate = update;

			switch(update.action) {
			case enter:
			case update:
				update = (PresenceMessage)update.clone();
				update.action = PresenceMessage.Action.present;
			case present:
				broadcastThisUpdate &= presence.put(update);
				if(updateInternalPresence)
					internalPresence.put(update);
				break;
			case leave:
				broadcastThisUpdate &= presence.remove(update);
				if(updateInternalPresence)
					internalPresence.remove(update);
				break;
			case absent:
			}

			/*
			 * RTP2g: Any incoming presence message that passes the newness check should be emitted on the
			 * Presence object, with an event name set to its original action.
			 */
			if (broadcastThisUpdate)
				broadcastPresence(new PresenceMessage[]{originalUpdate});
		}

		/* if this is the last message in a sequence of sync updates, end the sync */
		if(syncChannelSerial == null || syncCursor.length() <= 1) {
			endSyncAndEmitLeaves();
		}
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
		params = Channel.replacePlaceholderParams(channel, params);

		AblyRealtime ably = channel.ably;
		BodyHandler<PresenceMessage> bodyHandler = PresenceSerializer.getPresenceResponseHandler(channel.options);
		return new PaginatedQuery<>(ably.http, channel.basePath + "/presence/history", HttpUtils.defaultAcceptHeaders(ably.options.useBinaryProtocol), params, bodyHandler).get();
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
		pendingPresence.clear();
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
		pendingPresence.clear();
	}


	/************************************
	 * attach / detach
	 ************************************/

	void setAttached(boolean hasPresence) {
		/* Start sync, if hasPresence is not set end sync immediately dropping all the current presence members */
		presence.startSync();
		syncAsResultOfAttach = true;
		if (!hasPresence) {
			/*
			 * RTP19a  If the PresenceMap has existing members when an ATTACHED message is received without a
			 * HAS_PRESENCE flag, the client library should emit a LEAVE event for each existing member ...
			 */
			endSyncAndEmitLeaves();
		}
		sendQueuedMessages();
	}

	void setDetached(ErrorInfo reason) {
		/* Interrupt get() call if needed */
		synchronized (presence) {
			presence.notifyAll();
		}

		/**
		 * (RTP5a) If the channel enters the DETACHED or FAILED state then all queued presence
		 * messages will fail immediately, and the PresenceMap and internal PresenceMap is cleared.
		 * The latter ensures members are not automatically re-entered if the Channel later becomes attached
		 */
		failQueuedMessages(reason);
		presence.clear();
		internalPresence.clear();
	}

	void setSuspended(ErrorInfo reason) {
		/* Interrupt get() call if needed */
		synchronized (presence) {
			presence.notifyAll();
		}

		/*
		 * (RTP5f) If the channel enters the SUSPENDED state then all queued presence messages will fail
		 * immediately, and the PresenceMap is maintained
		 */
		failQueuedMessages(reason);
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
		 * Wait for sync to be complete. If we are in attaching state wait for initial sync to
		 * complete as well. Return false if wait was interrupted because channel transitioned to
		 * state other than attached or attaching
		 */
		synchronized void waitForSync() throws AblyException, InterruptedException {
			boolean syncIsComplete = false;	/* temporary variable to avoid potential race conditions */
			while((channel.state == ChannelState.attached || channel.state == ChannelState.attaching) &&
					/* = (and not ==) is intentional */
					!(syncIsComplete = (!syncInProgress && syncComplete)))
				wait();

			if (!syncIsComplete) {
				/* invalid channel state */
				int errorCode;
				String errorMessage;

				if (channel.state == ChannelState.suspended) {
					/* (RTP11d) If the Channel is in the SUSPENDED state then the get function will by default,
					 * or if waitForSync is set to true, result in an error with code 91005 and a message stating
					 * that the presence state is out of sync due to the channel being in a SUSPENDED state */
					errorCode = 91005;
					errorMessage = String.format("Channel %s: presence state is out of sync due to the channel being in a SUSPENDED state", channel.name);
				} else {
					errorCode = 90001;
					errorMessage = String.format("Channel %s: cannot get presence state because channel is in invalid state", channel.name);
				}
				Log.v(TAG, errorMessage);
				throw AblyException.fromErrorInfo(new ErrorInfo(errorMessage, errorCode));
			}
		}

		synchronized Collection<PresenceMessage> get(Param[] params) throws AblyException, InterruptedException {
			boolean waitForSync = true;
			String clientId = null;
			String connectionId = null;

			for (Param param: params) {
				switch (param.key) {
					case GET_WAITFORSYNC:
						waitForSync = Boolean.valueOf(param.value);
						break;
					case GET_CLIENTID:
						clientId = param.value;
						break;
					case GET_CONNECTIONID:
						connectionId = param.value;
						break;
				}
			}

			HashSet<PresenceMessage> result = new HashSet<>();
			if (waitForSync)
				waitForSync();

			for (Map.Entry<String, PresenceMessage> entry: members.entrySet()) {
				PresenceMessage member = entry.getValue();
				if ((clientId == null || member.clientId.equals(clientId)) &&
						(connectionId == null || member.connectionId.equals(connectionId)))
					result.add(member);
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
			String key = item.memberKey();
			/* we've seen this member, so do not remove it at the end of sync */
			if(residualMembers != null)
				residualMembers.remove(key);

			/* check if there is a newer existing member (or absent witness) */
			if (hasNewerItem(key, item))
				return false;

			members.put(key, item);
			return true;
		}

		/**
		 * Determine if there is a newer item already in the map
		 * @param key key used to search the item in the map
		 * @param item new presence message to be added
		 * @return true if there is a newer item
		 */
		synchronized boolean hasNewerItem(String key, PresenceMessage item) {
			PresenceMessage existingItem = members.get(key);
			if(existingItem == null)
				return false;

			/*
			 * (RTP2b1) If either presence message has a connectionId which is not an initial substring
			 * of its id, compare them by timestamp numerically. (This will be the case when one of them
			 * is a 'synthesized leave' event sent by realtime to indicate a connection disconnected
			 * unexpectedly 15s ago. Such messages will have an id that does not correspond to its
			 * connectionId, as it wasn’t actually published by that connection
			 */
			if(item.connectionId != null && existingItem.connectionId != null &&
					(!item.id.startsWith(item.connectionId) || !existingItem.id.startsWith(existingItem.connectionId)))
				return existingItem.timestamp >= item.timestamp;

			/*
			 * (RTP2b2) Else split the id of both presence messages (which will be of the form
			 * connid:msgSerial:index, e.g. aaaaaa:0:0) on the separator :, and parse the latter two as
			 * integers. Compare them first by msgSerial numerically, then (if @msgSerial@s are equal) by
			 * index numerically, larger being newer in both cases
			 */
			String[] itemComponents = item.id.split(":", 3);
			String[] existingItemComponents = existingItem.id.split(":", 3);

			if(itemComponents.length < 3 || existingItemComponents.length < 3)
				return false;

			try {
				long messageSerial = Long.valueOf(itemComponents[1]);
				long messageIndex = Long.valueOf(itemComponents[2]);
				long existingMessageSerial = Long.valueOf(existingItemComponents[1]);
				long existingMessageIndex = Long.valueOf(existingItemComponents[2]);

				return existingMessageSerial > messageSerial ||
						(existingMessageSerial == messageSerial && existingMessageIndex >= messageIndex);
			}
			catch(NumberFormatException e) {
				return false;
			}
		}

		/**
		 * Get all members based on the current state (even if sync is in progress)
		 * @return
		 */
		synchronized Collection<PresenceMessage> values() {
			try { return values(false); } catch (InterruptedException|AblyException e) { return null; }
		}

		/**
		 * Get all members, optionally waiting if a sync is in progress.
		 * @param wait
		 * @return
		 * @throws InterruptedException
		 */
		synchronized Collection<PresenceMessage> values(boolean wait) throws AblyException, InterruptedException {
			Set<PresenceMessage> result = new HashSet<PresenceMessage>();
			if(wait)
				waitForSync();
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
			String key = item.memberKey();
			if (hasNewerItem(key, item))
				return false;
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
		 * Finish a sync sequence. Returns "residual" items that were removed as a part of a sync
		 */
		synchronized List<PresenceMessage> endSync() {
			Log.v(TAG, "endSync(); channel = " + channel.name + "; syncInProgress = " + syncInProgress);
			ArrayList<PresenceMessage> removedEntries = new ArrayList<>();
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
				for(String itemKey: residualMembers) {
					/* clone presence message as it still can be in the internal presence map */
					removedEntries.add((PresenceMessage)members.get(itemKey).clone());
					members.remove(itemKey);
				}
				residualMembers = null;
	
				/* finish, notifying any waiters */
				syncInProgress = false;
			}
			syncComplete = true;
			notifyAll();
			return removedEntries;
		}

		/**
		 * Clear all entries
		 */
		synchronized void clear() {
			members.clear();
			if(residualMembers != null)
				residualMembers.clear();
		}

		private boolean syncInProgress;
		private Collection<String> residualMembers;
		private final HashMap<String, PresenceMessage> members = new HashMap<String, PresenceMessage>();
	}

	private final PresenceMap presence = new PresenceMap();
	private final PresenceMap internalPresence = new PresenceMap();

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

	/* channel serial if sync is in progress */
	private String currentSyncChannelSerial;
	/* Sync in progress is a result of attach operation */
	private boolean syncAsResultOfAttach;

	/**
	 * (RTP13) Presence#syncComplete returns true if the initial SYNC operation has completed for
	 * the members present on the channel
	 */
	public boolean syncComplete;
}
