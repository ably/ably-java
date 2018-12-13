package io.ably.lib.test.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import io.ably.lib.debug.DebugOptions;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Auth;
import io.ably.lib.rest.Auth.TokenDetails;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.Helpers.ChannelWaiter;
import io.ably.lib.test.common.Helpers.CompletionSet;
import io.ably.lib.test.common.Helpers.ConnectionWaiter;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Message;
import io.ably.lib.types.ProtocolMessage;

public class RealtimeAuthTest extends ParameterizedTest {

	/**
	 * RSA12a: The clientId attribute of a TokenRequest or TokenDetails
	 * used for authentication is null, or ConnectionDetails#clientId is null
	 * following a connection to Ably. In this case, the null value indicates
	 * that a clientId identity may not be assumed by this client i.e. the
	 * client is anonymous for all operations
	 *
	 * Verify null token clientId in TokenDetails translates to a null clientId
	 */
	@Test
	public void auth_client_match_tokendetails_null_clientId() {
		try {
			/* init ably for token */
			ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
			final AblyRest ablyForToken = new AblyRest(optsForToken);

			/* get token */
			Auth.TokenParams tokenParams = new Auth.TokenParams();
			tokenParams.clientId = null;
			Auth.TokenDetails tokenDetails = ablyForToken.auth.requestToken(tokenParams, null);
			assertNotNull("Expected token value", tokenDetails.token);

			/* create ably realtime with tokenDetails and clientId */
			ClientOptions opts = createOptions();
			opts.clientId = null;
			opts.tokenDetails = tokenDetails;
			AblyRealtime ablyRealtime = new AblyRealtime(opts);
			System.out.println("done create ably");

			/* wait for connected state */
			Helpers.ConnectionWaiter connectionWaiter = new Helpers.ConnectionWaiter(ablyRealtime.connection);
			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Verify connected state is reached", ConnectionState.connected, ablyRealtime.connection.state);

			/* check expected clientId */
			assertEquals("Auth#clientId is expected to be null", null, ablyRealtime.auth.clientId);

			ablyRealtime.close();
		} catch (AblyException e) {
			e.printStackTrace();
			fail();
		}
	}

	/**
	 * RSA12a: The clientId attribute of a TokenRequest or TokenDetails
	 * used for authentication is null, or ConnectionDetails#clientId is null
	 * following a connection to Ably. In this case, the null value indicates
	 * that a clientId identity may not be assumed by this client i.e. the
	 * client is anonymous for all operations
	 *
	 * Verify null token clientId in token translates to a null clientId
	 */
	@Test
	public void auth_client_match_token_null_clientId() {
		try {
			/* init ably for token */
			ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
			final AblyRest ablyForToken = new AblyRest(optsForToken);

			/* get token */
			Auth.TokenParams tokenParams = new Auth.TokenParams();
			tokenParams.clientId = null;
			Auth.TokenDetails tokenDetails = ablyForToken.auth.requestToken(tokenParams, null);
			assertNotNull("Expected token value", tokenDetails.token);

			/* create ably realtime with tokenDetails and clientId */
			ClientOptions opts = createOptions();
			opts.clientId = null;
			opts.token = tokenDetails.token;
			AblyRealtime ablyRealtime = new AblyRealtime(opts);
			System.out.println("done create ably");

			/* wait for connected state */
			Helpers.ConnectionWaiter connectionWaiter = new Helpers.ConnectionWaiter(ablyRealtime.connection);
			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Verify connected state is reached", ConnectionState.connected, ablyRealtime.connection.state);

			/* check expected clientId */
			assertEquals("Auth#clientId is expected to be null", null, ablyRealtime.auth.clientId);

			ablyRealtime.close();
		} catch (AblyException e) {
			e.printStackTrace();
			fail();
		}
	}

