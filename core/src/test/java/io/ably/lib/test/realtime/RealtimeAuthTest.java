package io.ably.lib.test.realtime;

import io.ably.lib.debug.DebugOptions;
import io.ably.lib.platform.Platform;
import io.ably.lib.push.PushBase;
import io.ably.lib.realtime.AblyRealtimeBase;
import io.ably.lib.realtime.RealtimeChannelBase;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.realtime.ConnectionEvent;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.realtime.ConnectionStateListener;
import io.ably.lib.rest.AblyBase;
import io.ably.lib.rest.Auth;
import io.ably.lib.rest.Auth.TokenDetails;
import io.ably.lib.rest.RestChannelBase;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.Helpers.ChannelWaiter;
import io.ably.lib.test.common.Helpers.CompletionSet;
import io.ably.lib.test.common.Helpers.ConnectionWaiter;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Message;
import io.ably.lib.types.Param;
import io.ably.lib.types.ProtocolMessage;
import io.ably.lib.types.ProxyOptions;
import io.ably.lib.util.Log;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

public abstract class RealtimeAuthTest extends ParameterizedTest {

    @Rule
    public Timeout testTimeout = Timeout.seconds(30);

    /**
     * Verifies an Exception is thrown, when client is initialized with an empty key
     *
     * @throws IllegalArgumentException
     */
    @Test(expected = IllegalArgumentException.class)
    public void auth_client_cannot_be_initialized_with_empty_key() throws AblyException {
        createAblyRealtime("");
    }

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
            final AblyBase<PushBase, Platform, RestChannelBase> ablyForToken = createAblyRest(optsForToken);

            /* get token */
            Auth.TokenParams tokenParams = new Auth.TokenParams();
            tokenParams.clientId = null;
            Auth.TokenDetails tokenDetails = ablyForToken.auth.requestToken(tokenParams, null);
            assertNotNull("Expected token value", tokenDetails.token);

            /* create ably realtime with tokenDetails and clientId */
            ClientOptions opts = createOptions();
            opts.clientId = null;
            opts.tokenDetails = tokenDetails;
            AblyRealtimeBase<PushBase, Platform, RealtimeChannelBase> ablyRealtime = createAblyRealtime(opts);
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
     * RSA4d: If a request by a realtime client to an authUrl results in an HTTP 403 response,
     * or any of an authUrl request, an authCallback, or a request to Ably to exchange
     * a TokenRequest for a TokenDetails result in an ErrorInfo with statusCode 403,
     * as part of an attempt by the realtime client to authenticate, then the client library
     * should transition to the FAILED state, with an ErrorInfo (with code 80019, statusCode 403,
     * and cause set to the underlying cause) emitted with the state change and set as the connection
     * errorReason
     *
     * Verify that if server responses with 403 error code on authorization attempt,
     * end connection state is failed.
     *
     * Spec: RSA4d, RSA4d1
     */
    @Test
    public void auth_client_fails_authorize_server_forbidden() {
        try {
            /* init ably for token */
            ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
            AblyBase<PushBase, Platform, RestChannelBase> ablyForToken = createAblyRest(optsForToken);
            /* get token */
            TokenDetails tokenDetails = ablyForToken.auth.requestToken(null, null);

            /* create ably realtime with tokenDetails and auth url which returns 403 error code */
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.autoConnect = false;
            opts.tokenDetails = tokenDetails;
            opts.useTokenAuth = true;
            opts.authUrl = "https://echo.ably.io/respondwith";
            opts.authParams = new Param[]{ new Param("status", 403)};

            final AblyRealtimeBase<PushBase, Platform, RealtimeChannelBase> ablyRealtime = createAblyRealtime(opts);
            ablyRealtime.connection.connect();

            /* wait for connected state */
            Helpers.ConnectionWaiter connectionWaiter = new Helpers.ConnectionWaiter(ablyRealtime.connection);
            connectionWaiter.waitFor(ConnectionState.connected);

            /* create listener for ConnectionEvent.failed */
            ablyRealtime.connection.once(ConnectionEvent.failed, new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange stateChange) {
                    /* assert that state changes correctly */
                    assertEquals(ConnectionState.connected, stateChange.previous);
                    assertEquals(80019, stateChange.reason.code);
                    assertEquals(80019, ablyRealtime.connection.reason.code);
                    assertEquals(403, ablyRealtime.connection.reason.statusCode);
                }
            });

