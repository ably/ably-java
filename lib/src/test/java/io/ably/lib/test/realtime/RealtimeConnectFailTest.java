package io.ably.lib.test.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.ably.lib.debug.DebugOptions;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.realtime.ChannelStateListener;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.realtime.ConnectionEvent;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.realtime.ConnectionStateListener;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Auth;
import io.ably.lib.rest.Auth.TokenCallback;
import io.ably.lib.rest.Auth.TokenDetails;
import io.ably.lib.rest.Auth.TokenParams;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.Helpers.ConnectionWaiter;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.test.util.MockWebsocketFactory;
import io.ably.lib.transport.Defaults;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.ProtocolMessage;

public class RealtimeConnectFailTest extends ParameterizedTest {

    @Rule
    public Timeout testTimeout = Timeout.seconds(300);

    /**
     * Verify that the connection enters the failed state, after attempting
     * to connect with invalid app
     * Spec: RTN4f
     */
    @Test
    public void connect_fail_notfound_error() throws AblyException {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions("not_an_app.invalid_key_id:invalid_key_value");
            ably = new AblyRealtime(opts);
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

            ErrorInfo fail = connectionWaiter.waitFor(ConnectionState.failed);
            assertEquals("Verify failed state is reached", ConnectionState.failed, ably.connection.state);
            // assertEquals("Verify correct error code is given", 404, fail.statusCode);
        } finally {
            ably.close();
        }
    }

    /**
     * Verify that the connection enters the failed state, after attempting
     * to connect with invalid key
     */
    @Test
    public void connect_fail_authorized_error() throws AblyException {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.appId + ".invalid_key_id:invalid_key_value");
            ably = new AblyRealtime(opts);
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

            ErrorInfo fail = connectionWaiter.waitFor(ConnectionState.failed);
            assertEquals("Verify failed state is reached", ConnectionState.failed, ably.connection.state);
            assertEquals("Verify correct error code is given", 401, fail.statusCode);
        } finally {
            ably.close();
        }
    }

    /**
     * Verify that the connection enters the disconnected state, after attempting
     * to connect to a non-existent ws host
     */
    @Test
    public void connect_fail_disconnected() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        opts.realtimeHost = "non.existent.host";
        opts.environment = null;
        AblyRealtime ably = new AblyRealtime(opts);
        ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

        connectionWaiter.waitFor(ConnectionState.disconnected);
        assertEquals("Verify disconnected state is reached", ConnectionState.disconnected, ably.connection.state);
        ably.close();
        connectionWaiter.waitFor(ConnectionState.closed);
        assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
    }

    /**
     * Verify that the connection enters the suspended state, after multiple attempts
     * to connect to a non-existent ws host
     */
    @Test
    public void connect_fail_suspended() {
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.realtimeHost = "non.existent.host";
            opts.environment = null;
            AblyRealtime ably = new AblyRealtime(opts);
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

            connectionWaiter.waitFor(ConnectionState.suspended);
            /* Wait 1s to force bug where it changes to disconnected right after
             * changing to suspended. Without this it fails only intermittently
             * when that bug is present. */
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {}
            assertEquals("Verify suspended state is reached", ConnectionState.suspended, ably.connection.state);
            assertTrue("Verify multiple connect attempts", connectionWaiter.getCount(ConnectionState.connecting) > 1);
            assertTrue("Verify multiple connect attempts", connectionWaiter.getCount(ConnectionState.disconnected) > 1);
            ably.close();
            connectionWaiter.waitFor(ConnectionState.closed);
            assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify that the connection in the disconnected state (after attempts to
     * connect to a non-existent ws host) allows an immediate explicit connect
     * attempt, instead of ignoring the explicit connect and waiting till the
     * next scheduled retry.
     */
    @Test
    public void connect_while_disconnected() {
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.realtimeHost = "non.existent.host";
            opts.environment = null;
            AblyRealtime ably = new AblyRealtime(opts);
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

            connectionWaiter.waitFor(ConnectionState.disconnected);
            assertEquals("Verify disconnected state is reached", ConnectionState.disconnected, ably.connection.state);

            long before = System.currentTimeMillis();
            ably.connection.connect();
            connectionWaiter.waitFor(ConnectionState.connecting);
            assertTrue("Verify explicit connect is actioned immediately", System.currentTimeMillis() - before < 1000L);

            ably.close();
            connectionWaiter.waitFor(ConnectionState.closed);
            assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify that the connection enters the disconnected state, after a token
     * used for successful connection expires
     */
    @Test
    public void connect_token_expire_disconnected() {
        try {
            final ClientOptions optsForToken = createOptions(testVars.keys[0].keyStr);
            final AblyRest ablyForToken = new AblyRest(optsForToken);
            Auth.AuthOptions restAuthOptions = new Auth.AuthOptions() {{
                key = optsForToken.key;
                queryTime = true;
            }};
            TokenDetails tokenDetails = ablyForToken.auth.requestToken(new TokenParams() {{ ttl = 8000L; }}, restAuthOptions);
            assertNotNull("Expected token value", tokenDetails.token);

            /* implement callback, using Ably instance with key */
            final class TokenGenerator implements TokenCallback {
                @Override
                public Object getTokenRequest(TokenParams params) throws AblyException {
                    ++cbCount;
                    return ablyForToken.auth.requestToken(params, null);
                }
                public int getCbCount() { return cbCount; }
                private int cbCount = 0;
            };

            TokenGenerator authCallback = new TokenGenerator();

            /* create Ably realtime instance without key */
            ClientOptions opts = createOptions();
            opts.tokenDetails = tokenDetails;
            opts.authCallback = authCallback;
            AblyRealtime ably = new AblyRealtime(opts);

            ably.connection.on(new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange state) {
                    System.out.println(state.current);
                }
            });

            /* wait for connected state */
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            connectionWaiter.waitFor(ConnectionState.connected);

            /* wait for disconnected state (on token expiry), with timeout */
            connectionWaiter.waitFor(ConnectionState.disconnected, 1, 30000L);

            /* wait for connected state (on token renewal) */
            connectionWaiter.waitFor(ConnectionState.connected, 2, 30000L);

            /* verify that our token generator was called */
            assertEquals("Expected token generator to be called", 1, authCallback.getCbCount());

            /* end */
            ably.close();
            connectionWaiter.waitFor(ConnectionState.closed);
            assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
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
    public void connect_token_expire_inplace_reauth() {
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

        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        }
    }

    /**
     * Verify that the connection fails when attempting to recover with a
     * malformed connection id
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void connect_invalid_recover_fail() {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.recover = "not_a_valid_connection_id:99";
            ably = new AblyRealtime(opts);
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            ErrorInfo fail = connectionWaiter.waitFor(ConnectionState.failed);
            assertEquals("Verify failed state is reached", ConnectionState.failed, ably.connection.state);
            assertEquals("Verify correct error code is given", 80018, fail.code);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            ably.close();
        }
    }

    /**
     * Verify that the connection creates a new connection but reports
     * a recovery error, when attempting to recover with an unknown
     * connection id
     */
    @Test
    public void connect_unknown_recover_fail() {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            String recoverConnectionId = "0123456789abcdef-99";
            opts.recover = recoverConnectionId + ":0";
            ably = new AblyRealtime(opts);
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            ErrorInfo connectedError = connectionWaiter.waitFor(ConnectionState.connected);
            assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);
            assertNotNull("Verify error is returned", connectedError);
            assertEquals("Verify correct error code is given", 80008, connectedError.code);
            assertFalse("Verify new connection id is assigned", recoverConnectionId.equals(ably.connection.key));
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            ably.close();
        }
    }

    /**
     * Test that connection manager correctly fails messages set stored in message queue
     * Spec: RTN7c
     */

    @Test
    public void connect_test_queued_messages_on_failure() {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);
            final int[] numberOfErrors = new int[]{0};

            // assume we are in connecting state now
            ably.connection.connectionManager.send(new ProtocolMessage(), true, new CompletionListener() {
                @Override
                public void onSuccess() {
                    fail("Unexpected success sending message");
                }

                @Override
                public void onError(ErrorInfo reason) {
                    numberOfErrors[0]++;
                }
            });

            // transition to suspended state failing messages
            ably.connection.connectionManager.requestState(ConnectionState.suspended);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
            }
            // transition once more to ensure onError() won't be called twice
            ably.connection.connectionManager.requestState(ConnectionState.closed);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
            }

            // onError() should be called only once
            assertEquals("Verifying number of onError() calls", numberOfErrors[0], 1);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception");
        } finally {
            if (ably != null)
                ably.close();
        }
    }

    /**
     * Allow token to expire and try to authorize with already expired token after that. Test that the connection state
     * is changed in the correct way and without duplicates:
     *
     * connecting -> connected -> disconnected -> connecting -> disconnected -> connecting -> disconnected
     */
    @Test
    public void connect_reauth_failure_state_flow_test() {

        try {
            AblyRest ablyRest = null;
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);

            ablyRest = new AblyRest(opts);
            final TokenDetails tokenDetails = ablyRest.auth.requestToken(new TokenParams() {{ ttl = 8000L; }}, null);
            assertNotNull("Expected token value", tokenDetails.token);

            final ArrayList<ConnectionState> stateHistory = new ArrayList<>();

            ClientOptions optsForRealtime = createOptions();
            optsForRealtime.authCallback = new TokenCallback() {
                @Override
                public Object getTokenRequest(TokenParams params) throws AblyException {
                    // return already expired token
                    return tokenDetails;
                }
            };
            optsForRealtime.tokenDetails = tokenDetails;
            final AblyRealtime ablyRealtime = new AblyRealtime(optsForRealtime);

            ablyRealtime.connection.on(ConnectionState.connected, new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange state) {
                    /* To go quicker into a disconnected state we use a
                     * smaller value for maxIdleInterval
                     */
                    try {
                        Field field = ablyRealtime.connection.connectionManager.getClass().getDeclaredField("maxIdleInterval");
                        field.setAccessible(true);
                        field.setLong(ablyRealtime.connection.connectionManager, 5000L);
                    } catch (NoSuchFieldException|IllegalAccessException e) {
                        fail("Unexpected exception in checking connectionStateTtl");
                    }
                }
            });

            (new ConnectionWaiter(ablyRealtime.connection)).waitFor(ConnectionState.connected);
            // TODO: improve by collecting and testing also auth attempts
            final List<ConnectionState> correctHistory = Arrays.asList(
                    ConnectionState.disconnected,
                    ConnectionState.connecting,
                    ConnectionState.disconnected,
                    ConnectionState.connecting,
                    ConnectionState.disconnected
            );
            final int maxDisconnections = 3;
            ablyRealtime.connection.on(new ConnectionStateListener() {
                int disconnections = 0;
                @Override
                public void onConnectionStateChanged(ConnectionStateChange state) {
                    synchronized (stateHistory) {
                        stateHistory.add(state.current);
                        if (state.current == ConnectionState.disconnected) {
                            disconnections++;
                            if (disconnections == maxDisconnections) {
                                assertTrue("Verifying state change history", stateHistory.equals(correctHistory));
                                ablyRealtime.close();
                            }
                        }
                    }
                }
            });

            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ablyRealtime.connection);
            connectionWaiter.waitFor(ConnectionState.closed);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        }
    }

    /**
     * Throw exception in authCallback repeatedly and check if connection goes into suspended state
     */
    @Test
    public void connect_auth_failure_and_suspend_test() {
        AblyRealtime ablyRealtime = null;
        AblyRest ablyRest = null;
        int oldDisconnectTimeout = Defaults.TIMEOUT_DISCONNECT;

        try {
            /* Make test faster */
            Defaults.TIMEOUT_DISCONNECT = 1000;

            final int[] numberOfAuthCalls = new int[] {0};
            final boolean[] reachedFinalState = new boolean[] {false};

            ClientOptions opts = createOptions(testVars.keys[0].keyStr);

            ablyRest = new AblyRest(opts);
            final TokenDetails tokenDetails = ablyRest.auth.requestToken(new TokenParams() {{ ttl = 5000L; }}, null);
            assertNotNull("Expected token value", tokenDetails.token);

            ClientOptions optsForRealtime = createOptions();
            optsForRealtime.authCallback = new TokenCallback() {
                @Override
                public Object getTokenRequest(TokenParams params) throws AblyException {
                    if (numberOfAuthCalls[0]++ == 0)
                        return tokenDetails;
                    else
                        throw AblyException.fromErrorInfo(new ErrorInfo("Auth failure", 90000));
                }
            };
            ablyRealtime = new AblyRealtime(optsForRealtime);

            ablyRealtime.connection.on(new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange state) {
                    System.out.format(Locale.ROOT, "New state: %s\n", state.current);
                    synchronized (reachedFinalState) {
                        reachedFinalState[0] = state.current == ConnectionState.closed ||
                                state.current == ConnectionState.suspended ||
                                state.current == ConnectionState.failed;
                        reachedFinalState.notify();
                    }
                }
            });

            synchronized (reachedFinalState) {
                while (!reachedFinalState[0]) {
                    try { reachedFinalState.wait(); } catch (InterruptedException e) {}
                }
            }

            assertEquals("Verify suspended state", ablyRealtime.connection.state, ConnectionState.suspended);
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            Defaults.TIMEOUT_DISCONNECT = oldDisconnectTimeout;
            if (ablyRealtime != null)
                ablyRealtime.close();
        }
    }

    /**
     * Connect to unknown host and check if timer time is jittered
     * Spec: RTB1
     */
    @Test
    public void disconnect_retry_connection_timeout_jitter() {
        int oldDisconnectTimeout = Defaults.TIMEOUT_DISCONNECT;
        int disconnectedRetryTimeout = 150;
        Defaults.TIMEOUT_DISCONNECT = 150;
        AblyRealtime ably = null;

        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.realtimeHost = "non.existent.host";
            opts.environment = null;
            ably = new AblyRealtime(opts);

            final AtomicInteger retryCount = new AtomicInteger(0);
            final ArrayList<Long> retryValues = new ArrayList();

            ably.connection.on(new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange state) {
                    System.out.println("onConnectionStateChanged current state is: " + state.current.name() + " previous state was: " + state.previous.name());
                    if (state.previous == ConnectionState.connecting && state.current == ConnectionState.disconnected) {
                        System.out.println("onConnectionStateChanged retry count is: " + retryCount.get());
                        if (retryCount.get() > 4) {
                            System.out.println("onConnectionStateChanged retry is successful and done!");
                            return;
                        }
                        retryCount.incrementAndGet();
                        retryValues.add(state.retryIn);
                    }
                }
            });

            int waitAtMost = 5 * 10; //5 seconds * 10 times per second
            int waitCount = 0;
            while (retryCount.get() < 4 && waitCount < waitAtMost) {
                try {
                    Thread.sleep(100);
                    waitCount++;
                } catch (InterruptedException e) {
                    fail(e.getMessage());
                }
            }
            System.out.println("wait done in: " + (waitCount / 10) + " seconds");

            assertTrue("Disconnect retry was not finished, count was: " + retryCount.get(), retryCount.get() >= 4);

            //check for all received retry times in onConnectionStateChanged callback
            System.out.println("------------------------------------------------------------");
            for (int i = 0; i < retryValues.size(); i++) {
                long retryTime = retryValues.get(i);
                long higherRange = disconnectedRetryTimeout + Math.min(i, 3) * 50L;
                double lowerRange = 0.6 * disconnectedRetryTimeout + Math.min(i, 3) * 50L;

                System.out.println("higher range: " + higherRange + " - lower range: " + lowerRange + " | checked value: " + retryTime);

                assertTrue("retry time higher range for count " + i + " is not in valid: " + retryTime + " expected: " + higherRange,
                    retryTime < higherRange);
                assertTrue("retry time lower range for count " + i + " is not in valid: " + retryTime + " expected: " + lowerRange,
                    retryTime > lowerRange);
            }
            System.out.println("------------------------------------------------------------");
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            Defaults.TIMEOUT_DISCONNECT = oldDisconnectTimeout;
            if (ably != null)
                ably.close();
        }
    }

    /**
     * Connect and check if timer time is jittered
     * Spec: RTB1
     */
    @Test
    public void disconnect_retry_channel_timeout_jitter() {
        long oldRealtimeTimeout = Defaults.realtimeRequestTimeout;
        int channelRetryTimeout = 150;
        /* Reduce timeout for test to run faster */
        Defaults.realtimeRequestTimeout = channelRetryTimeout;
        AblyRealtime ably = null;
        final String channelName = "failed_attach";
        final int errorCode = 12345;

        try {
            DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
            fillInOptions(opts);
            opts.channelRetryTimeout = channelRetryTimeout;
            opts.realtimeRequestTimeout = 1L;

            /* Mock transport to block send */
            final MockWebsocketFactory mockTransport = new MockWebsocketFactory();
            opts.transportFactory = mockTransport;
            mockTransport.allowSend();

            ably = new AblyRealtime(opts);
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            connectionWaiter.waitFor(ConnectionState.connected);

            Channel channel = ably.channels.get(channelName);
            Helpers.ChannelWaiter channelWaiter = new Helpers.ChannelWaiter(channel);
            channel.attach();
            channelWaiter.waitFor(ChannelState.attached);

            /* Block send() */
            mockTransport.blockSend();

            final AtomicInteger retryCount = new AtomicInteger(0);
            final ArrayList<Long> retryValues = new ArrayList();
            AtomicLong lastSuspended = new AtomicLong(System.currentTimeMillis());

            channel.on(new ChannelStateListener() {
                @Override
                public void onChannelStateChanged(ChannelStateChange stateChange) {
                    //System.out.println("onChannelStateChanged current state is: " + stateChange.current.name());
                    if (stateChange.current == ChannelState.suspended) {
                        if (retryCount.get() > 6) {
                            System.out.println("onConnectionStateChanged retry is successful and done!");
                            return;
                        }
                        long elapsedSinceSuspended = System.currentTimeMillis() - lastSuspended.get();
                        lastSuspended.set(System.currentTimeMillis());
                        retryValues.add(elapsedSinceSuspended);
                        retryCount.incrementAndGet();
                    }
                }
            });

            /* Inject detached message as if from the server */
            ProtocolMessage detachedMessage = new ProtocolMessage() {{
                action = Action.detached;
                channel = channelName;
                error = new ErrorInfo("Test error", errorCode);
            }};
            ably.connection.connectionManager.onMessage(null, detachedMessage);

            /* wait for the client reattempt attachment */
            channelWaiter.waitFor(ChannelState.attaching);

            /* Inject detached+error message as if from the server */
            ProtocolMessage errorMessage = new ProtocolMessage() {{
                action = Action.detached;
                channel = channelName;
                error = new ErrorInfo("Test error", errorCode);
            }};
            ably.connection.connectionManager.onMessage(null, errorMessage);

            int waitAtMost = 5 * 10; //5 seconds * 10 times per second
            int waitCount = 0;
            while (retryCount.get() < 6 && waitCount < waitAtMost) {
                try {
                    Thread.sleep(100);
                    waitCount++;
                } catch (InterruptedException e) {
                    fail(e.getMessage());
                }
            }
            System.out.println("wait done in: " + (waitCount / 10) + " seconds");

            mockTransport.allowSend();

            assertTrue("Disconnect retry was not finished, count was: " + retryCount.get(), retryCount.get() >= 6);

            System.out.println("------------------------------------------------------------");
            //check for all received retry times in onChannelStateChanged callback
            //ignore first one as it is immediately done and the second one as it is close to our calculation
            for (int i = 2; i < retryValues.size(); i++) {
                long retryTime = retryValues.get(i);
                long higherRange = channelRetryTimeout + Math.min(i, 3) * 50L * (i + 1);
                double lowerRange = 0.6 * channelRetryTimeout + Math.min(i, 3) * 50;
                System.out.println("higher range: " + higherRange + " - lower range: " + lowerRange + " | checked value: " + retryTime);

                assertTrue("retry time higher range for count " + i + " is not in valid: " + retryTime + " expected: " + higherRange,
                    retryTime < higherRange);
                assertTrue("retry time lower range for count " + i + " is not in valid: " + retryTime + " expected: " + lowerRange,
                    retryTime > lowerRange);
            }
            System.out.println("------------------------------------------------------------");
        } catch (AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if (ably != null)
                ably.close();
            /* Restore default values to run other tests */
            Defaults.realtimeRequestTimeout = oldRealtimeTimeout;
        }
    }

}