	/**
	 * Init library with a key and token; verify Auth.clientId is null before
	 * connection
	 * Spec: RSA12b, RSA7b2, RSA7b3
	 */
	@Test
	public void auth_clientid_null_before_auth() {
		try {
			final String clientId = "token clientId";

			/* create token with clientId */
			ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
			optsForToken.clientId = clientId;
			AblyRest ablyForToken = new AblyRest(optsForToken);
			TokenDetails tokenDetails = ablyForToken.auth.requestToken(null, null);

			/* create ably realtime */
			ClientOptions opts = createOptions();
			opts.clientId = null;
			opts.token = tokenDetails.token;
			opts.autoConnect = false;
			AblyRealtime ablyRealtime = new AblyRealtime(opts);

			/* check expected clientId */
			assertEquals("Auth#clientId is expected to be null", null, ablyRealtime.auth.clientId);

			/* wait for connected state */
			ablyRealtime.connection.connect();
			Helpers.ConnectionWaiter connectionWaiter = new Helpers.ConnectionWaiter(ablyRealtime.connection);
			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Verify connected state is reached", ConnectionState.connected, ablyRealtime.connection.state);

			/* check expected clientId */
			assertEquals("Auth#clientId is expected to be set", clientId, ablyRealtime.auth.clientId);

			ablyRealtime.close();
		} catch (AblyException e) {
			e.printStackTrace();
			fail();
		}
	}

	/**
	 * RSA15a: Any clientId provided in ClientOptions must match any
	 * non wildcard ('*') clientId value in TokenDetails
	 * RSA15b: If the clientId from TokenDetails or connectionDetails contains
	 * only a wildcard string '*', then the client is permitted to be either
	 * unidentified or identified by providing
	 * a clientId when communicating with Ably
	 *
	 * Verify wildcard token clientId in TokenDetails succeeds in
	 * authenticating a non-null clientId
	 */
	@Test
	public void auth_client_match_tokendetails_wildcard_clientId() {
		try {
			/* init ably for token */
			ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
			final AblyRest ablyForToken = new AblyRest(optsForToken);

			/* get token */
			Auth.TokenParams tokenParams = new Auth.TokenParams();
			tokenParams.clientId = "*";
			Auth.TokenDetails tokenDetails = ablyForToken.auth.requestToken(tokenParams, null);
			assertNotNull("Expected token value", tokenDetails.token);

			/* create ably realtime with tokenDetails and clientId */
			ClientOptions opts = createOptions();
			opts.clientId = "options clientId";
			opts.tokenDetails = tokenDetails;
			AblyRealtime ablyRealtime = new AblyRealtime(opts);
			System.out.println("done create ably");

			/* wait for connected state */
			Helpers.ConnectionWaiter connectionWaiter = new Helpers.ConnectionWaiter(ablyRealtime.connection);
			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Verify connected state is reached", ConnectionState.connected, ablyRealtime.connection.state);

			/* check expected clientId */
			assertEquals("Auth#clientId is expected to be set", "options clientId", ablyRealtime.auth.clientId);

			ablyRealtime.close();
		} catch (AblyException e) {
			e.printStackTrace();
			fail();
		}
	}

	/**
	 * RSA15a: Any clientId provided in ClientOptions must match any
	 * non wildcard ('*') clientId value in TokenDetails
	 * RSA15b: If the clientId from TokenDetails or connectionDetails contains
	 * only a wildcard string '*', then the client is permitted to be either
	 * unidentified (i.e. authorised to act on behalf of any clientId) or
	 * identified by providing a clientId when communicating with Ably
	 *
	 * Verify wildcard token clientId in token succeeds in
	 * authenticating a non-null clientId
	 */
	@Test
	public void auth_client_match_token_wildcard_clientId() {
		try {
			/* init ably for token */
			ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
			final AblyRest ablyForToken = new AblyRest(optsForToken);

			/* get token */
			Auth.TokenParams tokenParams = new Auth.TokenParams();
			tokenParams.clientId = "*";
			Auth.TokenDetails tokenDetails = ablyForToken.auth.requestToken(tokenParams, null);
			assertNotNull("Expected token value", tokenDetails.token);

			/* create ably realtime with token and clientId */
			ClientOptions opts = createOptions();
			opts.clientId = "options clientId";
			opts.token = tokenDetails.token;
			AblyRealtime ablyRealtime = new AblyRealtime(opts);
			System.out.println("done create ably");

			/* wait for connected state */
			Helpers.ConnectionWaiter connectionWaiter = new Helpers.ConnectionWaiter(ablyRealtime.connection);
			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Verify connected state is reached", ConnectionState.connected, ablyRealtime.connection.state);

			/* check expected clientId */
			assertEquals("Auth#clientId is expected to be set", "options clientId", ablyRealtime.auth.clientId);

			ablyRealtime.close();
		} catch (AblyException e) {
			e.printStackTrace();
			fail();
		}
	}

