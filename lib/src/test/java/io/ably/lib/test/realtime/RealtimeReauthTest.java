package io.ably.lib.test.realtime;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.ArrayList;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.realtime.ConnectionEvent;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.realtime.ConnectionStateListener;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Auth;
import io.ably.lib.rest.Auth.TokenCallback;
import io.ably.lib.rest.Auth.TokenDetails;
import io.ably.lib.rest.Auth.TokenParams;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.Capability;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.util.AblyError;

/**
 * Created by VOstopolets on 8/26/16.
 */
public class RealtimeReauthTest extends ParameterizedTest {

    @Rule
    public Timeout testTimeout = Timeout.seconds(90);

    /**
     * RTC8a: In-place reauthorization on a connected connection.
     * RTC8a1: A test should exist that performs an upgrade of
     * capabilities without any loss of continuity or connectivity
     * during the upgrade process.
     */
    @Test
    public void reauth_tokenDetails() {
        String wrongChannel = "wrongchannel";
        String rightChannel = "rightchannel";
        String testClientId = "testClientId";

        try {
            /* init ably for token */
            ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
            final AblyRest ablyForToken = new AblyRest(optsForToken);

            /* get first token */
            Auth.TokenParams tokenParams = new Auth.TokenParams();
            Capability capability = new Capability();
            capability.addResource(wrongChannel, "*");
            tokenParams.capability = capability.toString();
            tokenParams.clientId = testClientId;

            Auth.TokenDetails firstToken = ablyForToken.auth.requestToken(tokenParams, null);
            assertNotNull("Expected token value", firstToken.token);

            /* create ably realtime with tokenDetails and clientId */
            ClientOptions opts = createOptions();
            opts.clientId = testClientId;
            opts.tokenDetails = firstToken;
            AblyRealtime ablyRealtime = new AblyRealtime(opts);

            /* wait for connected state */
            Helpers.ConnectionWaiter connectionWaiter = new Helpers.ConnectionWaiter(ablyRealtime.connection);
            connectionWaiter.waitFor(ConnectionState.connected);
            assertEquals("Verify connected state is reached", ConnectionState.connected, ablyRealtime.connection.state);

            /* create a channel and check can't attach */
            Channel channel = ablyRealtime.channels.get(rightChannel);
            Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
            channel.attach(waiter);
            ErrorInfo error = waiter.waitFor();
            assertNotNull("Expected error", error);
            assertEquals("Verify error code 40160 (channel is denied access)", error.code, AblyError.OPERATION_NOT_PERMITTED_WITH_PROVIDED_CAPABILITY);

            /* get second token */
            tokenParams = new Auth.TokenParams();
            capability = new Capability();
            capability.addResource(wrongChannel, "*");
            capability.addResource(rightChannel, "*");
            tokenParams.capability = capability.toString();
            tokenParams.clientId = testClientId;

            Auth.TokenDetails secondToken = ablyForToken.auth.requestToken(tokenParams, null);
            assertNotNull("Expected token value", secondToken.token);

            /* reauthorize */
            connectionWaiter.reset();
            Auth.AuthOptions authOptions = new Auth.AuthOptions();
            authOptions.key = testVars.keys[0].keyStr;
            authOptions.tokenDetails = secondToken;
            Auth.TokenDetails reauthTokenDetails = ablyRealtime.auth.authorize(null, authOptions);
            assertNotNull("Expected token value", reauthTokenDetails.token);

            /* re-attach to the channel */
            waiter = new Helpers.CompletionWaiter();
            channel.attach(waiter);

            /* verify onSuccess callback gets called */
            waiter.waitFor();
            assertThat(waiter.success, is(true));
            /* Verify that the connection never disconnected (0.9 in-place authorization) */
            assertTrue("Expected in-place authorization", connectionWaiter.getCount(ConnectionState.connecting) == 0);

            ablyRealtime.close();
        } catch (AblyException e) {
            e.printStackTrace();
            fail();
        }
    }

