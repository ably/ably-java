package io.ably.lib.test.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static io.ably.lib.util.AblyErrors.OPERATION_NOT_PERMITTED_WITH_PROVIDED_CAPABILITY;
import static io.ably.lib.util.AblyErrors.TOKEN_EXPIRED;

import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import io.ably.lib.debug.DebugOptions;
import io.ably.lib.debug.DebugOptions.RawProtocolListener;
import io.ably.lib.http.HttpCore;
import io.ably.lib.http.HttpCore.ResponseHandler;
import io.ably.lib.http.HttpHelpers;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.realtime.ConnectionEvent;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.realtime.ConnectionStateListener;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Auth.TokenCallback;
import io.ably.lib.rest.Auth.TokenParams;
import io.ably.lib.test.common.Helpers.ChannelWaiter;
import io.ably.lib.test.common.Helpers.ConnectionWaiter;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.test.common.Setup.Key;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Message;
import io.ably.lib.types.Param;
import io.ably.lib.types.ProtocolMessage;

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
    private static final String echoServer = "https://echo.ably.io/createJWT";

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
                    assertEquals("Unexpected error code", OPERATION_NOT_PERMITTED_WITH_PROVIDED_CAPABILITY.code, error.code);
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

            final AtomicBoolean publishError = new AtomicBoolean(true);

            /* publish, verify that it succeeds then close */
            final Message message = new Message(messageName, null);
            channel.publish(message, new CompletionListener() {
                @Override
                public void onSuccess() {
                    System.out.println("Message " + messageName + " published successfully");
                    publishError.set(false);
                    ablyRealtime.close();
                }

                @Override
                public void onError(ErrorInfo reason) {
                    publishError.set(true);
                    ablyRealtime.close();
                    fail("Publish should not fail");
                }
            });
            connectionWaiter.waitFor(ConnectionState.closed);
            assertFalse("Publish should not fail", publishError.get());
        } catch (AblyException e) {
            e.printStackTrace();
            fail();
        }
    }

    /**
     * Request a JWT with a ttl of 5 seconds and
     * verify the correct error and message in the disconnected state change.
     * Spec: RTN15h1
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
                    assertEquals("Unexpected connection stage change", TOKEN_EXPIRED.code, stateChange.reason.code);
                    assertTrue("Unexpected error message", stateChange.reason.message.contains("Key/token status changed (expire)"));
                }
            });
            connectionWaiter.waitFor(ConnectionState.failed);
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
            final boolean[] updateEvents = new boolean[] { false };

            /* create ably realtime with authUrl and params that include a ttl of 35 seconds */
            DebugOptions options = new DebugOptions(testVars.keys[0].keyStr);
            options.environment = createOptions().environment;
            options.authUrl = echoServer;
            options.authParams = mergeParams(keys, mediumTokenTtl);
            options.protocolListener = new RawProtocolListener() {
                @Override
                public void onRawConnectRequested(String url) {}
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
                    updateEvents[0] = true;
                    ablyRealtime.close();
                }
            });

            /* wait for connected state */
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ablyRealtime.connection);
            connectionWaiter.waitFor(ConnectionState.connected);
            assertEquals("Connected state was NOT reached", ConnectionState.connected, ablyRealtime.connection.state);

            /* wait for closed state */
            connectionWaiter.waitFor(ConnectionState.closed);
            assertTrue("Update event not received", updateEvents[0]);
        } catch (AblyException e) {
            e.printStackTrace();
            fail();
        }
    }

    /**
     * Request a JWT with a ttl of 35 seconds and
     * verify that the client reauths without going through a disconnected state using an authCallback. (RTC8a4)
     */
    @Test
    public void auth_jwt_with_client_than_reauths_without_disconnecting_via_authCallback() {
        try {
            final String[] tokens = new String[1];
            final boolean[] authMessages = new boolean[] { false };
            final boolean[] updateEvents = new boolean[] { false };
            final List<Boolean> callbackCalled = new ArrayList<Boolean>();

            /* authCallback that with params that include a ttl of 35 seconds */
            TokenCallback authCallback = new TokenCallback() {
                @Override
                public Object getTokenRequest(TokenParams params) throws AblyException {
                    final String[] resultToken = new String[1];
                    AblyRest rest = new AblyRest(createOptions(testVars.keys[0].keyStr));
                    HttpHelpers.getUri(rest.httpCore, echoServer, new Param[]{}, mergeParams(keys, mediumTokenTtl), new ResponseHandler() {
                        @Override
                        public Object handleResponse(HttpCore.Response response, ErrorInfo error) throws AblyException {
                            try {
                                callbackCalled.add(true);
                                resultToken[0] = new String(response.body, "UTF-8");
                            } catch (UnsupportedEncodingException e) {
                                e.printStackTrace();
                                fail("Error in fetching a JWT token using authCallback");
                            }
                            return null;
                        }
                    });
                    return resultToken[0];
                }
            };

            /* create ably realtime with authCallback defined above */
            DebugOptions options = new DebugOptions(testVars.keys[0].keyStr);
            options.environment = createOptions().environment;
            options.authCallback = authCallback;
            options.protocolListener = new RawProtocolListener() {
                @Override
                public void onRawConnectRequested(String url) {}
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

            /* Once connected for the first time capture the assigned token and
            * verify the callback has been called once */
            ablyRealtime.connection.once(ConnectionEvent.connected, new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange stateChange) {
                    assertTrue("Callback not called the first time", callbackCalled.get(0));
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
                    ablyRealtime.close();
                    fail("Should NOT enter the disconnected state");
                }
            });

            /* Once receiving the update event check that the token is a new one,
             * verify the auth protocol message has been received and verify the callback has been called twice. */
            ablyRealtime.connection.on(ConnectionEvent.update, new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange state) {
                    assertTrue("Callback not called the second time", callbackCalled.get(1));
                    assertEquals("Callback not called 2 times", callbackCalled.size(), 2);
                    assertNotEquals("Token should not be the same", tokens[0], ablyRealtime.auth.getTokenDetails().token);
                    assertTrue("Auth protocol message has not been received", authMessages[0]);
                    updateEvents[0] = true;
                    ablyRealtime.close();
                }
            });

            /* wait for connected state */
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ablyRealtime.connection);
            connectionWaiter.waitFor(ConnectionState.connected);
            assertEquals("Connected state was NOT reached", ConnectionState.connected, ablyRealtime.connection.state);

            /* wait for closed state */
            connectionWaiter.waitFor(ConnectionState.closed);
            assertTrue("Update event not received", updateEvents[0]);
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
            final String[] resultToken = new String[1];
            AblyRest rest = new AblyRest(createOptions(testVars.keys[0].keyStr));
            HttpHelpers.getUri(rest.httpCore, echoServer, null, params, new ResponseHandler() {
                @Override
                public Object handleResponse(HttpCore.Response response, ErrorInfo error) throws AblyException {
                    try {
                        resultToken[0] = new String(response.body, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                        fail("Error in fetching a JWT token " + e);
                    }
                    return null;
                }
            });
            ClientOptions realtimeOptions = createOptions();
            realtimeOptions.token = resultToken[0];
            return realtimeOptions;
        } catch (AblyException e) {
            fail("Failure in fetching a JWT token to create ClientOptions " + e);
            return null;
        }
    }

}
