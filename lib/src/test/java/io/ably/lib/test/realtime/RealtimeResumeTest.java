package io.ably.lib.test.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.ably.lib.types.Message;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.test.common.Helpers.ChannelWaiter;
import io.ably.lib.test.common.Helpers.CompletionSet;
import io.ably.lib.test.common.Helpers.ConnectionWaiter;
import io.ably.lib.test.common.Helpers.MessageWaiter;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;

import java.util.List;

public class RealtimeResumeTest extends ParameterizedTest {

	private static final String TAG = RealtimeResumeTest.class.getName();

	@Rule
	public Timeout testTimeout = Timeout.seconds(60);

	/**
	 * Connect to the service and attach a channel.
	 * Don't publish any messages; disconnect and then reconnect; verify that
	 * the channel is still attached.
	 */
	@Test
	public void resume_none() {
		AblyRealtime ably = null;
		String channelName = "resume_none";
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			/* create and attach channel */
			final Channel channel = ably.channels.get(channelName);
			System.out.println("Attaching");
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* disconnect the connection, without closing;
			 * NOTE this depends on knowledge of the internal structure
			 * of the library, to simulate a dropped transport without
			 * causing the connection itself to be disposed */
			System.out.println("Simulating dropped transport");
			ably.connection.connectionManager.requestState(ConnectionState.disconnected);

			/* reconnect the rx connection */
			ably.connection.connect();
			System.out.println("Waiting for reconnection");
			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);

			/* wait */
			System.out.println("Got reconnection; waiting 2s");
			try { Thread.sleep(2000L); } catch(InterruptedException e) {}