	/**
	 * RSA15b: If the clientId from TokenDetails or connectionDetails contains
	 * only a wildcard string '*', then the client is permitted to be either
	 * unidentified (i.e. authorised to act on behalf of any clientId) or
	 * identified by providing a clientId when communicating with Ably
	 *
	 * Verify wildcard token clientId in TokenDetails succeeds in
	 * authenticating a null clientId
	 */
	@Test
	public void auth_client_null_match_tokendetails_wildcard_clientId() {
		try {
			/* init ably for token */
			ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
			final AblyRest ablyForToken = new AblyRest(optsForToken);

			/* get token */
			Auth.TokenParams tokenParams = new Auth.TokenParams();
			tokenParams.clientId = "*";
			Auth.TokenDetails tokenDetails = ablyForToken.auth.requestToken(tokenParams, null);
			assertNotNull("Expected token value", tokenDetails.token);

			/* create ably realtime with tokenDetails and clientId */
			ClientOptions opts = createOptions();
			opts.clientId = null;
			opts.tokenDetails = tokenDetails;
			AblyRealtime ablyRealtime = new AblyRealtime(opts);
			System.out.println("done create ably");

			/* wait for connected state */
			Helpers.ConnectionWaiter connectionWaiter = new Helpers.ConnectionWaiter(ablyRealtime.connection);
			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Verify connected state is reached", ConnectionState.connected, ablyRealtime.connection.state);

			/* check expected clientId */
			assertEquals("Auth#clientId is expected to be set", "*", ablyRealtime.auth.clientId);

			ablyRealtime.close();
		} catch (AblyException e) {
			e.printStackTrace();
			fail();
		}
	}

	/**
	 * RSA15b: If the clientId from TokenDetails or connectionDetails contains
	 * only a wildcard string '*', then the client is permitted to be either
	 * unidentified (i.e. authorised to act on behalf of any clientId) or
	 * identified by providing a clientId when communicating with Ably
	 *
	 * Verify wildcard token clientId in token succeeds in
	 * authenticating a null clientId
	 */
	@Test
	public void auth_client_null_match_token_wildcard_clientId() {
		try {
			/* init ably for token */
			ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
			final AblyRest ablyForToken = new AblyRest(optsForToken);

			/* get token */
			Auth.TokenParams tokenParams = new Auth.TokenParams();
			tokenParams.clientId = "*";
			Auth.TokenDetails tokenDetails = ablyForToken.auth.requestToken(tokenParams, null);
			assertNotNull("Expected token value", tokenDetails.token);

			/* create ably realtime with token and clientId */
			ClientOptions opts = createOptions();
			opts.clientId = null;
			opts.token = tokenDetails.token;
			AblyRealtime ablyRealtime = new AblyRealtime(opts);
			System.out.println("done create ably");

			/* wait for connected state */
			Helpers.ConnectionWaiter connectionWaiter = new Helpers.ConnectionWaiter(ablyRealtime.connection);
			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Verify connected state is reached", ConnectionState.connected, ablyRealtime.connection.state);

			/* check expected clientId */
			assertEquals("Auth#clientId is expected to be set", "*", ablyRealtime.auth.clientId);

			ablyRealtime.close();
		} catch (AblyException e) {
			e.printStackTrace();
			fail();
		}
	}

