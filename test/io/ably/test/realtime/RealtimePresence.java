package io.ably.test.realtime;

import static org.junit.Assert.*;

import java.util.HashMap;

import io.ably.realtime.AblyRealtime;
import io.ably.realtime.Channel;
import io.ably.realtime.Channel.ChannelState;
import io.ably.realtime.Connection.ConnectionState;
import io.ably.rest.AblyRest;
import io.ably.rest.Auth;
import io.ably.rest.Auth.TokenParams;
import io.ably.test.realtime.RealtimeSetup.TestVars;
import io.ably.test.realtime.Helpers.ChannelWaiter;
import io.ably.test.realtime.Helpers.CompletionSet;
import io.ably.test.realtime.Helpers.CompletionWaiter;
import io.ably.test.realtime.Helpers.ConnectionWaiter;
import io.ably.test.realtime.Helpers.PresenceWaiter;
import io.ably.types.AblyException;
import io.ably.types.Options;
import io.ably.types.PaginatedResult;
import io.ably.types.Param;
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

	private static PresenceMessage contains(PresenceMessage[] messages, String clientId) {
		for(PresenceMessage message : messages)
			if(clientId.equals(message.clientId))
				return message;
		return null;
	}

	private PresenceMessage contains(PresenceMessage[] messages, String clientId, PresenceMessage.Action action) {
		for(PresenceMessage message : messages)
			if(clientId.equals(message.clientId) && action == message.action)
				return message;
		return null;
	}

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		testVars = RealtimeSetup.getTestVars();

		/* create tokens for specific clientIds */
		Options opts = new Options(testVars.keys[0].keyStr);
		testVars.fillInOptions(opts);
		rest = new AblyRest(opts);
		token1 = rest.auth.requestToken(null, new TokenParams() {{ clientId = testClientId1; }});
		token2 = rest.auth.requestToken(null, new TokenParams() {{ clientId = testClientId2; }});

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
			String enterString = "Test data (enter_simple)";
			client1Channel.presence.enter(enterString, enterComplete);
			presenceWaiter.waitFor(testClientId1, Action.ENTER);
			assertNotNull(presenceWaiter.contains(testClientId1, Action.ENTER));
			assertEquals(presenceWaiter.receivedMessages.get(0).data, enterString);

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
			String enterString = "Test data (enter_before_attach)";
			client1Channel.presence.enter(enterString, enterComplete);
			presenceWaiter.waitFor(testClientId1, Action.ENTER);
			PresenceMessage expectedPresent = presenceWaiter.contains(testClientId1, clientAbly1.connection.id, Action.ENTER);
			assertNotNull(expectedPresent);
			assertEquals(expectedPresent.data, enterString);

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
			String enterString = "Test data (enter_before_connect)";
			client1Channel.presence.enter(enterString, enterComplete);
			presenceWaiter.waitFor(testClientId1, Action.ENTER);
			PresenceMessage expectedPresent = presenceWaiter.contains(testClientId1, clientAbly1.connection.id, Action.ENTER);
			assertNotNull(expectedPresent);
			assertEquals(expectedPresent.data, enterString);

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
			String enterString = "Test data (enter_before_connect)";
			client1Channel.presence.enter(enterString, enterComplete);
			presenceWaiter.waitFor(testClientId1, Action.ENTER);
			assertNotNull(presenceWaiter.contains(testClientId1, Action.ENTER));
			assertEquals(presenceWaiter.receivedMessages.get(0).data, enterString);
			presenceWaiter.reset();

			/* verify enter callback called on completion */
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);

			/* let client1 leave the channel and wait for the leave event to be delivered */
			CompletionWaiter leaveComplete = new CompletionWaiter();
			String leaveString = "Test data (enter_before_connect), leaving";
			client1Channel.presence.leave(leaveString, leaveComplete);
			presenceWaiter.waitFor(testClientId1, Action.LEAVE);
			PresenceMessage expectedLeft = presenceWaiter.contains(testClientId1, clientAbly1.connection.id, Action.LEAVE);
			assertNotNull(expectedLeft);
			assertEquals(expectedLeft.data, leaveString);

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
	 * Enter, then enter again, expecting update event
	 */
	@Test
	public void enter_enter_simple() {
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
			String enterString = "Test data (enter_enter_simple)";
			client1Channel.presence.enter(enterString, enterComplete);
			presenceWaiter.waitFor(testClientId1, Action.ENTER);
			assertNotNull(presenceWaiter.contains(testClientId1, Action.ENTER));
			assertEquals(presenceWaiter.receivedMessages.get(0).data, enterString);
			presenceWaiter.reset();

			/* verify enter callback called on completion */
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);

			/* let client1 reenter the channel and wait for the update event to be delivered */
			CompletionWaiter reenterComplete = new CompletionWaiter();
			String reenterString = "Test data (enter_enter_simple), reentering";
			client1Channel.presence.enter(reenterString, reenterComplete);
			presenceWaiter.waitFor(testClientId1, Action.UPDATE);
			assertNotNull(presenceWaiter.contains(testClientId1, Action.UPDATE));
			assertEquals(presenceWaiter.receivedMessages.get(0).data, reenterString);

			/* verify reenter callback called on completion */
			reenterComplete.waitFor();
			assertTrue("Verify reenter callback called on completion", reenterComplete.success);

			/* let client1 leave the channel and wait for the leave event to be delivered */
			CompletionWaiter leaveComplete = new CompletionWaiter();
			String leaveString = "Test data (enter_enter_simple), leaving";
			client1Channel.presence.leave(leaveString, leaveComplete);
			presenceWaiter.waitFor(testClientId1, Action.LEAVE);
			PresenceMessage expectedLeft = presenceWaiter.contains(testClientId1, clientAbly1.connection.id, Action.LEAVE);
			assertNotNull(expectedLeft);
			assertEquals(expectedLeft.data, leaveString);

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
	 * Enter, then update, expecting update event
	 */
	@Test
	public void enter_update_simple() {
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
			String enterString = "Test data (enter_update_simple)";
			client1Channel.presence.enter(enterString, enterComplete);
			presenceWaiter.waitFor(testClientId1, Action.ENTER);
			assertNotNull(presenceWaiter.contains(testClientId1, Action.ENTER));
			assertEquals(presenceWaiter.receivedMessages.get(0).data, enterString);
			presenceWaiter.reset();

			/* verify enter callback called on completion */
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);

			/* let client1 update the channel and wait for the update event to be delivered */
			CompletionWaiter updateComplete = new CompletionWaiter();
			String reenterString = "Test data (enter_update_simple), updating";
			client1Channel.presence.enter(reenterString, updateComplete);
			presenceWaiter.waitFor(testClientId1, Action.UPDATE);
			assertNotNull(presenceWaiter.contains(testClientId1, Action.UPDATE));
			assertEquals(presenceWaiter.receivedMessages.get(0).data, reenterString);

			/* verify reenter callback called on completion */
			updateComplete.waitFor();
			assertTrue("Verify reenter callback called on completion", updateComplete.success);

			/* let client1 leave the channel and wait for the leave event to be delivered */
			CompletionWaiter leaveComplete = new CompletionWaiter();
			String leaveString = "Test data (enter_update_simple), leaving";
			client1Channel.presence.leave(leaveString, leaveComplete);
			presenceWaiter.waitFor(testClientId1, Action.LEAVE);
			PresenceMessage expectedLeft = presenceWaiter.contains(testClientId1, clientAbly1.connection.id, Action.LEAVE);
			assertNotNull(expectedLeft);
			assertEquals(expectedLeft.data, leaveString);

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
	 * Enter, then update with null data, expecting previous data to be superseded
	 */
	@Test
	public void enter_update_null() {
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
			client1Opts.useBinaryProtocol = false;
			clientAbly1 = new AblyRealtime(client1Opts);

			/* get channel */
			Channel client1Channel = clientAbly1.channels.get(presenceChannelName);

			/* let client1 enter the channel and wait for the entered event to be delivered */
			CompletionWaiter enterComplete = new CompletionWaiter();
			String enterString = "Test data (enter_update_null)";
			client1Channel.presence.enter(enterString, enterComplete);
			presenceWaiter.waitFor(testClientId1, Action.ENTER);
			assertNotNull(presenceWaiter.contains(testClientId1, Action.ENTER));
			assertEquals(presenceWaiter.receivedMessages.get(0).data, enterString);
			presenceWaiter.reset();

			/* verify enter callback called on completion */
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);

			/* let client1 update the channel and wait for the update event to be delivered */
			CompletionWaiter updateComplete = new CompletionWaiter();
			String updateString = null;
			client1Channel.presence.enter(updateString, updateComplete);
			presenceWaiter.waitFor(testClientId1, Action.UPDATE);
			assertNotNull(presenceWaiter.contains(testClientId1, Action.UPDATE));
			assertEquals(presenceWaiter.receivedMessages.get(0).data, updateString);

			/* verify reenter callback called on completion */
			updateComplete.waitFor();
			assertTrue("Verify reenter callback called on completion", updateComplete.success);

			/* let client1 leave the channel and wait for the leave event to be delivered */
			CompletionWaiter leaveComplete = new CompletionWaiter();
			String leaveString = "Test data (enter_update_null), leaving";
			client1Channel.presence.leave(leaveString, leaveComplete);
			presenceWaiter.waitFor(testClientId1, Action.LEAVE);
			PresenceMessage expectedLeft = presenceWaiter.contains(testClientId1, clientAbly1.connection.id, Action.LEAVE);
			assertNotNull(expectedLeft);
			assertEquals(expectedLeft.data, leaveString);

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
	 * Update without having first entered, expecting enter event
	 */
	@Test
	public void update_noenter() {
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
			String updateString = "Test data (update_noenter)";
			client1Channel.presence.update(updateString, enterComplete);
			presenceWaiter.waitFor(testClientId1, Action.ENTER);
			assertNotNull(presenceWaiter.contains(testClientId1, Action.ENTER));
			assertEquals(presenceWaiter.receivedMessages.get(0).data, updateString);
			presenceWaiter.reset();

			/* verify enter callback called on completion */
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);

			/* let client1 leave the channel and wait for the leave event to be delivered */
			CompletionWaiter leaveComplete = new CompletionWaiter();
			String leaveString = "Test data (update_noenter), leaving";
			client1Channel.presence.leave(leaveString, leaveComplete);
			presenceWaiter.waitFor(testClientId1, Action.LEAVE);
			PresenceMessage expectedLeft = presenceWaiter.contains(testClientId1, clientAbly1.connection.id, Action.LEAVE);
			assertNotNull(expectedLeft);
			assertEquals(expectedLeft.data, leaveString);

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
	 * Enter, then leave (with no data) and await leave event,
	 * expecting enter data to be in leave event
	 */
	@Test
	public void enter_leave_nodata() {
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
			String enterString = "Test data (enter_before_connect)";
			client1Channel.presence.enter(enterString, enterComplete);
			presenceWaiter.waitFor(testClientId1, Action.ENTER);
			assertNotNull(presenceWaiter.contains(testClientId1, Action.ENTER));
			assertEquals(presenceWaiter.receivedMessages.get(0).data, enterString);
			presenceWaiter.reset();

			/* verify enter callback called on completion */
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);

			/* let client1 leave the channel and wait for the leave event to be delivered */
			CompletionWaiter leaveComplete = new CompletionWaiter();
			client1Channel.presence.leave(leaveComplete);
			presenceWaiter.waitFor(testClientId1, Action.LEAVE);
			assertNotNull(presenceWaiter.contains(testClientId1, Action.LEAVE));
			assertEquals(presenceWaiter.receivedMessages.get(0).data, enterString);

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
			String enterString = "Test data (get_simple)";
			client1Channel.presence.enter(enterString, enterComplete);
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);

			/* get presence set and verify client present */
			presenceWaiter.waitFor(testClientId1);
			PresenceMessage[] presences = rtPresenceChannel.presence.get();
			PresenceMessage expectedPresent = contains(presences, testClientId1, Action.PRESENT);
			assertNotNull("Verify expected client is in presence set", expectedPresent);
			assertEquals(expectedPresent.data, enterString);
			
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
			assertNull("Verify expected client is in presence set", contains(presences, testClientId1));
			
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
	public void attach_enter_simple() {
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
			String enterString = "Test data (attach_enter)";
			client1Channel.presence.enter(enterString, enterComplete);
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
			client2Waiter.waitFor(testClientId1, Action.PRESENT);

			/* get presence set and verify client present */
			PresenceMessage[] presences = client2Channel.presence.get();
			PresenceMessage expectedPresent = contains(presences, testClientId1, Action.PRESENT);
			assertNotNull("Verify expected client is in presence set", expectedPresent);
			assertEquals(expectedPresent.data, enterString);
			
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
	 * Attach to channel, enter presence channel with large number of clientIds,
	 * then initiate second connection, seeing existing members in sync subsequent
	 * to second attach response
	 */
	@Test
	public void attach_enter_multiple() {
		AblyRealtime clientAbly1 = null;
		AblyRealtime clientAbly2 = null;
		int clientCount = 300;
		long delay = 50L;
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

			/* let client1 enter the channel for multiple clients and wait for the success callback */
			CompletionSet enterComplete = new CompletionSet();
			for(int i = 0; i < clientCount; i++) {
				client1Channel.presence.enterClient("client" + i, "Test data (attach_enter_multiple) " + i, enterComplete.add());
				try { Thread.sleep(delay); } catch(InterruptedException e){}
			}
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.errors.isEmpty());

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

			/* get channel */
			Channel client2Channel = clientAbly2.channels.get(presenceChannelName);
			client2Channel.attach();
			(new ChannelWaiter(client2Channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", client2Channel.state, ChannelState.attached);

			/* get presence set and verify client present */
			HashMap<String, PresenceMessage> memberIndex = new HashMap<String, PresenceMessage>();
			PresenceMessage[] members = client2Channel.presence.get(true);
			assertNotNull("Expected non-null messages", members);
			assertEquals("Expected " + clientCount + " messages", members.length, clientCount);

			/* index received messages */
			for(int i = 0; i < members.length; i++) {
				PresenceMessage member = members[i];
				memberIndex.put(member.clientId, member);
			}

			/* verify that all clientIds were received */
			assertEquals("Expected " + clientCount + " members", memberIndex.size(), clientCount);
			for(int i = 0; i < clientCount; i++) {
				String clientId = "client" + i;
				assertTrue("Expected client with id " + clientId, memberIndex.containsKey(clientId));
			}
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
			String enterString1 = "Test data (enter_multiple, clientId1)";
			client1Channel.presence.enter(enterString1, enter1Complete);
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
			String enterString2 = "Test data (enter_multiple, clientId2)";
			client2Channel.presence.enter(enterString2, enter2Complete);
			enter2Complete.waitFor();
			assertTrue("Verify enter callback called on completion", enter2Complete.success);

			/* verify enter events for both clients are received */
			waiter.waitFor(testClientId1, Action.ENTER);
			waiter.waitFor(testClientId2, Action.ENTER);

			/* get presence set and verify clients present */
			PresenceMessage[] presences = rtPresenceChannel.presence.get();
			PresenceMessage expectedPresent1 = contains(presences, testClientId1, Action.PRESENT);
			PresenceMessage expectedPresent2 = contains(presences, testClientId2, Action.PRESENT);
			assertNotNull("Verify expected clients are in presence set", expectedPresent1);
			assertNotNull("Verify expected clients are in presence set", expectedPresent2);
			assertEquals(expectedPresent1.data, enterString1);
			assertEquals(expectedPresent2.data, enterString2);
			
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
			String enterString = "Test data (get_simple)";
			client1Channel.presence.enter(enterString, enterComplete);
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);

			/* get presence set and verify client present */
			PresenceMessage[] presences = restPresenceChannel.presence.get(null).asArray();
			PresenceMessage expectedPresent = contains(presences, testClientId1, Action.PRESENT);
			assertNotNull("Verify expected client is in presence set", expectedPresent);
			assertEquals(expectedPresent.data, enterString);

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
			assertNull("Verify expected client is in presence set", contains(presences, testClientId1));

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
			String enterString1 = "Test data (enter_multiple, clientId1)";
			client1Channel.presence.enter(enterString1, enter1Complete);
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
			String enterString2 = "Test data (enter_multiple, clientId2)";
			client2Channel.presence.enter(enterString2, enter2Complete);
			enter2Complete.waitFor();
			assertTrue("Verify enter callback called on completion", enter2Complete.success);

			/* get presence set and verify client present */
			PresenceMessage[] presences = restPresenceChannel.presence.get(null).asArray();
			PresenceMessage expectedPresent1 = contains(presences, testClientId1, Action.PRESENT);
			PresenceMessage expectedPresent2 = contains(presences, testClientId2, Action.PRESENT);
			assertNotNull("Verify expected clients are in presence set", expectedPresent1);
			assertNotNull("Verify expected clients are in presence set", expectedPresent2);
			assertEquals(expectedPresent1.data, enterString1);
			assertEquals(expectedPresent2.data, enterString2);

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
	 * Attach and enter channel multiple times on a single connection,
	 * retrieving members using paginated rest get() */
	@Test
	public void rest_paginated_get() {
		AblyRealtime clientAbly1 = null;
		int clientCount = 30;
		long delay = 100L;
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

			/* enter multiple clients */
			CompletionSet enterComplete = new CompletionSet();
			for(int i = 0; i < clientCount; i++) {
				client1Channel.presence.enterClient("client" + i, "Test data (rest_paginated_get) " + i, enterComplete.add());
				try { Thread.sleep(delay); } catch(InterruptedException e){}
			}
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.errors.isEmpty());

			/* get the presence for this channel */
			HashMap<String, PresenceMessage> memberIndex = new HashMap<String, PresenceMessage>();
			PaginatedResult<PresenceMessage> members = restPresenceChannel.presence.get(new Param[] { new Param("limit", "10") });
			assertNotNull("Expected non-null messages", members);
			assertEquals("Expected 10 messages", members.asArray().length, 10);

			/* index received messages */
			for(int i = 0; i < 10; i++) {
				PresenceMessage member = members.asArray()[i];
				memberIndex.put(member.clientId, member);
			}

			/* get next page */
			members = restPresenceChannel.presence.get(members.getNext());
			assertNotNull("Expected non-null messages", members);
			assertEquals("Expected 10 messages", members.asArray().length, 10);

			/* index received messages */
			for(int i = 0; i < 10; i++) {
				PresenceMessage member = members.asArray()[i];
				memberIndex.put(member.clientId, member);
			}

			/* get next page */
			members = restPresenceChannel.presence.get(members.getNext());
			assertNotNull("Expected non-null messages", members);
			assertEquals("Expected 10 messages", members.asArray().length, 10);

			/* index received messages */
			for(int i = 0; i < 10; i++) {
				PresenceMessage member = members.asArray()[i];
				memberIndex.put(member.clientId, member);
			}

			/* verify there is no next page */
			assertNull("Expected null next page", members.getNext());

			/* verify that all clientIds were received */
			assertEquals("Expected " + clientCount + " members", memberIndex.size(), clientCount);
			for(int i = 0; i < clientCount; i++) {
				String clientId = "client" + i;
				assertTrue("Expected client with id " + clientId, memberIndex.containsKey(clientId));
			}
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
			String enterString = "Test data (disconnect_leave)";
			client1Channel.presence.enter(enterString, enterComplete);
			presenceWaiter.waitFor(testClientId1, Action.ENTER);
			PresenceMessage expectedPresent = presenceWaiter.contains(testClientId1, Action.ENTER);
			assertNotNull(expectedPresent);
			assertEquals(expectedPresent.data, enterString);

			/* verify enter callback called on completion */
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);

			/* close client1 connection and wait for the leave event to be delivered */
			clientAbly1.close();
			requiresClose = false;
			presenceWaiter.waitFor(testClientId1, Action.LEAVE);
			PresenceMessage expectedLeft = presenceWaiter.contains(testClientId1, Action.LEAVE);
			assertNotNull(expectedLeft);
			/* verify leave message contains data that was published with enter */
			assertEquals(expectedLeft.data, enterString);

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
