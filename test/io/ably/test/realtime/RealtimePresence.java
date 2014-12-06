package io.ably.test.realtime;

import static org.junit.Assert.*;

import io.ably.realtime.AblyRealtime;
import io.ably.realtime.Channel;
import io.ably.realtime.Channel.ChannelState;
import io.ably.realtime.Connection.ConnectionState;
import io.ably.rest.AblyRest;
import io.ably.rest.Auth;
import io.ably.rest.Auth.TokenParams;
import io.ably.test.realtime.RealtimeSetup.TestVars;
import io.ably.test.realtime.Helpers.ChannelWaiter;
import io.ably.test.realtime.Helpers.CompletionWaiter;
import io.ably.test.realtime.Helpers.ConnectionWaiter;
import io.ably.test.realtime.Helpers.PresenceWaiter;
import io.ably.types.AblyException;
import io.ably.types.Options;
import io.ably.types.PresenceMessage;
import io.ably.types.PresenceMessage.Action;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class RealtimePresence {

	private static TestVars testVars;
	private static AblyRest rest;
	private static AblyRealtime ably;
	private static final String testClientId1 = "testClientId1";
	private static final String testClientId2 = "testClientId2";
	private static final String presenceChannelName = "presence0";
	private static Auth.TokenDetails token1;
	private static Auth.TokenDetails token2;
	private static Channel rtPresenceChannel;
	private static io.ably.rest.Channel restPresenceChannel;

	private static boolean contains(PresenceMessage[] messages, String clientId) {
		for(PresenceMessage message : messages)
			if(clientId.equals(message.clientId))
				return true;
		return false;
	}

	private boolean contains(PresenceMessage[] messages, String clientId, PresenceMessage.Action action) {
		for(PresenceMessage message : messages)
			if(clientId.equals(message.clientId) && action == message.action)
				return true;
		return false;
	}

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		testVars = RealtimeSetup.getTestVars();

		/* create tokens for specific clientIds */
		Options opts = new Options(testVars.keys[0].keyStr);
		testVars.fillInOptions(opts);
		rest = new AblyRest(opts);
		token1 = rest.auth.requestToken(null, new TokenParams() {{ client_id = testClientId1; }});
		token2 = rest.auth.requestToken(null, new TokenParams() {{ client_id = testClientId2; }});

		/* get rest channel */
		restPresenceChannel = rest.channels.get(presenceChannelName);

		Options rtOpts = new Options(testVars.keys[0].keyStr);
		testVars.fillInOptions(rtOpts);
		ably = new AblyRealtime(rtOpts);
		(rtPresenceChannel = ably.channels.get(presenceChannelName)).attach();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		ably.close();
	}

	/**
	 * Attach to channel, enter presence channel and await entered event
	 */
	@Test
	public void enter_simple() {
		AblyRealtime clientAbly1 = null;
		try {
			/* subscribe for presence events in the anonymous connection */
			PresenceWaiter presenceWaiter = new PresenceWaiter(rtPresenceChannel);
			/* set up a connection with specific clientId */
			Options client1Opts = new Options() {{
				authToken = token1.id;
				clientId = testClientId1;
			}};
			testVars.fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* wait until connected */
			(new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

			/* get channel and attach */
			Channel client1Channel = clientAbly1.channels.get(presenceChannelName);
			client1Channel.attach();
			(new ChannelWaiter(client1Channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", client1Channel.state, ChannelState.attached);

			/* let client1 enter the channel and wait for the entered event to be delivered */
			CompletionWaiter enterComplete = new CompletionWaiter();
			client1Channel.presence.enter("Test data (enter_simple)", enterComplete);
			presenceWaiter.waitFor(testClientId1, Action.ENTER);
			assertTrue(presenceWaiter.contains(testClientId1, Action.ENTER));

			/* verify enter callback called on completion */
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);
		} catch(AblyException e) {
			e.printStackTrace();
			fail("enter0: Unexpected exception running test");
		} catch(Throwable t) {
			t.printStackTrace();
			fail("enter0: Unexpected exception running test");
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
		}
	}

	/**
	 * Enter presence channel without prior attach and await entered event
	 */
	@Test
	public void enter_before_attach() {
		AblyRealtime clientAbly1 = null;
		try {
			/* subscribe for presence events in the anonymous connection */
			PresenceWaiter presenceWaiter = new PresenceWaiter(rtPresenceChannel);
			/* set up a connection with specific clientId */
			Options client1Opts = new Options() {{
				authToken = token1.id;
				clientId = testClientId1;
			}};
			testVars.fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* wait until connected */
			(new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

			/* get channel */
			Channel client1Channel = clientAbly1.channels.get(presenceChannelName);

			/* let client1 enter the channel and wait for the entered event to be delivered */
			CompletionWaiter enterComplete = new CompletionWaiter();
			client1Channel.presence.enter("Test data (enter_before_attach)", enterComplete);
			presenceWaiter.waitFor(testClientId1, Action.ENTER);
			assertTrue(presenceWaiter.contains(testClientId1, Action.ENTER));

			/* verify enter callback called on completion */
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);
		} catch(AblyException e) {
			e.printStackTrace();
			fail("enter0: Unexpected exception running test");
		} catch(Throwable t) {
			t.printStackTrace();
			fail("enter0: Unexpected exception running test");
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
		}
	}

	/**
	 * Enter presence channel without prior connect and await entered event
	 */
	@Test
	public void enter_before_connect() {
		AblyRealtime clientAbly1 = null;
		try {
			/* subscribe for presence events in the anonymous connection */
			PresenceWaiter presenceWaiter = new PresenceWaiter(rtPresenceChannel);
			/* set up a connection with specific clientId */
			Options client1Opts = new Options() {{
				authToken = token1.id;
				clientId = testClientId1;
			}};
			testVars.fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* get channel */
			Channel client1Channel = clientAbly1.channels.get(presenceChannelName);

			/* let client1 enter the channel and wait for the entered event to be delivered */
			CompletionWaiter enterComplete = new CompletionWaiter();
			client1Channel.presence.enter("Test data (enter_before_connect)", enterComplete);
			presenceWaiter.waitFor(testClientId1, Action.ENTER);
			assertTrue(presenceWaiter.contains(testClientId1, Action.ENTER));

			/* verify enter callback called on completion */
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);
		} catch(AblyException e) {
			e.printStackTrace();
			fail("enter0: Unexpected exception running test");
		} catch(Throwable t) {
			t.printStackTrace();
			fail("enter0: Unexpected exception running test");
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
		}
	}

	/**
	 * Enter, then leave, presence channel and await leave event
	 */
	@Test
	public void enter_leave_simple() {
		AblyRealtime clientAbly1 = null;
		try {
			/* subscribe for presence events in the anonymous connection */
			PresenceWaiter presenceWaiter = new PresenceWaiter(rtPresenceChannel);
			/* set up a connection with specific clientId */
			Options client1Opts = new Options() {{
				authToken = token1.id;
				clientId = testClientId1;
			}};
			testVars.fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* get channel */
			Channel client1Channel = clientAbly1.channels.get(presenceChannelName);

			/* let client1 enter the channel and wait for the entered event to be delivered */
			CompletionWaiter enterComplete = new CompletionWaiter();
			client1Channel.presence.enter("Test data (enter_before_connect)", enterComplete);
			presenceWaiter.waitFor(testClientId1, Action.ENTER);
			assertTrue(presenceWaiter.contains(testClientId1, Action.ENTER));

			/* verify enter callback called on completion */
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);

			/* let client1 leave the channel and wait for the leave event to be delivered */
			CompletionWaiter leaveComplete = new CompletionWaiter();
			client1Channel.presence.leave(leaveComplete);
			presenceWaiter.waitFor(testClientId1, Action.LEAVE);
			assertTrue(presenceWaiter.contains(testClientId1, Action.LEAVE));

			/* verify leave callback called on completion */
			leaveComplete.waitFor();
			assertTrue("Verify leave callback called on completion", leaveComplete.success);

		} catch(AblyException e) {
			e.printStackTrace();
			fail("enter0: Unexpected exception running test");
		} catch(Throwable t) {
			t.printStackTrace();
			fail("enter0: Unexpected exception running test");
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
		}
	}

	/**
	 * Attach to channel, enter presence channel and get presence using realtime get()
	 */
	@Test
	public void realtime_get_simple() {
		AblyRealtime clientAbly1 = null;
		try {
			/* subscribe for presence events in the anonymous connection */
			PresenceWaiter presenceWaiter = new PresenceWaiter(rtPresenceChannel);

			/* set up a connection with specific clientId */
			Options client1Opts = new Options() {{
				authToken = token1.id;
				clientId = testClientId1;
			}};
			testVars.fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* wait until connected */
			(new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

			/* get channel and attach */
			Channel client1Channel = clientAbly1.channels.get(presenceChannelName);
			client1Channel.attach();
			(new ChannelWaiter(client1Channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", client1Channel.state, ChannelState.attached);

			/* let client1 enter the channel and wait for the success callback */
			CompletionWaiter enterComplete = new CompletionWaiter();
			client1Channel.presence.enter("Test data (get_simple)", enterComplete);
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);

			/* get presence set and verify client present */
			presenceWaiter.waitFor(testClientId1);
			PresenceMessage[] presences = rtPresenceChannel.presence.get();
			assertTrue("Verify expected client is in presence set", contains(presences, testClientId1, Action.ENTER));
			
		} catch(AblyException e) {
			e.printStackTrace();
			fail("enter0: Unexpected exception running test");
		} catch(Throwable t) {
			t.printStackTrace();
			fail("enter0: Unexpected exception running test");
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
		}
	}

	/**
	 * Attach to channel, enter+leave presence channel and get presence with realtime get()
	 */
	@Test
	public void realtime_get_leave() {
		AblyRealtime clientAbly1 = null;
		try {
			/* subscribe for presence events in the anonymous connection */
			PresenceWaiter presenceWaiter = new PresenceWaiter(rtPresenceChannel);
			/* set up a connection with specific clientId */
			Options client1Opts = new Options() {{
				authToken = token1.id;
				clientId = testClientId1;
			}};
			testVars.fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* wait until connected */
			(new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

			/* get channel and attach */
			Channel client1Channel = clientAbly1.channels.get(presenceChannelName);
			client1Channel.attach();
			(new ChannelWaiter(client1Channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", client1Channel.state, ChannelState.attached);

			/* let client1 enter the channel and wait for the success callback */
			CompletionWaiter enterComplete = new CompletionWaiter();
			client1Channel.presence.enter("Test data (get_leave)", enterComplete);
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);

			/* let client1 leave the channel; wait for the success callback and event */
			CompletionWaiter leaveComplete = new CompletionWaiter();
			client1Channel.presence.leave(leaveComplete);
			leaveComplete.waitFor();
			assertTrue("Verify leave callback called on completion", leaveComplete.success);
			presenceWaiter.waitFor(testClientId1, Action.LEAVE);
			assertTrue("Verify leave callback called on completion", leaveComplete.success);

			/* get presence set and verify client absent */
			PresenceMessage[] presences = rtPresenceChannel.presence.get();
			assertFalse("Verify expected client is in presence set", contains(presences, testClientId1));
			
		} catch(AblyException e) {
			e.printStackTrace();
			fail("enter0: Unexpected exception running test");
		} catch(Throwable t) {
			t.printStackTrace();
			fail("enter0: Unexpected exception running test");
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
		}
	}

	/**
	 * Attach to channel, enter presence channel, then initiate second
	 * connection, seeing existing member in message subsequent to second attach response
	 */
	@Test
	public void attach_enter() {
		AblyRealtime clientAbly1 = null;
		AblyRealtime clientAbly2 = null;
		try {
			/* subscribe for presence events in the anonymous connection */
			new PresenceWaiter(rtPresenceChannel);
			/* set up a connection with specific clientId */
			Options client1Opts = new Options() {{
				authToken = token1.id;
				clientId = testClientId1;
			}};
			testVars.fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* wait until connected */
			(new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

			/* get channel and attach */
			Channel client1Channel = clientAbly1.channels.get(presenceChannelName);
			client1Channel.attach();
			(new ChannelWaiter(client1Channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", client1Channel.state, ChannelState.attached);

			/* let client1 enter the channel and wait for the success callback */
			CompletionWaiter enterComplete = new CompletionWaiter();
			client1Channel.presence.enter("Test data (attach_enter)", enterComplete);
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);

			/* set up a second connection with different clientId */
			Options client2Opts = new Options() {{
				authToken = token2.id;
				clientId = testClientId2;
			}};
			testVars.fillInOptions(client2Opts);
			clientAbly2 = new AblyRealtime(client2Opts);

			/* wait until connected */
			(new ConnectionWaiter(clientAbly2.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", clientAbly2.connection.state, ConnectionState.connected);

			/* get channel and subscribe to presence */
			Channel client2Channel = clientAbly2.channels.get(presenceChannelName);
			PresenceWaiter client2Waiter = new PresenceWaiter(client2Channel);
			client2Waiter.waitFor(testClientId1, Action.ENTER);

			/* get presence set and verify client present */
			PresenceMessage[] presences = client2Channel.presence.get();
			assertTrue("Verify expected client is in presence set", contains(presences, testClientId1, Action.ENTER));
			
		} catch(AblyException e) {
			e.printStackTrace();
			fail("enter0: Unexpected exception running test");
		} catch(Throwable t) {
			t.printStackTrace();
			fail("enter0: Unexpected exception running test");
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
			if(clientAbly2 != null)
				clientAbly2.close();
		}
	}

	/**
	 * Attach and enter channel on two connections, seeing
	 * both members in presence returned by realtime get() */
	@Test
	public void realtime_enter_multiple() {
		AblyRealtime clientAbly1 = null;
		AblyRealtime clientAbly2 = null;
		try {
			/* subscribe for presence events in the anonymous connection */
			PresenceWaiter waiter = new PresenceWaiter(rtPresenceChannel);

			/* set up a connection with specific clientId */
			Options client1Opts = new Options() {{
				authToken = token1.id;
				clientId = testClientId1;
			}};
			testVars.fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* get channel and attach */
			Channel client1Channel = clientAbly1.channels.get(presenceChannelName);
			CompletionWaiter enter1Complete = new CompletionWaiter();
			client1Channel.presence.enter("Test data (enter_multiple, clientId1)", enter1Complete);
			enter1Complete.waitFor();
			assertTrue("Verify enter callback called on completion", enter1Complete.success);

			/* set up a second connection with different clientId */
			Options client2Opts = new Options() {{
				authToken = token2.id;
				clientId = testClientId2;
			}};
			testVars.fillInOptions(client2Opts);
			clientAbly2 = new AblyRealtime(client2Opts);

			/* get channel and subscribe to presence */
			Channel client2Channel = clientAbly2.channels.get(presenceChannelName);
			CompletionWaiter enter2Complete = new CompletionWaiter();
			client2Channel.presence.enter("Test data (enter_multiple, clientId2)", enter2Complete);
			enter2Complete.waitFor();
			assertTrue("Verify enter callback called on completion", enter2Complete.success);

			/* verify enter events for both clients are received */
			waiter.waitFor(testClientId1, Action.ENTER);
			waiter.waitFor(testClientId2, Action.ENTER);

			/* get presence set and verify clients present */
			PresenceMessage[] presences = rtPresenceChannel.presence.get();
			assertTrue("Verify expected clients are in presence set", contains(presences, testClientId1, Action.ENTER));
			assertTrue("Verify expected clients are in presence set", contains(presences, testClientId2, Action.ENTER));
			
		} catch(AblyException e) {
			e.printStackTrace();
			fail("enter0: Unexpected exception running test");
		} catch(Throwable t) {
			t.printStackTrace();
			fail("enter0: Unexpected exception running test");
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
			if(clientAbly2 != null)
				clientAbly2.close();
		}
	}

	/**
	 * Attach to channel, enter presence channel and get presence using rest get()
	 */
	@Test
	public void rest_get_simple() {
		AblyRealtime clientAbly1 = null;
		try {
			/* subscribe for presence events in the anonymous connection */
			new PresenceWaiter(rtPresenceChannel);
			/* set up a connection with specific clientId */
			Options client1Opts = new Options() {{
				authToken = token1.id;
				clientId = testClientId1;
			}};
			testVars.fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* wait until connected */
			(new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

			/* get channel and attach */
			Channel client1Channel = clientAbly1.channels.get(presenceChannelName);
			client1Channel.attach();
			(new ChannelWaiter(client1Channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", client1Channel.state, ChannelState.attached);

			/* let client1 enter the channel and wait for the success callback */
			CompletionWaiter enterComplete = new CompletionWaiter();
			client1Channel.presence.enter("Test data (get_simple)", enterComplete);
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);

			/* get presence set and verify client present */
			PresenceMessage[] presences = restPresenceChannel.presence.get(null).asArray();
			assertTrue("Verify expected client is in presence set", contains(presences, testClientId1, Action.ENTER));

		} catch(AblyException e) {
			e.printStackTrace();
			fail("enter0: Unexpected exception running test");
		} catch(Throwable t) {
			t.printStackTrace();
			fail("enter0: Unexpected exception running test");
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
		}
	}

	/**
	 * Attach to channel, enter+leave presence channel and get presence with rest get()
	 */
	@Test
	public void rest_get_leave() {
		AblyRealtime clientAbly1 = null;
		try {
			/* subscribe for presence events in the anonymous connection */
			PresenceWaiter presenceWaiter = new PresenceWaiter(rtPresenceChannel);
			/* set up a connection with specific clientId */
			Options client1Opts = new Options() {{
				authToken = token1.id;
				clientId = testClientId1;
			}};
			testVars.fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* wait until connected */
			(new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

			/* get channel and attach */
			Channel client1Channel = clientAbly1.channels.get(presenceChannelName);
			client1Channel.attach();
			(new ChannelWaiter(client1Channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", client1Channel.state, ChannelState.attached);

			/* let client1 enter the channel and wait for the success callback */
			CompletionWaiter enterComplete = new CompletionWaiter();
			client1Channel.presence.enter("Test data (get_leave)", enterComplete);
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);

			/* let client1 leave the channel; wait for the success callback and event */
			CompletionWaiter leaveComplete = new CompletionWaiter();
			client1Channel.presence.leave(leaveComplete);
			leaveComplete.waitFor();
			assertTrue("Verify leave callback called on completion", leaveComplete.success);
			presenceWaiter.waitFor(testClientId1, Action.LEAVE);
			assertTrue("Verify leave callback called on completion", leaveComplete.success);

			/* get presence set and verify client absent */
			PresenceMessage[] presences = restPresenceChannel.presence.get(null).asArray();
			assertFalse("Verify expected client is in presence set", contains(presences, testClientId1));

		} catch(AblyException e) {
			e.printStackTrace();
			fail("enter0: Unexpected exception running test");
		} catch(Throwable t) {
			t.printStackTrace();
			fail("enter0: Unexpected exception running test");
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
		}
	}

	/**
	 * Attach and enter channel on two connections, seeing
	 * both members in presence returned by rest get() */
	@Test
	public void rest_enter_multiple() {
		AblyRealtime clientAbly1 = null;
		AblyRealtime clientAbly2 = null;
		try {
			/* subscribe for presence events in the anonymous connection */
			new PresenceWaiter(rtPresenceChannel);
			/* set up a connection with specific clientId */
			Options client1Opts = new Options() {{
				authToken = token1.id;
				clientId = testClientId1;
			}};
			testVars.fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* get channel and attach */
			Channel client1Channel = clientAbly1.channels.get(presenceChannelName);
			CompletionWaiter enter1Complete = new CompletionWaiter();
			client1Channel.presence.enter("Test data (enter_multiple, clientId1)", enter1Complete);
			enter1Complete.waitFor();
			assertTrue("Verify enter callback called on completion", enter1Complete.success);

			/* set up a second connection with different clientId */
			Options client2Opts = new Options() {{
				authToken = token2.id;
				clientId = testClientId2;
			}};
			testVars.fillInOptions(client2Opts);
			clientAbly2 = new AblyRealtime(client2Opts);

			/* get channel and subscribe to presence */
			Channel client2Channel = clientAbly2.channels.get(presenceChannelName);
			CompletionWaiter enter2Complete = new CompletionWaiter();
			client2Channel.presence.enter("Test data (enter_multiple, clientId2)", enter2Complete);
			enter2Complete.waitFor();
			assertTrue("Verify enter callback called on completion", enter2Complete.success);

			/* get presence set and verify client present */
			PresenceMessage[] presences = restPresenceChannel.presence.get(null).asArray();
			assertTrue("Verify expected clients are in presence set", contains(presences, testClientId1, Action.ENTER));
			assertTrue("Verify expected clients are in presence set", contains(presences, testClientId2, Action.ENTER));

		} catch(AblyException e) {
			e.printStackTrace();
			fail("enter0: Unexpected exception running test");
		} catch(Throwable t) {
			t.printStackTrace();
			fail("enter0: Unexpected exception running test");
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
			if(clientAbly2 != null)
				clientAbly2.close();
		}
	}

	/**
	 * Attach to channel, enter presence channel, disconnect and await leave event
	 */
	@Test
	public void disconnect_leave() {
		AblyRealtime clientAbly1 = null;
		boolean requiresClose = false;
		try {
			/* subscribe for presence events in the anonymous connection */
			PresenceWaiter presenceWaiter = new PresenceWaiter(rtPresenceChannel);
			/* set up a connection with specific clientId */
			Options client1Opts = new Options() {{
				authToken = token1.id;
				clientId = testClientId1;
			}};
			testVars.fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);
			requiresClose = true;

			/* get channel */
			Channel client1Channel = clientAbly1.channels.get(presenceChannelName);

			/* let client1 enter the channel and wait for the entered event to be delivered */
			CompletionWaiter enterComplete = new CompletionWaiter();
			client1Channel.presence.enter("Test data (enter_before_connect)", enterComplete);
			presenceWaiter.waitFor(testClientId1, Action.ENTER);
			assertTrue(presenceWaiter.contains(testClientId1, Action.ENTER));

			/* verify enter callback called on completion */
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);

			/* close client1 connection and wait for the leave event to be delivered */
			clientAbly1.close();
			requiresClose = false;
			presenceWaiter.waitFor(testClientId1, Action.LEAVE);
			assertTrue(presenceWaiter.contains(testClientId1, Action.LEAVE));

		} catch(AblyException e) {
			e.printStackTrace();
			fail("enter0: Unexpected exception running test");
		} catch(Throwable t) {
			t.printStackTrace();
			fail("enter0: Unexpected exception running test");
		} finally {
			if(requiresClose)
				clientAbly1.close();
		}
	}

}
