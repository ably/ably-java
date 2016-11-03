package io.ably.lib.test.realtime;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.Test;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.Channel.MessageListener;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.Helpers.ChannelWaiter;
import io.ably.lib.test.common.Helpers.ConnectionWaiter;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.transport.ConnectionManager;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Message;
import io.ably.lib.types.ProtocolMessage;

public class RealtimeChannelTest extends ParameterizedTest {

	private Comparator<Message> messageComparator = new Comparator<Message>() {
		@Override
		public int compare(Message o1, Message o2) {
			int result = o1.name.compareTo(o2.name);
			return (result == 0)?(((String) o1.data).compareTo((String) o2.data)):(result);
		}
	};

	/**
	 * Connect to the service and attach to a channel,
	 * confirming that the attached state is reached.
	 */
	@Test
	public void attach() {
		String channelName = "attach_" + testParams.name;
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			/* wait until connected */
			(new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

			/* create a channel and attach */
			final Channel channel = ably.channels.get(channelName);
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Connect to the service using the default (binary) protocol
	 * and attach before the connected state is reached.
	 */
	@Test
	public void attach_before_connect() {
		String channelName = "attach_before_connect_" + testParams.name;
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			/* create a channel and attach */
			final Channel channel = ably.channels.get(channelName);
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Connect to the service using the default (binary) protocol
	 * and attach, then detach
	 */
	@Test
	public void attach_detach() {
		String channelName = "attach_detach_" + testParams.name;
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			/* create a channel and attach */
			final Channel channel = ably.channels.get(channelName);
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* detach */
			channel.detach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.detached);
			assertEquals("Verify detached state reached", channel.state, ChannelState.detached);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Connect to the service and attach, then subscribe and unsubscribe
	 */
	@Test
	public void subscribe_unsubscribe() {
		String channelName = "subscribe_unsubscribe_" + testParams.name;
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			/* create a channel and attach */
			final Channel channel = ably.channels.get(channelName);
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* subscribe */
			MessageListener testListener =  new MessageListener() {
				@Override
				public void onMessage(Message message) {
				}};
			channel.subscribe("test_event", testListener);
			/* unsubscribe */
			channel.unsubscribe("test_event", testListener);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * <p>
	 * Verifies that unsubscribe call with no argument removes all listeners,
	 * and any of the previously subscribed listeners doesn't receive any message
	 * after that.
	 * </p>
	 * <p>
	 * Spec: RTL8a
	 * </p>
	 */
	@Test
	public void unsubscribe_all() throws AblyException {
		/* Ably instance that will emit messages */
		AblyRealtime ably1 = null;
		/* Ably instance that will receive messages */
		AblyRealtime ably2 = null;

		String channelName = "test.channel.unsubscribe.all" + System.currentTimeMillis();
		Message[] messages = new Message[] {
				new Message("name1", "Lorem ipsum dolor sit amet"),
				new Message("name2", "Consectetur adipiscing elit."),
				new Message("name3", "Pellentesque nulla lorem"),
				new Message("name4", "Efficitur ac consequat a, commodo ut orci."),
		};

		try {
			ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
			option1.clientId = "emitter client";
			ClientOptions option2 = createOptions(testVars.keys[0].keyStr);
			option2.clientId = "receiver client";

			ably1 = new AblyRealtime(option1);
			ably2 = new AblyRealtime(option2);

			Channel channel1 = ably1.channels.get(channelName);
			channel1.attach();
			new ChannelWaiter(channel1).waitFor(ChannelState.attached);

			Channel channel2 = ably2.channels.get(channelName);
			channel2.attach();
			new ChannelWaiter(channel2).waitFor(ChannelState.attached);

			/* Create a listener that collect received messages */
			ArrayList<Message> receivedMessageStack = new ArrayList<>();
			MessageListener listener = new MessageListener() {
				List<Message> messageStack;

				@Override
				public void onMessage(Message message) {
					messageStack.add(message);
				}

				public MessageListener setMessageStack(List<Message> messageStack) {
					this.messageStack = messageStack;
					return this;
				}
			}.setMessageStack(receivedMessageStack);

			/* Subscribe using various alternatives of {@code Channel#subscribe()} */
			channel2.subscribe(listener);
			channel2.subscribe(messages[0].name, listener);
			channel2.subscribe(new String[] {messages[1].name, messages[2].name}, listener);

			/* Unsubscribe */
			channel2.unsubscribe();

			/* Start emitting channel with ably client 1 (emitter) */
			Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
			channel1.publish(messages, waiter);
			waiter.waitFor();

			/* Validate that we didn't received anything
			 */
			assertThat(receivedMessageStack, Matchers.is(Matchers.emptyCollectionOf(Message.class)));
		} finally {
			if (ably1 != null) ably1.close();
			if (ably2 != null) ably2.close();
		}
	}

	/**
	 * <p>
	 * Validates channel removes a subscriber,
	 * when {@code Channel#unsubscribe()} gets called with a listener argument.
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void unsubscribe_single() throws AblyException {
		/* Ably instance that will emit messages */
		AblyRealtime ably1 = null;
		/* Ably instance that will receive messages */
		AblyRealtime ably2 = null;

		String channelName = "test.channel.unsubscribe.single" + System.currentTimeMillis();
		Message[] messages = new Message[] {
				new Message("name1", "Lorem ipsum dolor sit amet"),
				new Message("name2", "Consectetur adipiscing elit."),
				new Message("name3", "Pellentesque nulla lorem"),
				new Message("name4", "Efficitur ac consequat a, commodo ut orci."),
		};

		try {
			ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
			option1.clientId = "emitter client";
			ClientOptions option2 = createOptions(testVars.keys[0].keyStr);
			option2.clientId = "receiver client";

			ably1 = new AblyRealtime(option1);
			ably2 = new AblyRealtime(option2);

			Channel channel1 = ably1.channels.get(channelName);
			channel1.attach();
			new ChannelWaiter(channel1).waitFor(ChannelState.attached);

			Channel channel2 = ably2.channels.get(channelName);
			channel2.attach();
			new ChannelWaiter(channel2).waitFor(ChannelState.attached);

			/* Create a listener that collect received messages */
			ArrayList<Message> receivedMessageStack = new ArrayList<>();
			MessageListener listener = new MessageListener() {
				List<Message> messageStack;

				@Override
				public void onMessage(Message message) {
					messageStack.add(message);
				}

				public MessageListener setMessageStack(List<Message> messageStack) {
					this.messageStack = messageStack;
					return this;
				}
			}.setMessageStack(receivedMessageStack);

			/* Subscribe using various alternatives of {@code Channel#subscribe()} */
			channel2.subscribe(listener);
			channel2.subscribe(messages[0].name, listener);
			channel2.subscribe(new String[] {messages[1].name, messages[2].name}, listener);

			/* Unsubscribe */
			channel2.unsubscribe(listener);

			/* Start emitting channel with ably client 1 (emitter) */
			Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
			channel1.publish(messages, waiter);
			waiter.waitFor();

			/* Validate that we didn't received anything
			 */
			assertThat(receivedMessageStack, Matchers.is(Matchers.emptyCollectionOf(Message.class)));
		} finally {
			if (ably1 != null) ably1.close();
			if (ably2 != null) ably2.close();
		}
	}

	/**
	 * <p>
	 * Validates a client can observe channel messages of other client,
	 * when they entered to the same channel and observing client subscribed
	 * to all messages.
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void subscribe_all() throws AblyException {
		/* Ably instance that will emit channel messages */
		AblyRealtime ably1 = null;
		/* Ably instance that will receive channel messages */
		AblyRealtime ably2 = null;

		String channelName = "test.channel.subscribe.all" + System.currentTimeMillis();
		Message[] messages = new Message[]{
				new Message("name1", "Lorem ipsum dolor sit amet,"),
				new Message("name2", "Consectetur adipiscing elit."),
				new Message("name3", "Pellentesque nulla lorem.")
		};

		try {
			ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
			option1.clientId = "emitter client";
			ClientOptions option2 = createOptions(testVars.keys[0].keyStr);
			option2.clientId = "receiver client";

			ably1 = new AblyRealtime(option1);
			ably2 = new AblyRealtime(option2);

			Channel channel1 = ably1.channels.get(channelName);
			channel1.attach();
			new ChannelWaiter(channel1).waitFor(ChannelState.attached);

			Channel channel2 = ably2.channels.get(channelName);
			channel2.attach();
			new ChannelWaiter(channel2).waitFor(ChannelState.attached);

			/* Create a listener that collect received messages */
			ArrayList<Message> receivedMessageStack = new ArrayList<>();
			MessageListener listener = new MessageListener() {
				List<Message> messageStack;

				@Override
				public void onMessage(Message messages) {
					messageStack.add(messages);
				}

				public MessageListener setMessageStack(List<Message> messageStack) {
					this.messageStack = messageStack;
					return this;
				}
			}.setMessageStack(receivedMessageStack);

			channel2.subscribe(listener);

			/* Start emitting channel with ably client 1 (emitter) */
			channel1.publish(messages, null);

			/* Wait until receiver client (ably2) observes {@code }
			 * is emitted from emitter client (ably1)
			 */
			new Helpers.MessageWaiter(channel2).waitFor(messages.length);

			/* Validate that,
			 *	- we received every message that has been published
			 */
			assertThat(receivedMessageStack.size(), is(equalTo(messages.length)));

			Collections.sort(receivedMessageStack, messageComparator);
			for (int i = 0; i < messages.length; i++) {
				Message message = messages[i];
				if(Collections.binarySearch(receivedMessageStack, message, messageComparator) < 0) {
					fail("Unable to find expected message: " + message);
				}
			}
		} finally {
			if (ably1 != null) ably1.close();
			if (ably2 != null) ably2.close();
		}
	}

	/**
	 * <p>
	 * Validates a client can observe channel messages of other client,
	 * when they entered to the same channel and observing client subscribed
	 * to multiple messages.
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void subscribe_multiple() throws AblyException {
		/* Ably instance that will emit channel messages */
		AblyRealtime ably1 = null;
		/* Ably instance that will receive channel messages */
		AblyRealtime ably2 = null;

		String channelName = "test.channel.subscribe.multiple" + System.currentTimeMillis();
		Message[] messages = new Message[] {
				new Message("name1", "Lorem ipsum dolor sit amet,"),
				new Message("name2", "Consectetur adipiscing elit."),
				new Message("name3", "Pellentesque nulla lorem.")
		};

		String[] messageNames = new String[] {
				messages[0].name,
				messages[1].name,
				messages[2].name
		};

		try {
			ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
			option1.clientId = "emitter client";
			ClientOptions option2 = createOptions(testVars.keys[0].keyStr);
			option2.clientId = "receiver client";

			ably1 = new AblyRealtime(option1);
			ably2 = new AblyRealtime(option2);

			Channel channel1 = ably1.channels.get(channelName);
			channel1.attach();
			new ChannelWaiter(channel1).waitFor(ChannelState.attached);

			Channel channel2 = ably2.channels.get(channelName);
			channel2.attach();
			new ChannelWaiter(channel2).waitFor(ChannelState.attached);

			/* Create a listener that collect received messages */
			ArrayList<Message> receivedMessageStack = new ArrayList<>();
			MessageListener listener = new MessageListener() {
				List<Message> messageStack;

				@Override
				public void onMessage(Message message) {
					messageStack.add(message);
				}

				public MessageListener setMessageStack(List<Message> messageStack) {
					this.messageStack = messageStack;
					return this;
				}
			}.setMessageStack(receivedMessageStack);
			channel2.subscribe(messageNames, listener);

			/* Start emitting channel with ably client 1 (emitter) */
			channel1.publish("nonTrackedMessageName", "This message should be ignore by second client (ably2).", null);
			channel1.publish(messages, null);
			channel1.publish("nonTrackedMessageName", "This message should be ignore by second client (ably2).", null);

			/* Wait until receiver client (ably2) observes {@code Message}
			 * on subscribed channel (channel2) emitted by emitter client (ably1)
			 */
			new Helpers.MessageWaiter(channel2).waitFor(messages.length + 2);

			/* Validate that,
			 *	- we received specific messages
			 */
			assertThat(receivedMessageStack.size(), is(equalTo(messages.length)));

			Collections.sort(receivedMessageStack, messageComparator);
			for (int i = 0; i < messages.length; i++) {
				Message message = messages[i];
				if(Collections.binarySearch(receivedMessageStack, message, messageComparator) < 0) {
					fail("Unable to find expected message: " + message);
				}
			}
		} finally {
			if (ably1 != null) ably1.close();
			if (ably2 != null) ably2.close();
		}
	}

	/**
	 * <p>
	 * Validates a client can observe channel messages of other client,
	 * when they entered to the same channel and observing client subscribed
	 * to a single message.
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void subscribe_single() throws AblyException {
		/* Ably instance that will emit channel messages */
		AblyRealtime ably1 = null;
		/* Ably instance that will receive channel messages */
		AblyRealtime ably2 = null;

		String channelName = "test.channel.subscribe.single" + System.currentTimeMillis();
		String messageName = "name";
		Message[] messages = new Message[] {
				new Message(messageName, "Lorem ipsum dolor sit amet,"),
				new Message(messageName, "Consectetur adipiscing elit."),
				new Message(messageName, "Pellentesque nulla lorem.")
		};

		try {
			ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
			option1.clientId = "emitter client";
			ClientOptions option2 = createOptions(testVars.keys[0].keyStr);
			option2.clientId = "receiver client";

			ably1 = new AblyRealtime(option1);
			ably2 = new AblyRealtime(option2);

			Channel channel1 = ably1.channels.get(channelName);
			channel1.attach();
			new ChannelWaiter(channel1).waitFor(ChannelState.attached);

			Channel channel2 = ably2.channels.get(channelName);
			channel2.attach();
			new ChannelWaiter(channel2).waitFor(ChannelState.attached);

			ArrayList<Message> receivedMessageStack = new ArrayList<>();
			MessageListener listener = new MessageListener() {
				List<Message> messageStack;

				@Override
				public void onMessage(Message message) {
					messageStack.add(message);
				}

				public MessageListener setMessageStack(List<Message> messageStack) {
					this.messageStack = messageStack;
					return this;
				}
			}.setMessageStack(receivedMessageStack);
			channel2.subscribe(messageName, listener);

			/* Start emitting channel with ably client 1 (emitter) */
			channel1.publish("nonTrackedMessageName", "This message should be ignore by second client (ably2).", null);
			channel1.publish(messages, null);
			channel1.publish("nonTrackedMessageName", "This message should be ignore by second client (ably2).", null);

			/* Wait until receiver client (ably2) observes {@code Message}
			 * on subscribed channel (channel2) emitted by emitter client (ably1)
			 */
			new Helpers.MessageWaiter(channel2).waitFor(messages.length + 2);

			/* Validate that,
			 *	- received same amount of emitted specific message
			 *  - received messages are the ones we emitted
			 */
			assertThat(receivedMessageStack.size(), is(equalTo(messages.length)));

			Collections.sort(receivedMessageStack, messageComparator);
			for (int i = 0; i < messages.length; i++) {
				Message message = messages[i];
				if(Collections.binarySearch(receivedMessageStack, message, messageComparator) < 0) {
					fail("Unable to find expected message: " + message);
				}
			}
		} finally {
			if (ably1 != null) ably1.close();
			if (ably2 != null) ably2.close();
		}
	}


