package io.ably.lib.test.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.test.common.Setup;
import io.ably.lib.test.common.Helpers.ChannelWaiter;
import io.ably.lib.test.common.Helpers.CompletionSet;
import io.ably.lib.test.common.Helpers.CompletionWaiter;
import io.ably.lib.test.common.Helpers.MessageWaiter;
import io.ably.lib.test.common.Setup.TestVars;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.Message;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.Param;

import java.util.HashMap;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class RealtimeChannelHistoryTest {

	private static AblyRealtime ably;
	private static long timeOffset;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		TestVars testVars = Setup.getTestVars();
		ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
		ably = new AblyRealtime(opts);
		long timeFromService = ably.time();
		timeOffset = timeFromService - System.currentTimeMillis();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		Setup.clearTestVars();
	}

	/**
	 * Send a single message on a channel and verify that it can be
	 * retrieved using channel.history() without needing to wait for
	 * it to be persisted.
	 */
	@Test
	public void channelhistory_simple_binary() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);
			String channelName = "persisted:channelhistory_simple_binary";
			String messageText = "Test message (channelhistory_simple_binary)";

			/* create a channel */
			final Channel channel = ably.channels.get(channelName);

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* publish to the channel */
			CompletionWaiter msgComplete = new CompletionWaiter();
			channel.publish("test_event", messageText, msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);

			/* get the history for this channel */
			PaginatedResult<Message> messages = channel.history(null);
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 1 message", messages.items().length, 1);

			/* verify message contents */
			assertEquals("Expect correct message text", messages.items()[0].data, messageText);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("single_history_binary: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Send a single message on a channel and verify that it can be
	 * retrieved using channel.history() without needing to wait for
	 * it to be persisted.
	 */
	@Test
	public void channelhistory_simple_text() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			opts.useBinaryProtocol = false;
			ably = new AblyRealtime(opts);
			String channelName = "persisted:channelhistory_simple_text";
			String messageText = "Test message (channelhistory_simple_text)";

			/* create a channel */
			final Channel channel = ably.channels.get(channelName);

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* publish to the channel */
			CompletionWaiter msgComplete = new CompletionWaiter();
			channel.publish("test_event", messageText, msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);

			/* get the history for this channel */
			PaginatedResult<Message> messages = channel.history(null);
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 1 message", messages.items().length, 1);

			/* verify message contents */
			assertEquals("Expect correct message text", messages.items()[0].data, messageText);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("single_history_binary: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Publish events with data of various datatypes
	 */
	@Test
	public void channelhistory_types_binary() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);
			String channelName = "persisted:channelhistory_types_binary";

			/* create a channel */
			final Channel channel = ably.channels.get(channelName);

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* publish to the channel */
			CompletionSet msgComplete = new CompletionSet();
			channel.publish("history0", "This is a string message payload", msgComplete.add());
			channel.publish("history1", "This is a byte[] message payload".getBytes(), msgComplete.add());

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.errors.isEmpty());

			/* get the history for this channel */
			PaginatedResult<Message> messages = channel.history(null);
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 2 messages", messages.items().length, 2);
			HashMap<String, Message> messageContents = new HashMap<String, Message>();

			/* verify message contents */
			for(Message message : messages.items())
				messageContents.put(message.name, message);
			assertEquals("Expect history0 to be expected String", messageContents.get("history0").data, "This is a string message payload");
			assertEquals("Expect history1 to be expected byte[]", new String((byte[])messageContents.get("history1").data), "This is a byte[] message payload");

			/* verify message order */
			Message[] expectedMessageHistory = new Message[]{
				messageContents.get("history1"),
				messageContents.get("history0")
			};
			Assert.assertArrayEquals("Expect messages in reverse order", messages.items(), expectedMessageHistory);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("channelhistory_types: Unexpected exception");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Publish events with data of various datatypes
	 */
	@Test
	public void channelhistory_types_text() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			opts.useBinaryProtocol = false;
			ably = new AblyRealtime(opts);
			String channelName = "persisted:channelhistory_types_text";

			/* create a channel */
			final Channel channel = ably.channels.get(channelName);

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* publish to the channel */
			CompletionSet msgComplete = new CompletionSet();
			channel.publish("history0", "This is a string message payload", msgComplete.add());
			channel.publish("history1", "This is a byte[] message payload".getBytes(), msgComplete.add());

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.errors.isEmpty());

			/* get the history for this channel */
			PaginatedResult<Message> messages = channel.history(null);
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 2 messages", messages.items().length, 2);
			HashMap<String, Message> messageContents = new HashMap<String, Message>();

			/* verify message contents */
			for(Message message : messages.items())
				messageContents.put(message.name, message);
			assertEquals("Expect history0 to be expected String", messageContents.get("history0").data, "This is a string message payload");
			assertEquals("Expect history1 to be expected byte[]", new String((byte[])messageContents.get("history1").data), "This is a byte[] message payload");

			/* verify message order */
			Message[] expectedMessageHistory = new Message[]{
				messageContents.get("history1"),
				messageContents.get("history0")
			};
			Assert.assertArrayEquals("Expect messages in reverse order", messages.items(), expectedMessageHistory);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("channelhistory_types: Unexpected exception");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Publish events with data of various datatypes
	 */
	@Test
	public void channelhistory_types_forward() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);
			String channelName = "persisted:channelhistory_types_forward";

			/* create a channel */
			final Channel channel = ably.channels.get(channelName);

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* publish to the channel */
			CompletionSet msgComplete = new CompletionSet();
			channel.publish("history0", "This is a string message payload", msgComplete.add());
			channel.publish("history1", "This is a byte[] message payload".getBytes(), msgComplete.add());

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.errors.isEmpty());

			/* get the history for this channel */
			PaginatedResult<Message> messages = channel.history(new Param[] { new Param("direction", "forwards") });
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 2 messages", messages.items().length, 2);
			HashMap<String, Message> messageContents = new HashMap<String, Message>();

			/* verify message contents */
			for(Message message : messages.items())
				messageContents.put(message.name, message);
			assertEquals("Expect history0 to be expected String", messageContents.get("history0").data, "This is a string message payload");
			assertEquals("Expect history1 to be expected byte[]", new String((byte[])messageContents.get("history1").data), "This is a byte[] message payload");

			/* verify message order */
			Message[] expectedMessageHistory = new Message[]{
				messageContents.get("history0"),
				messageContents.get("history1")
			};
			Assert.assertArrayEquals("Expect messages in sent order", messages.items(), expectedMessageHistory);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("channelhistory_types_forward: Unexpected exception");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Connect twice to the service, each using the default (binary) protocol.
	 * Publish messages on one connection to a given channel; then attach
	 * the second connection to the same channel and verify a complete message
	 * history can be obtained. 
	 */
	@Test
	public void channelhistory_second_channel() {
		AblyRealtime txAbly = null, rxAbly = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions txOpts = testVars.createOptions(testVars.keys[0].keyStr);
			txAbly = new AblyRealtime(txOpts);
			ClientOptions rxOpts = testVars.createOptions(testVars.keys[0].keyStr);
			rxAbly = new AblyRealtime(rxOpts);
			String channelName = "persisted:channelhistory_second_channel";
	
			/* create a channel */
			final Channel txChannel = txAbly.channels.get(channelName);
			final Channel rxChannel = rxAbly.channels.get(channelName);
	
			/* attach sender */
			txChannel.attach();
			(new ChannelWaiter(txChannel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", txChannel.state, ChannelState.attached);
	
			/* publish to the channel */
			String messageText = "Test message (channelhistory_second_channel)";
			CompletionWaiter msgComplete = new CompletionWaiter();
			txChannel.publish("test_event", messageText, msgComplete);
	
			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);
	
			/* attach receiver */
			rxChannel.attach();
			(new ChannelWaiter(rxChannel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", rxChannel.state, ChannelState.attached);

			/* get the history for this channel */
			PaginatedResult<Message> messages = rxChannel.history(null);
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 1 message", messages.items().length, 1);

			/* verify message contents */
			assertEquals("Expect correct message text", messages.items()[0].data, messageText);
	
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(txAbly != null)
				txAbly.close();
			if(rxAbly != null)
				rxAbly.close();
		}
	}

	/**
	 * Send a single message on a channel and verify that it can be
	 * retrieved using channel.history() after waiting for it to be
	 * persisted.
	 */
	@Test
	public void channelhistory_wait_binary_b() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);
			String channelName = "persisted:channelhistory_wait_binary_b";
			String messageText = "Test message (channelhistory_wait_binary_b)";

			/* create a channel */
			final Channel channel = ably.channels.get(channelName);

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* publish to the channel */
			CompletionWaiter msgComplete = new CompletionWaiter();
			channel.publish("test_event", messageText, msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);

			/* wait for the history to be persisted */
			try {
				Thread.sleep(16000);
			} catch(InterruptedException ie) {}

			/* get the history for this channel */
			PaginatedResult<Message> messages = channel.history(null);
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 1 message", messages.items().length, 1);

			/* verify message contents */
			assertEquals("Expect correct message text", messages.items()[0].data, messageText);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("single_history_binary: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Send a single message on a channel using the text protocol and verify
	 * that it can be retrieved using channel.history() after waiting for it
	 * to be persisted.
	 */
	@Test
	public void channelhistory_wait_text_b() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			opts.useBinaryProtocol = false;
			ably = new AblyRealtime(opts);
			String channelName = "persisted:channelhistory_wait_text_b";
			String messageText = "Test message (channelhistory_wait_text_b)";

			/* create a channel */
			final Channel channel = ably.channels.get(channelName);

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* publish to the channel */
			CompletionWaiter msgComplete = new CompletionWaiter();
			channel.publish("test_event", messageText, msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);

			/* wait for the history to be persisted */
			try {
				Thread.sleep(16000);
			} catch(InterruptedException ie) {}

			/* get the history for this channel */
			PaginatedResult<Message> messages = channel.history(null);
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 1 message", messages.items().length, 1);

			/* verify message contents */
			assertEquals("Expect correct message text", messages.items()[0].data, messageText);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("single_history_binary: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Send a single message on a channel and verify that it can be
	 * retrieved using channel.history(direction=forwards) after waiting
	 * for it to be persisted.
	 */
	@Test
	public void channelhistory_wait_binary_f() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);
			String channelName = "persisted:channelhistory_wait_binary_f";
			String messageText = "Test message (channelhistory_wait_binary_f)";

			/* create a channel */
			final Channel channel = ably.channels.get(channelName);

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* publish to the channel */
			CompletionWaiter msgComplete = new CompletionWaiter();
			channel.publish("test_event", messageText, msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);

			/* wait for the history to be persisted */
			try {
				Thread.sleep(16000);
			} catch(InterruptedException ie) {}

			/* get the history for this channel */
			PaginatedResult<Message> messages = channel.history(new Param[]{ new Param("direction", "forwards") });
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 1 message", messages.items().length, 1);

			/* verify message contents */
			assertEquals("Expect correct message text", messages.items()[0].data, messageText);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("single_history_binary: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Send a single message on a channel, wait enough time for it to
	 * persist, then send a second message. Verify that both can be
	 * retrieved using channel.history() without any further wait.
	 */
	@Test
	public void channelhistory_mixed_binary_b() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);
			String channelName = "persisted:channelhistory_mixed_binary_b";
			String messageText = "Test message (channelhistory_mixed_binary_b)";
			String persistEventName = "test_event (persisted)";
			String liveEventName = "test_event (live)";

			/* create a channel */
			final Channel channel = ably.channels.get(channelName);

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* publish to the channel */
			CompletionWaiter msgComplete = new CompletionWaiter();
			channel.publish(persistEventName, messageText, msgComplete);

			/* wait for the history to be persisted */
			try {
				Thread.sleep(16000);
			} catch(InterruptedException ie) {}

			/* publish to the channel */
			msgComplete = new CompletionWaiter();
			channel.publish(liveEventName, messageText, msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);

			/* get the history for this channel */
			PaginatedResult<Message> messages = channel.history(null);
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 2 messages", messages.items().length, 2);

			/* verify message contents */
			assertEquals("Expect correct message event", messages.items()[0].name, liveEventName);
			assertEquals("Expect correct message text", messages.items()[0].data, messageText);
			assertEquals("Expect correct message event", messages.items()[1].name, persistEventName);
			assertEquals("Expect correct message text", messages.items()[1].data, messageText);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("single_history_binary: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Send a single message on a channel, wait enough time for it to
	 * persist, then send a second message. Verify that both can be
	 * retrieved using channel.history(direction=forwards) without any
	 * further wait.
	 */
	@Test
	public void channelhistory_mixed_binary_f() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);
			String channelName = "persisted:channelhistory_mixed_binary_f";
			String messageText = "Test message (channelhistory_mixed_binary_f)";
			String persistEventName = "test_event (persisted)";
			String liveEventName = "test_event (live)";

			/* create a channel */
			final Channel channel = ably.channels.get(channelName);

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* publish to the channel */
			CompletionWaiter msgComplete = new CompletionWaiter();
			channel.publish(persistEventName, messageText, msgComplete);

			/* wait for the history to be persisted */
			try {
				Thread.sleep(16000);
			} catch(InterruptedException ie) {}

			/* publish to the channel */
			msgComplete = new CompletionWaiter();
			channel.publish(liveEventName, messageText, msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);

			/* get the history for this channel */
			PaginatedResult<Message> messages = channel.history(new Param[]{ new Param("direction", "forwards") });
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 2 messages", messages.items().length, 2);

			/* verify message contents */
			assertEquals("Expect correct message event", messages.items()[0].name, persistEventName);
			assertEquals("Expect correct message text", messages.items()[0].data, messageText);
			assertEquals("Expect correct message event", messages.items()[1].name, liveEventName);
			assertEquals("Expect correct message text", messages.items()[1].data, messageText);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("single_history_binary: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Publish events, get limited history and check expected order (forwards)
	 */
	@Test
	public void channelhistory_limit_f() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);
			String channelName = "persisted:channelhistory_limit_f";

			/* create a channel */
			final Channel channel = ably.channels.get(channelName);

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* publish to the channel */
			CompletionSet msgComplete = new CompletionSet();
			for(int i = 0; i < 50; i++) {
				try {
					channel.publish("history" + i,  String.valueOf(i), msgComplete.add());
				} catch(AblyException e) {
					e.printStackTrace();
					fail("channelhistory_limit_f: Unexpected exception");
					return;
				}
			}

			/* wait for the publish callbacks to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.errors.isEmpty());

			/* get the history for this channel */
			PaginatedResult<Message> messages = channel.history(new Param[] { new Param("direction", "forwards"), new Param("limit", "25") });
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 25 messages", messages.items().length, 25);
			HashMap<String, Message> messageContents = new HashMap<String, Message>();
			for(Message message : messages.items())
				messageContents.put(message.name, message);
			/* verify message order */
			Message[] expectedMessageHistory = new Message[25];
			for(int i = 0; i < 25; i++)
				expectedMessageHistory[i] = messageContents.get("history" + i);
			Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("channelhistory_limit_f: Unexpected exception");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Publish events, get limited history and check expected order (backwards)
	 */
	@Test
	public void channelhistory_limit_b() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);
			String channelName = "persisted:channelhistory_limit_b";

			/* create a channel */
			final Channel channel = ably.channels.get(channelName);

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* publish to the channel */
			CompletionSet msgComplete = new CompletionSet();
			for(int i = 0; i < 50; i++) {
				try {
					channel.publish("history" + i,  String.valueOf(i), msgComplete.add());
				} catch(AblyException e) {
					e.printStackTrace();
					fail("channelhistory_limit_b: Unexpected exception");
					return;
				}
			}

			/* wait for the publish callbacks to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.errors.isEmpty());

			/* get the history for this channel */
			PaginatedResult<Message> messages = channel.history(new Param[] { new Param("direction", "backwards"), new Param("limit", "25") });
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 25 messages", messages.items().length, 25);
			HashMap<String, Message> messageContents = new HashMap<String, Message>();
			for(Message message : messages.items())
				messageContents.put(message.name, message);

			/* verify message order */
			Message[] expectedMessageHistory = new Message[25];
			for(int i = 0; i < 25; i++)
				expectedMessageHistory[i] = messageContents.get("history" + (49 - i));
			Assert.assertArrayEquals("Expect messages in backward order", messages.items(), expectedMessageHistory);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("channelhistory_limit_b: Unexpected exception");
			return;
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Publish events and check expected history based on time slice (forwards)
	 */
	@Test
	public void channelhistory_time_f() {
		AblyRealtime ably = null;
		try {
			/* first, publish some messages */
			long intervalStart = 0, intervalEnd = 0;
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);
			String channelName = "persisted:channelhistory_time_f";
	
			/* create a channel */
			final Channel channel = ably.channels.get(channelName);
	
			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);
	
			/* send batches of messages with shprt inter-message delay */
			CompletionSet msgComplete = new CompletionSet();
			for(int i = 0; i < 20; i++) {
				channel.publish("history" + i, String.valueOf(i), msgComplete.add());
				Thread.sleep(100L);
			}
			Thread.sleep(1000L);
			intervalStart = timeOffset + System.currentTimeMillis();
			for(int i = 20; i < 40; i++) {
				channel.publish("history" + i, String.valueOf(i), msgComplete.add());
				Thread.sleep(100L);
			}
			intervalEnd = timeOffset + System.currentTimeMillis() - 1;
			Thread.sleep(1000L);
			for(int i = 40; i < 60; i++) {
				channel.publish("history" + i, String.valueOf(i), msgComplete.add());
				Thread.sleep(100L);
			}

			/* wait for message callbacks */
			msgComplete.waitFor();

			/* get the history for this channel */
			PaginatedResult<Message> messages = channel.history(new Param[] {
				new Param("direction", "forwards"),
				new Param("start", String.valueOf(intervalStart - 500)),
				new Param("end", String.valueOf(intervalEnd + 500))
			});
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 20 messages", messages.items().length, 20);
			HashMap<String, Message> messageContents = new HashMap<String, Message>();
			for(Message message : messages.items())
				messageContents.put(message.name, message);
			/* verify message order */
			Message[] expectedMessageHistory = new Message[20];
			for(int i = 20; i < 40; i++)
				expectedMessageHistory[i - 20] = messageContents.get("history" + i);
			Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("channelhistory_time_f: Unexpected exception");
		} catch (InterruptedException e) {
			e.printStackTrace();
			fail("channelhistory_time_f: Unexpected exception");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Publish events and check expected history based on time slice (backwards)
	 */
	@Test
	public void channelhistory_time_b() {
		AblyRealtime ably = null;
		try {
			/* first, publish some messages */
			long intervalStart = 0, intervalEnd = 0;
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);
			String channelName = "persisted:channelhistory_time_b";
	
			/* create a channel */
			final Channel channel = ably.channels.get(channelName);
	
			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);
	
			/* send batches of messages with shprt inter-message delay */
			CompletionSet msgComplete = new CompletionSet();
			for(int i = 0; i < 20; i++) {
				channel.publish("history" + i, String.valueOf(i), msgComplete.add());
				Thread.sleep(100L);
			}
			Thread.sleep(1000L);
			intervalStart = timeOffset + System.currentTimeMillis();
			for(int i = 20; i < 40; i++) {
				channel.publish("history" + i, String.valueOf(i), msgComplete.add());
				Thread.sleep(100L);
			}
			intervalEnd = timeOffset + System.currentTimeMillis() - 1;
			Thread.sleep(1000L);
			for(int i = 40; i < 60; i++) {
				channel.publish("history" + i, String.valueOf(i), msgComplete.add());
				Thread.sleep(100L);
			}

			/* wait for message callbacks */
			msgComplete.waitFor();

			/* get the history for this channel */
			PaginatedResult<Message> messages = channel.history(new Param[] {
				new Param("direction", "backwards"),
				new Param("start", String.valueOf(intervalStart - 500)),
				new Param("end", String.valueOf(intervalEnd + 500))
			});
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 20 messages", messages.items().length, 20);
			HashMap<String, Message> messageContents = new HashMap<String, Message>();
			for(Message message : messages.items())
				messageContents.put(message.name, message);
			/* verify message order */
			Message[] expectedMessageHistory = new Message[20];
			for(int i = 20; i < 40; i++)
				expectedMessageHistory[i - 20] = messageContents.get("history" + (59 - i));
			Assert.assertArrayEquals("Expect messages in backwards order", messages.items(), expectedMessageHistory);
		} catch (AblyException e) {
			e.printStackTrace();
			fail("channelhistory_time_b: Unexpected exception");
		} catch (InterruptedException e) {
			e.printStackTrace();
			fail("channelhistory_time_b: Unexpected exception");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Check query pagination (forwards)
	 */
	@Test
	public void channelhistory_paginate_f() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);
			String channelName = "persisted:channelhistory_paginate_f";

			/* create a channel */
			final Channel channel = ably.channels.get(channelName);

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* publish to the channel */
			CompletionSet msgComplete = new CompletionSet();
			for(int i = 0; i < 50; i++) {
				try {
					channel.publish("history" + i,  String.valueOf(i), msgComplete.add());
				} catch(AblyException e) {
					e.printStackTrace();
					fail("channelhistory_paginate_f: Unexpected exception");
					return;
				}
			}

			/* wait for the publish callbacks to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.errors.isEmpty());

			/* get the history for this channel */
			PaginatedResult<Message> messages = channel.history(new Param[] { new Param("direction", "forwards"), new Param("limit", "10") });
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 10 messages", messages.items().length, 10);

			/* log all messages */
			HashMap<String, Message> messageContents = new HashMap<String, Message>();
			for(Message message : messages.items())
				messageContents.put(message.name, message);

			/* verify message order */
			Message[] expectedMessageHistory = new Message[10];
			for(int i = 0; i < 10; i++)
				expectedMessageHistory[i] = messageContents.get("history" + i);
			Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);

			/* get next page */
			messages = messages.next();
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 10 messages", messages.items().length, 10);

			/* log all messages */
			for(Message message : messages.items())
				messageContents.put(message.name, message);

			/* verify message order */
			for(int i = 0; i < 10; i++)
				expectedMessageHistory[i] = messageContents.get("history" + String.valueOf(i + 10));
			Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);

			/* get next page */
			messages = messages.next();
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 10 messages", messages.items().length, 10);

			/* log all messages */
			for(Message message : messages.items())
				messageContents.put(message.name, message);

			/* verify message order */
			for(int i = 0; i < 10; i++)
				expectedMessageHistory[i] = messageContents.get("history" + String.valueOf(i + 20));
			Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("channelhistory_paginate_f: Unexpected exception");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Check query pagination (backwards)
	 */
	@Test
	public void channelhistory_paginate_b() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);
			String channelName = "persisted:channelhistory_paginate_b";

			/* create a channel */
			final Channel channel = ably.channels.get(channelName);

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* publish to the channel */
			CompletionSet msgComplete = new CompletionSet();
			for(int i = 0; i < 50; i++) {
				try {
					channel.publish("history" + i,  String.valueOf(i), msgComplete.add());
				} catch(AblyException e) {
					e.printStackTrace();
					fail("channelhistory_paginate_f: Unexpected exception");
					return;
				}
			}

			/* wait for the publish callbacks to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.errors.isEmpty());

			/* get the history for this channel */
			PaginatedResult<Message> messages = channel.history(new Param[] { new Param("direction", "backwards"), new Param("limit", "10") });
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 10 messages", messages.items().length, 10);

			/* log all messages */
			HashMap<String, Message> messageContents = new HashMap<String, Message>();
			for(Message message : messages.items())
				messageContents.put(message.name, message);

			/* verify message order */
			Message[] expectedMessageHistory = new Message[10];
			for(int i = 0; i < 10; i++)
				expectedMessageHistory[i] = messageContents.get("history" + String.valueOf(49 - i));
			Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);

			/* get next page */
			messages = messages.next();
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 10 messages", messages.items().length, 10);

			/* log all messages */
			for(Message message : messages.items())
				messageContents.put(message.name, message);

			/* verify message order */
			for(int i = 0; i < 10; i++)
				expectedMessageHistory[i] = messageContents.get("history" + String.valueOf(39 - i));
			Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);

			/* get next page */
			messages = messages.next();
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 10 messages", messages.items().length, 10);

			/* log all messages */
			for(Message message : messages.items())
				messageContents.put(message.name, message);

			/* verify message order */
			for(int i = 0; i < 10; i++)
				expectedMessageHistory[i] = messageContents.get("history" + String.valueOf(29 - i));
			Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("channelhistory_paginate_b: Unexpected exception");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Check query pagination "rel=first" (forwards)
	 */
	@Test
	public void channelhistory_paginate_first_f() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);
			String channelName = "persisted:channelhistory_paginate_first_f";

			/* create a channel */
			final Channel channel = ably.channels.get(channelName);

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* publish to the channel */
			CompletionSet msgComplete = new CompletionSet();
			for(int i = 0; i < 50; i++) {
				try {
					channel.publish("history" + i,  String.valueOf(i), msgComplete.add());
				} catch(AblyException e) {
					e.printStackTrace();
					fail("channelhistory_paginate_f: Unexpected exception");
					return;
				}
			}

			/* wait for the publish callbacks to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.errors.isEmpty());

			/* get the history for this channel */
			PaginatedResult<Message> messages = channel.history(new Param[] { new Param("direction", "forwards"), new Param("limit", "10") });
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 10 messages", messages.items().length, 10);

			/* log all messages */
			HashMap<String, Message> messageContents = new HashMap<String, Message>();
			for(Message message : messages.items())
				messageContents.put(message.name, message);

			/* verify message order */
			Message[] expectedMessageHistory = new Message[10];
			for(int i = 0; i < 10; i++)
				expectedMessageHistory[i] = messageContents.get("history" + i);
			Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);

			/* get next page */
			messages = messages.next();
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 10 messages", messages.items().length, 10);

			/* log all messages */
			for(Message message : messages.items())
				messageContents.put(message.name, message);

			/* verify message order */
			for(int i = 0; i < 10; i++)
				expectedMessageHistory[i] = messageContents.get("history" + String.valueOf(i + 10));
			Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);

			/* get first page */
			messages = messages.first();
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 10 messages", messages.items().length, 10);

			/* log all messages */
			for(Message message : messages.items())
				messageContents.put(message.name, message);

			/* verify message order */
			for(int i = 0; i < 10; i++)
				expectedMessageHistory[i] = messageContents.get("history" + String.valueOf(i));
			Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("channelhistory_paginate_first_f: Unexpected exception");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Check query pagination "rel=first" (backwards)
	 */
	@Test
	public void channelhistory_paginate_first_b() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);
			String channelName = "persisted:channelhistory_paginate_first_b";

			/* create a channel */
			final Channel channel = ably.channels.get(channelName);

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* publish to the channel */
			CompletionSet msgComplete = new CompletionSet();
			for(int i = 0; i < 50; i++) {
				try {
					channel.publish("history" + i,  String.valueOf(i), msgComplete.add());
				} catch(AblyException e) {
					e.printStackTrace();
					fail("channelhistory_paginate_f: Unexpected exception");
					return;
				}
			}

			/* wait for the publish callbacks to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.errors.isEmpty());

			/* get the history for this channel */
			PaginatedResult<Message> messages = channel.history(new Param[] { new Param("direction", "backwards"), new Param("limit", "10") });
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 10 messages", messages.items().length, 10);

			/* log all messages */
			HashMap<String, Message> messageContents = new HashMap<String, Message>();
			for(Message message : messages.items())
				messageContents.put(message.name, message);

			/* verify message order */
			Message[] expectedMessageHistory = new Message[10];
			for(int i = 0; i < 10; i++)
				expectedMessageHistory[i] = messageContents.get("history" + String.valueOf(49 - i));
			Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);

			/* get next page */
			messages = messages.next();
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 10 messages", messages.items().length, 10);

			/* log all messages */
			for(Message message : messages.items())
				messageContents.put(message.name, message);

			/* verify message order */
			for(int i = 0; i < 10; i++)
				expectedMessageHistory[i] = messageContents.get("history" + String.valueOf(39 - i));
			Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);

			/* get first page */
			messages = messages.first();
			assertNotNull("Expected non-null messages", messages);
			assertEquals("Expected 10 messages", messages.items().length, 10);

			/* log all messages */
			for(Message message : messages.items())
				messageContents.put(message.name, message);

			/* verify message order */
			for(int i = 0; i < 10; i++)
				expectedMessageHistory[i] = messageContents.get("history" + String.valueOf(49 - i));
			Assert.assertArrayEquals("Expect messages in forward order", messages.items(), expectedMessageHistory);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("channelhistory_paginate_first_b: Unexpected exception");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Connect twice to the service, each using the default (binary) protocol.
	 * Publish messages on one connection to a given channel; while in progress,
	 * attach the second connection to the same channel and verify a message
	 * history up to the point of attachment can be obtained. 
	 */
	@Test
	public void channelhistory_from_attach() {
		AblyRealtime txAbly = null, rxAbly = null;
		try {
			io.ably.lib.test.common.Setup.TestVars testVars = Setup.getTestVars();
			ClientOptions txOpts = testVars.createOptions(testVars.keys[0].keyStr);
			txAbly = new AblyRealtime(txOpts);
			ClientOptions rxOpts = testVars.createOptions(testVars.keys[0].keyStr);
			rxAbly = new AblyRealtime(rxOpts);
			String channelName = "persisted:channelhistory_from_attach";
	
			/* create a channel */
			final Channel txChannel = txAbly.channels.get(channelName);
			final Channel rxChannel = rxAbly.channels.get(channelName);
	
			/* attach sender */
			txChannel.attach();
			(new ChannelWaiter(txChannel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", txChannel.state, ChannelState.attached);
	
			/* publish messages to the channel */
			final CompletionSet msgComplete = new CompletionSet();
			Thread publisherThread = new Thread() {
				@Override
				public void run() {
					for(int i = 0; i < 50; i++) {
						try {
							txChannel.publish("history" + i,  String.valueOf(i), msgComplete.add());
							try {
								sleep(100L);
							} catch(InterruptedException ie) {}
						} catch(AblyException e) {
							e.printStackTrace();
							fail("channelhistory_from_attach: Unexpected exception");
							return;
						}
					}
				}
			};
			publisherThread.start();

			/* wait 2 seconds */
			try {
				Thread.sleep(2000L);
			} catch(InterruptedException ie) {}

			/* subscribe; this will trigger the attach */
			MessageWaiter messageWaiter =  new MessageWaiter(rxChannel);

			/* get the channel history from the attachSerial when we get the attach indication */
			(new ChannelWaiter(rxChannel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", rxChannel.state, ChannelState.attached);
			assertNotNull("Verify attachSerial provided", rxChannel.attachSerial);

			/* wait for the subscription callback to be called on the first received message */
			messageWaiter.waitFor(1);

			/* wait for the publisher thread to complete */
			try {
				publisherThread.join();
			} catch (InterruptedException e) {}

			/* get the history for this channel */
			PaginatedResult<Message> messages = rxChannel.history(new Param[] { new Param("from_serial", rxChannel.attachSerial)});
			assertNotNull("Expected non-null messages", messages);
			assertTrue("Expected at least one message", messages.items().length >= 1);

			/* verify that the history and received messages meet */
			int earliestReceivedOnConnection = Integer.valueOf((String)messageWaiter.receivedMessages.get(0).data).intValue();
			int latestReceivedInHistory = Integer.valueOf((String)messages.items()[0].data).intValue();
			assertEquals("Verify that the history and received messages meet", earliestReceivedOnConnection, latestReceivedInHistory + 1);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("channelhistory_from_attach: Unexpected exception instantiating library");
		} finally {
			if(txAbly != null)
				txAbly.close();
			if(rxAbly != null)
				rxAbly.close();
		}
	}
}
