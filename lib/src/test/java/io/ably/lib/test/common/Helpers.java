package io.ably.lib.test.common;

import java.util.*;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.ably.lib.debug.RawProtocolListener;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.Channel.MessageListener;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.realtime.ChannelStateListener;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.realtime.Connection;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.realtime.ConnectionStateListener;
import io.ably.lib.realtime.Presence.PresenceListener;
import io.ably.lib.transport.ConnectionManager;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.BaseMessage;
import io.ably.lib.types.Callback;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Message;
import io.ably.lib.types.PresenceMessage;
import io.ably.lib.types.ProtocolMessage;
import io.ably.lib.types.ProtocolMessage.Action;

public class Helpers {

	/**
	 * Trivial container for an int as counter.
	 * @author paddy
	 *
	 */
	private static class Counter {
		public int value;
		public int incr() { return ++value; }
	}

	/**
	 * A class that may be passed as a listener for completion
	 * of an async operation.
	 * @author paddy
	 *
	 */
	public static class CompletionWaiter implements CompletionListener {
		public boolean success;
		public ErrorInfo error;

		/**
		 * Public API
		 */
		public CompletionWaiter() {}

		public synchronized ErrorInfo waitFor() {
			while(!success && error == null)
				try { wait(); } catch(InterruptedException e) {}
			return error;
		}

		/**
		 * CompletionListener methods
		 */
		@Override
		public void onSuccess() {
			synchronized(this) {
				success = true;
				notifyAll();
			}
		}
		@Override
		public void onError(ErrorInfo reason) {
			synchronized(this) {
				error = reason;
				notifyAll();
			}
		}
	}

	/**
	 * A class that subscribes to a channel and tracks messages received.
	 * @author paddy
	 *
	 */
	public static class MessageWaiter implements MessageListener {
		public List<Message> receivedMessages;

		/**
		 * Public API
		 */

		/**
		 * Track all messages on a channel.
		 * @param channel
		 */
		public MessageWaiter(Channel channel) {
			reset();
			try {
				channel.subscribe(this);
			} catch(AblyException e) {}
		}

		/**
		 * Track messages on a channel with a given event name
		 * @param channel
		 * @param event
		 */
		public MessageWaiter(Channel channel, String event) {
			try {
				channel.subscribe(event, this);
			} catch(AblyException e) {}
		}

		/**
		 * Wait for a given number of messages
		 * @param count
		 */
		public synchronized void waitFor(int count) {
			while(receivedMessages.size() < count)
				try { wait(); } catch(InterruptedException e) {}
		}

		/**
		 * Wait for a given interval for a number of messages
		 * @param count
		 */
		public synchronized void waitFor(int count, long time) {
			long targetTime = System.currentTimeMillis() + time;
			long remaining = time;
			while(receivedMessages.size() < count && remaining > 0) {
				try { wait(remaining); } catch(InterruptedException e) {}
				remaining = targetTime - System.currentTimeMillis();
			}
		}

		/**
		 * Reset the counter. Waiters will continue to
		 * wait, and will be unblocked when the revised count
		 * meets their requirements.
		 */
		public synchronized void reset() {
			receivedMessages = new ArrayList<Message>();
		}

		/**
		 * MessageListener interface
		 */
		@Override
		public void onMessage(Message message) {
			synchronized(this) {
				receivedMessages.add(message);
				notify();
			}
		}
	}

	/**
	 * A class that tracks presence events on a channel
	 * @author paddy
	 *
	 */
	public static class PresenceWaiter implements PresenceListener {
		public List<PresenceMessage> receivedMessages;

		/**
		 * Public API
		 * @param channel
		 */
		public PresenceWaiter(Channel channel) {
			reset();
			try {
				channel.presence.subscribe(this);
			} catch(AblyException e) {}
		}

		public PresenceWaiter(PresenceMessage.Action event, Channel channel) throws AblyException {
			reset();
			channel.presence.subscribe(event, this);
		}

		public PresenceWaiter(EnumSet<PresenceMessage.Action> events, Channel channel) throws AblyException {
			reset();
			channel.presence.subscribe(events, this);
		}

		/**
		 * Wait for a given count of any type of message
		 * @param count
		 */
		public synchronized void waitFor(int count) {
			while(receivedMessages.size() < count)
				try { wait(); } catch(InterruptedException e) {}
		}

		/**
		 * Wait for a given clientId.
		 * @param clientId
		 */
		public synchronized void waitFor(String clientId) {
			while(contains(clientId) == null)
				try { wait(); } catch(InterruptedException e) {}
		}