			/* Check the channel is still attached. */
			assertEquals("Verify channel still attached", channel.state, ChannelState.attached);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ably != null) {
				ably.close();
			}
		}
	}

	/**
	 * Connect to the service using two library instances to set
	 * up separate send and recv connections.
	 * Disconnect and then reconnect one connection; verify that
	 * the connection continues to receive messages on attached
	 * channels after reconnection.
	 */
	@Test
	public void resume_simple() {
		AblyRealtime ablyTx = null;
		AblyRealtime ablyRx = null;
		String channelName = "resume_simple";
		int messageCount = 5;
		long delay = 200;
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			ablyRx = new AblyRealtime(opts);
			ablyTx = new AblyRealtime(opts);

			/* create and attach channel to send on */
			final Channel channelTx = ablyTx.channels.get(channelName);
			channelTx.attach();
			(new ChannelWaiter(channelTx)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached for tx", channelTx.state, ChannelState.attached);

			/* create and attach channel to recv on */
			final Channel channelRx = ablyRx.channels.get(channelName);
			channelRx.attach();
			(new ChannelWaiter(channelRx)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached for rx", channelRx.state, ChannelState.attached);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(channelRx);

			/* publish first messages to the channel */
			CompletionSet msgComplete1 = new CompletionSet();
			for(int i = 0; i < messageCount; i++) {
				channelTx.publish("test_event", "Test message (resume_simple) " + i, msgComplete1.add());
				try { Thread.sleep(delay); } catch(InterruptedException e){}
			}

			/* wait for the publish callback to be called */
			ErrorInfo[] errors = msgComplete1.waitFor();
			assertTrue("Verify success from all message callbacks", errors.length == 0);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(messageCount);
			assertEquals("Verify message subscriptions all called", messageWaiter.receivedMessages.size(), messageCount);
			messageWaiter.reset();

			/* disconnect the rx connection, without closing;
			 * NOTE this depends on knowledge of the internal structure
			 * of the library, to simulate a dropped transport without
			 * causing the connection itself to be disposed */
			ablyRx.connection.connectionManager.requestState(ConnectionState.disconnected);

			/* wait */
			try { Thread.sleep(2000L); } catch(InterruptedException e) {}

			/* reconnect the rx connection */
			ablyRx.connection.connect();

			/* publish further messages to the channel */
			CompletionSet msgComplete2 = new CompletionSet();
			for(int i = 0; i < messageCount; i++) {
				channelTx.publish("test_event", "Test message (resume_simple) " + i, msgComplete2.add());
				try { Thread.sleep(delay); } catch(InterruptedException e){}
			}

			/* wait for the publish callback to be called */
			errors = msgComplete2.waitFor();
			assertTrue("Verify success from all message callbacks", errors.length == 0);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(messageCount);
			assertEquals("Verify message subscriptions all called after reconnection", messageWaiter.receivedMessages.size(), messageCount);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ablyTx != null) {
				ablyTx.close();
			}
			if(ablyRx != null) {
				ablyRx.close();
			}
		}
	}

	/**
	 * Connect to the service using two library instances to set
	 * up separate send and recv connections.
	 * Send on one connection while the other is disconnected;
	 * verify that the messages sent whilst disconnected are delivered
	 * on resume
	 */
	@Test
	public void resume_disconnected() {
		AblyRealtime ablyTx = null;
		AblyRealtime ablyRx = null;
		String channelName = "resume_disconnected";
		int messageCount = 5;
		long delay = 200;
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			ablyRx = new AblyRealtime(opts);
			ablyTx = new AblyRealtime(opts);

			/* create and attach channel to send on */
			final Channel channelTx = ablyTx.channels.get(channelName);
			channelTx.attach();
			(new ChannelWaiter(channelTx)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached for tx", channelTx.state, ChannelState.attached);

			/* create and attach channel to recv on */
			final Channel channelRx = ablyRx.channels.get(channelName);
			channelRx.attach();
			(new ChannelWaiter(channelRx)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached for rx", channelRx.state, ChannelState.attached);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(channelRx);

			/* publish first messages to the channel */
			CompletionSet msgComplete1 = new CompletionSet();
			for(int i = 0; i < messageCount; i++) {
				channelTx.publish("test_event", "Test message (resume_disconnected) " + i, msgComplete1.add());
				try { Thread.sleep(delay); } catch(InterruptedException e){}
			}

			/* wait for the publish callback to be called */
			ErrorInfo[] errors = msgComplete1.waitFor();
			assertTrue("Verify success from all message callbacks", errors.length == 0);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(messageCount);
			assertEquals("Verify message subscriptions all called", messageWaiter.receivedMessages.size(), messageCount);
			messageWaiter.reset();

			/* disconnect the rx connection, without closing;
			 * NOTE this depends on knowledge of the internal structure
			 * of the library, to simulate a dropped transport without
			 * causing the connection itself to be disposed */
			ablyRx.connection.connectionManager.requestState(ConnectionState.disconnected);

			/* wait */
			try { Thread.sleep(2000L); } catch(InterruptedException e) {}

			/* publish next messages to the channel */
			CompletionSet msgComplete2 = new CompletionSet();
			for(int i = 0; i < messageCount; i++) {
				channelTx.publish("test_event", "Test message (resume_disconnected) " + i, msgComplete2.add());
				try { Thread.sleep(delay); } catch(InterruptedException e){}
			}

			/* wait for the publish callback to be called */
			errors = msgComplete2.waitFor();
			assertTrue("Verify success from all message callbacks", errors.length == 0);

			/* reconnect the rx connection, and expect the messages to be delivered */
			ablyRx.connection.connect();
			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(messageCount);
			assertEquals("Verify message subscriptions all called after reconnection", messageWaiter.receivedMessages.size(), messageCount);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ablyTx != null) {
				ablyTx.close();
			}
			if(ablyRx != null) {
				ablyRx.close();
			}
		}
	}

	/**
	 * Verify resume behaviour with multiple channels
	 */
	@Test
	public void resume_multiple_channel() {
		AblyRealtime ablyTx = null;
		AblyRealtime ablyRx = null;
		String channelName = "resume_multiple_channel";
		int messageCount = 5;
		long delay = 200;
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			ablyRx = new AblyRealtime(opts);
			ablyTx = new AblyRealtime(opts);

			/* create and attach channels to send on */
			final Channel channelTx1 = ablyTx.channels.get(channelName + "_1");
			channelTx1.attach();
			(new ChannelWaiter(channelTx1)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached for tx", channelTx1.state, ChannelState.attached);
			final Channel channelTx2 = ablyTx.channels.get(channelName + "_2");
			channelTx2.attach();
			(new ChannelWaiter(channelTx2)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached for tx", channelTx2.state, ChannelState.attached);

			/* create and attach channel to recv on */
			final Channel channelRx1 = ablyRx.channels.get(channelName + "_1");
			channelRx1.attach();
			(new ChannelWaiter(channelRx1)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached for rx", channelRx1.state, ChannelState.attached);
			final Channel channelRx2 = ablyRx.channels.get(channelName + "_2");
			channelRx2.attach();
			(new ChannelWaiter(channelRx2)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached for rx", channelRx2.state, ChannelState.attached);

			/* subscribe */
			MessageWaiter messageWaiter1 =  new MessageWaiter(channelRx1);
			MessageWaiter messageWaiter2 =  new MessageWaiter(channelRx2);

			/* publish first messages to channels */
			CompletionSet msgComplete1 = new CompletionSet();
			for(int i = 0; i < messageCount; i++) {
				channelTx1.publish("test_event1", "Test message (resume_multiple_channel) " + i, msgComplete1.add());
				channelTx2.publish("test_event2", "Test message (resume_multiple_channel) " + i, msgComplete1.add());
				try { Thread.sleep(delay); } catch(InterruptedException e){}
			}

			/* wait for the publish callback to be called */
			ErrorInfo[] errors = msgComplete1.waitFor();
			assertTrue("Verify success from all message callbacks", errors.length == 0);

			/* wait for the subscription callback to be called */
			messageWaiter1.waitFor(messageCount);
			assertEquals("Verify message subscriptions all called", messageWaiter1.receivedMessages.size(), messageCount);
			messageWaiter1.reset();
			messageWaiter2.waitFor(messageCount);
			assertEquals("Verify message subscriptions all called", messageWaiter2.receivedMessages.size(), messageCount);
			messageWaiter2.reset();

			/* disconnect the rx connection, without closing;
			 * NOTE this depends on knowledge of the internal structure
			 * of the library, to simulate a dropped transport without
			 * causing the connection itself to be disposed */
			ablyRx.connection.connectionManager.requestState(ConnectionState.disconnected);

			/* wait */
			try { Thread.sleep(2000L); } catch(InterruptedException e) {}

			/* publish next messages to the channel */
			CompletionSet msgComplete2 = new CompletionSet();
			for(int i = 0; i < messageCount; i++) {
				channelTx1.publish("test_event1", "Test message (resume_multiple_channel) " + i, msgComplete2.add());
				channelTx2.publish("test_event2", "Test message (resume_multiple_channel) " + i, msgComplete2.add());
				try { Thread.sleep(delay); } catch(InterruptedException e){}
			}

			/* wait for the publish callback to be called */
			errors = msgComplete2.waitFor();
			assertTrue("Verify success from all message callbacks", errors.length == 0);

			/* reconnect the rx connection, and expect the messages to be delivered */
			ablyRx.connection.connect();
			/* wait for the subscription callback to be called */
			messageWaiter1.waitFor(messageCount);
			messageWaiter2.waitFor(messageCount);
			assertEquals("Verify message subscriptions all called after reconnection", messageWaiter1.receivedMessages.size(), messageCount);
			assertEquals("Verify message subscriptions all called after reconnection", messageWaiter2.receivedMessages.size(), messageCount);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ablyTx != null) {
				ablyTx.close();
			}
			if(ablyRx != null) {
				ablyRx.close();
			}
		}
	}

	/**
	 * Verify resume behaviour across disconnect periods covering
	 * multiple subminute intervals
	 */
	@Test
	public void resume_multiple_interval() {
		AblyRealtime ablyTx = null;
		AblyRealtime ablyRx = null;
		String channelName = "resume_multiple_interval";
		int messageCount = 5;
		long delay = 200;
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			ablyRx = new AblyRealtime(opts);
			ablyTx = new AblyRealtime(opts);

			/* create and attach channel to send on */
			final Channel channelTx = ablyTx.channels.get(channelName);
			channelTx.attach();
			(new ChannelWaiter(channelTx)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached for tx", channelTx.state, ChannelState.attached);

			/* create and attach channel to recv on */
			final Channel channelRx = ablyRx.channels.get(channelName);
			channelRx.attach();
			(new ChannelWaiter(channelRx)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached for rx", channelRx.state, ChannelState.attached);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(channelRx);

			/* publish first messages to the channel */
			CompletionSet msgComplete1 = new CompletionSet();
			for(int i = 0; i < messageCount; i++) {
				channelTx.publish("test_event", "Test message (resume_multiple_interval) " + i, msgComplete1.add());
				try { Thread.sleep(delay); } catch(InterruptedException e){}
			}

			/* wait for the publish callback to be called */
			ErrorInfo[] errors = msgComplete1.waitFor();
			assertTrue("Verify success from all message callbacks", errors.length == 0);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(messageCount);
			assertEquals("Verify message subscriptions all called", messageWaiter.receivedMessages.size(), messageCount);
			messageWaiter.reset();

			/* disconnect the rx connection, without closing;
			 * NOTE this depends on knowledge of the internal structure
			 * of the library, to simulate a dropped transport without
			 * causing the connection itself to be disposed */
			ablyRx.connection.connectionManager.requestState(ConnectionState.disconnected);

			/* wait */
			try { Thread.sleep(20000L); } catch(InterruptedException e) {}

			/* publish next messages to the channel */
			CompletionSet msgComplete2 = new CompletionSet();
			for(int i = 0; i < messageCount; i++) {
				channelTx.publish("test_event", "Test message (resume_multiple_interval) " + i, msgComplete2.add());
				try { Thread.sleep(delay); } catch(InterruptedException e){}
			}

			/* wait for the publish callback to be called */
			errors = msgComplete2.waitFor();
			assertTrue("Verify success from all message callbacks", errors.length == 0);

			/* reconnect the rx connection, and expect the messages to be delivered */
			ablyRx.connection.connect();
			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(messageCount);
			assertEquals("Verify message subscriptions all called after reconnection", messageWaiter.receivedMessages.size(), messageCount);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ablyTx != null) {
				ablyTx.close();
			}
			if(ablyRx != null) {
				ablyRx.close();
			}
		}
	}

	/**
	 * Connect to the service using two library instances to set
	 * up separate send and recv connections.
	 * Disconnect and then reconnect the send connection; verify that
	 * each subsequent publish causes a CompletionListener call.
	 */
	@Test
	public void resume_verify_publish() {
		AblyRealtime ablyTx = null;
		AblyRealtime ablyRx = null;
		String channelName = "resume_verify_publish";
		int messageCount = 5;
		long delay = 200;
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			ablyRx = new AblyRealtime(opts);
			ablyTx = new AblyRealtime(opts);

			/* create and attach channel to send on */
			final Channel channelTx = ablyTx.channels.get(channelName);
			channelTx.attach();
			(new ChannelWaiter(channelTx)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached for tx", channelTx.state, ChannelState.attached);

			/* create and attach channel to recv on */
			final Channel channelRx = ablyRx.channels.get(channelName);
			channelRx.attach();
			(new ChannelWaiter(channelRx)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached for rx", channelRx.state, ChannelState.attached);

			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(channelRx);

			/* publish first messages to the channel */
			CompletionSet msgComplete1 = new CompletionSet();
			for(int i = 0; i < messageCount; i++) {
				channelTx.publish("test_event", "Test message (resume_simple) " + i, msgComplete1.add());
				try { Thread.sleep(delay); } catch(InterruptedException e){}
			}

			/* wait for the publish callback to be called */
			ErrorInfo[] errors = msgComplete1.waitFor();
			assertTrue("Verify success from all message callbacks", errors.length == 0);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(messageCount);
			assertEquals("Verify message subscriptions all called", messageWaiter.receivedMessages.size(), messageCount);
			messageWaiter.reset();

			/* disconnect the tx connection, without closing;
			 * NOTE this depends on knowledge of the internal structure
			 * of the library, to simulate a dropped transport without
			 * causing the connection itself to be disposed */
			System.out.println("*** about to disconnect tx connection");
			ablyTx.connection.connectionManager.requestState(ConnectionState.disconnected);

			/* wait */
			try { Thread.sleep(2000L); } catch(InterruptedException e) {}

			/* reconnect the tx connection */
			System.out.println("*** about to reconnect tx connection");
			ablyTx.connection.connect();
			(new ConnectionWaiter(ablyTx.connection)).waitFor(ConnectionState.connected);

			/* publish further messages to the channel */
			CompletionSet msgComplete2 = new CompletionSet();
			for(int i = 0; i < messageCount; i++) {
				channelTx.publish("test_event", "Test message (resume_simple) " + i, msgComplete2.add());
				try { Thread.sleep(delay); } catch(InterruptedException e){}
			}

			/* wait for the publish callback to be called. This never finishes if
			 * https://github.com/ably/ably-java/issues/170
			 * is not fixed. */
			System.out.println("*** published. About to wait for callbacks");
			errors = msgComplete2.waitFor();
			System.out.println("*** done");
			assertTrue("Verify success from all message callbacks", errors.length == 0);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(messageCount);
			assertEquals("Verify message subscriptions all called after reconnection", messageWaiter.receivedMessages.size(), messageCount);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ablyTx != null) {
				ablyTx.close();
			}
			if(ablyRx != null) {
				ablyRx.close();
			}
		}
	}

	/**
	 * Connect to the service using two library instances to set
	 * up separate send and recv connections.
	 *
	 * Send some messages, drop the sender's transport, then send another
	 * round of messages which should be queued and published after
	 * we reconnect the sender.
	 */
	@Test
	public void resume_publish_queue() {
		AblyRealtime receiver = null;
		AblyRealtime sender = null;
		String channelName = "resume_publish_queue";
		int messageCount = 3;
		long delay = 200;
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			receiver = new AblyRealtime(opts);
			sender = new AblyRealtime(opts);

			/* create and attach channel to send on */
			final Channel senderChannel = sender.channels.get(channelName);
			senderChannel.attach();
			(new ChannelWaiter(senderChannel)).waitFor(ChannelState.attached);
			assertEquals(
			    "The sender's channel should be attached",
			    senderChannel.state, ChannelState.attached
			);

			/* create and attach channel to recv on */
			final Channel receiverChannel = receiver.channels.get(channelName);
			receiverChannel.attach();
			(new ChannelWaiter(receiverChannel)).waitFor(ChannelState.attached);
			assertEquals(
				"The receiver's channel should be attached",
				receiverChannel.state, ChannelState.attached
			);
			/* subscribe */
			MessageWaiter messageWaiter =  new MessageWaiter(receiverChannel);

			/* publish first messages to the channel */
			CompletionSet msgComplete1 = new CompletionSet();
			for(int i = 0; i < messageCount; i++) {
				senderChannel.publish("test_event", "Test message (resume_publish_queue) " + i, msgComplete1.add());
				try { Thread.sleep(delay); } catch(InterruptedException e){}
			}

			/* wait for the publish callback to be called */
			ErrorInfo[] errors = msgComplete1.waitFor();
			assertTrue(
			    "First round of messages has errors", errors.length == 0
			);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(messageCount);
			assertEquals(
			    "Did not receive the entire first round of messages",
			    messageWaiter.receivedMessages.size(), messageCount
			);
			messageWaiter.reset();

			/* disconnect the sender, without closing;
			 * NOTE this depends on knowledge of the internal structure
			 * of the library, to simulate a dropped transport without
			 * causing the connection itself to be disposed */
			sender.connection.connectionManager.requestState(ConnectionState.disconnected);

			/* wait */
			try { Thread.sleep(2000L); } catch(InterruptedException e) {}

			/*
			 *  publish further messages to the channel, which should be queued
			 *  because the channel is currently disconnected.
			 */
			CompletionSet msgComplete2 = new CompletionSet();
			for(int i = 0; i < messageCount; i++) {
				senderChannel.publish("queued_message_" + i, "Test queued message (resume_publish_queue) " + i, msgComplete2.add());
				try { Thread.sleep(delay); } catch(InterruptedException e){}
			}

			/* reconnect the sender */
			sender.connection.connect();
			(new ConnectionWaiter(sender.connection)).waitFor(ConnectionState.connected);


			/* wait for the publish callback to be called.*/
			errors = msgComplete2.waitFor();
			assertTrue(
			    "Second round of messages (queued) has errors",
				errors.length == 0
			);

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(messageCount);

			List<Message> received = messageWaiter.receivedMessages;
			assertEquals(
				"Did not receive the entire second round of messages (queued)",
				received.size(), messageCount
			);
			for(int i=0; i<received.size(); i++) {
				assertEquals(
					"Received unexpected queued message",
					received.get(i).name, "queued_message_" + i
				);
			}
		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(sender != null) {
				sender.close();
			}
			if(receiver != null) {
				receiver.close();
			}
		}
	}
}