	/**
	 * RSA15a: Any clientId provided in ClientOptions must match any
	 * non wildcard ('*') clientId value in TokenDetails
	 *
	 * Verify matching token clientId in TokenDetails succeeds
	 * in authenticating a non-null clientId
	 */
	@Test
	public void auth_client_match_tokendetails_clientId() {
		try {
			/* init ably for token */
			ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
			final AblyRest ablyForToken = new AblyRest(optsForToken);

			/* get token */
			Auth.TokenParams tokenParams = new Auth.TokenParams();
			tokenParams.clientId = "options clientId";
			Auth.TokenDetails tokenDetails = ablyForToken.auth.requestToken(tokenParams, null);
			assertNotNull("Expected token value", tokenDetails.token);

			/* create ably realtime with tokenDetails and clientId */
			ClientOptions opts = createOptions();
			opts.clientId = "options clientId";
			opts.tokenDetails = tokenDetails;
			AblyRealtime ablyRealtime = new AblyRealtime(opts);

			/* wait for connected state */
			Helpers.ConnectionWaiter connectionWaiter = new Helpers.ConnectionWaiter(ablyRealtime.connection);
			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Verify connected state is reached", ConnectionState.connected, ablyRealtime.connection.state);

			/* check expected clientId */
			assertEquals("Auth#clientId is expected to be set", "options clientId", ablyRealtime.auth.clientId);

			ablyRealtime.close();
		} catch (AblyException e) {
			e.printStackTrace();
			fail();
		}
	}

	/**
	 * RSA15a: Any clientId provided in ClientOptions must match any
	 * non wildcard ('*') clientId value in TokenDetails
	 * in authenticating a non-null clientId
	 * 
	 * Verify matching token clientId in token succeeds
	 */
	@Test
	public void auth_client_match_token_clientId() {
		try {
			/* init ably for token */
			ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
			final AblyRest ablyForToken = new AblyRest(optsForToken);

			/* get token */
			Auth.TokenParams tokenParams = new Auth.TokenParams();
			tokenParams.clientId = "options clientId";
			Auth.TokenDetails tokenDetails = ablyForToken.auth.requestToken(tokenParams, null);
			assertNotNull("Expected token value", tokenDetails.token);

			/* create ably realtime with token and clientId */
			ClientOptions opts = createOptions();
			opts.clientId = "options clientId";
			opts.token = tokenDetails.token;
			AblyRealtime ablyRealtime = new AblyRealtime(opts);

			/* wait for connected state */
			Helpers.ConnectionWaiter connectionWaiter = new Helpers.ConnectionWaiter(ablyRealtime.connection);
			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Verify connected state is reached", ConnectionState.connected, ablyRealtime.connection.state);

			/* check expected clientId */
			assertEquals("Auth#clientId is expected to be set", "options clientId", ablyRealtime.auth.clientId);

			ablyRealtime.close();
		} catch (AblyException e) {
			e.printStackTrace();
			fail();
		}
	}

	/**
	 * RSA15a: Any clientId provided in ClientOptions must match any
	 * non wildcard ('*') clientId value in TokenDetails
	 * Verify non-matching token clientId fails to authenticate a non-null clientId
	 * RSA15c: Following an auth request which uses a TokenDetails or TokenRequest
	 * object that contains an incompatible clientId, the library should ... transition
	 *  the connection state to FAILED
	 */
	@Test
	public void auth_client_match_tokendetails_clientId_fail() {
		try {
			/* init ably for token */
			ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
			final AblyRest ablyForToken = new AblyRest(optsForToken);

			/* get token */
			Auth.TokenParams tokenParams = new Auth.TokenParams();
			tokenParams.clientId = "token clientId";
			Auth.TokenDetails tokenDetails = ablyForToken.auth.requestToken(tokenParams, null);
			assertNotNull("Expected token value", tokenDetails.token);

			/* create ably realtime with token and clientId */
			ClientOptions opts = createOptions();
			opts.clientId = "options clientId";
			opts.tokenDetails = tokenDetails;
			AblyRealtime ablyRealtime = new AblyRealtime(opts);
		} catch (AblyException e) {
			assertEquals("Verify error code indicates clientId mismatch", e.errorInfo.code, 40101);
		}
	}