		/**
		 * Wait for a given count of messages for a given clientId
		 * having a given action.
		 * @param clientId
		 * @param action
		 */
		public synchronized void waitFor(String clientId, PresenceMessage.Action action) {
			while(contains(clientId, action) == null)
				try { wait(); } catch(InterruptedException e) {}
		}

		/**
		 * Reset the counter. Waiters will continue to
		 * wait, and will be unblocked when the revised count
		 * meets their requirements.
		 */
		public synchronized void reset() {
			receivedMessages = new ArrayList<PresenceMessage>();
		}

		/**
		 * PresenceListener API
		 */
		@Override
		public void onPresenceMessage(PresenceMessage message) {
			synchronized(this) {
				receivedMessages.add(message);
				notify();
			}
		}

		/**
		 * Internal
		 */
		PresenceMessage contains(String clientId) {
			for(PresenceMessage message : receivedMessages)
				if(clientId.equals(message.clientId))
					return message;
			return null;
		}
		public PresenceMessage contains(String clientId, PresenceMessage.Action action) {
			for(PresenceMessage message : receivedMessages)
				if(clientId.equals(message.clientId) && action == message.action)
					return message;
			return null;
		}
		public PresenceMessage contains(String clientId, String connectionId, PresenceMessage.Action action) {
			for(PresenceMessage message : receivedMessages)
				if(clientId.equals(message.clientId) && connectionId.equals(message.connectionId) && action == message.action)
					return message;
			return null;
		}
	}

	/**
	 * A class that listens for state change events on a connection.
	 * @author paddy
	 *
	 */
	public static class ConnectionWaiter implements ConnectionStateListener {
		public Map<ConnectionState, Counter> stateCounts;

		/**
		 * Public API
		 */
		public ConnectionWaiter(Connection connection) {
			reset();
			this.connection = connection;
			connection.on(this);
		}

		/**
		 * Wait for a given state to be reached.
		 * @param state
		 * @return error info
		 */
		public synchronized ErrorInfo waitFor(ConnectionState state) {
			while(connection.state != state)
				try { wait(); } catch(InterruptedException e) {}
			return reason;
		}

		/**
		 * Wait for a given state to be reached a given number of times.
		 * @param state
		 * @param count
		 */
		public synchronized void waitFor(ConnectionState state, int count) {
			while(connection.state != state || stateCounts.get(state).value < count)
				try { wait(); } catch(InterruptedException e) {}
		}

		/**
		 * Get the count of number of times visited for a given state.
		 * @param state
		 * @return
		 */
		public synchronized int getCount(ConnectionState state) {
			return stateCounts.get(state).value;
		}

		/**
		 * Reset counters. Waiters will continue to
		 * wait, and will be unblocked when the revised count
		 * meets their requirements.
		 */
		public synchronized void reset() {
			stateCounts = new HashMap<ConnectionState, Counter>();
		}

		/**
		 * ConnectionStateListener interface
		 */
		@Override
		public void onConnectionStateChanged(ConnectionStateListener.ConnectionStateChange state) {
			synchronized(this) {
				reason = state.reason;
				Counter counter = stateCounts.get(state.current); if(counter == null) stateCounts.put(state.current, (counter = new Counter()));
				counter.incr();
				notify();
			}
		}

		/**
		 * Internal
		 */
		private Connection connection;
		private ErrorInfo reason;
	}

	/**
	 * A class that listens for state change events on a {@code ConnectionManager}.
	 */
	public static class ConnectionManagerWaiter {
		private static final long INTERVAL_POLLING = 1000;

		/**
		 * Public API
		 */
		public ConnectionManagerWaiter(ConnectionManager connectionManager) {
			this.connectionManager = connectionManager;
		}

		/**
		 * Wait for a given state to be reached.
		 * @param state
		 * @return error info
		 */
		public synchronized ErrorInfo waitFor(ConnectionState state) {
			while(connectionManager.getConnectionState().state != state)
				try { wait(INTERVAL_POLLING); } catch(InterruptedException e) {}
			return connectionManager.getConnectionState().defaultErrorInfo;
		}

		/**
		 * Internal
		 */
		private ConnectionManager connectionManager;
	}

	/**
	 * A class that listens for state change events on a channel.
	 * @author paddy
	 *
	 */
	public static class ChannelWaiter implements ChannelStateListener {

		/**
		 * Public API
		 * @param channel
		 */
		public ChannelWaiter(Channel channel) {
			this.channel = channel;
			channel.on(this);
		}

		/**
		 * Wait for a given state to be reached.
		 * @param state
		 */
		public synchronized ErrorInfo waitFor(ChannelState state) {
			while(channel.state != state)
				try { wait(); } catch(InterruptedException e) {}
			return channel.reason;
		}