    /**
    /* RTC8a1: Another test should exist where the capabilities are
     * downgraded resulting in Ably sending an ERROR ProtocolMessage
     * with a channel property, causing the channel to enter the FAILED
     * state. That test must assert that the channel becomes failed
     * soon after the token update and the reason is included in the
     * channel state change event.
     */
    @Test
    public void reauth_downgrade() {
        String wrongChannel = "wrongchannel";
        String rightChannel = "rightchannel";
        String testClientId = "testClientId";

        try {
            /* init ably for token */
            ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
            final AblyRest ablyForToken = new AblyRest(optsForToken);

            /* get first (good) token */
            Auth.TokenParams tokenParams = new Auth.TokenParams();
            Capability capability = new Capability();
            capability.addResource(wrongChannel, "*");
            capability.addResource(rightChannel, "*");
            tokenParams.capability = capability.toString();
            tokenParams.clientId = testClientId;
            Auth.TokenDetails firstToken = ablyForToken.auth.requestToken(tokenParams, null);
            assertNotNull("Expected token value", firstToken.token);

            /* create ably realtime with tokenDetails and clientId */
            ClientOptions opts = createOptions();
            opts.clientId = testClientId;
            opts.tokenDetails = firstToken;
            AblyRealtime ablyRealtime = new AblyRealtime(opts);

            /* wait for connected state */
            Helpers.ConnectionWaiter connectionWaiter = new Helpers.ConnectionWaiter(ablyRealtime.connection);
            connectionWaiter.waitFor(ConnectionState.connected);
            assertEquals("Verify connected state is reached", ConnectionState.connected, ablyRealtime.connection.state);

            /* create a channel and check attached */
            Channel channel = ablyRealtime.channels.get(rightChannel);
            Helpers.ChannelWaiter waiter = new Helpers.ChannelWaiter(channel);
            channel.attach();
            /* verify onSuccess callback gets called */
            waiter.waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* get second (bad) token */
            tokenParams = new Auth.TokenParams();
            capability = new Capability();
            capability.addResource(wrongChannel, "*");
            tokenParams.capability = capability.toString();
            tokenParams.clientId = testClientId;
            Auth.TokenDetails secondToken = ablyForToken.auth.requestToken(tokenParams, null);
            assertNotNull("Expected token value", secondToken.token);

            /* reauthorize */
            connectionWaiter.reset();
            Auth.AuthOptions authOptions = new Auth.AuthOptions();
            authOptions.tokenDetails = secondToken;
            Auth.TokenDetails reauthTokenDetails = ablyRealtime.auth.authorize(null, authOptions);
            assertNotNull("Expected token value", reauthTokenDetails.token);

            /* Check that the channel moves to failed state within 2s, and that
             * we get the expected error code. */
            long before = System.currentTimeMillis();
            ErrorInfo err = waiter.waitFor(ChannelState.failed);
            assertEquals("Verify failed state reached", channel.state, ChannelState.failed);
            assertEquals("Verify error code", err.code, AblyError.OPERATION_NOT_PERMITTED_WITH_PROVIDED_CAPABILITY);
            assertTrue("Expected channel to fail quickly", System.currentTimeMillis() - before < 2000);

            ablyRealtime.close();
        } catch (AblyException e) {
            e.printStackTrace();
            fail();
        }
    }