	/**
	 * Connect to the service using the default (binary) protocol
	 * and attempt to attach to a channel with credentials that do
	 * not have access, confirming that the failed state is reached.
	 */
	@Test
	public void attach_fail() {
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[1].keyStr);
			ably = new AblyRealtime(opts);

			/* wait until connected */
			(new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

			/* create a channel and attach */
			final Channel channel = ably.channels.get("attach_fail");
			channel.attach();
			ErrorInfo fail = (new ChannelWaiter(channel)).waitFor(ChannelState.failed);
			assertEquals("Verify failed state reached", channel.state, ChannelState.failed);
			assertEquals("Verify reason code gives correct failure reason", fail.statusCode, 401);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * When client attaches to a channel successfully, verify
	 * attach {@code CompletionListener#onSuccess()} gets called.
	 */
	@Test
	public void attach_success_callback() {
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			/* wait until connected */
			(new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

			/* create a channel and attach */
			final Channel channel = ably.channels.get("attach_success");
			Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
			channel.attach(waiter);
			new ChannelWaiter(channel).waitFor(ChannelState.attached);
			assertEquals("Verify failed state reached", channel.state, ChannelState.attached);

			/* Verify onSuccess callback gets called */
			waiter.waitFor();
			assertThat(waiter.success, is(true));
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * When client failed to attach to a channel, verify
	 * attach {@code CompletionListener#onError(ErrorInfo)}
	 * gets called.
	 */
	@Test
	public void attach_fail_callback() {
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[1].keyStr);
			ably = new AblyRealtime(opts);

			/* wait until connected */
			(new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

			/* create a channel and attach */
			final Channel channel = ably.channels.get("attach_fail");
			Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
			channel.attach(waiter);
			ErrorInfo fail = (new ChannelWaiter(channel)).waitFor(ChannelState.failed);
			assertEquals("Verify failed state reached", channel.state, ChannelState.failed);
			assertEquals("Verify reason code gives correct failure reason", fail.statusCode, 401);

			/* Verify error callback gets called with correct status code */
			waiter.waitFor();
			assertThat(waiter.error.statusCode, is(equalTo(401)));
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * When client detaches from a channel successfully after initialized state,
	 * verify attach {@code CompletionListener#onSuccess()} gets called.
	 */
	@Test
	public void detach_success_callback_initialized() {
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			/* wait until connected */
			(new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

			/* create a channel and attach */
			final Channel channel = ably.channels.get("detach_success");
			assertEquals("Verify failed state reached", channel.state, ChannelState.initialized);

			/* detach */
			Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
			channel.detach(waiter);

			/* Verify onSuccess callback gets called */
			waiter.waitFor();
			assertThat(waiter.success, is(true));
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * When client detaches from a channel successfully after attached state,
	 * verify attach {@code CompletionListener#onSuccess()} gets called.
	 */
	@Test
	public void detach_success_callback_attached() throws AblyException {
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			/* wait until connected */
			(new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

			/* create a channel and attach */
			final Channel channel = ably.channels.get("detach_success");
			channel.attach();
			new ChannelWaiter(channel).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* detach */
			Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
			channel.detach(waiter);

			/* Verify onSuccess callback gets called */
			waiter.waitFor();
			assertThat(waiter.success, is(true));
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * When client detaches from a channel successfully after detaching state,
	 * verify attach {@code CompletionListener#onSuccess()} gets called.
	 */
	@Test
	public void detach_success_callback_detaching() throws AblyException {
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			/* wait until connected */
			(new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

			/* create a channel and attach */
			final Channel channel = ably.channels.get("detach_success");
			channel.attach();
			new ChannelWaiter(channel).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* detach */
			channel.detach();
			assertEquals("Verify detaching state reached", channel.state, ChannelState.detaching);
			Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
			channel.detach(waiter);

			/* Verify onSuccess callback gets called */
			waiter.waitFor();
			assertThat(waiter.success, is(true));
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * When client detaches from a channel successfully after detached state,
	 * verify attach {@code CompletionListener#onSuccess()} gets called.
	 */
	@Test
	public void detach_success_callback_detached() throws AblyException {
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			/* wait until connected */
			(new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

			/* create a channel and attach */
			final Channel channel = ably.channels.get("detach_success");
			channel.attach();
			new ChannelWaiter(channel).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* detach */
			channel.detach();
			new ChannelWaiter(channel).waitFor(ChannelState.detached);

			Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
			channel.detach(waiter);

			/* Verify onSuccess callback gets called */
			waiter.waitFor();
			assertThat(waiter.success, is(true));
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * <p>
	 * Validate publish will result in an error, when the channel moves
	 * to the FAILED state before the operation succeeds
	 * </p>
	 * <p>
	 * Spec: RTL6c3
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void attach_implicit_publish_fail() throws AblyException {
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[1].keyStr);
			ably = new AblyRealtime(opts);

			/* wait until connected */
			new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

			/* create a channel and subscribe */
			final Channel channel = ably.channels.get("publish_fail");
			Helpers.CompletionWaiter completionWaiter = new Helpers.CompletionWaiter();
			channel.publish("Lorem", "Ipsum!", completionWaiter);
			assertEquals("Verify attaching state reached", channel.state, ChannelState.attaching);

			ErrorInfo errorInfo = completionWaiter.waitFor();

			assertEquals("Verify failed state reached", channel.state, ChannelState.failed);
			assertEquals("Verify reason code gives correct failure reason", errorInfo.statusCode, 401);
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * <p>
	 * Validate subscribe will result in an error, when the channel moves
	 * to the FAILED state before the operation succeeds
	 * </p>
	 * <p>
	 * Spec: RTL7c
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void attach_implicit_subscribe_fail() throws AblyException {
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[1].keyStr);
			ably = new AblyRealtime(opts);

			/* wait until connected */
			new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

			/* create a channel and subscribe */
			final Channel channel = ably.channels.get("subscribe_fail");
			channel.subscribe(null);
			assertEquals("Verify attaching state reached", channel.state, ChannelState.attaching);

			ErrorInfo fail = new ChannelWaiter(channel).waitFor(ChannelState.failed);
			assertEquals("Verify failed state reached", channel.state, ChannelState.failed);
			assertEquals("Verify reason code gives correct failure reason", fail.statusCode, 401);
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	@Test
	public void ensure_detach_with_error_does_not_move_to_failed() {
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			/* wait until connected */
			(new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

			/* create a channel and attach */
			final Channel channel = ably.channels.get("test");
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			ProtocolMessage protoMessage = new ProtocolMessage(ProtocolMessage.Action.detach, "test");
			protoMessage.error = new ErrorInfo("test error", 123);

			ConnectionManager connectionManager = ably.connection.connectionManager;
			connectionManager.onMessage(protoMessage);

			assertEquals("channel state should be detached now", channel.state, ChannelState.detached);
			assertEquals("channel error reason should be set", channel.reason, protoMessage.error);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	@Test
	public void detach_on_clean_connection_preserves_channel() {
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);

			/* connect with these options to get a valid connection recover key */
			ably = new AblyRealtime(opts);

			/* wait until connected */
			(new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

			/* keep connection details and close */
			String recoverConnectionId = ably.connection.key;
			long recoverConnectionSerial = ably.connection.serial;
			ably.close();

			/* establish a new connection */
			ably = new AblyRealtime(opts);

			/* wait until connected */
			(new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

			/* create a channel and attach */
			final String channelName = "detach_on_clean_connection_preserves_channel";
			final Channel channel = ably.channels.get(channelName);
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* disconnect the connection, without closing;
			 * NOTE this depends on knowledge of the internal structure
			 * of the library, to simulate a dropped transport without
			 * causing the connection itself to be disposed */
			ably.connection.connectionManager.requestState(ConnectionState.failed);

			/* wait */
			try { Thread.sleep(2000L); } catch(InterruptedException e) {}

			/* reconnect the connection; this time attempting to recover the (now-closed) recovery key */
			ably.options.recover = recoverConnectionId + ':' + String.valueOf(recoverConnectionSerial);
			ably.connection.key = null;
			ably.connection.connect();

			/* wait until connected */
			(new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);
			assertNotEquals("Verify new connection established", recoverConnectionId, ably.connection.id);
			assertNotNull("Verify error was returned with connected state", ably.connection.reason);

			/* verify existing channel is failed but not removed */
			(new ChannelWaiter(channel)).waitFor(ChannelState.failed);
			assertEquals("Verify failed state reached", channel.state, ChannelState.failed);
			assertTrue("Verify the original channel remains in the channel set", ably.channels.get(channelName) == channel);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}
}
