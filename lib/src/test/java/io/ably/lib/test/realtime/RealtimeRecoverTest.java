package io.ably.lib.test.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.test.common.Setup;
import io.ably.lib.test.common.Helpers.ChannelWaiter;
import io.ably.lib.test.common.Helpers.CompletionSet;
import io.ably.lib.test.common.Helpers.ConnectionWaiter;
import io.ably.lib.test.common.Helpers.MessageWaiter;
import io.ably.lib.test.common.Setup.TestVars;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;

import org.junit.Test;

public class RealtimeRecoverTest {

	private static final String TAG = RealtimeRecoverTest.class.getName();

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		Setup.getTestVars();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		Setup.clearTestVars();
	}

	/**
	 * Connect to the service using two library instances to set
	 * up separate send and recv connections.
	 * Send on one connection while the other is disconnected;
	 * Open a third connection to inherit from the disconnected connection
	 * and explicitly wait for the connection before re-attaching the channel.
	 * verify that the messages sent whilst disconnected are delivered
	 * on recover
	 */
	@Test
	public void recover_disconnected() {
		AblyRealtime ablyRx = null, ablyTx = null, ablyRxRecover = null;
		String channelName = "recover_disconnected";
		int messageCount = 5;
		long delay = 200;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
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
				channelTx.publish("test_event", "Test message (recover_disconnected) " + i, msgComplete1.add());
				try { Thread.sleep(delay); } catch(InterruptedException e){}
			}

			/* wait for the publish callback to be called */
			ErrorInfo[] errors = msgComplete1.waitFor();
			assertTrue("Verify success from all message callbacks", errors.length == 0);				

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(messageCount);
			assertEquals("Verify message subscriptions all called", messageWaiter.receivedMessages.size(), messageCount);

			/* disconnect the rx connection, without closing;
			 * NOTE this depends on knowledge of the internal structure
			 * of the library, to simulate a dropped transport without
			 * causing the connection itself to be disposed */
			String recoverConnectionId = ablyRx.connection.key;
			long recoverConnectionSerial = ablyRx.connection.serial;
			ablyRx.connection.connectionManager.requestState(ConnectionState.failed);

			/* wait */
			try { Thread.sleep(2000L); } catch(InterruptedException e) {}

			/* publish next messages to the channel */
			CompletionSet msgComplete2 = new CompletionSet();
			for(int i = 0; i < messageCount; i++) {
				channelTx.publish("test_event", "Test message (recover_disconnected) " + i, msgComplete2.add());
				try { Thread.sleep(delay); } catch(InterruptedException e){}
			}

			/* wait for the publish callback to be called */
			errors = msgComplete2.waitFor();
			assertTrue("Verify success from all message callbacks", errors.length == 0);				

			/* establish a new rx connection with recover string, and wait for connection */
			ClientOptions recoverOpts = testVars.createOptions(testVars.keys[0].keyStr);
			recoverOpts.recover = recoverConnectionId + ':' + String.valueOf(recoverConnectionSerial);
			ablyRxRecover = new AblyRealtime(recoverOpts);
			(new ConnectionWaiter(ablyRxRecover.connection)).waitFor(ConnectionState.connected);

			/* subscribe to channel */
			final Channel channelRxRecover = ablyRxRecover.channels.get(channelName);
			MessageWaiter messageWaiterRecover =  new MessageWaiter(channelRxRecover);

			/* wait for the subscription callback to be called */
			messageWaiterRecover.waitFor(messageCount);
			assertEquals("Verify message subscriptions all called after recovery", messageWaiterRecover.receivedMessages.size(), messageCount);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ablyTx != null)
				ablyTx.close();
			if(ablyRx != null)
				ablyRx.close();
			if(ablyRxRecover != null)
				ablyRxRecover.close();
		}
	}

	/**
	 * Connect to the service using two library instances to set
	 * up separate send and recv connections.
	 * Send on one connection while the other is disconnected;
	 * Open a third connection to inherit from the disconnected connection
	 * without an explicit wait for connection.
	 * verify that the messages sent whilst disconnected are delivered
	 * on recover
	 */
	@Test
	public void recover_implicit_connect() {
		AblyRealtime ablyRx = null, ablyTx = null, ablyRxRecover = null;
		String channelName = "recover_implicit_connect";
		int messageCount = 5;
		long delay = 200;
		try {
			TestVars testVars = Setup.getTestVars();
			ClientOptions opts = testVars.createOptions(testVars.keys[0].keyStr);
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
			MessageWaiter messageWaiter = new MessageWaiter(channelRx);

			/* publish first messages to the channel */
			CompletionSet msgComplete1 = new CompletionSet();
			for(int i = 0; i < messageCount; i++) {
				channelTx.publish("test_event", "Test message (recover_implicit_connect) " + i, msgComplete1.add());
				try { Thread.sleep(delay); } catch(InterruptedException e){}
			}

			/* wait for the publish callback to be called */
			ErrorInfo[] errors = msgComplete1.waitFor();
			assertTrue("Verify success from all message callbacks", errors.length == 0);				

			/* wait for the subscription callback to be called */
			messageWaiter.waitFor(messageCount);
			assertEquals("Verify message subscriptions all called", messageWaiter.receivedMessages.size(), messageCount);

			/* disconnect the rx connection, without closing;
			 * NOTE this depends on knowledge of the internal structure
			 * of the library, to simulate a dropped transport without
			 * causing the connection itself to be disposed */
			String recoverConnectionKey = ablyRx.connection.key;
			long recoverConnectionSerial = ablyRx.connection.serial;
			ablyRx.connection.connectionManager.requestState(ConnectionState.failed);

			/* wait */
			try { Thread.sleep(2000L); } catch(InterruptedException e) {}

			/* publish next messages to the channel */
			CompletionSet msgComplete2 = new CompletionSet();
			for(int i = 0; i < messageCount; i++) {
				channelTx.publish("test_event", "Test message (recover_implicit_connect) " + i, msgComplete2.add());
				try { Thread.sleep(delay); } catch(InterruptedException e){}
			}

			/* wait for the publish callback to be called */
			errors = msgComplete2.waitFor();
			assertTrue("Verify success from all message callbacks", errors.length == 0);				

			/* establish a new rx connection with recover string, and wait for connection */
			ClientOptions recoverOpts = testVars.createOptions(testVars.keys[0].keyStr);
			recoverOpts.recover = recoverConnectionKey + ':' + String.valueOf(recoverConnectionSerial);
			ablyRxRecover = new AblyRealtime(recoverOpts);

			/* subscribe to channel */
			final Channel channelRxRecover = ablyRxRecover.channels.get(channelName);
			MessageWaiter messageWaiterRecover =  new MessageWaiter(channelRxRecover);

			/* wait for the subscription callback to be called */
			messageWaiterRecover.waitFor(messageCount);
			assertEquals("Verify message subscriptions all called after recovery", messageWaiterRecover.receivedMessages.size(), messageCount);

		} catch (AblyException e) {
			e.printStackTrace();
			fail("init0: Unexpected exception instantiating library");
		} finally {
			if(ablyTx != null)
				ablyTx.close();
			if(ablyRx != null)
				ablyRx.close();
			if(ablyRxRecover != null)
				ablyRxRecover.close();
		}
	}

}