    /**
    /* RTC8a2: If the authentication token change fails, then Ably will send an
     * ERROR ProtocolMessage triggering the connection to transition to the
     * FAILED state. A test should exist for a token change that fails (such as
     * sending a new token with an incompatible clientId)
     */
    @Test
    public void reauth_fail() {
        String rightChannel = "rightchannel";
        String testClientId = "testClientId";
        String badClientId = "badClientId";

        try {
            /* init ably for token */
            ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
            final AblyRest ablyForToken = new AblyRest(optsForToken);

            /* get first (good) token */
            Auth.TokenParams tokenParams = new Auth.TokenParams();
            Capability capability = new Capability();
            capability.addResource(rightChannel, "*");
            tokenParams.capability = capability.toString();
            tokenParams.clientId = testClientId;
            Auth.TokenDetails firstToken = ablyForToken.auth.requestToken(tokenParams, null);
            assertNotNull("Expected token value", firstToken.token);

            /* create ably realtime with tokenDetails and clientId */
            ClientOptions opts = createOptions();
            opts.clientId = testClientId;
            opts.tokenDetails = firstToken;
            AblyRealtime ablyRealtime = new AblyRealtime(opts);

            /* wait for connected state */
            Helpers.ConnectionWaiter connectionWaiter = new Helpers.ConnectionWaiter(ablyRealtime.connection);
            connectionWaiter.waitFor(ConnectionState.connected);
            assertEquals("Verify connected state is reached", ConnectionState.connected, ablyRealtime.connection.state);

            /* create a channel and check attached */
            Channel channel = ablyRealtime.channels.get(rightChannel);
            Helpers.ChannelWaiter waiter = new Helpers.ChannelWaiter(channel);
            channel.attach();
            /* verify onSuccess callback gets called */
            waiter.waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* get second (bad) token */
            tokenParams.clientId = badClientId;
            Auth.TokenDetails secondToken = ablyForToken.auth.requestToken(tokenParams, null);
            /* revert client id in token details, otherwise it will be blocked by the client library */
            secondToken.clientId = testClientId;
            assertNotNull("Expected token value", secondToken.token);

            /* Reauthorize. We expect this to throw an exception from the
             * mismatched client id and end up in failed state. */
            connectionWaiter.reset();
            Auth.AuthOptions authOptions = new Auth.AuthOptions();
            authOptions.tokenDetails = secondToken;
            try {
                Auth.TokenDetails reauthTokenDetails = ablyRealtime.auth.authorize(null, authOptions);
                assertFalse("Expecting exception", true);
                System.out.println("Authorize failed to throw an exception");
            } catch (AblyException e) {
                assertEquals("Expecting failed", ConnectionState.failed, ablyRealtime.connection.state);
                System.out.println("Got failed connection");
            }

            /**
             * RTC8c: If the connection is in the DISCONNECTED, SUSPENDED, FAILED, or
             * CLOSED state when auth#authorize is called, after obtaining a token the
             * library should move to the CONNECTING state and initiate a connection
             * attempt using the new token, and RTC8b1 applies.
             */

            /* Reauthorize with good token. We expect this to connect. */
            connectionWaiter.reset();
            authOptions = new Auth.AuthOptions();
            authOptions.tokenDetails = firstToken;
            Auth.TokenDetails reauthTokenDetails = ablyRealtime.auth.authorize(null, authOptions);
            assertNotNull("Expected token value", reauthTokenDetails.token);
            connectionWaiter.waitFor(ConnectionState.connected);
            assertEquals("Verify connected state is reached", ConnectionState.connected, ablyRealtime.connection.state);

            ablyRealtime.close();
        } catch (AblyException e) {
            e.printStackTrace();
            fail();
        }
    }

    /**
     * RSA4c
     *  If authorize fails we should get the event for the failure
     */
    @Test
    public void reauth_failure_test() {
        String testClientId = "testClientId";

        try {
            final ArrayList<ConnectionStateListener.ConnectionStateChange> stateChangeHistory = new ArrayList<>();

            /* init ably for token */
            ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
            final AblyRest ablyForToken = new AblyRest(optsForToken);

            /* get first token */
            Auth.TokenParams tokenParams = new Auth.TokenParams();
            tokenParams.clientId = testClientId;
            Auth.TokenDetails firstToken = ablyForToken.auth.requestToken(tokenParams, null);
            assertNotNull("Expected token value", firstToken.token);

            /* create ably realtime with tokenDetails and clientId */
            ClientOptions opts = createOptions();
            opts.clientId = testClientId;
            opts.tokenDetails = firstToken;
            AblyRealtime ablyRealtime = new AblyRealtime(opts);

            ablyRealtime.connection.on(new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange state) {
                    synchronized (stateChangeHistory) {
                        stateChangeHistory.add(state);
                        stateChangeHistory.notify();
                    }
                }
            });

            /* wait for connected state */
            synchronized (stateChangeHistory) {
                while (ablyRealtime.connection.state != ConnectionState.connected) {
                    try { stateChangeHistory.wait(); } catch (InterruptedException e) {}
                }
            }

            assertEquals("Verify connected state is reached", ConnectionState.connected, ablyRealtime.connection.state);

            int stateChangeCount = stateChangeHistory.size();

            /* fail getting the second token */
            /* reauthorize and fail */
            Auth.AuthOptions authOptions = new Auth.AuthOptions();
            authOptions.key = testVars.keys[0].keyStr;
            authOptions.authUrl = "https://nonexistent-domain-abcdef.com";
            try {
                ablyRealtime.auth.authorize(null, authOptions);
                // should not succeed
                fail();
            }
            catch (AblyException e) {
                // nothing
            }

