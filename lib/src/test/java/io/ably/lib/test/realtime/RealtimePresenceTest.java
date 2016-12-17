package io.ably.lib.test.realtime;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isOneOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.realtime.Presence;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Auth;
import io.ably.lib.rest.Auth.TokenParams;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.Helpers.ChannelWaiter;
import io.ably.lib.test.common.Helpers.CompletionSet;
import io.ably.lib.test.common.Helpers.CompletionWaiter;
import io.ably.lib.test.common.Helpers.ConnectionWaiter;
import io.ably.lib.test.common.Helpers.PresenceWaiter;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.test.util.MockWebsocketFactory;
import io.ably.lib.transport.Defaults;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.Param;
import io.ably.lib.types.PresenceMessage;
import io.ably.lib.types.PresenceMessage.Action;
import io.ably.lib.types.ProtocolMessage;

public class RealtimePresenceTest extends ParameterizedTest {

	private static final String testClientId1 = "testClientId1";
	private static final String testClientId2 = "testClientId2";
	private Auth.TokenDetails token1;
	private Auth.TokenDetails token2;
	private Auth.TokenDetails wildcardToken;

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

	private static String random() {
		return UUID.randomUUID().toString();
	}

	private class TestChannel {
		TestChannel() {
			try {
				ClientOptions opts = createOptions(testVars.keys[0].keyStr);
				rest = new AblyRest(opts);
				restChannel = rest.channels.get(channelName);
				realtime = new AblyRealtime(opts);
				realtimeChannel = realtime.channels.get(channelName);
				realtimeChannel.attach();
				(new ChannelWaiter(realtimeChannel)).waitFor(ChannelState.attached);
			} catch(AblyException ae) {}
		}

		void dispose() {
			if(realtime != null)
				realtime.close();
		}

		String channelName = random();
		AblyRest rest;
		AblyRealtime realtime;
		io.ably.lib.rest.Channel restChannel;
		io.ably.lib.realtime.Channel realtimeChannel;
	}

	@Before
	public void setUpBefore() throws Exception {
		/* create tokens for specific clientIds */
		ClientOptions opts = createOptions(testVars.keys[0].keyStr);
		AblyRest rest = new AblyRest(opts);
		token1 = rest.auth.requestToken(new TokenParams() {{ clientId = testClientId1; }}, null);
		token2 = rest.auth.requestToken(new TokenParams() {{ clientId = testClientId2; }}, null);
		wildcardToken = rest.auth.requestToken(new TokenParams() {{ clientId = "*"; }}, null);
	}