	/**
	 * RSA15a: Any clientId provided in ClientOptions must match any
	 * non wildcard ('*') clientId value in TokenDetails
	 * Verify non-matching token clientId fails to authenticate a non-null clientId
	 * RSA15c: Following an auth request which uses a TokenDetails or TokenRequest
	 * object that contains an incompatible clientId, the library should ... transition
	 *  the connection state to FAILED
	 */
	@Test
	public void auth_client_match_token_clientId_fail() {
		try {
			/* init ably for token */
			ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
			final AblyRest ablyForToken = new AblyRest(optsForToken);

			/* get token */
			Auth.TokenParams tokenParams = new Auth.TokenParams();
			tokenParams.clientId = "token clientId";
			Auth.TokenDetails tokenDetails = ablyForToken.auth.requestToken(tokenParams, null);
			assertNotNull("Expected token value", tokenDetails.token);

			/* create ably realtime with tokenDetails and clientId */
			ClientOptions opts = createOptions();
			opts.clientId = "options clientId";
			opts.token = tokenDetails.token;
			AblyRealtime ablyRealtime = new AblyRealtime(opts);

			/* wait for failed state */
			Helpers.ConnectionWaiter connectionWaiter = new Helpers.ConnectionWaiter(ablyRealtime.connection);
			ErrorInfo failure = connectionWaiter.waitFor(ConnectionState.failed);
			assertEquals("Verify failed state is reached", ConnectionState.failed, ablyRealtime.connection.state);
			assertEquals("Verify failure error code indicates clientId mismatch", failure.code, 40101);
		} catch (AblyException e) {
			e.printStackTrace();
			fail();
		}
	}