            try {
                opts.tokenDetails = null;
                /* try to authorize */
                ablyRealtime.auth.authorize(null, opts);
            } catch (AblyException e) {
                /* check expected error codes */
                assertEquals(403, e.errorInfo.statusCode);
                assertEquals(80019, e.errorInfo.code);
            }

            /* wait for failed state */
            connectionWaiter.waitFor(ConnectionState.failed);
            assertEquals("Verify connected state has failed", ConnectionState.failed, ablyRealtime.connection.state);
            assertEquals("Check correct cause error code", 403, ablyRealtime.connection.reason.statusCode);
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
            final AblyBase<PushBase, Platform, RestChannelBase> ablyForToken = createAblyRest(optsForToken);

            /* get token */
            Auth.TokenParams tokenParams = new Auth.TokenParams();
            tokenParams.clientId = null;
            Auth.TokenDetails tokenDetails = ablyForToken.auth.requestToken(tokenParams, null);
            assertNotNull("Expected token value", tokenDetails.token);

            /* create ably realtime with tokenDetails and clientId */
            ClientOptions opts = createOptions();
            opts.clientId = null;
            opts.token = tokenDetails.token;
            AblyRealtimeBase<PushBase, Platform, RealtimeChannelBase> ablyRealtime = createAblyRealtime(opts);
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
     * Spec: RSA12b, RSA7b2, RSA7b3, RTC4a
     */
    @Test
    public void auth_clientid_null_before_auth() {
        try {
            final String clientId = "token clientId";

            /* create token with clientId */
            ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
            optsForToken.clientId = clientId;
            AblyBase<PushBase, Platform, RestChannelBase> ablyForToken = createAblyRest(optsForToken);
            TokenDetails tokenDetails = ablyForToken.auth.requestToken(null, null);

            /* create ably realtime */
            ClientOptions opts = createOptions();
            opts.clientId = null;
            opts.token = tokenDetails.token;
            opts.autoConnect = false;
            AblyRealtimeBase<PushBase, Platform, RealtimeChannelBase> ablyRealtime = createAblyRealtime(opts);

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
            final AblyBase<PushBase, Platform, RestChannelBase> ablyForToken = createAblyRest(optsForToken);

            /* get token */
            Auth.TokenParams tokenParams = new Auth.TokenParams();
            tokenParams.clientId = "*";
            Auth.TokenDetails tokenDetails = ablyForToken.auth.requestToken(tokenParams, null);
            assertNotNull("Expected token value", tokenDetails.token);

            /* create ably realtime with tokenDetails and clientId */
            ClientOptions opts = createOptions();
            opts.clientId = "options clientId";
            opts.tokenDetails = tokenDetails;
            AblyRealtimeBase<PushBase, Platform, RealtimeChannelBase> ablyRealtime = createAblyRealtime(opts);
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
            final AblyBase<PushBase, Platform, RestChannelBase> ablyForToken = createAblyRest(optsForToken);

            /* get token */
            Auth.TokenParams tokenParams = new Auth.TokenParams();
            tokenParams.clientId = "*";
            Auth.TokenDetails tokenDetails = ablyForToken.auth.requestToken(tokenParams, null);
            assertNotNull("Expected token value", tokenDetails.token);

            /* create ably realtime with token and clientId */
            ClientOptions opts = createOptions();
            opts.clientId = "options clientId";
            opts.token = tokenDetails.token;
            AblyRealtimeBase<PushBase, Platform, RealtimeChannelBase> ablyRealtime = createAblyRealtime(opts);
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
            final AblyBase<PushBase, Platform, RestChannelBase> ablyForToken = createAblyRest(optsForToken);

            /* get token */
            Auth.TokenParams tokenParams = new Auth.TokenParams();
            tokenParams.clientId = "*";
            Auth.TokenDetails tokenDetails = ablyForToken.auth.requestToken(tokenParams, null);
            assertNotNull("Expected token value", tokenDetails.token);

            /* create ably realtime with tokenDetails and clientId */
            ClientOptions opts = createOptions();
            opts.clientId = null;
            opts.tokenDetails = tokenDetails;
            AblyRealtimeBase<PushBase, Platform, RealtimeChannelBase> ablyRealtime = createAblyRealtime(opts);
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
            final AblyBase<PushBase, Platform, RestChannelBase> ablyForToken = createAblyRest(optsForToken);

            /* get token */
            Auth.TokenParams tokenParams = new Auth.TokenParams();
            tokenParams.clientId = "*";
            Auth.TokenDetails tokenDetails = ablyForToken.auth.requestToken(tokenParams, null);
            assertNotNull("Expected token value", tokenDetails.token);

            /* create ably realtime with token and clientId */
            ClientOptions opts = createOptions();
            opts.clientId = null;
            opts.token = tokenDetails.token;
            AblyRealtimeBase<PushBase, Platform, RealtimeChannelBase> ablyRealtime = createAblyRealtime(opts);
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
            final AblyBase<PushBase, Platform, RestChannelBase> ablyForToken = createAblyRest(optsForToken);

            /* get token */
            Auth.TokenParams tokenParams = new Auth.TokenParams();
            tokenParams.clientId = "options clientId";
            Auth.TokenDetails tokenDetails = ablyForToken.auth.requestToken(tokenParams, null);
            assertNotNull("Expected token value", tokenDetails.token);

            /* create ably realtime with tokenDetails and clientId */
            ClientOptions opts = createOptions();
            opts.clientId = "options clientId";
            opts.tokenDetails = tokenDetails;
            AblyRealtimeBase<PushBase, Platform, RealtimeChannelBase> ablyRealtime = createAblyRealtime(opts);

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
            final AblyBase<PushBase, Platform, RestChannelBase> ablyForToken = createAblyRest(optsForToken);

            /* get token */
            Auth.TokenParams tokenParams = new Auth.TokenParams();
            tokenParams.clientId = "options clientId";
            Auth.TokenDetails tokenDetails = ablyForToken.auth.requestToken(tokenParams, null);
            assertNotNull("Expected token value", tokenDetails.token);

            /* create ably realtime with token and clientId */
            ClientOptions opts = createOptions();
            opts.clientId = "options clientId";
            opts.token = tokenDetails.token;
            AblyRealtimeBase<PushBase, Platform, RealtimeChannelBase> ablyRealtime = createAblyRealtime(opts);

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
            final AblyBase<PushBase, Platform, RestChannelBase> ablyForToken = createAblyRest(optsForToken);

            /* get token */
            Auth.TokenParams tokenParams = new Auth.TokenParams();
            tokenParams.clientId = "token clientId";
            Auth.TokenDetails tokenDetails = ablyForToken.auth.requestToken(tokenParams, null);
            assertNotNull("Expected token value", tokenDetails.token);

            /* create ably realtime with token and clientId */
            ClientOptions opts = createOptions();
            opts.clientId = "options clientId";
            opts.tokenDetails = tokenDetails;
            AblyRealtimeBase<PushBase, Platform, RealtimeChannelBase> ablyRealtime = createAblyRealtime(opts);
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
    @Ignore("FIXME: fix exception")
    @Test
    public void auth_client_match_token_clientId_fail() {
        try {
            /* init ably for token */
            ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
            final AblyBase<PushBase, Platform, RestChannelBase> ablyForToken = createAblyRest(optsForToken);

            /* get token */
            Auth.TokenParams tokenParams = new Auth.TokenParams();
            tokenParams.clientId = "token clientId";
            Auth.TokenDetails tokenDetails = ablyForToken.auth.requestToken(tokenParams, null);
            assertNotNull("Expected token value", tokenDetails.token);

            /* create ably realtime with tokenDetails and clientId */
            ClientOptions opts = createOptions();
            opts.clientId = "options clientId";
            opts.token = tokenDetails.token;
            AblyRealtimeBase<PushBase, Platform, RealtimeChannelBase> ablyRealtime = createAblyRealtime(opts);

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
     * Spec: RTL6g1a,RTL6g1b,RTL6g2,RTL6g3,RSA7e1
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
            AblyRealtimeBase<PushBase, Platform, RealtimeChannelBase> ably = createAblyRealtime(options);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            /* create a channel and attach */
            RealtimeChannelBase channel = ably.channels.get("auth_clientid_publish_implicit_" + testParams.name);
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* Publish a message */
            Message messageToPublish = new Message(
                    "I have clientId",    /* name */
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
                    "I have clientId",    /* name */
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
                    "I have clientId",   /* name */
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
                    "I have clientId",    /* name */
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
    @Ignore("FIXME: fix exception")
    @Test
    public void auth_clientid_publish_explicit_before_identified() {
        AblyRealtimeBase<PushBase, Platform, RealtimeChannelBase> ably = null;
        try {
            String clientId = "test clientId";
            ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
            optsForToken.clientId = clientId;
            AblyBase<PushBase, Platform, RestChannelBase> ablyForToken = createAblyRest(optsForToken);
            TokenDetails tokenDetails = ablyForToken.auth.requestToken(null, null);

            /* create Ably instance with token and implied clientId */
            Helpers.RawProtocolMonitor protocolListener = Helpers.RawProtocolMonitor.createMonitor(ProtocolMessage.Action.message, ProtocolMessage.Action.message);
            DebugOptions options = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(options);
            options.token = tokenDetails.token;
            options.protocolListener = protocolListener;
            ably = createAblyRealtime(options);

            /* verify we don't yet know the implied clientId */
            assertNull("Verify clientId is unknown", ably.auth.clientId);

            /* create a channel */
            RealtimeChannelBase channel = ably.channels.get("auth_clientid_publish_explicit_before_identified_" + testParams.name);

            /* publish before connection and attach */
            Message messageToPublish = new Message(
                    "I have clientId",    /* name */
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
                    "I have clientId",  /* name */
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

    /**
     * Call renew() whilst connecting; verify there's no crash (see https://github.com/ably/ably-java/issues/503)
     */
    @Test
    public void auth_renew_whilst_connecting() {
        try {
            /* get a TokenDetails */
            final String testKey = testVars.keys[0].keyStr;
            ClientOptions optsForToken = createOptions(testKey);
            final AblyBase<PushBase, Platform, RestChannelBase> ablyForToken = createAblyRest(optsForToken);

            final TokenDetails tokenDetails = ablyForToken.auth.requestToken(new Auth.TokenParams(){{ ttl = 1000L; }}, null);
            assertNotNull("Expected token value", tokenDetails.token);

            /* create Ably realtime instance with token and authCallback */
            class ProtocolListener extends DebugOptions implements DebugOptions.RawProtocolListener {
                ProtocolListener() {
//                    Setup.getTestVars().fillInOptions(this);
                    testVars.fillInOptions(this);
                    protocolListener = this;
                }
                @Override
                public void onRawConnectRequested(String url) {
                    synchronized(this) {
                        notify();
                    }
                }

                @Override
                public void onRawConnect(String url) {}
                @Override
                public void onRawMessageSend(ProtocolMessage message) {}
                @Override
                public void onRawMessageRecv(ProtocolMessage message) {}
            }

            ProtocolListener opts = new ProtocolListener();
            opts.autoConnect = false;
            opts.tokenDetails = tokenDetails;
            opts.authCallback = new Auth.TokenCallback() {
                /* implement callback, using Ably instance with key */
                @Override
                public Object getTokenRequest(Auth.TokenParams params) {
                    return tokenDetails;
                }
            };

            final AblyRealtimeBase<PushBase, Platform, RealtimeChannelBase> ably = createAblyRealtime(opts);
            synchronized (opts) {
                ably.connect();
                try {
                    opts.wait();
                } catch(InterruptedException ie) {}
                ably.auth.renew();
            }

            Helpers.ConnectionWaiter connectionWaiter = new Helpers.ConnectionWaiter(ably.connection);
            boolean isConnected = connectionWaiter.waitFor(ConnectionState.connected, 1, 4000L);
            if(isConnected) {
                /* done */
                ably.close();
            } else {
                fail("auth_expired_token_expire_renew: unable to connect; final state = " + ably.connection.state);
            }
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_expired_token_expire_renew: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify that with queryTime=false, when instancing with an already-expired token and authCallback,
     * connection can succeed
     */
    @Test
    public void auth_expired_token_expire_before_connect_renew() {
        try {
            /* get a TokenDetails */
            final String testKey = testVars.keys[0].keyStr;
            ClientOptions optsForToken = createOptions(testKey);
            optsForToken.queryTime = false;
            final AblyBase<PushBase, Platform, RestChannelBase> ablyForToken = createAblyRest(optsForToken);

            TokenDetails tokenDetails = ablyForToken.auth.requestToken(new Auth.TokenParams(){{ ttl = 100L; }}, null);
            assertNotNull("Expected token value", tokenDetails.token);

            /* allow to expire */
            try { Thread.sleep(200L); } catch(InterruptedException ie) {}

            /* create Ably realtime instance with token and authCallback */
            ClientOptions opts = createOptions();
            opts.tokenDetails = tokenDetails;
            opts.authCallback = new Auth.TokenCallback() {
                /* implement callback, using Ably instance with key */
                @Override
                public Object getTokenRequest(Auth.TokenParams params) throws AblyException {
                    return ablyForToken.auth.requestToken(params, null);
                }
            };

            /* disable token validity check */
            opts.queryTime = false;

            final AblyRealtimeBase<PushBase, Platform, RealtimeChannelBase> ably = createAblyRealtime(opts);

            Helpers.ConnectionWaiter connectionWaiter = new Helpers.ConnectionWaiter(ably.connection);
            boolean isConnected = connectionWaiter.waitFor(ConnectionState.connected, 1, 30000L);

            if(isConnected) {
                /* done */
                ably.close();
            } else {
                fail("auth_expired_token_expire_renew: unable to connect; final state = " + ably.connection.state);
            }
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_expired_token_expire_renew: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify that with queryTime=false, when instancing with an already-expired token and authCallback,
     * connection can succeed
     */
    @Test
    public void auth_expired_token_expire_after_connect_renew() {
        try {
            /* get a TokenDetails and allow to expire */
            final String testKey = testVars.keys[0].keyStr;
            ClientOptions optsForToken = createOptions(testKey);
            optsForToken.queryTime = true;
            final AblyBase<PushBase, Platform, RestChannelBase> ablyForToken = createAblyRest(optsForToken);
            TokenDetails tokenDetails = ablyForToken.auth.requestToken(new Auth.TokenParams(){{ ttl = 2000L; }}, null);
            assertNotNull("Expected token value", tokenDetails.token);

            /* create Ably realtime with token and authCallback */
            ClientOptions opts = createOptions();
            opts.tokenDetails = tokenDetails;
            opts.queryTime = false;
            opts.authCallback = new Auth.TokenCallback() {
                /* implement callback, using Ably instance with key */
                @Override
                public Object getTokenRequest(Auth.TokenParams params) throws AblyException {
                    return ablyForToken.auth.requestToken(params, null);
                }
            };

            final AblyRealtimeBase<PushBase, Platform, RealtimeChannelBase> ably = createAblyRealtime(opts);

            Helpers.ConnectionWaiter connectionWaiter = new Helpers.ConnectionWaiter(ably.connection);
            if(connectionWaiter.waitFor(ConnectionState.connected, 2, 4000L)) {
                /* done */
                ably.close();
            } else {
                fail("auth_expired_token_expire_renew: unable to connect; final state = " + ably.connection.state);
            }
        } catch (AblyException e) {
            e.printStackTrace();
            fail("auth_expired_token_expire_renew: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify that instance of ClientOptions cannot change AblyRealtime options once it is provided to constructor
     */
    @Test
    public void auth_client_options_immutable() {
        try {
            /* create token with clientId */
            ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
            optsForToken.clientId = "token_clientId";
            AblyBase<PushBase, Platform, RestChannelBase> ablyForToken = createAblyRest(optsForToken);
            TokenDetails tokenDetails = ablyForToken.auth.requestToken(null, null);

            /* create ably realtime */
            ClientOptions opts = new ClientOptions();
            opts.clientId = null;
            opts.token = tokenDetails.token;
            opts.autoConnect = false;
            opts.headers = new HashMap<>();
            opts.headers.put("old_key", "old_value");
            opts.tokenDetails = new TokenDetails("my_old_details_token");
            opts.logHandler = new Log.DefaultHandler();
            opts.authCallback = new Auth.TokenCallback() {
                @Override
                public Object getTokenRequest(Auth.TokenParams params) {
                    return null;
                }
            };
            opts.authHeaders = new Param[1];
            opts.authHeaders[0] = new Param("old_key", "old_key");
            opts.proxy = new ProxyOptions();
            opts.proxy.host = "https://ably.com";
            opts.proxy.port = 8080;
            AblyRealtimeBase<PushBase, Platform, RealtimeChannelBase> ablyRealtime = createAblyRealtime(opts);

            assertEquals("clientId given for options must be equal to the one in in AblyRealtime before change", opts.clientId, ablyRealtime.options.clientId);
            String origClientId = ablyRealtime.options.clientId;
            opts.clientId = "my_new_clientId";
            assertNotEquals("clientId given for options should not be equal to the one in in AblyRealtime after change", opts.clientId, ablyRealtime.options.clientId);
            assertEquals("clientId in AblyRealtime should be equal to original one after ClientOptions change", origClientId, ablyRealtime.options.clientId);

            assertEquals("logLevel given for options must be equal to the one in in AblyRealtime before change", opts.logLevel, ablyRealtime.options.logLevel);
            int origLogLevel = ablyRealtime.options.logLevel;
            opts.logLevel = 33;
            assertNotEquals("logLevel given for options should not be equal to the one in in AblyRealtime after change", opts.logLevel, ablyRealtime.options.logLevel);
            assertEquals("logLevel in AblyRealtime should be equal to original one after ClientOptions change", origLogLevel, ablyRealtime.options.logLevel);

            assertEquals("autoConnect given for options must be equal to the one in in AblyRealtime before change", opts.autoConnect, ablyRealtime.options.autoConnect);
            boolean origAutoConnect = ablyRealtime.options.autoConnect;
            opts.autoConnect = true;
            assertNotEquals("autoConnect given for options should not be equal to the one in in AblyRealtime after change", opts.autoConnect, ablyRealtime.options.autoConnect);
            assertEquals("autoConnect in AblyRealtime should be equal to original one after ClientOptions change", origAutoConnect, ablyRealtime.options.autoConnect);

            assertEquals("logHandler given for options must be equal to the one in in AblyRealtime before change", opts.logHandler, ablyRealtime.options.logHandler);
            Log.LogHandler origHandler = ablyRealtime.options.logHandler;
            opts.logHandler = new Log.DefaultHandler();
            assertNotEquals("logHandler given for options should not be equal to the one in in AblyRealtime after change", opts.logHandler, ablyRealtime.options.logHandler);
            assertEquals("logHandler in AblyRealtime should be equal to original one after ClientOptions change", origHandler, ablyRealtime.options.logHandler);

            assertEquals("authCallback given for options must be equal to the one in in AblyRealtime before change", opts.authCallback, ablyRealtime.options.authCallback);
            Auth.TokenCallback origTokenCallback = ablyRealtime.options.authCallback;
            opts.authCallback = new Auth.TokenCallback() {
                @Override
                public Object getTokenRequest(Auth.TokenParams params) {
                    return null;
                }
            };
            assertNotEquals("authCallback given for options should not be equal to the one in in AblyRealtime after change", opts.authCallback, ablyRealtime.options.authCallback);
            assertEquals("authCallback in AblyRealtime should be equal to original one after ClientOptions change", origTokenCallback, ablyRealtime.options.authCallback);

            assertEquals("token given for options must be equal to the one in in AblyRealtime before change", opts.token, ablyRealtime.options.token);
            String origToken = ablyRealtime.options.token;
            opts.token = "my_new_token";
            assertNotEquals("token given for options should not be equal to the one in in AblyRealtime after change", opts.token, ablyRealtime.options.token);
            assertEquals("token in AblyRealtime should be equal to original one after ClientOptions change", origToken, ablyRealtime.options.token);

            assertEquals("tokenDetails given for options must be equal to the one in in AblyRealtime before change", opts.tokenDetails, ablyRealtime.options.tokenDetails);
            TokenDetails origTokenDetails = ablyRealtime.options.tokenDetails;
            opts.tokenDetails = new TokenDetails("my_new_details_token");
            assertNotEquals("tokenDetails given for options should not be equal to the one in in AblyRealtime after change", opts.tokenDetails, ablyRealtime.options.tokenDetails);
            assertEquals("tokenDetails in AblyRealtime should be equal to original one after ClientOptions change", origTokenDetails, ablyRealtime.options.tokenDetails);

            assertEquals("headers given for options must be equal to the one in in AblyRealtime before change", opts.headers, ablyRealtime.options.headers);
            Map<String, String> origHeaders = ablyRealtime.options.headers;
            String origHeaderElementValue = origHeaders.entrySet().iterator().next().getValue();
            opts.headers.clear();
            opts.headers.put("new_key", "new_value");
            assertNotEquals("headers given for options should not be equal to the one in in AblyRealtime after change", opts.headers, ablyRealtime.options.headers);
            assertEquals("headers in AblyRealtime should be equal to original one after ClientOptions change", origHeaders, ablyRealtime.options.headers);
            assertEquals("first header in AblyRealtime should be equal to original one after ClientOptions change", origHeaderElementValue, ablyRealtime.options.headers.entrySet().iterator().next().getValue());

            assertArrayEquals("authHeaders given for options must be equal to the one in in AblyRealtime before change", opts.authHeaders, ablyRealtime.options.authHeaders);
            Param[] origParams = ablyRealtime.options.authHeaders;
            String origParamValue = ablyRealtime.options.authHeaders[0].value;
            opts.authHeaders[0] = new Param("new_key", "new_key");
            assertNotEquals("authHeaders given for options should not be equal to the one in in AblyRealtime after change", opts.authHeaders, ablyRealtime.options.authHeaders);
            assertArrayEquals("authHeaders in AblyRealtime should be equal to original one after ClientOptions change", origParams, ablyRealtime.options.authHeaders);
            assertEquals("first authHeader in AblyRealtime should be equal to original one after ClientOptions change", origParamValue, ablyRealtime.options.authHeaders[0].value);

            assertEquals("proxy.port given for options must be equal to the one in in AblyRealtime before change", opts.proxy.port, ablyRealtime.options.proxy.port);
            int origProxyPort = ablyRealtime.options.proxy.port;
            opts.proxy.port = 9090;
            assertNotEquals("proxy.port given for options should not be equal to the one in in AblyRealtime after change", opts.proxy.port, ablyRealtime.options.proxy.port);
            assertEquals("proxy.port in AblyRealtime should be equal to original one after ClientOptions change", origProxyPort, ablyRealtime.options.proxy.port);

            ablyRealtime.close();
        } catch (AblyException e) {
            e.printStackTrace();
            fail();
        }
    }

}
