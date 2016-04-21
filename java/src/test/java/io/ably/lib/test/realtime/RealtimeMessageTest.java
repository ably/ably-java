package io.ably.lib.test.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

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
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.util.Log;

public class RealtimeMessageTest {

	private static final String TAG = RealtimeMessageTest.class.getName();

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		Setup.getTestVars();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		Setup.clearTestVars();
	}

	/**
	 * Connect to the service using the default (binary) protocol
	 * and attach, subscribe to an event, and publish on that channel
	 */
	@Test
	public void single_send_binary() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			/* create a channel */
			final Channel channel = ably.channels.get("subscribe_send_binary");

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(channel);

			/* publish to the channel */
			CompletionWaiter msgComplete = new CompletionWaiter();
			channel.publish("test_event", "Test message (subscribe_send_binary)", msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(1);
			assertEquals("Verify message subscription was called", messageWaiter.receivedMessages.size(), 1);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Connect to the service using the text protocol
	 * and attach, subscribe to an event, and publish on that channel
	 */
	@Test
	public void single_send_text() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			opts.useBinaryProtocol = false;
			ably = new AblyRealtime(opts);

			/* create a channel */
			final Channel channel = ably.channels.get("subscribe_send_text");

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(channel);

			/* publish to the channel */
			CompletionWaiter msgComplete = new CompletionWaiter();
			channel.publish("test_event", "Test message (subscribe_send_text)", msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(1);
			assertEquals("Verify message subscription was called", messageWaiter.receivedMessages.size(), 1);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Connect to the service using the default (binary) protocol on
	 * two connections; attach, subscribe to an event, publish on one
	 * connection and confirm receipt on the other.
	 */
	@Test
	public void single_send_binary_noecho() {
		AblyRealtime txAbly = null;
		AblyRealtime rxAbly = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			opts.echoMessages = false;
			txAbly = new AblyRealtime(opts);
			rxAbly = new AblyRealtime(opts);
			String channelName = "subscribe_send_binary_noecho";

			/* create a channel */
			final Channel txChannel = txAbly.channels.get(channelName);
			final Channel rxChannel = rxAbly.channels.get(channelName);

			/* attach both connections */
			txChannel.attach();
			(new ChannelWaiter(txChannel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", txChannel.state, ChannelState.attached);
			rxChannel.attach();
			(new ChannelWaiter(rxChannel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", rxChannel.state, ChannelState.attached);

			/* subscribe on both connections */
			MessageWaiter txMessageWaiter =  new MessageWaiter(txChannel);
			MessageWaiter rxMessageWaiter =  new MessageWaiter(rxChannel);

			/* publish to the channel */
			CompletionWaiter msgComplete = new CompletionWaiter();
			txChannel.publish("test_event", "Test message (subscribe_send_binary_noecho)", msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);

			/* wait for the subscription callback to be called */
			rxMessageWaiter.waitFor(1);
			assertEquals("Verify rx message subscription was called", rxMessageWaiter.receivedMessages.size(), 1);

			/* wait to verify that the subscription callback is not called on txConnection */
			txMessageWaiter.waitFor(1, 1000L);
			assertEquals("Verify tx message subscription was not called", txMessageWaiter.receivedMessages.size(), 0);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("single_send_binary_noecho: Unexpected exception instantiating library");
		} finally {
			if(txAbly != null)
				txAbly.close();
			if(rxAbly != null)
				rxAbly.close();
		}
	}

	/**
	 * Connect to the service using the text protocol on
	 * two connections; attach, subscribe to an event, publish on one
	 * connection and confirm receipt on the other.
	 */
	@Test
	public void single_send_text_noecho() {
		AblyRealtime txAbly = null;
		AblyRealtime rxAbly = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			opts.echoMessages = false;
			opts.useBinaryProtocol = false;
			txAbly = new AblyRealtime(opts);
			rxAbly = new AblyRealtime(opts);
			String channelName = "subscribe_send_text_noecho";

			/* create a channel */
			final Channel txChannel = txAbly.channels.get(channelName);
			final Channel rxChannel = rxAbly.channels.get(channelName);

			/* attach both connections */
			txChannel.attach();
			(new ChannelWaiter(txChannel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", txChannel.state, ChannelState.attached);
			rxChannel.attach();
			(new ChannelWaiter(rxChannel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", rxChannel.state, ChannelState.attached);

			/* subscribe on both connections */
			MessageWaiter txMessageWaiter =  new MessageWaiter(txChannel);
			MessageWaiter rxMessageWaiter =  new MessageWaiter(rxChannel);

			/* publish to the channel */
			CompletionWaiter msgComplete = new CompletionWaiter();
			txChannel.publish("test_event", "Test message (subscribe_send_text_noecho)", msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);

			/* wait for the subscription callback to be called */
			rxMessageWaiter.waitFor(1);
			assertEquals("Verify rx message subscription was called", rxMessageWaiter.receivedMessages.size(), 1);

			/* wait to verify that the subscription callback is not called on txConnection */
			txMessageWaiter.waitFor(1, 1000L);
			assertEquals("Verify tx message subscription was not called", txMessageWaiter.receivedMessages.size(), 0);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("single_send_binary_noecho: Unexpected exception instantiating library");
		} finally {
			if(txAbly != null)
				txAbly.close();
			if(rxAbly != null)
				rxAbly.close();
		}
	}

	/**
	 * Get a channel and subscribe without explicitly attaching.
	 * Verify that the channel reaches the attached state.
	 */
	@Test
	public void subscribe_implicit_attach_binary() {
		AblyRealtime ably = null;
		String channelName = "subscribe_implicit_attach_binary";
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			/* create a channel */
			final Channel channel = ably.channels.get(channelName);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(channel);

			/* verify attached state is reached */
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* publish to the channel */
			CompletionWaiter msgComplete = new CompletionWaiter();
			channel.publish("test_event", "Test message (" + channelName + ")", msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(1);
			assertEquals("Verify message subscription was called", messageWaiter.receivedMessages.size(), 1);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Get a channel and subscribe without explicitly attaching.
	 * Verify that the channel reaches the attached state.
	 */
	@Test
	public void subscribe_implicit_attach_text() {
		AblyRealtime ably = null;
		String channelName = "subscribe_implicit_attach_text";
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			opts.useBinaryProtocol = false;
			ably = new AblyRealtime(opts);

			/* create a channel */
			final Channel channel = ably.channels.get(channelName);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(channel);

			/* verify attached state is reached */
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* publish to the channel */
			CompletionWaiter msgComplete = new CompletionWaiter();
			channel.publish("test_event", "Test message (" + channelName + ")", msgComplete);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(1);
			assertEquals("Verify message subscription was called", messageWaiter.receivedMessages.size(), 1);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Get a channel and publish without explicitly attaching.
	 * Verify that the channel reaches the attached state.
	 */
	@Test
	public void publish_implicit_attach_binary() {
		AblyRealtime pubAbly = null;
		AblyRealtime subAbly = null;
		String channelName = "publish_implicit_attach_binary";
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			pubAbly = new AblyRealtime(opts);
			subAbly = new AblyRealtime(opts);

			/* create a channel */
			final Channel pubChannel = pubAbly.channels.get(channelName);
			final Channel subChannel = subAbly.channels.get(channelName);

			/* subscribe and wait for subscription channel to attach */
			MessageWaiter messageWaiter =  new MessageWaiter(subChannel);
			(new ChannelWaiter(subChannel)).waitFor(ChannelState.attached);

			/* publish to the channel */
			CompletionWaiter msgComplete = new CompletionWaiter();
			pubChannel.publish("test_event", "Test message (" + channelName + ")", msgComplete);

			/* verify attached state is reached */
			(new ChannelWaiter(pubChannel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", pubChannel.state, ChannelState.attached);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(1);
			assertEquals("Verify message subscription was called", messageWaiter.receivedMessages.size(), 1);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(pubAbly != null)
				pubAbly.close();
			if(subAbly != null)
				subAbly.close();
		}
	}

	/**
	 * Get a channel and publish without explicitly attaching.
	 * Verify that the channel reaches the attached state.
	 */
	@Test
	public void publish_implicit_attach_text() {
		AblyRealtime pubAbly = null;
		AblyRealtime subAbly = null;
		String channelName = "publish_implicit_attach_text";
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			opts.useBinaryProtocol = false;
			pubAbly = new AblyRealtime(opts);
			subAbly = new AblyRealtime(opts);

			/* create a channel */
			final Channel pubChannel = pubAbly.channels.get(channelName);
			final Channel subChannel = subAbly.channels.get(channelName);

			/* subscribe and wait for subscription channel to attach */
			MessageWaiter messageWaiter =  new MessageWaiter(subChannel);
			(new ChannelWaiter(subChannel)).waitFor(ChannelState.attached);

			/* publish to the channel */
			CompletionWaiter msgComplete = new CompletionWaiter();
			pubChannel.publish("test_event", "Test message (" + channelName + ")", msgComplete);

			/* verify attached state is reached */
			(new ChannelWaiter(pubChannel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", pubChannel.state, ChannelState.attached);

			/* wait for the publish callback to be called */
			msgComplete.waitFor();
			assertTrue("Verify success callback was called", msgComplete.success);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(1);
			assertEquals("Verify message subscription was called", messageWaiter.receivedMessages.size(), 1);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(pubAbly != null)
				pubAbly.close();
			if(subAbly != null)
				subAbly.close();
		}
	}

	/**
	 * Connect to the service using the default (binary) protocol
	 * and attach, subscribe to an event, and publish multiple
	 * messages on that channel
	 */
	private void _multiple_send(String channelName, boolean useBinaryProtocol, int messageCount, long delay) {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			opts.useBinaryProtocol = useBinaryProtocol;
			ably = new AblyRealtime(opts);

			/* create a channel */
			final Channel channel = ably.channels.get(channelName);

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(channel);

			/* publish to the channel */
			CompletionSet msgComplete = new CompletionSet();
			for(int i = 0; i < messageCount; i++) {
				channel.publish("test_event", "Test message (_multiple_send) " + i, msgComplete.add());
				try { Thread.sleep(delay); } catch(InterruptedException e){}
			}

			/* wait for the publish callback to be called */
			ErrorInfo[] errors = msgComplete.waitFor();
			assertTrue("Verify success from all message callbacks", errors.length == 0);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(messageCount);
			assertEquals("Verify message subscriptions all called", messageWaiter.receivedMessages.size(), messageCount);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("channelName: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Connect to the service using the default (binary) protocol
	 * and attach, subscribe to an event, and publish multiple
	 * messages on that channel
	 */
	private void _multiple_send_batch(String channelName, boolean useBinaryProtocol, int messageCount, int batchCount, long batchDelay) {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
			opts.useBinaryProtocol = useBinaryProtocol;
			ably = new AblyRealtime(opts);

			/* create a channel */
			final Channel channel = ably.channels.get(channelName);

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(channel);

			/* publish to the channel */
			CompletionSet msgComplete = new CompletionSet();
			for(int i = 0; i < (int)(messageCount / batchCount); i++) {
				for(int j = 0; j < batchCount; j++) {
					channel.publish("test_event", "Test message (_multiple_send_batch) " + i * batchCount + j, msgComplete.add());
				}
				try { Thread.sleep(batchDelay); } catch(InterruptedException e){}
			}

			/* wait for the publish callback to be called */
			ErrorInfo[] errors = msgComplete.waitFor();
			assertTrue("Verify success from all message callbacks", errors.length == 0);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(messageCount);
			assertEquals("Verify message subscriptions all called", messageWaiter.receivedMessages.size(), messageCount);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("channelName: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	@Test
	public void multiple_send_binary_10_1000() {
		int messageCount = 10;
		long delay = 1000L;
		_multiple_send("multiple_send_binary_10_1000", true, messageCount, delay);
	}

	@Test
	public void multiple_send_text_10_1000() {
		int messageCount = 10;
		long delay = 1000L;
		_multiple_send("multiple_send_text_10_1000", false, messageCount, delay);
	}

	@Test
	public void multiple_send_binary_20_200() {
		int messageCount = 20;
		long delay = 200L;
		_multiple_send("multiple_send_binary_20_200", true, messageCount, delay);
	}

	@Test
	public void multiple_send_binary_200_50() {
		int messageCount = 200;
		long delay = 50L;
		_multiple_send("multiple_send_binary_200_50", false, messageCount, delay);
	}

	@Test
	public void multiple_send_binary_1000_10() {
		int messageCount = 1000;
		long delay = 10L;
		_multiple_send("multiple_send_binary_1000_10", true, messageCount, delay);
	}

	@Test
	public void multiple_send_binary_2000_5() {
		int messageCount = 2000;
		long delay = 5L;
		_multiple_send("multiple_send_binary_2000_5", true, messageCount, delay);
	}

	@Test
	public void multiple_send_binary_1000_2() {
		int messageCount = 1000;
		long delay = 2L;
		_multiple_send("multiple_send_binary_1000_2", true, messageCount, delay);
	}

	@Test
	public void multiple_send_binary_1000_1() {
		int messageCount = 1000;
		long delay = 1L;
		_multiple_send("multiple_send_binary_1000_1", true, messageCount, delay);
	}

	@Test
	public void multiple_send_binary_1000_20_5() {
		int messageCount = 1000;
		int batchCount = 20;
		long batchDelay = 5L;
		_multiple_send_batch("multiple_send_binary_1000_20_5", true, messageCount, batchCount, batchDelay);
	}

	/**
	 * Connect to the service using the default (binary) protocol
	 * using credentials that are unable to publish,and attach.
	 * Attempt to publish and verify that an error is received.
	 */
	@Test
	public void single_error_binary() {
		AblyRealtime ably = null;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[4].keyStr);
			ably = new AblyRealtime(opts);

			/* create a channel; channel3 can subscribe but not publish
			 * with this key */
			final Channel channel = ably.channels.get("channel3");

			/* attach */
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* publish to the channel */
			CompletionWaiter msgComplete = new CompletionWaiter();
			channel.publish("test_event", "Test message (single_error_binary)", msgComplete);

			/* wait for the publish callback to be called */
			ErrorInfo fail = msgComplete.waitFor();
			assertEquals("Verify error callback was called", fail.statusCode, 401);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("single_error_binary: Unexpected exception instantiating library");
		} finally {
			if(ably != null)
				ably.close();
		}
	}

}