	/**
	 * Verify message does not have explicit client id populated
	 * when library is identified
	 * Spec: RTL6g1a,RTL6g1b,RTL6g2,RTL6g3
	 */
	@Test
	public void auth_clientid_publish_implicit() {
		try {
			String clientId = "test clientId";

			/* create Ably instance with clientId */
			Helpers.RawProtocolMonitor protocolListener = Helpers.RawProtocolMonitor.createMonitor(ProtocolMessage.Action.message, ProtocolMessage.Action.message);
			DebugOptions options = new DebugOptions(testVars.keys[0].keyStr);
			fillInOptions(options);
			options.clientId = clientId;
			options.protocolListener = protocolListener;
			AblyRealtime ably = new AblyRealtime(options);

			/* wait until connected */
			(new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

			/* create a channel and attach */
			Channel channel = ably.channels.get("auth_clientid_publish_implicit_" + testParams.name);
			channel.attach();
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* Publish a message */
			Message messageToPublish = new Message(
					"I have clientId",	/* name */
					String.valueOf(System.currentTimeMillis()) /* data */
			);
			channel.publish(new Message[] { messageToPublish });

			/* wait until message seen on transport */
			protocolListener.waitForSend(1);

			/* Get sent message */
			Message messagePublished = protocolListener.sentMessages.get(0).messages[0];
			assertEquals("Sent message does not contain clientId", messagePublished.clientId, null);

			/* wait until message received on transport */
			protocolListener.waitForRecv(1);

			/* Get received message */
			Message messageReceived = protocolListener.receivedMessages.get(0).messages[0];
			assertEquals("Received message does contain clientId", messageReceived.clientId, clientId);

			/* Publish a message with explicit clientId */
			protocolListener.reset();
			messageToPublish = new Message(
					"I have clientId",	/* name */
					String.valueOf(System.currentTimeMillis()),
					clientId /* clientId */
			);

			channel.publish(new Message[] { messageToPublish });

			/* wait until message seen on transport */
			protocolListener.waitForSend(1);

			/* Get sent message */
			messagePublished = protocolListener.sentMessages.get(0).messages[0];
			assertEquals("Sent message does contain clientId", messagePublished.clientId, clientId);

			/* wait until message received on transport */
			protocolListener.waitForRecv(1);

			/* Get sent message */
			messageReceived = protocolListener.receivedMessages.get(0).messages[0];
			assertEquals("Received message was accepted and does contain clientId", messageReceived.clientId, clientId);

			/* Publish a message with incorrect clientId */
			protocolListener.reset();
			messageToPublish = new Message(
					"I have clientId",	/* name */
					String.valueOf(System.currentTimeMillis()),
					"invalid clientId" /* clientId */
			);

			/* wait for the error callback */
			CompletionSet pubComplete = new CompletionSet();
			channel.publish(messageToPublish, pubComplete.add());
			pubComplete.waitFor();
			assertTrue("Verify publish callback called on completion", pubComplete.pending.isEmpty());
			assertTrue("Verify publish callback returns an error", pubComplete.errors.size() == 1);
			assertEquals("Verify publish callback error has expected error code", pubComplete.errors.iterator().next().code, 40012);

			/* verify no message sent or received on transport */
			assertTrue("Verify no messages sent", protocolListener.sentMessages.isEmpty());
			assertTrue("Verify no messages received", protocolListener.receivedMessages.isEmpty());

			/* Publish a message to verify that use of the channel can continue */
			messageToPublish = new Message(
					"I have clientId",	/* name */
					String.valueOf(System.currentTimeMillis()) /* data */
			);
			channel.publish(new Message[] { messageToPublish });

			/* wait until message seen on transport */
			protocolListener.waitForSend(1);

			/* Get sent message */
			messagePublished = protocolListener.sentMessages.get(0).messages[0];
			assertEquals("Sent message does not contain clientId", messagePublished.clientId, null);

			/* wait until message received on transport */
			protocolListener.waitForRecv(1);

			/* Get received message */
			messageReceived = protocolListener.receivedMessages.get(0).messages[0];
			assertEquals("Received message does contain clientId", messageReceived.clientId, clientId);

			ably.close();
		} catch (Exception e) {
			e.printStackTrace();
			fail("auth_clientid_publish_implicit: Unexpected exception");
		}
	}

	/**
	 * Verify message does not have implicit client id
	 * if sent before library is identified, so messages
	 * are sent with explicit clientId
	 * Spec: RTL6g4
	 */
	@Test
	public void auth_clientid_publish_explicit_before_identified() {
		AblyRealtime ably = null;
		try {
			String clientId = "test clientId";
			ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
			optsForToken.clientId = clientId;
			AblyRest ablyForToken = new AblyRest(optsForToken);
			TokenDetails tokenDetails = ablyForToken.auth.requestToken(null, null);

			/* create Ably instance with token and implied clientId */
			Helpers.RawProtocolMonitor protocolListener = Helpers.RawProtocolMonitor.createMonitor(ProtocolMessage.Action.message, ProtocolMessage.Action.message);
			DebugOptions options = new DebugOptions(testVars.keys[0].keyStr);
			fillInOptions(options);
			options.token = tokenDetails.token;
			options.protocolListener = protocolListener;
			ably = new AblyRealtime(options);

			/* verify we don't yet know the implied clientId */
			assertNull("Verify clientId is unknown", ably.auth.clientId);

			/* create a channel */
			Channel channel = ably.channels.get("auth_clientid_publish_explicit_before_identified_" + testParams.name);

			/* publish before connection and attach */
			Message messageToPublish = new Message(
					"I have clientId",	/* name */
					String.valueOf(System.currentTimeMillis()),
					clientId /* clientId */
			);
			channel.attach();
			channel.publish(new Message[] { messageToPublish });

			/* wait until connected and attached */
			(new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
			assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);
			(new ChannelWaiter(channel)).waitFor(ChannelState.attached);
			assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

			/* wait until message seen on transport */
			protocolListener.waitForSend(1);

			/* Get sent message */
			Message messagePublished = protocolListener.sentMessages.get(0).messages[0];
			assertEquals("Sent message does contain explicit clientId", messagePublished.clientId, clientId);

			/* wait until message received on transport */
			protocolListener.waitForRecv(1);

			/* Get received message */
			Message messageReceived = protocolListener.receivedMessages.get(0).messages[0];
			assertEquals("Received message does contain clientId", messageReceived.clientId, clientId);

			/* Publish a message to verify that use of the channel can continue */
			protocolListener.reset();
			messageToPublish = new Message(
					"I have clientId",	/* name */
					String.valueOf(System.currentTimeMillis()) /* data */
			);
			channel.publish(new Message[] { messageToPublish });

			/* wait until message seen on transport */
			protocolListener.waitForSend(1);

			/* Get sent message */
			messagePublished = protocolListener.sentMessages.get(0).messages[0];
			assertEquals("Sent message does not contain clientId", messagePublished.clientId, null);

			/* wait until message received on transport */
			protocolListener.waitForRecv(1);

			/* Get received message */
			messageReceived = protocolListener.receivedMessages.get(0).messages[0];
			assertEquals("Received message does contain clientId", messageReceived.clientId, clientId);
		} catch (Exception e) {
			e.printStackTrace();
			fail("auth_clientid_publish_implicit: Unexpected exception");
		} finally {
			if(ably != null) {
				ably.close();
			}
		}
	}
}
