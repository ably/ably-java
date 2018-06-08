package io.ably.lib.test.realtime;

import static org.junit.Assert.*;

import io.ably.lib.debug.DebugOptions;
import io.ably.lib.debug.DebugOptions.RawProtocolListener;
import io.ably.lib.test.common.Setup.Key;
import org.junit.Before;
import org.junit.Test;

import io.ably.lib.types.*;
import io.ably.lib.realtime.*;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Auth.*;
import io.ably.lib.test.common.Helpers.*;
import io.ably.lib.test.common.ParameterizedTest;

import java.util.UUID;


public class RealtimeJWTTest extends ParameterizedTest {

	private AblyRest restJWTRequester;
	private ClientOptions jwtRequesterOptions;
	private Key key = testVars.keys[0];
	private final String clientId = "testJWTClientID";
	private final String channelName = "testJWTChannel" + UUID.randomUUID().toString();
	private final String messageName = "testJWTMessage" + UUID.randomUUID().toString();
	Param[] keys = new Param[]{ new Param("keyName", key.keyName), new Param("keySecret", key.keySecret) };
	Param[] clientIdParam = new Param[] { new Param("clientId", clientId) };
	Param[] shortTokenTtl = new Param[] { new Param("expiresIn", 5) };
	Param[] mediumTokenTtl = new Param[] { new Param("expiresIn", 35) };
	private final String susbcribeOnlyCapability = "{\"" + channelName + "\": [\"subscribe\"]}";
	private final String publishCapability = "{\"" + channelName + "\": [\"publish\"]}";