            /* wait for new entries in state change history */
            synchronized (stateChangeHistory) {
                while (stateChangeHistory.size() <= stateChangeCount) {
                    try { stateChangeHistory.wait(); } catch (InterruptedException e) {}
                }

                /* should stay in connected state, errorInfo should indicate authentication non-fatal error */
                ConnectionStateListener.ConnectionStateChange lastChange = stateChangeHistory.get(stateChangeHistory.size()-1);
                assertEquals("Verify connection stayed in connected state", lastChange.current, ConnectionState.connected);
                assertEquals("Verify authentication failure error code", lastChange.reason.code, AblyError.CLIENT_AUTH_REQUEST_FAILED);
            }

            ablyRealtime.close();
        } catch (AblyException e) {
            e.printStackTrace();
            fail();
        }
    }

    /**
     * Verify that the server issues reauth message 30 seconds before token expiration time, authCallback is
     * called to obtain new token and in-place re-authorization takes place with connection staying in connected
     * state. Also tests if UPDATE event is delivered on the connection
     *
     * Test for RTN4h, RTC8a1, RTN24 features
     */
    @Test
    public void reauth_token_expire_inplace_reauth() {
        try {
            ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
            final AblyRest ablyForToken = new AblyRest(optsForToken);
            /* Server will send reauth message 30 seconds before token expiration time i.e. in 4 seconds */
            TokenDetails tokenDetails = ablyForToken.auth.requestToken(new TokenParams() {{ ttl = 34000L; }}, null);
            assertNotNull("Expected token value", tokenDetails.token);

            final boolean[] flags = new boolean[] {
                    false, /* authCallback is called */
                    false, /* state other than connected is reached */
                    false  /* update event was delivered */
            };

            /* create Ably realtime instance without key */
            ClientOptions opts = createOptions();
            opts.tokenDetails = tokenDetails;
            opts.authCallback = new TokenCallback() {
                /* implement callback, using Ably instance with key */
                @Override
                public Object getTokenRequest(TokenParams params) throws AblyException {
                    synchronized (flags) {
                        flags[0] = true;
                    }
                    return ablyForToken.auth.requestToken(params, null);
                }
            };
            AblyRealtime ably = new AblyRealtime(opts);

            /* Test UPDATE event delivery */
            ably.connection.on(ConnectionEvent.update, new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange state) {
                    flags[2] = true;
                }
            });
            ably.connection.on(new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange state) {
                    if (state.previous == ConnectionState.connected && state.current != ConnectionState.connected) {
                        synchronized (flags) {
                            flags[1] = true;
                            flags.notify();
                        }
                    }
                }
            });

            synchronized (flags) {
                try {
                    flags.wait(8000);
                } catch (InterruptedException e) {}
            }

            assertTrue("Verify token generation was called", flags[0]);
            assertFalse("Verify connection didn't leave connected state", flags[1]);
            assertTrue("Verify UPDATE event was delivered", flags[2]);

            ably.close();
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify that the server issues reauth message 30 seconds before token expiration time of a token =
     * derived from a locally-supplied key, authCallback is called to obtain new token and in-place
     * re-authorization takes place with connection staying in connected
     * state. Also tests if UPDATE event is delivered on the connection
     *
     * Test for RTN4h, RTC8a1, RTN24 features
     */
    @Test
    public void reauth_key_expire_inplace_reauth() {
        try {
            final boolean[] flags = new boolean[] {
                    false, /* state other than connected is reached */
                    false  /* update event was delivered */
            };

            /* create Ably realtime instance with key */
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.clientId = "testClientId";
            opts.useTokenAuth = true;
            opts.defaultTokenParams.ttl = 34000L;
            AblyRealtime ably = new AblyRealtime(opts);

            /* Test UPDATE event delivery */
            ably.connection.on(ConnectionEvent.update, new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange state) {
                    flags[1] = true;
                }
            });
            ably.connection.on(new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange state) {
                    if (state.previous == ConnectionState.connected && state.current != ConnectionState.connected) {
                        synchronized (flags) {
                            flags[0] = true;
                            flags.notify();
                        }
                    }
                }
            });

            synchronized (flags) {
                try {
                    flags.wait(8000);
                } catch (InterruptedException e) {}
            }

            assertFalse("Verify connection didn't leave connected state", flags[0]);
            assertTrue("Verify UPDATE event was delivered", flags[1]);

            ably.close();
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        }
    }
}