	/**
	 * Attach to channel, enter presence channel and await entered event
	 */
	@Test
	public void enter_simple() {
		AblyRealtime clientAbly1 = null;
		TestChannel testChannel = new TestChannel();
		try {
			/* subscribe for presence events in the anonymous connection */
			PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
			/* set up a connection with specific clientId */
			ClientOptions client1Opts = new ClientOptions() {{
				tokenDetails = token1;
				clientId = testClientId1;
			}};
			fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* wait until connected */
			(new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

			/* get channel and attach */
			Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
			client1Channel.attach();
			(new ChannelWaiter(client1Channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", client1Channel.state, ChannelState.attached);

			/* let client1 enter the channel and wait for the entered event to be delivered */
			CompletionWaiter enterComplete = new CompletionWaiter();
			String enterString = "Test data (enter_simple)";
			client1Channel.presence.enter(enterString, enterComplete);
			presenceWaiter.waitFor(testClientId1, Action.enter);
			assertNotNull(presenceWaiter.contains(testClientId1, Action.enter));
			assertEquals(presenceWaiter.receivedMessages.get(0).data, enterString);

			/* verify enter callback called on completion */
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);
		} catch(AblyException e) {
			e.printStackTrace();
			fail("Unexpected exception running test: " + e.getMessage());
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
			if(testChannel != null)
				testChannel.dispose();
		}
	}

	/**
	 * Enter presence channel without prior attach and await entered event
	 */
	@Test
	public void enter_before_attach() {
		AblyRealtime clientAbly1 = null;
		TestChannel testChannel = new TestChannel();
		try {
			/* subscribe for presence events in the anonymous connection */
			PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
			/* set up a connection with specific clientId */
			ClientOptions client1Opts = new ClientOptions() {{
				tokenDetails = token1;
				clientId = testClientId1;
			}};
			fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* wait until connected */
			(new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

			/* get channel */
			Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);

			/* let client1 enter the channel and wait for the entered event to be delivered */
			CompletionWaiter enterComplete = new CompletionWaiter();
			String enterString = "Test data (enter_before_attach)";
			client1Channel.presence.enter(enterString, enterComplete);
			presenceWaiter.waitFor(testClientId1, Action.enter);
			PresenceMessage expectedPresent = presenceWaiter.contains(testClientId1, clientAbly1.connection.id, Action.enter);
			assertNotNull(expectedPresent);
			assertEquals(expectedPresent.data, enterString);

			/* verify enter callback called on completion */
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);
		} catch(AblyException e) {
			e.printStackTrace();
			fail("Unexpected exception running test: " + e.getMessage());
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
			if(testChannel != null)
				testChannel.dispose();
		}
	}

	/**
	 * Enter presence channel without prior connect and await entered event
	 */
	@Test
	public void enter_before_connect() {
		AblyRealtime clientAbly1 = null;
		TestChannel testChannel = new TestChannel();
		try {
			/* subscribe for presence events in the anonymous connection */
			PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
			/* set up a connection with specific clientId */
			ClientOptions client1Opts = new ClientOptions() {{
				tokenDetails = token1;
				clientId = testClientId1;
			}};
			fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* get channel */
			Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);

			/* let client1 enter the channel and wait for the entered event to be delivered */
			CompletionWaiter enterComplete = new CompletionWaiter();
			String enterString = "Test data (enter_before_connect)";
			client1Channel.presence.enter(enterString, enterComplete);
			presenceWaiter.waitFor(testClientId1, Action.enter);
			PresenceMessage expectedPresent = presenceWaiter.contains(testClientId1, clientAbly1.connection.id, Action.enter);
			assertNotNull(expectedPresent);
			assertEquals(expectedPresent.data, enterString);

			/* verify enter callback called on completion */
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);
		} catch(AblyException e) {
			e.printStackTrace();
			fail("Unexpected exception running test: " + e.getMessage());
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
			if(testChannel != null)
				testChannel.dispose();
		}
	}

	/**
	 * Enter, then leave, presence channel and await leave event
	 */
	@Test
	public void enter_leave_simple() {
		AblyRealtime clientAbly1 = null;
		TestChannel testChannel = new TestChannel();
		try {
			/* subscribe for presence events in the anonymous connection */
			PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
			/* set up a connection with specific clientId */
			ClientOptions client1Opts = new ClientOptions() {{
				tokenDetails = token1;
				clientId = testClientId1;
			}};
			fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* get channel */
			Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);

			/* let client1 enter the channel and wait for the entered event to be delivered */
			CompletionWaiter enterComplete = new CompletionWaiter();
			String enterString = "Test data (enter_before_connect)";
			client1Channel.presence.enter(enterString, enterComplete);
			presenceWaiter.waitFor(testClientId1, Action.enter);
			assertNotNull(presenceWaiter.contains(testClientId1, Action.enter));
			assertEquals(presenceWaiter.receivedMessages.get(0).data, enterString);
			presenceWaiter.reset();

			/* verify enter callback called on completion */
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);

			/* let client1 leave the channel and wait for the leave event to be delivered */
			CompletionWaiter leaveComplete = new CompletionWaiter();
			String leaveString = "Test data (enter_before_connect), leaving";
			client1Channel.presence.leave(leaveString, leaveComplete);
			presenceWaiter.waitFor(testClientId1, Action.leave);
			PresenceMessage expectedLeft = presenceWaiter.contains(testClientId1, clientAbly1.connection.id, Action.leave);
			assertNotNull(expectedLeft);
			assertEquals(expectedLeft.data, leaveString);

			/* verify leave callback called on completion */
			leaveComplete.waitFor();
			assertTrue("Verify leave callback called on completion", leaveComplete.success);

		} catch(AblyException e) {
			e.printStackTrace();
			fail("Unexpected exception running test: " + e.getMessage());
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
			if(testChannel != null)
				testChannel.dispose();
		}
	}

	/**
	 * Enter, then enter again, expecting update event
	 */
	@Test
	public void enter_enter_simple() {
		AblyRealtime clientAbly1 = null;
		TestChannel testChannel = new TestChannel();
		try {
			/* subscribe for presence events in the anonymous connection */
			PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
			/* set up a connection with specific clientId */
			ClientOptions client1Opts = new ClientOptions() {{
				tokenDetails = token1;
				clientId = testClientId1;
			}};
			fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* get channel */
			Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);

			/* let client1 enter the channel and wait for the entered event to be delivered */
			CompletionWaiter enterComplete = new CompletionWaiter();
			String enterString = "Test data (enter_enter_simple)";
			client1Channel.presence.enter(enterString, enterComplete);
			presenceWaiter.waitFor(testClientId1, Action.enter);
			assertNotNull(presenceWaiter.contains(testClientId1, Action.enter));
			assertEquals(presenceWaiter.receivedMessages.get(0).data, enterString);
			presenceWaiter.reset();

			/* verify enter callback called on completion */
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);

			/* let client1 reenter the channel and wait for the update event to be delivered */
			CompletionWaiter reenterComplete = new CompletionWaiter();
			String reenterString = "Test data (enter_enter_simple), reentering";
			client1Channel.presence.enter(reenterString, reenterComplete);
			presenceWaiter.waitFor(testClientId1, Action.update);
			assertNotNull(presenceWaiter.contains(testClientId1, Action.update));
			assertEquals(presenceWaiter.receivedMessages.get(0).data, reenterString);

			/* verify reenter callback called on completion */
			reenterComplete.waitFor();
			assertTrue("Verify reenter callback called on completion", reenterComplete.success);

			/* let client1 leave the channel and wait for the leave event to be delivered */
			CompletionWaiter leaveComplete = new CompletionWaiter();
			String leaveString = "Test data (enter_enter_simple), leaving";
			client1Channel.presence.leave(leaveString, leaveComplete);
			presenceWaiter.waitFor(testClientId1, Action.leave);
			PresenceMessage expectedLeft = presenceWaiter.contains(testClientId1, clientAbly1.connection.id, Action.leave);
			assertNotNull(expectedLeft);
			assertEquals(expectedLeft.data, leaveString);

			/* verify leave callback called on completion */
			leaveComplete.waitFor();
			assertTrue("Verify leave callback called on completion", leaveComplete.success);
		} catch(AblyException e) {
			e.printStackTrace();
			fail("Unexpected exception running test: " + e.getMessage());
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
			if(testChannel != null)
				testChannel.dispose();
		}
	}

	/**
	 * Enter, then update, expecting update event
	 */
	@Test
	public void enter_update_simple() {
		AblyRealtime clientAbly1 = null;
		TestChannel testChannel = new TestChannel();
		try {
			/* subscribe for presence events in the anonymous connection */
			PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
			/* set up a connection with specific clientId */
			ClientOptions client1Opts = new ClientOptions() {{
				tokenDetails = token1;
				clientId = testClientId1;
			}};
			fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* get channel */
			Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);

			/* let client1 enter the channel and wait for the entered event to be delivered */
			CompletionWaiter enterComplete = new CompletionWaiter();
			String enterString = "Test data (enter_update_simple)";
			client1Channel.presence.enter(enterString, enterComplete);
			presenceWaiter.waitFor(testClientId1, Action.enter);
			assertNotNull(presenceWaiter.contains(testClientId1, Action.enter));
			assertEquals(presenceWaiter.receivedMessages.get(0).data, enterString);
			presenceWaiter.reset();

			/* verify enter callback called on completion */
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);

			/* let client1 update the channel and wait for the update event to be delivered */
			CompletionWaiter updateComplete = new CompletionWaiter();
			String reenterString = "Test data (enter_update_simple), updating";
			client1Channel.presence.enter(reenterString, updateComplete);
			presenceWaiter.waitFor(testClientId1, Action.update);
			assertNotNull(presenceWaiter.contains(testClientId1, Action.update));
			assertEquals(presenceWaiter.receivedMessages.get(0).data, reenterString);

			/* verify reenter callback called on completion */
			updateComplete.waitFor();
			assertTrue("Verify reenter callback called on completion", updateComplete.success);

			/* let client1 leave the channel and wait for the leave event to be delivered */
			CompletionWaiter leaveComplete = new CompletionWaiter();
			String leaveString = "Test data (enter_update_simple), leaving";
			client1Channel.presence.leave(leaveString, leaveComplete);
			presenceWaiter.waitFor(testClientId1, Action.leave);
			PresenceMessage expectedLeft = presenceWaiter.contains(testClientId1, clientAbly1.connection.id, Action.leave);
			assertNotNull(expectedLeft);
			assertEquals(expectedLeft.data, leaveString);

			/* verify leave callback called on completion */
			leaveComplete.waitFor();
			assertTrue("Verify leave callback called on completion", leaveComplete.success);
		} catch(AblyException e) {
			e.printStackTrace();
			fail("Unexpected exception running test: " + e.getMessage());
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
			if(testChannel != null)
				testChannel.dispose();
		}
	}

	/**
	 * Enter, then update with null data, expecting previous data to be superseded
	 */
	@Test
	public void enter_update_null() {
		AblyRealtime clientAbly1 = null;
		TestChannel testChannel = new TestChannel();
		try {
			/* subscribe for presence events in the anonymous connection */
			PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
			/* set up a connection with specific clientId */
			ClientOptions client1Opts = new ClientOptions() {{
				tokenDetails = token1;
				clientId = testClientId1;
			}};
			fillInOptions(client1Opts);
			client1Opts.useBinaryProtocol = true;
			clientAbly1 = new AblyRealtime(client1Opts);

			/* get channel */
			Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);

			/* let client1 enter the channel and wait for the entered event to be delivered */
			CompletionWaiter enterComplete = new CompletionWaiter();
			String enterString = "Test data (enter_update_null)";
			client1Channel.presence.enter(enterString, enterComplete);
			presenceWaiter.waitFor(testClientId1, Action.enter);
			assertNotNull(presenceWaiter.contains(testClientId1, Action.enter));
			assertEquals(presenceWaiter.receivedMessages.get(0).data, enterString);
			presenceWaiter.reset();

			/* verify enter callback called on completion */
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);

			/* let client1 update the channel and wait for the update event to be delivered */
			CompletionWaiter updateComplete = new CompletionWaiter();
			String updateString = null;
			client1Channel.presence.enter(updateString, updateComplete);
			presenceWaiter.waitFor(testClientId1, Action.update);
			assertNotNull(presenceWaiter.contains(testClientId1, Action.update));
			assertEquals(presenceWaiter.receivedMessages.get(0).data, updateString);

			/* verify reenter callback called on completion */
			updateComplete.waitFor();
			assertTrue("Verify reenter callback called on completion", updateComplete.success);

			/* let client1 leave the channel and wait for the leave event to be delivered */
			CompletionWaiter leaveComplete = new CompletionWaiter();
			String leaveString = "Test data (enter_update_null), leaving";
			client1Channel.presence.leave(leaveString, leaveComplete);
			presenceWaiter.waitFor(testClientId1, Action.leave);
			PresenceMessage expectedLeft = presenceWaiter.contains(testClientId1, clientAbly1.connection.id, Action.leave);
			assertNotNull(expectedLeft);
			assertEquals(expectedLeft.data, leaveString);

			/* verify leave callback called on completion */
			leaveComplete.waitFor();
			assertTrue("Verify leave callback called on completion", leaveComplete.success);
		} catch(AblyException e) {
			e.printStackTrace();
			fail("Unexpected exception running test: " + e.getMessage());
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
			if(testChannel != null)
				testChannel.dispose();
		}
	}

	/**
	 * Update without having first entered, expecting enter event
	 */
	@Test
	public void update_noenter() {
		AblyRealtime clientAbly1 = null;
		TestChannel testChannel = new TestChannel();
		try {
			/* subscribe for presence events in the anonymous connection */
			PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
			/* set up a connection with specific clientId */
			ClientOptions client1Opts = new ClientOptions() {{
				tokenDetails = token1;
				clientId = testClientId1;
			}};
			fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* get channel */
			Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);

			/* let client1 enter the channel and wait for the entered event to be delivered */
			CompletionWaiter enterComplete = new CompletionWaiter();
			String updateString = "Test data (update_noenter)";
			client1Channel.presence.update(updateString, enterComplete);
			presenceWaiter.waitFor(testClientId1, Action.enter);
			assertNotNull(presenceWaiter.contains(testClientId1, Action.enter));
			assertEquals(presenceWaiter.receivedMessages.get(0).data, updateString);
			presenceWaiter.reset();

			/* verify enter callback called on completion */
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);

			/* let client1 leave the channel and wait for the leave event to be delivered */
			CompletionWaiter leaveComplete = new CompletionWaiter();
			String leaveString = "Test data (update_noenter), leaving";
			client1Channel.presence.leave(leaveString, leaveComplete);
			presenceWaiter.waitFor(testClientId1, Action.leave);
			PresenceMessage expectedLeft = presenceWaiter.contains(testClientId1, clientAbly1.connection.id, Action.leave);
			assertNotNull(expectedLeft);
			assertEquals(expectedLeft.data, leaveString);

			/* verify leave callback called on completion */
			leaveComplete.waitFor();
			assertTrue("Verify leave callback called on completion", leaveComplete.success);
		} catch(AblyException e) {
			e.printStackTrace();
			fail("Unexpected exception running test: " + e.getMessage());
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
			if(testChannel != null)
				testChannel.dispose();
		}
	}

	/**
	 * Enter, then leave (with no data) and await leave event,
	 * expecting enter data to be in leave event
	 */
	@Test
	public void enter_leave_nodata() {
		AblyRealtime clientAbly1 = null;
		TestChannel testChannel = new TestChannel();
		try {
			/* subscribe for presence events in the anonymous connection */
			PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
			/* set up a connection with specific clientId */
			ClientOptions client1Opts = new ClientOptions() {{
				tokenDetails = token1;
				clientId = testClientId1;
			}};
			fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* get channel */
			Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);

			/* let client1 enter the channel and wait for the entered event to be delivered */
			CompletionWaiter enterComplete = new CompletionWaiter();
			String enterString = "Test data (enter_leave_nodata)";
			client1Channel.presence.enter(enterString, enterComplete);
			presenceWaiter.waitFor(testClientId1, Action.enter);
			assertNotNull(presenceWaiter.contains(testClientId1, Action.enter));
			assertEquals(presenceWaiter.receivedMessages.get(0).data, enterString);
			presenceWaiter.reset();

			/* verify enter callback called on completion */
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);

			/* let client1 leave the channel and wait for the leave event to be delivered */
			CompletionWaiter leaveComplete = new CompletionWaiter();
			client1Channel.presence.leave(leaveComplete);
			presenceWaiter.waitFor(testClientId1, Action.leave);
			assertNotNull(presenceWaiter.contains(testClientId1, Action.leave));
			assertEquals(presenceWaiter.receivedMessages.get(0).data, enterString);

			/* verify leave callback called on completion */
			leaveComplete.waitFor();
			assertTrue("Verify leave callback called on completion", leaveComplete.success);

		} catch(AblyException e) {
			e.printStackTrace();
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
			if(testChannel != null)
				testChannel.dispose();
		}
	}

	/**
	 * Attach to channel, enter presence channel and get presence using realtime get()
	 */
	@Test
	public void realtime_get_simple() {
		AblyRealtime clientAbly1 = null;
		TestChannel testChannel = new TestChannel();
		try {
			/* subscribe for presence events in the anonymous connection */
			PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);

			/* set up a connection with specific clientId */
			ClientOptions client1Opts = new ClientOptions() {{
				tokenDetails = token1;
				clientId = testClientId1;
			}};
			fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* wait until connected */
			(new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

			/* get channel and attach */
			Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
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
			PresenceMessage[] presences = testChannel.realtimeChannel.presence.get();
			PresenceMessage expectedPresent = contains(presences, testClientId1, Action.present);
			assertNotNull("Verify expected client is in presence set", expectedPresent);
			assertEquals(expectedPresent.data, enterString);
			
		} catch(AblyException e) {
			e.printStackTrace();
			fail("Unexpected exception running test: " + e.getMessage());
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
			if(testChannel != null)
				testChannel.dispose();
		}
	}

	/**
	 * Attach to channel, enter+leave presence channel and get presence with realtime get()
	 */
	@Test
	public void realtime_get_leave() {
		AblyRealtime clientAbly1 = null;
		TestChannel testChannel = new TestChannel();
		try {
			/* subscribe for presence events in the anonymous connection */
			PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
			/* set up a connection with specific clientId */
			ClientOptions client1Opts = new ClientOptions() {{
				tokenDetails = token1;
				clientId = testClientId1;
			}};
			fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* wait until connected */
			(new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

			/* get channel and attach */
			Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
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
			presenceWaiter.waitFor(testClientId1, Action.leave);
			assertTrue("Verify leave callback called on completion", leaveComplete.success);

			/* get presence set and verify client absent */
			PresenceMessage[] presences = testChannel.realtimeChannel.presence.get();
			assertNull("Verify expected client is in presence set", contains(presences, testClientId1));
			
		} catch(AblyException e) {
			e.printStackTrace();
			fail("Unexpected exception running test: " + e.getMessage());
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
			if(testChannel != null)
				testChannel.dispose();
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
		TestChannel testChannel = new TestChannel();
		try {
			/* subscribe for presence events in the anonymous connection */
			new PresenceWaiter(testChannel.realtimeChannel);
			/* set up a connection with specific clientId */
			ClientOptions client1Opts = new ClientOptions() {{
				tokenDetails = token1;
				clientId = testClientId1;
			}};
			fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* wait until connected */
			(new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

			/* get channel and attach */
			Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
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
			ClientOptions client2Opts = new ClientOptions() {{
				tokenDetails = token2;
				clientId = testClientId2;
			}};
			fillInOptions(client2Opts);
			clientAbly2 = new AblyRealtime(client2Opts);

			/* wait until connected */
			(new ConnectionWaiter(clientAbly2.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", clientAbly2.connection.state, ConnectionState.connected);

			/* get channel and subscribe to presence */
			Channel client2Channel = clientAbly2.channels.get(testChannel.channelName);
			PresenceWaiter client2Waiter = new PresenceWaiter(client2Channel);
			client2Waiter.waitFor(testClientId1, Action.present);

			/* get presence set and verify client present */
			PresenceMessage[] presences = client2Channel.presence.get();
			PresenceMessage expectedPresent = contains(presences, testClientId1, Action.present);
			assertNotNull("Verify expected client is in presence set", expectedPresent);
			assertEquals(expectedPresent.data, enterString);
			
		} catch(AblyException e) {
			e.printStackTrace();
			fail("Unexpected exception running test: " + e.getMessage());
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
			if(clientAbly2 != null)
				clientAbly2.close();
			if(testChannel != null)
				testChannel.dispose();
		}
	}

	/**
	 * Attach to channel, enter presence channel with large number of clientIds,
	 * then initiate second connection, seeing existing members in sync subsequent
	 * to second attach response
	 * DISABLED: See issue https://github.com/ably/ably-java/issues/159
	 */
	/*@Test*/
	public void attach_enter_multiple() {
		AblyRealtime clientAbly1 = null;
		AblyRealtime clientAbly2 = null;
		TestChannel testChannel = new TestChannel();
		int clientCount = 20;
		long delay = 50L;
		try {
			/* subscribe for presence events in the anonymous connection */
			new PresenceWaiter(testChannel.realtimeChannel);
			/* set up a connection with specific clientId */
			ClientOptions client1Opts = new ClientOptions() {{
				tokenDetails = wildcardToken;
				clientId = testClientId1;
			}};
			fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* wait until connected */
			(new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

			/* get channel and attach */
			Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
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
			assertTrue("Verify enter callback called on completion", enterComplete.pending.isEmpty());
			assertTrue("Verify no enter errors", enterComplete.errors.isEmpty());

			/* set up a second connection with different clientId */
			ClientOptions client2Opts = new ClientOptions() {{
				tokenDetails = token2;
				clientId = testClientId2;
			}};
			fillInOptions(client2Opts);
			clientAbly2 = new AblyRealtime(client2Opts);

			/* wait until connected */
			(new ConnectionWaiter(clientAbly2.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", clientAbly2.connection.state, ConnectionState.connected);

			/* get channel */
			Channel client2Channel = clientAbly2.channels.get(testChannel.channelName);
			client2Channel.attach();
			(new ChannelWaiter(client2Channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", client2Channel.state, ChannelState.attached);

			/* get presence set and verify client present */
			HashMap<String, PresenceMessage> memberIndex = new HashMap<String, PresenceMessage>();
			PresenceMessage[] members = client2Channel.presence.get(true);
			Thread.sleep(10000L);
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
			fail("Unexpected exception running test: " + e.getMessage());
		} catch (InterruptedException e) {
			e.printStackTrace();
			fail("Unexpected exception running test: " + e.getMessage());
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
			if(clientAbly2 != null)
				clientAbly2.close();
			if(testChannel != null)
				testChannel.dispose();
		}
	}

	/**
	 * Attach and enter channel on two connections, seeing
	 * both members in presence returned by realtime get() */
	@Test
	public void realtime_enter_multiple() {
		AblyRealtime clientAbly1 = null;
		AblyRealtime clientAbly2 = null;
		TestChannel testChannel = new TestChannel();
		try {
			/* subscribe for presence events in the anonymous connection */
			PresenceWaiter waiter = new PresenceWaiter(testChannel.realtimeChannel);

			/* set up a connection with specific clientId */
			ClientOptions client1Opts = new ClientOptions() {{
				tokenDetails = token1;
				clientId = testClientId1;
			}};
			fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* get channel and attach */
			Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
			CompletionWaiter enter1Complete = new CompletionWaiter();
			String enterString1 = "Test data (enter_multiple, clientId1)";
			client1Channel.presence.enter(enterString1, enter1Complete);
			enter1Complete.waitFor();
			assertTrue("Verify enter callback called on completion", enter1Complete.success);

			/* set up a second connection with different clientId */
			ClientOptions client2Opts = new ClientOptions() {{
				tokenDetails = token2;
				clientId = testClientId2;
			}};
			fillInOptions(client2Opts);
			clientAbly2 = new AblyRealtime(client2Opts);

			/* get channel and subscribe to presence */
			Channel client2Channel = clientAbly2.channels.get(testChannel.channelName);
			CompletionWaiter enter2Complete = new CompletionWaiter();
			String enterString2 = "Test data (enter_multiple, clientId2)";
			client2Channel.presence.enter(enterString2, enter2Complete);
			enter2Complete.waitFor();
			assertTrue("Verify enter callback called on completion", enter2Complete.success);

			/* verify enter events for both clients are received */
			waiter.waitFor(testClientId1, Action.enter);
			waiter.waitFor(testClientId2, Action.enter);

			/* get presence set and verify clients present */
			PresenceMessage[] presences = testChannel.realtimeChannel.presence.get();
			PresenceMessage expectedPresent1 = contains(presences, testClientId1, Action.present);
			PresenceMessage expectedPresent2 = contains(presences, testClientId2, Action.present);
			assertNotNull("Verify expected clients are in presence set", expectedPresent1);
			assertNotNull("Verify expected clients are in presence set", expectedPresent2);
			assertEquals(expectedPresent1.data, enterString1);
			assertEquals(expectedPresent2.data, enterString2);
			
		} catch(AblyException e) {
			e.printStackTrace();
			fail("Unexpected exception running test: " + e.getMessage());
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
			if(clientAbly2 != null)
				clientAbly2.close();
			if(testChannel != null)
				testChannel.dispose();
		}
	}

	/**
	 * Attach to channel, enter presence channel and get presence using rest get()
	 */
	@Test
	public void rest_get_simple() {
		AblyRealtime clientAbly1 = null;
		TestChannel testChannel = new TestChannel();
		try {
			/* subscribe for presence events in the anonymous connection */
			new PresenceWaiter(testChannel.realtimeChannel);
			/* set up a connection with specific clientId */
			ClientOptions client1Opts = new ClientOptions() {{
				tokenDetails = token1;
				clientId = testClientId1;
			}};
			fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* wait until connected */
			(new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

			/* get channel and attach */
			Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
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
			PresenceMessage[] presences = testChannel.restChannel.presence.get(null).items();
			PresenceMessage expectedPresent = contains(presences, testClientId1, Action.present);
			assertNotNull("Verify expected client is in presence set", expectedPresent);
			assertEquals(expectedPresent.data, enterString);

		} catch(AblyException e) {
			e.printStackTrace();
			fail("Unexpected exception running test: " + e.getMessage());
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
			if(testChannel != null)
				testChannel.dispose();
		}
	}

	/**
	 * Attach to channel, enter+leave presence channel and get presence with rest get()
	 */
	@Test
	public void rest_get_leave() {
		AblyRealtime clientAbly1 = null;
		TestChannel testChannel = new TestChannel();
		try {
			/* subscribe for presence events in the anonymous connection */
			PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
			/* set up a connection with specific clientId */
			ClientOptions client1Opts = new ClientOptions() {{
				tokenDetails = token1;
				clientId = testClientId1;
			}};
			fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* wait until connected */
			(new ConnectionWaiter(clientAbly1.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", clientAbly1.connection.state, ConnectionState.connected);

			/* get channel and attach */
			Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
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
			presenceWaiter.waitFor(testClientId1, Action.leave);
			assertTrue("Verify leave callback called on completion", leaveComplete.success);

			/* get presence set and verify client absent */
			PresenceMessage[] presences = testChannel.restChannel.presence.get(null).items();
			assertNull("Verify expected client is in presence set", contains(presences, testClientId1));

		} catch(AblyException e) {
			e.printStackTrace();
			fail("Unexpected exception running test: " + e.getMessage());
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
			if(testChannel != null)
				testChannel.dispose();
		}
	}

	/**
	 * Attach and enter channel on two connections, seeing
	 * both members in presence returned by rest get() */
	@Test
	public void rest_enter_multiple() {
		AblyRealtime clientAbly1 = null;
		AblyRealtime clientAbly2 = null;
		TestChannel testChannel = new TestChannel();
		try {
			/* subscribe for presence events in the anonymous connection */
			new PresenceWaiter(testChannel.realtimeChannel);
			/* set up a connection with specific clientId */
			ClientOptions client1Opts = new ClientOptions() {{
				tokenDetails = token1;
				clientId = testClientId1;
			}};
			fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* get channel and attach */
			Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);
			CompletionWaiter enter1Complete = new CompletionWaiter();
			String enterString1 = "Test data (enter_multiple, clientId1)";
			client1Channel.presence.enter(enterString1, enter1Complete);
			enter1Complete.waitFor();
			assertTrue("Verify enter callback called on completion", enter1Complete.success);

			/* set up a second connection with different clientId */
			ClientOptions client2Opts = new ClientOptions() {{
				tokenDetails = token2;
				clientId = testClientId2;
			}};
			fillInOptions(client2Opts);
			clientAbly2 = new AblyRealtime(client2Opts);

			/* get channel and subscribe to presence */
			Channel client2Channel = clientAbly2.channels.get(testChannel.channelName);
			CompletionWaiter enter2Complete = new CompletionWaiter();
			String enterString2 = "Test data (enter_multiple, clientId2)";
			client2Channel.presence.enter(enterString2, enter2Complete);
			enter2Complete.waitFor();
			assertTrue("Verify enter callback called on completion", enter2Complete.success);

			/* get presence set and verify client present */
			PresenceMessage[] presences = testChannel.restChannel.presence.get(null).items();
			PresenceMessage expectedPresent1 = contains(presences, testClientId1, Action.present);
			PresenceMessage expectedPresent2 = contains(presences, testClientId2, Action.present);
			assertNotNull("Verify expected clients are in presence set", expectedPresent1);
			assertNotNull("Verify expected clients are in presence set", expectedPresent2);
			assertEquals(expectedPresent1.data, enterString1);
			assertEquals(expectedPresent2.data, enterString2);

		} catch(AblyException e) {
			e.printStackTrace();
			fail("Unexpected exception running test: " + e.getMessage());
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
			if(clientAbly2 != null)
				clientAbly2.close();
			if(testChannel != null)
				testChannel.dispose();
		}
	}

	/**
	 * Attach and enter channel multiple times on a single connection,
	 * retrieving members using paginated rest get() */
	@Test
	public void rest_paginated_get() {
		AblyRealtime clientAbly1 = null;
		TestChannel testChannel = new TestChannel();
		int clientCount = 30;
		long delay = 100L;
		try {
			/* subscribe for presence events in the anonymous connection */
			new PresenceWaiter(testChannel.realtimeChannel);
			/* set up a connection with specific clientId */
			ClientOptions client1Opts = new ClientOptions() {{
				tokenDetails = wildcardToken;
				clientId = testClientId1;
			}};
			fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);

			/* get channel and attach */
			Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);

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
			PaginatedResult<PresenceMessage> members = testChannel.restChannel.presence.get(new Param[] { new Param("limit", "10") });
			assertNotNull("Expected non-null messages", members);
			assertEquals("Expected 10 messages", members.items().length, 10);

			/* index received messages */
			for(int i = 0; i < 10; i++) {
				PresenceMessage member = members.items()[i];
				memberIndex.put(member.clientId, member);
			}

			/* get next page */
			members = members.next();
			assertNotNull("Expected non-null messages", members);
			assertEquals("Expected 10 messages", members.items().length, 10);

			/* index received messages */
			for(int i = 0; i < 10; i++) {
				PresenceMessage member = members.items()[i];
				memberIndex.put(member.clientId, member);
			}

			/* get next page */
			members = members.next();
			assertNotNull("Expected non-null messages", members);
			assertEquals("Expected 10 messages", members.items().length, 10);

			/* index received messages */
			for(int i = 0; i < 10; i++) {
				PresenceMessage member = members.items()[i];
				memberIndex.put(member.clientId, member);
			}

			/* verify there is no next page */
			assertFalse("Expected null next page", members.hasNext());

			/* verify that all clientIds were received */
			assertEquals("Expected " + clientCount + " members", memberIndex.size(), clientCount);
			for(int i = 0; i < clientCount; i++) {
				String clientId = "client" + i;
				assertTrue("Expected client with id " + clientId, memberIndex.containsKey(clientId));
			}
		} catch(AblyException e) {
			e.printStackTrace();
			fail("Unexpected exception running test: " + e.getMessage());
		} finally {
			if(clientAbly1 != null)
				clientAbly1.close();
			if(testChannel != null)
				testChannel.dispose();
		}
	}

	/**
	 * Attach to channel, enter presence channel, disconnect and await leave event
	 */
	@Test
	public void disconnect_leave() {
		AblyRealtime clientAbly1 = null;
		TestChannel testChannel = new TestChannel();
		boolean requiresClose = false;
		try {
			/* subscribe for presence events in the anonymous connection */
			PresenceWaiter presenceWaiter = new PresenceWaiter(testChannel.realtimeChannel);
			/* set up a connection with specific clientId */
			ClientOptions client1Opts = new ClientOptions() {{
				tokenDetails = token1;
				clientId = testClientId1;
			}};
			fillInOptions(client1Opts);
			clientAbly1 = new AblyRealtime(client1Opts);
			requiresClose = true;

			/* get channel */
			Channel client1Channel = clientAbly1.channels.get(testChannel.channelName);

			/* let client1 enter the channel and wait for the entered event to be delivered */
			CompletionWaiter enterComplete = new CompletionWaiter();
			String enterString = "Test data (disconnect_leave)";
			client1Channel.presence.enter(enterString, enterComplete);
			presenceWaiter.waitFor(testClientId1, Action.enter);
			PresenceMessage expectedPresent = presenceWaiter.contains(testClientId1, Action.enter);
			assertNotNull(expectedPresent);
			assertEquals(expectedPresent.data, enterString);

			/* verify enter callback called on completion */
			enterComplete.waitFor();
			assertTrue("Verify enter callback called on completion", enterComplete.success);

			/* close client1 connection and wait for the leave event to be delivered */
			clientAbly1.close();
			requiresClose = false;
			presenceWaiter.waitFor(testClientId1, Action.leave);
			PresenceMessage expectedLeft = presenceWaiter.contains(testClientId1, Action.leave);
			assertNotNull(expectedLeft);
			/* verify leave message contains data that was published with enter */
			assertEquals(expectedLeft.data, enterString);

		} catch(AblyException e) {
			e.printStackTrace();
			fail("Unexpected exception running test: " + e.getMessage());
		} finally {
			if(requiresClose)
				clientAbly1.close();
			if(testChannel != null)
				testChannel.dispose();
		}
	}

	/**
	 * <p>
	 * Validates channel removes all subscribers,
	 * when {@code Channel#unsubscribe()} with no argument gets called.
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void realtime_presence_unsubscribe_all() throws AblyException {
		/* Ably instance that will emit presence events */
		AblyRealtime ably1 = null;
		/* Ably instance that will receive presence events */
		AblyRealtime ably2 = null;

		String channelName = "test.presence.unsubscribe.all" + System.currentTimeMillis();

		try {
			ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
			option1.clientId = "emitter client";
			ClientOptions option2 = createOptions(testVars.keys[0].keyStr);
			option2.clientId = "receiver client";

			ably1 = new AblyRealtime(option1);
			ably2 = new AblyRealtime(option2);

			Channel channel1 = ably1.channels.get(channelName);
			channel1.attach();
			(new ChannelWaiter(channel1)).waitFor(ChannelState.attached);

			Channel channel2 = ably2.channels.get(channelName);
			channel2.attach();
			(new ChannelWaiter(channel2)).waitFor(ChannelState.attached);

			ArrayList<PresenceMessage> receivedMessageStack = new ArrayList<>();
			Presence.PresenceListener listener = new Presence.PresenceListener() {
				List<PresenceMessage> messageStack;

				@Override
				public void onPresenceMessage(PresenceMessage message) {
					messageStack.add(message);
				}

				public Presence.PresenceListener setMessageStack(List<PresenceMessage> messageStack) {
					this.messageStack = messageStack;
					return this;
				}
			}.setMessageStack(receivedMessageStack);

			/* Subscribe using various alternatives of {@code Presence#subscribe()} */
			channel2.presence.subscribe(listener);
			channel2.presence.subscribe(Action.present, listener);
			channel2.presence.subscribe(EnumSet.of(Action.update, Action.leave), listener);

			/* Unsubscribe */
			channel2.presence.unsubscribe();

			/* Start emitting channel with ably client 1 (emitter) */
			channel1.presence.enter("Hello, #2!", null);
			channel1.presence.update("Lorem ipsum", null);
			channel1.presence.update("Dolor sit!", null);
			channel1.presence.leave(null);

			/* Wait until receiver client (ably2) observes {@code Action.leave}
			 * is emitted from emitter client (ably1)
			 */
			Helpers.PresenceWaiter leavePresenceWaiter = new Helpers.PresenceWaiter(channel2);
			leavePresenceWaiter.waitFor(ably1.options.clientId, Action.leave);

			/* Validate that we didn't received anything
			 */
			assertThat(receivedMessageStack, is(emptyCollectionOf(PresenceMessage.class)));
		} finally {
			if (ably1 != null) ably1.close();
			if (ably2 != null) ably2.close();
		}
	}

	/**
	 * <p>
	 * Validates channel removes a subscriber,
	 * when {@code Channel#unsubscribe()} gets called with a listener.
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void realtime_presence_unsubscribe_single() throws AblyException {
		/* Ably instance that will emit presence events */
		AblyRealtime ably1 = null;
		/* Ably instance that will receive presence events */
		AblyRealtime ably2 = null;

		String channelName = "test.presence.unsubscribe.single" + System.currentTimeMillis();

		try {
			ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
			option1.clientId = "emitter client";
			ClientOptions option2 = createOptions(testVars.keys[0].keyStr);
			option2.clientId = "receiver client";

			ably1 = new AblyRealtime(option1);
			ably2 = new AblyRealtime(option2);

			Channel channel1 = ably1.channels.get(channelName);
			channel1.attach();
			(new ChannelWaiter(channel1)).waitFor(ChannelState.attached);

			Channel channel2 = ably2.channels.get(channelName);
			channel2.attach();
			(new ChannelWaiter(channel2)).waitFor(ChannelState.attached);

			ArrayList<PresenceMessage> receivedMessageStack = new ArrayList<>();
			Presence.PresenceListener listener = new Presence.PresenceListener() {
				List<PresenceMessage> messageStack;

				@Override
				public void onPresenceMessage(PresenceMessage message) {
					messageStack.add(message);
				}

				public Presence.PresenceListener setMessageStack(List<PresenceMessage> messageStack) {
					this.messageStack = messageStack;
					return this;
				}
			}.setMessageStack(receivedMessageStack);

			/* Subscribe using various alternatives of {@code Presence#subscribe()} */
			channel2.presence.subscribe(listener);
			channel2.presence.subscribe(Action.present, listener);
			channel2.presence.subscribe(EnumSet.of(Action.update, Action.leave), listener);

			/* Unsubscribe */
			channel2.presence.unsubscribe(listener);

			/* Start emitting channel with ably client 1 (emitter) */
			channel1.presence.enter("Hello, #2!", null);
			channel1.presence.update("Lorem ipsum", null);
			channel1.presence.update("Dolor sit!", null);
			channel1.presence.leave(null);

			/* Wait until receiver client (ably2) observes {@code Action.leave}
			 * is emitted from emitter client (ably1)
			 */
			Helpers.PresenceWaiter leavePresenceWaiter = new Helpers.PresenceWaiter(channel2);
			leavePresenceWaiter.waitFor(ably1.options.clientId, Action.leave);

			/* Validate that we didn't received anything
			 */
			assertThat(receivedMessageStack, is(emptyCollectionOf(PresenceMessage.class)));
		} finally {
			if (ably1 != null) ably1.close();
			if (ably2 != null) ably2.close();
		}
	}

	/**
	 * <p>
	 * Validates a client can observe presence messages of other client,
	 * when they entered to the same channel and observing client subscribed
	 * to multiple actions.
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void realtime_presence_subscribe_all() throws AblyException {
		/* Ably instance that will emit presence events */
		AblyRealtime ably1 = null;
		/* Ably instance that will receive presence events */
		AblyRealtime ably2 = null;

		String channelName = "test.presence.subscribe.all" + System.currentTimeMillis();

		try {
			ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
			option1.clientId = "emitter client";
			ClientOptions option2 = createOptions(testVars.keys[0].keyStr);
			option2.clientId = "receiver client";

			ably1 = new AblyRealtime(option1);
			ably2 = new AblyRealtime(option2);

			Channel channel1 = ably1.channels.get(channelName);
			channel1.attach();
			(new ChannelWaiter(channel1)).waitFor(ChannelState.attached);

			Channel channel2 = ably2.channels.get(channelName);
			channel2.attach();
			(new ChannelWaiter(channel2)).waitFor(ChannelState.attached);

			ArrayList<PresenceMessage> receivedMessageStack = new ArrayList<>();
			channel2.presence.subscribe(new Presence.PresenceListener() {
				List<PresenceMessage> messageStack;

				@Override
				public void onPresenceMessage(PresenceMessage message) {
					messageStack.add(message);
				}

				public Presence.PresenceListener setMessageStack(List<PresenceMessage> messageStack) {
					this.messageStack = messageStack;
					return this;
				}
			}.setMessageStack(receivedMessageStack));

			/* Start emitting channel with ably client 1 (emitter) */
			channel1.presence.enter("Hello, #2!", null);
			channel1.presence.update("Lorem ipsum", null);
			channel1.presence.update("Dolor sit!", null);
			channel1.presence.leave(null);

			/* Wait until receiver client (ably2) observes {@code Action.leave}
			 * is emitted from emitter client (ably1)
			 */
			Helpers.PresenceWaiter leavePresenceWaiter = new Helpers.PresenceWaiter(channel2);
			leavePresenceWaiter.waitFor(ably1.options.clientId, Action.leave);

			/* Validate that,
			 *	- we received all actions
			 */
			assertThat(receivedMessageStack.size(), is(equalTo(4)));
			for (PresenceMessage message : receivedMessageStack) {
				assertThat(message.action, isOneOf(Action.enter, Action.update, Action.leave));
			}
		} finally {
			if (ably1 != null) ably1.close();
			if (ably2 != null) ably2.close();
		}
	}

	/**
	 * <p>
	 * Validates a client can observe presence messages of other client,
	 * when they entered to the same channel and observing client subscribed
	 * to multiple actions.
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void realtime_presence_subscribe_multiple() throws AblyException {
		/* Ably instance that will emit presence events */
		AblyRealtime ably1 = null;
		/* Ably instance that will receive presence events */
		AblyRealtime ably2 = null;

		String channelName = "test.presence.subscribe.multiple" + System.currentTimeMillis();
		EnumSet<PresenceMessage.Action> actions = EnumSet.of(Action.update, Action.leave);

		try {
			ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
			option1.clientId = "emitter client";
			ClientOptions option2 = createOptions(testVars.keys[0].keyStr);
			option2.clientId = "receiver client";

			ably1 = new AblyRealtime(option1);
			ably2 = new AblyRealtime(option2);

			Channel channel1 = ably1.channels.get(channelName);
			channel1.attach();
			(new ChannelWaiter(channel1)).waitFor(ChannelState.attached);

			Channel channel2 = ably2.channels.get(channelName);
			channel2.attach();
			(new ChannelWaiter(channel2)).waitFor(ChannelState.attached);

			final ArrayList<PresenceMessage> receivedMessageStack = new ArrayList<>();
			channel2.presence.subscribe(actions, new Presence.PresenceListener() {
				@Override
				public void onPresenceMessage(PresenceMessage message) {
					synchronized (receivedMessageStack) {
						receivedMessageStack.add(message);
						receivedMessageStack.notify();
					}
				}
			});

			/* Start emitting channel with ably client 1 (emitter) */
			channel1.presence.enter("Hello, #2!", null);
			channel1.presence.update("Lorem ipsum", null);
			channel1.presence.update("Dolor sit!", null);
			channel1.presence.leave(null);

			/* Wait until receiver client (ably2) observes {@code Action.leave}
			 * is emitted from emitter client (ably1)
			 */
			try {
				synchronized (receivedMessageStack) {
					while (receivedMessageStack.size() == 0 ||
							!receivedMessageStack.get(receivedMessageStack.size()-1).clientId.equals(ably1.options.clientId) ||
							receivedMessageStack.get(receivedMessageStack.size()-1).action != Action.leave)
						receivedMessageStack.wait();
				}
			} catch(InterruptedException e) {}

			/* Validate that,
			 *	- we received specific actions
			 */
			assertThat(receivedMessageStack.size(), is(equalTo(3)));
			for (PresenceMessage message : receivedMessageStack) {
				assertTrue(actions.contains(message.action));
			}
		} finally {
			if (ably1 != null) ably1.close();
			if (ably2 != null) ably2.close();
		}
	}

	/**
	 * <p>
	 * Validates a client can observe presence messages of other client,
	 * when they entered to the same channel and observing client subscribed
	 * to a single action.
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void realtime_presence_subscribe_single() throws AblyException {
		/* Ably instance that will emit presence events */
		AblyRealtime ably1 = null;
		/* Ably instance that will receive presence events */
		AblyRealtime ably2 = null;

		String channelName = "test.presence.subscribe.single." + System.currentTimeMillis();
		PresenceMessage.Action action = Action.enter;

		try {
			ClientOptions option1 = createOptions(testVars.keys[0].keyStr);
			option1.clientId = "emitter client";
			ClientOptions option2 = createOptions(testVars.keys[0].keyStr);
			option2.clientId = "receiver client";

			ably1 = new AblyRealtime(option1);
			ably2 = new AblyRealtime(option2);

			Channel channel1 = ably1.channels.get(channelName);
			channel1.attach();
			(new ChannelWaiter(channel1)).waitFor(ChannelState.attached);

			Channel channel2 = ably2.channels.get(channelName);
			channel2.attach();
			(new ChannelWaiter(channel2)).waitFor(ChannelState.attached);

			ArrayList<PresenceMessage> receivedMessageStack = new ArrayList<>();
			channel2.presence.subscribe(action, new Presence.PresenceListener() {
				List<PresenceMessage> messageStack;

				@Override
				public void onPresenceMessage(PresenceMessage message) {
					messageStack.add(message);
				}

				public Presence.PresenceListener setMessageStack(List<PresenceMessage> messageStack) {
					this.messageStack = messageStack;
					return this;
				}
			}.setMessageStack(receivedMessageStack));

			/* Start emitting presence with ably client 1 (emitter) */
			channel1.presence.enter("Hello, #2!", null);
			channel1.presence.updatePresence(new PresenceMessage(Action.present, ably1.options.clientId), null);
			channel1.presence.update("Lorem Ipsum", null);
			channel1.presence.leave(null);

			/* Wait until receiver client (ably2) observes {@code Action.leave}
			 * is emitted from emitter client (ably1)
			 */
			new Helpers.PresenceWaiter(channel2).waitFor(ably1.options.clientId, Action.leave);

			/* Validate that,
			 *	- we received specific actions
			 */
			assertThat(receivedMessageStack, is(not(empty())));
			for (PresenceMessage message : receivedMessageStack) {
				assertThat(message.action, is(equalTo(action)));
			}
		} finally {
			if (ably1 != null) ably1.close();
			if (ably2 != null) ably2.close();
		}
	}

	/**
	 * <p>
	 * Validate {@code Presence#subscribe(...)} will result in the listener not being
	 * registered and an error being indicated, when the channel moves to the FAILED
	 * state before the operation succeeds
	 * </p>
	 * <p>
	 * Spec: RTP6c
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void realtime_presence_attach_implicit_subscribe_fail() throws AblyException {
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[1].keyStr);
			ably = new AblyRealtime(opts);

			/* wait until connected */
			new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

			/* create a channel and subscribe */
			final Channel channel = ably.channels.get("subscribe_fail");
			channel.presence.subscribe(null);
			assertEquals("Verify attaching state reached", channel.state, ChannelState.attaching);

			ErrorInfo fail = new ChannelWaiter(channel).waitFor(ChannelState.failed);
			assertEquals("Verify failed state reached", channel.state, ChannelState.failed);
			assertEquals("Verify reason code gives correct failure reason", fail.statusCode, 401);
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * <p>
	 * Validate {@code Presence#enter(...)} will result in the listener not being
	 * registered and an error being indicated, when the channel moves to the
	 * FAILED state before the operation succeeds
	 * </p>
	 * <p>
	 * Spec: RTP8d
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void realtime_presence_attach_implicit_enter_fail() throws AblyException {
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[1].keyStr);
			opts.clientId = "theClient";
			ably = new AblyRealtime(opts);

			/* wait until connected */
			new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

			/* create a channel and subscribe */
			final Channel channel = ably.channels.get("enter_fail");
			CompletionWaiter completionWaiter = new CompletionWaiter();
			channel.presence.enter("Lorem Ipsum", completionWaiter);
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
	 * Validate {@code Presence#get(...)} will result in an error, when the channel
	 * moves to the FAILED state before the operation succeeds
	 * </p>
	 * <p>
	 * Spec: RTP11b
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void realtime_presence_attach_implicit_get_fail() throws AblyException {
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[1].keyStr);
			ably = new AblyRealtime(opts);

			/* wait until connected */
			new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

			/* create a channel and subscribe */
			final Channel channel = ably.channels.get("get_fail");
			channel.presence.get();
			assertEquals("Verify attaching state reached", channel.state, ChannelState.attaching);

			ErrorInfo fail = new ChannelWaiter(channel).waitFor(ChannelState.failed);
			assertEquals("Verify failed state reached", channel.state, ChannelState.failed);
			assertEquals("Verify reason code gives correct failure reason", fail.statusCode, 401);
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * <p>
	 * Validate {@code Presence#enterClient(...)} will result in the listener not being
	 * registered and an error being indicated, when the channel moves to the FAILED
	 * state before the operation succeeds
	 * </p>
	 * <p>
	 * Spec: RTP15e
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void realtime_presence_attach_implicit_enterclient_fail() throws AblyException {
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[1].keyStr);
			ably = new AblyRealtime(opts);

			/* wait until connected */
			new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

			/* create a channel and subscribe */
			final Channel channel = ably.channels.get("enterclient_fail");
			CompletionWaiter completionWaiter = new CompletionWaiter();
			channel.presence.enterClient("theClient", "Lorem Ipsum", completionWaiter);
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
	 * Validate {@code Presence#updateClient(...)} will result in the listener not being
	 * registered and an error being indicated, when the channel is in or moves to the
	 * FAILED state before the operation succeeds
	 * </p>
	 * <p>
	 * Spec: RTP15e
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void realtime_presence_attach_implicit_updateclient_fail() throws AblyException {
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[1].keyStr);
			ably = new AblyRealtime(opts);

			/* wait until connected */
			new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

			/* create a channel and subscribe */
			final Channel channel = ably.channels.get("updateclient_fail");
			CompletionWaiter completionWaiter = new CompletionWaiter();
			channel.presence.updateClient("theClient", "Lorem Ipsum", completionWaiter);
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
	 * Validate {@code Presence#leaveClient(...)} will result in the listener not being
	 * registered and an error being indicated, when the channel is in or moves to the
	 * FAILED state before the operation succeeds
	 * </p>
	 * <p>
	 * Spec: RTP15e
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void realtime_presence_attach_implicit_leaveclient_fail() throws AblyException {
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[1].keyStr);
			ably = new AblyRealtime(opts);

			/* wait until connected */
			new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

			/* create a channel and subscribe */
			final Channel channel = ably.channels.get("leaveclient_fail");
			CompletionWaiter completionWaiter = new CompletionWaiter();
			channel.presence.leaveClient("theClient", "Lorem Ipsum", completionWaiter);
			assertEquals("Verify attaching state reached", channel.state, ChannelState.attaching);
			completionWaiter.waitFor();

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
	 * Validate {@code Presence#get(...)} throws an exception, when the channel
	 * is in the FAILED state
	 * </p>
	 *
	 * @throws AblyException
	 */
	@Test
	public void realtime_presence_get_throws_when_channel_failed() throws AblyException {
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[1].keyStr);
			ably = new AblyRealtime(opts);

			/* wait until connected */
			new ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

			/* create a channel and subscribe */
			final Channel channel = ably.channels.get("get_fail");
			channel.attach();
			new ChannelWaiter(channel).waitFor(ChannelState.failed);

			try {
				channel.presence.get();
				fail("Presence#get(...) should throw an exception when channel is in failed state");
			} catch(AblyException e) {
				assertThat(e.errorInfo.code, is(equalTo(90001)));
				assertThat(e.errorInfo.message, is(equalTo("channel operation failed (invalid channel state)")));
			}
		} finally {
			if(ably != null)
				ably.close();
		}
	}

	/**
	 * Test if after reattach when returning from suspended mode client re-enters the channel with the same data
	 * @throws AblyException
	 *
	 * Tests RTP17
	 */
	@Test
	public void realtime_presence_suspended_reenter() throws AblyException {
		AblyRealtime ably = null;
		String oldWebsockFactory = Defaults.TRANSPORT;
		try {
			Defaults.TRANSPORT = MockWebsocketFactory.class.getName();
			MockWebsocketFactory.allowSend();

			final String channelName = "presence_suspended_reenter";
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
			connectionWaiter.waitFor(ConnectionState.connected);

			final boolean[] presenceWaiter = new boolean[] {false};

			final Channel channel = ably.channels.get(channelName);
			channel.attach();
			ChannelWaiter channelWaiter = new ChannelWaiter(channel);

			channelWaiter.waitFor(ChannelState.attached);

			final String presenceData = "PRESENCE_DATA";
			final String connId = ably.connection.id;
			CompletionWaiter completionWaiter = new CompletionWaiter();
			channel.presence.enterClient(testClientId1, presenceData, completionWaiter);
			completionWaiter.waitFor();

			/*
			 * We put testClientId2 presence data into the client library presence map but we
			 * don't send it to the server
			 */

			MockWebsocketFactory.blockSend();
			channel.presence.enterClient(testClientId2, presenceData);

			ProtocolMessage msg = new ProtocolMessage();
			msg.connectionId = connId;
			msg.action = ProtocolMessage.Action.sync;
			msg.channel = channelName;
			msg.presence = new PresenceMessage[] {
					new PresenceMessage() {{
						action = Action.present;
						id = String.format("%s:0:0", connId);
						timestamp = System.currentTimeMillis();
						clientId = testClientId2;
						connectionId = connId;
						data = presenceData;
					}}
			};
			ably.connection.connectionManager.onMessage(msg);

			MockWebsocketFactory.allowSend();

			ably.connection.connectionManager.requestState(ConnectionState.suspended);
			channelWaiter.waitFor(ChannelState.suspended);

			/*
			 * When restoring from suspended state server will send sync message erasing
			 * testClientId2 record from the presence map. Client should re-send presence message
			 * for testClientId2 and restore its presence data.
			 */

			ably.connection.connectionManager.requestState(ConnectionState.connected);
			channelWaiter.waitFor(ChannelState.attached);

			try {
				Thread.sleep(500);
				assertEquals("Verify correct presence message data has been received",
						channel.presence.get(testClientId2, true)[0].data, presenceData);
			} catch (InterruptedException e) {}

		} finally {
			if(ably != null)
				ably.close();
			Defaults.TRANSPORT = oldWebsockFactory;
		}
	}

	/**
	 * Test comparison for newness
	 * Tests RTP2b* features
	 */
	@Test
	public void realtime_presence_newness_comparison_test() throws AblyException {
		AblyRealtime ably = null;
		try {
			ClientOptions opts = createOptions(testVars.keys[0].keyStr);
			ably = new AblyRealtime(opts);

			Channel channel = ably.channels.get("newness_comparison");
			channel.attach();
			ChannelWaiter channelWaiter = new ChannelWaiter(channel);
			channelWaiter.waitFor(ChannelState.attached);

			final String wontPass = "Won't pass newness test";

			Presence presence = channel.presence;
			final ArrayList<PresenceMessage> presenceMessages = new ArrayList<>();
			presence.subscribe(new Presence.PresenceListener() {
				@Override
				public void onPresenceMessage(PresenceMessage message) {
					synchronized (presenceMessages) {
						assertNotEquals("Verify wrong message didn't pass the newness test",
								message.data, wontPass);
						presenceMessages.add(message);
					}
				}
			});

			PresenceMessage[] testData = new PresenceMessage[] {
					new PresenceMessage() {{
						clientId = "1";
						action = Action.enter;
						connectionId = "1";
						id = "1:0";
					}},
					new PresenceMessage() {{
						clientId = "2";
						action = Action.enter;
						connectionId = "2";
						id = "2:1:0";
					}},
					/* Should be newer than previous one */
					new PresenceMessage() {{
						clientId = "2";
						action = Action.update;
						connectionId = "2";
						id = "2:2:1";
					}},
					/* Shouldn't pass newness test because of message serial */
					new PresenceMessage() {{
						clientId = "2";
						action = Action.update;
						connectionId = "2";
						id = "2:1:1";
						data = wontPass;
					}},
					/* Shouldn't pass because of message index */
					new PresenceMessage() {{
						clientId = "2";
						action = Action.update;
						connectionId = "2";
						id = "2:2:0";
						data = wontPass;
					}},
					/* Should pass because id is not in form connId:clientId:index and timestamp is greater */
					new PresenceMessage() {{
						clientId = "2";
						action = Action.update;
						connectionId = "2";
						id = "weird_id";
						timestamp = 1000;
					}},
					/* Shouldn't pass because of timestamp */
					new PresenceMessage() {{
						clientId = "2";
						action = Action.update;
						connectionId = "2";
						id = "2:3:1";
						timestamp = 500;
						data = wontPass;
					}}
			};

			for (final PresenceMessage msg: testData) {
				ProtocolMessage protocolMessage = new ProtocolMessage() {{
						channel = "newness_comparison";
						action = Action.presence;
						presence = new PresenceMessage[]{msg};
					}};

				ably.connection.connectionManager.onMessage(protocolMessage);
			}

			int n = 0;
			for (PresenceMessage testMsg: testData) {
				if (testMsg.data != wontPass) {
					PresenceMessage factualMsg = n < presenceMessages.size() ? presenceMessages.get(n++) : null;
					assertTrue("Verify message passed newness test",
							factualMsg != null && factualMsg.id.equals(testMsg.id));
				}
			}
		}
		finally {
			if (ably != null)
				ably.close();
		}
	}
}