	/**
	 * Request a JWT that specifies a clientId
	 * Verifies that the clientId matches the one requested
	 */
	@Test
	public void auth_clientid_match_the_one_requested_in_jwt() {
		try {
			/* create ably realtime with JWT token */
			ClientOptions realtimeOptions = buildClientOptions(mergeParams(keys, clientIdParam), null);
			assertNotNull("Expected token value", realtimeOptions.token);
			AblyRealtime ablyRealtime = new AblyRealtime(realtimeOptions);

			/* wait for connected state */
			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ablyRealtime.connection);
			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Connected state was NOT reached", ConnectionState.connected, ablyRealtime.connection.state);

			/* check expected clientId */
			assertEquals("clientId does NOT match the one requested", clientId, ablyRealtime.auth.clientId);

			ablyRealtime.close();
		} catch (AblyException e) {
			e.printStackTrace();
			fail();
		}
	}

	/**
	 * Request a JWT with subscribe-only capabilities
	 * Verifies that publishing on a channel fails
	 */
	@Test
	public void auth_jwt_with_subscribe_only_capability() {
		try {
			/* create ably realtime with JWT token that has subscribe-only capabilities */
			ClientOptions realtimeOptions = buildClientOptions(keys, susbcribeOnlyCapability);
			assertNotNull("Expected token value", realtimeOptions.token);
			final AblyRealtime ablyRealtime = new AblyRealtime(realtimeOptions);

			/* wait for connected state */
			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ablyRealtime.connection);
			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Connected state was NOT reached", ConnectionState.connected, ablyRealtime.connection.state);

			/* attach to channel and verify attached state */
			Channel channel = ablyRealtime.channels.get(channelName);
			channel.attach();
			new ChannelWaiter(channel).waitFor(ChannelState.attached);

			/* publish and verify that it fails */
			channel.publish(messageName, null, new CompletionListener() {
				@Override
				public void onSuccess() {
					ablyRealtime.close();
					fail("It should not succeed");
				}

				@Override
				public void onError(ErrorInfo error) {
					assertEquals("Unexpected status code", 401, error.statusCode);
					assertEquals("Unexpected error code", 40160, error.code);
					assertEquals("Unexpected error message", "Unable to perform channel operation (permission denied)", error.message);
					ablyRealtime.close();
				}
			});
			connectionWaiter.waitFor(ConnectionState.closed);
		} catch (AblyException e) {
			e.printStackTrace();
			fail();
		}
	}

	/**
	 * Request a JWT with publish capabilities
	 * Verifies that publishing on a channel succeeds
	 */
	@Test
	public void auth_jwt_with_publish_capability() {
		try {
			/* create ably realtime with JWT token that has publish capabilities */
			ClientOptions realtimeOptions = buildClientOptions(keys, publishCapability);
			assertNotNull("Expected token value", realtimeOptions.token);
			final AblyRealtime ablyRealtime = new AblyRealtime(realtimeOptions);

			/* wait for connected state */
			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ablyRealtime.connection);
			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Connected state was NOT reached", ConnectionState.connected, ablyRealtime.connection.state);

			/* attach to channel and verify attached state */
			Channel channel = ablyRealtime.channels.get(channelName);
			channel.attach();
			new ChannelWaiter(channel).waitFor(ChannelState.attached);

			/* publish, verify that it succeeds then close */
			final Message message = new Message(messageName, null);
			channel.publish(message, new CompletionListener() {
				@Override
				public void onSuccess() {
					System.out.println("Message " + messageName + " published successfully");
					ablyRealtime.close();
				}

				@Override
				public void onError(ErrorInfo reason) {
					ablyRealtime.close();
					fail("Publish should not fail");
				}
			});
			connectionWaiter.waitFor(ConnectionState.closed);
		} catch (AblyException e) {
			e.printStackTrace();
			fail();
		}
	}

	/**
	 * Request a JWT with a ttl of 5 seconds and
	 * verify the correct error and message in the disconnected state change.
	 */
	@Test
	public void auth_jwt_with_token_that_expires() {
		try {
			/* create ably realtime with JWT token that expires in 5 seconds */
			ClientOptions realtimeOptions = buildClientOptions(mergeParams(keys, shortTokenTtl), null);
			assertNotNull("Expected token value", realtimeOptions.token);
			final AblyRealtime ablyRealtime = new AblyRealtime(realtimeOptions);

			/* wait for connected state */
			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ablyRealtime.connection);
			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Connected state was NOT reached", ConnectionState.connected, ablyRealtime.connection.state);

			/* Verify the expected error reason when disconnected */
			ablyRealtime.connection.once(ConnectionEvent.disconnected, new ConnectionStateListener() {

				@Override
				public void onConnectionStateChanged(ConnectionStateChange stateChange) {
					assertEquals("Unexpected connection stage change", 40142, stateChange.reason.code);
					assertEquals("Unexpected error message", "Key/token status changed (expire)", stateChange.reason.message);
					ablyRealtime.close();
				}
			});
			connectionWaiter.waitFor(ConnectionState.disconnected);
			connectionWaiter.waitFor(ConnectionState.closed);
		} catch (AblyException e) {
			e.printStackTrace();
			fail();
		}
	}

	/**
	 * Request a JWT with a ttl of 35 seconds and
	 * verify that the client reauths without going through a disconnected state. (RTC8a4)
	 */
	@Test
	public void auth_jwt_with_client_than_reauths_without_disconnecting() {
		try {
			final String[] tokens = new String[1];
			final boolean[] authMessages = new boolean[] { false };

			/* create ably realtime with authUrl and params that include a ttl of 35 seconds */
			DebugOptions options = new DebugOptions(testVars.keys[0].keyStr);
			options.environment = createOptions().environment;
			options.authUrl = echoServer;
			options.authParams = mergeParams(keys, mediumTokenTtl);
			options.protocolListener = new RawProtocolListener() {
				@Override
				public void onRawConnect(String url) { }
				@Override
				public void onRawMessageSend(ProtocolMessage message) { }
				@Override
				public void onRawMessageRecv(ProtocolMessage message) {
					if (message.action == ProtocolMessage.Action.auth) {
						authMessages[0] = true;
					}
				}
			};
			final AblyRealtime ablyRealtime = new AblyRealtime(options);

			/* Once connected for the first time capture the assigned token */
			ablyRealtime.connection.once(ConnectionEvent.connected, new ConnectionStateListener() {
				@Override
				public void onConnectionStateChanged(ConnectionStateChange stateChange) {
					assertEquals("State is not connected", ConnectionState.connected, stateChange.current);
					synchronized (tokens) {
						tokens[0] = ablyRealtime.auth.getTokenDetails().token;
					}
				}
			});

			/* Fail if the disconnected state is ever reached */
			ablyRealtime.connection.once(ConnectionEvent.disconnected, new ConnectionStateListener() {
				@Override
				public void onConnectionStateChanged(ConnectionStateChange stateChange) {
					fail("Should NOT enter the disconnected state");
				}
			});

			/* Once receiving the update event check that the token is a new one
			 * and verify the auth protocol message has been received. */
			ablyRealtime.connection.on(ConnectionEvent.update, new ConnectionStateListener() {
				@Override
				public void onConnectionStateChanged(ConnectionStateChange state) {
					assertNotEquals("Token should not be the same", tokens[0], ablyRealtime.auth.getTokenDetails().token);
					assertTrue("Auth protocol message has not been received", authMessages[0]);
					ablyRealtime.close();
				}
			});

			/* wait for connected state */
			ConnectionWaiter connectionWaiter = new ConnectionWaiter(ablyRealtime.connection);
			connectionWaiter.waitFor(ConnectionState.connected);
			assertEquals("Connected state was NOT reached", ConnectionState.connected, ablyRealtime.connection.state);

			/* wait for closed state */
			connectionWaiter.waitFor(ConnectionState.closed);
		} catch (AblyException e) {
			e.printStackTrace();
			fail();
		}
	}

	/**
	 * Helper to create ClientOptions with a JWT token fetched via authUrl according to the parameters
	 */
	private ClientOptions buildClientOptions(Param[] params, String capability) {
		try {
			jwtRequesterOptions = createOptions(testVars.keys[0].keyStr);
			jwtRequesterOptions.authUrl = echoServer;
			jwtRequesterOptions.authParams = params;
			restJWTRequester = new AblyRest(jwtRequesterOptions);
			TokenParams t = new TokenParams();
			if (capability != null) {
				t.capability = capability;
			}
			TokenDetails tokenDetails = restJWTRequester.auth.requestToken(t, null);
			ClientOptions realtimeOptions = createOptions();
			realtimeOptions.token = tokenDetails.token;
			return realtimeOptions;
		} catch (AblyException e) {
			fail("Failure in fetching a JWT token to create ClientOptions " + e);
			return null;
		}
	}

}