		/**
		 * ChannelStateListener interface
		 */
		@Override
		public void onChannelStateChanged(ChannelState state, ErrorInfo reason) {
			synchronized(this) { notify(); }
		}

		/**
		 * Internal
		 */
		private Channel channel;
	}

	/**
	 * A class that waits for raw protocol messages.
	 *
	 */
	public static class RawProtocolWaiter implements RawProtocolListener {
		public List<ProtocolMessage> receivedMessages;
		public Action action;

		/**
		 * Public API
		 */
		public RawProtocolWaiter(Action action) {
			this.action = action;
			reset();
		}

		/**
		 * Wait for a given number of messages
		 * @param count
		 */
		public void waitFor() {
			waitFor(1);
		}

		/**
		 * Wait for a given number of messages
		 * @param count
		 */
		public synchronized void waitFor(int count) {
			while(receivedMessages.size() < count) {
				try { wait(); } catch(InterruptedException e) {}
			}
		}

		/**
		 * Reset the counter. Waiters will continue to
		 * wait, and will be unblocked when the revised count
		 * meets their requirements.
		 */
		public synchronized void reset() {
			receivedMessages = new ArrayList<ProtocolMessage>();
		}


		/**
		 * RawProtocolListener interface
		 */
		@Override
		public void onRawMessage(ProtocolMessage message) {
			if(message.action == action) {
				synchronized(this) {
					receivedMessages.add(message);
					notify();
				}
			}
		}
	}

	/**
	 * A class that allows a series of async operations to be
	 * tracked, and allows a caller to wait for all to complete.
	 * @author paddy
	 *
	 */
	public static class CompletionSet {
		public Set<Member> pending = new HashSet<Member>();
		public Set<ErrorInfo> errors = new HashSet<ErrorInfo>();

		/**
		 * Member type. A Member instance exists for each of
		 * the operations added to a CompletionSet.
		 * @author paddy
		 *
		 */
		public class Member implements CompletionListener {
			@Override
			public void onSuccess() {
				synchronized(CompletionSet.this) {
					pending.remove(this);
					CompletionSet.this.notifyAll();
				}
			}
			@Override
			public void onError(ErrorInfo reason) {
				synchronized(CompletionSet.this) {
					pending.remove(this);
					errors.add(reason);
					CompletionSet.this.notifyAll();
				}
			}
		}

		/**
		 * Obtain a new member/listener to associate with an
		 * operation to be added to this set.
		 * @return
		 */
		public Member add() {
			Member member = new Member();
			synchronized(CompletionSet.this) { pending.add(member); }
			return member;
		}

		/**
		 * Wait for all members to complete.
		 * @return an array of errors.
		 */
		public ErrorInfo[] waitFor() {
			synchronized(CompletionSet.this) {
				while(pending.size() > 0)
					try { CompletionSet.this.wait(); } catch(InterruptedException e) {}
			}
			return errors.toArray(new ErrorInfo[errors.size()]);
		}
	}

	public static boolean compareString(String one, String two) {
		return (one == null) ? (two == null) : (one.equals(two));
	}

	public static boolean compareBytes(byte[] one, byte[] two) {
		if(one == null) return (two == null);
		if(one.length != two.length) return false;
		for(int i = 0; i < one.length; i++)
			if(one[i] != two[i]) return false;
		return true;
	}

	public static boolean compareMessage(BaseMessage one, BaseMessage two) {
		if(!compareString(one.encoding, two.encoding)) return false;
		if(one.data == null) return (two.data == null);
		if(one.getClass() != two.getClass()) return false;
		if(one.data instanceof String) {
			return compareString((String)one.data, (String)two.data);
		}
		if(one.data instanceof byte[]) {
			return compareBytes((byte[])one.data, (byte[])two.data);
		}
		if(one.data instanceof JsonObject || one.data instanceof JsonArray) {
			Gson gson = new Gson();
			return compareString(gson.toJson((JsonElement)one.data), gson.toJson((JsonElement)two.data));
		}
		return false;
	}

	public static class AsyncWaiter<T> implements Callback<T> {

		@Override
		public synchronized void onSuccess(T result) {
			this.result = result;
			notify();
		}

		@Override
		public synchronized void onError(ErrorInfo error) {
			this.error = error;
			notify();
		}

		public synchronized void waitFor() {
			try {
				while(result == null && error == null) {
					wait();
				}
			} catch(InterruptedException e) {}
		}

		public T result;
		public ErrorInfo error;
	}
}
