package io.ably.lib.test.realtime;

import io.ably.lib.debug.DebugOptions;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.ChannelEvent;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.realtime.ChannelStateListener;
import io.ably.lib.realtime.Connection;
import io.ably.lib.realtime.ConnectionEvent;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.realtime.ConnectionStateListener;
import io.ably.lib.rest.Auth.AuthMethod;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.Helpers.ChannelWaiter;
import io.ably.lib.test.common.Helpers.ConnectionWaiter;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.test.util.EmptyPlatformAgentProvider;
import io.ably.lib.test.util.MockWebsocketFactory;
import io.ably.lib.transport.ConnectionManager;
import io.ably.lib.transport.Defaults;
import io.ably.lib.transport.Hosts;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ClientOptions;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Created by gokhanbarisaker on 3/9/16.
 */
public class ConnectionManagerTest extends ParameterizedTest {

    @Rule
    public Timeout testTimeout = Timeout.seconds(60);

    /**
     * <p>
     * Verifies that ably connects to default host,
     * when everything is fine.
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void connectionmanager_fallback_none() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        try (AblyRealtime ably = new AblyRealtime(opts)) {
            ConnectionManager connectionManager = ably.connection.connectionManager;

            new Helpers.ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);

            /* Verify that,
             *   - connectionManager is connected
             *   - connectionManager is connected to the host without any fallback
             */
            assertThat(connectionManager.getConnectionState().state, is(ConnectionState.connected));
            assertThat(connectionManager.getHost(), is(equalTo(opts.environment + "-realtime.ably.io")));
        }
    }

    /**
     * <p>
     * Verifies that fallback behaviour doesn't apply, when the default
     * custom endpoint is being used
     * </p>
     * <p>
     * Spec: RTN17b
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void connectionmanager_fallback_none_customhost() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        opts.realtimeHost = "un.reachable.host.example.com";
        opts.environment = null;
        try(AblyRealtime ably = new AblyRealtime(opts)) {
            ConnectionManager connectionManager = ably.connection.connectionManager;

            new Helpers.ConnectionWaiter(ably.connection).waitFor(ConnectionState.disconnected);

            /* Verify that,
             *   - connectionManager is disconnected
             *   - connectionManager's last host did not have any fallback
             */
            assertThat(connectionManager.getConnectionState().state, is(ConnectionState.disconnected));
            assertThat(connectionManager.getHost(), is(equalTo(opts.realtimeHost)));
        }
    }

    /**
     * <p>
     * Verifies that the {@code ConnectionManager} first checks if an internet connection is
     * available by issuing a GET request to https://internet-up.ably-realtime.com/is-the-internet-up.txt
     * , when In the case of an error necessitating use of an alternative host (see RTN17d).
     * </p>
     * <p>
     * Spec: RTN17c
     * </p>
     *
     * @throws AblyException
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void connectionmanager_fallback_none_withoutconnection() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        opts.realtimeHost = "un.reachable.host";
        opts.environment = null;
        opts.autoConnect = false;
        try(AblyRealtime ably = new AblyRealtime(opts)) {
            Connection connection = Mockito.mock(Connection.class);
            final ConnectionManager.Channels channels = Mockito.mock(ConnectionManager.Channels.class);

            ConnectionManager connectionManager = new ConnectionManager(ably, connection, channels, new EmptyPlatformAgentProvider()) {
                @Override
                protected boolean checkConnectivity() {
                    return false;
                }
            };

            connectionManager.connect();

            new Helpers.ConnectionManagerWaiter(connectionManager).waitFor(ConnectionState.disconnected);

            /* Verify that,
             *   - connectionManager is disconnected
             *   - connectionManager did not apply any fallback behavior
             */
            assertThat(connectionManager.getConnectionState().state, is(ConnectionState.disconnected));
            assertThat(connectionManager.getHost(), is(equalTo(opts.realtimeHost)));

            connectionManager.close();
        }
    }

    /**
     * <p>
     * Verifies that fallback behaviour is applied and HTTP client is using same fallback
     * endpoint, when the default realtime.ably.io endpoint is being used and has not been
     * overriden, and a fallback is applied
     * </p>
     * <p>
     * Spec: RTN17b, RTN17c
     * </p>
     *
     * @throws AblyException
     */
    @Test
    public void connectionmanager_default_fallback_applied() throws AblyException {
        DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
        fillInOptions(opts);

        final Hosts hosts = new Hosts(null, Defaults.HOST_REALTIME, opts);
        final String primaryHost = hosts.getPrimaryHost();

        /* clear the environment override, so we trigger default fallback behaviour */
        opts.environment = null;

        /* set up mock transport */
        MockWebsocketFactory mockTransport = new MockWebsocketFactory();
        opts.transportFactory = mockTransport;

        /* ensure that all connection attempts ultimately resolve to the primary host */
        mockTransport.setHostTransform(new MockWebsocketFactory.HostTransform() {
            @Override
            public String transformHost(String givenHost) {
                return primaryHost;
            }
        });

        /* set up a filter on a mock transport to fail connections to the primary host */
        mockTransport.failConnect(new MockWebsocketFactory.HostFilter() {
            @Override
            public boolean matches(String hostname) {
                return hostname.equals(primaryHost);
            }
        });

        try (AblyRealtime ably = new AblyRealtime(opts)) {
            ConnectionManager connectionManager = ably.connection.connectionManager;

            new Helpers.ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);

            /* Verify that,
             *   - connectionManager is connected
             *   - connectionManager's last host was a fallback host
             */
            assertThat(connectionManager.getConnectionState().state, is(ConnectionState.connected));
            assertThat(connectionManager.getHost(), is(not(equalTo(primaryHost))));
        }
    }

    /**
     * Verify that when environment is overridden, no fallback is used by default
     *
     * <p>
     * Spec: RTN17b
     * </p>
     */
    @Test
    public void connectionmanager_default_endpoint_no_fallback() throws AblyException {
        DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
        fillInOptions(opts);

        final Hosts hosts = new Hosts(null, Defaults.HOST_REALTIME, opts);
        final String primaryHost = hosts.getPrimaryHost();

        MockWebsocketFactory mockTransport = new MockWebsocketFactory();
        opts.transportFactory = mockTransport;

        /* ensure that all connection attempts ultimately resolve to the primary host */
        mockTransport.setHostTransform(new MockWebsocketFactory.HostTransform() {
            @Override
            public String transformHost(String givenHost) {
                return primaryHost;
            }
        });

        /* set up a filter on a mock transport to fail connections to the primary host */
        mockTransport.failConnect(new MockWebsocketFactory.HostFilter() {
            @Override
            public boolean matches(String hostname) {
                return hostname.equals(primaryHost);
            }
        });

        try (AblyRealtime ably = new AblyRealtime(opts)) {
            ConnectionManager connectionManager = ably.connection.connectionManager;

            System.out.println("waiting for disconnected");
            new Helpers.ConnectionWaiter(ably.connection).waitFor(ConnectionState.disconnected);
            System.out.println("got disconnected");

            /* Verify that,
             *   - connectionManager is disconnected
             *   - connectionManager's last host was the primary host
             */
            assertThat(connectionManager.getConnectionState().state, is(ConnectionState.disconnected));
            assertThat(connectionManager.getHost(), is(equalTo(primaryHost)));
        }
    }

    /**
     * Verify that when environment is overridden and fallback specified, the fallback is used
     *
     * <p>
     * Spec: RTN17b
     * </p>
     */
    @Test
    public void connectionmanager_default_endpoint_explicit_fallback() throws AblyException {
        DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
        fillInOptions(opts);

        final Hosts hosts = new Hosts(null, Defaults.HOST_REALTIME, opts);
        final String primaryHost = hosts.getPrimaryHost();

        opts.fallbackHosts = new String[]{"fallback 1", "fallback 2"};

        MockWebsocketFactory mockTransport = new MockWebsocketFactory();
        opts.transportFactory = mockTransport;

        /* ensure that all connection attempts ultimately resolve to the primary host */
        mockTransport.setHostTransform(new MockWebsocketFactory.HostTransform() {
            @Override
            public String transformHost(String givenHost) {
                return primaryHost;
            }
        });

        /* set up a filter on a mock transport to fail connections to the primary host */
        mockTransport.failConnect(new MockWebsocketFactory.HostFilter() {
            @Override
            public boolean matches(String hostname) {
                return hostname.equals(primaryHost);
            }
        });

        try (AblyRealtime ably = new AblyRealtime(opts)) {
            ConnectionManager connectionManager = ably.connection.connectionManager;

            System.out.println("waiting for connected");
            new Helpers.ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);
            System.out.println("got connected");

            /* Verify that,
             *   - connectionManager is connected
             *   - connectionManager's last host was a fallback host
             */
            assertThat(connectionManager.getConnectionState().state, is(ConnectionState.connected));
            assertThat(connectionManager.getHost(), is(not(equalTo(primaryHost))));
        }
    }

    /**
     * Test that default fallback happens with a non-default host if
     * fallbackHostsUseDefault is set.
     */
    @Ignore("FIXME: fix exception")
    @Test
    public void connectionmanager_reconnect_default_fallback() throws AblyException {
        DebugOptions opts = new DebugOptions(testVars.keys[0].keyStr);
        fillInOptions(opts);

        opts.fallbackHostsUseDefault = true;

        final Hosts hosts = new Hosts(null, Defaults.HOST_REALTIME, opts);
        final String primaryHost = hosts.getPrimaryHost();

        MockWebsocketFactory mockTransport = new MockWebsocketFactory();
        opts.transportFactory = mockTransport;

        /* ensure that all connection attempts ultimately resolve to the primary host */
        mockTransport.setHostTransform(new MockWebsocketFactory.HostTransform() {
            @Override
            public String transformHost(String givenHost) {
                return primaryHost;
            }
        });

        /* set up a filter on a mock transport to fail connections to the primary host */
        mockTransport.failConnect(new MockWebsocketFactory.HostFilter() {
            @Override
            public boolean matches(String hostname) {
                return hostname.equals(primaryHost);
            }
        });

        try (AblyRealtime ably = new AblyRealtime(opts)) {
            ConnectionManager connectionManager = ably.connection.connectionManager;

            System.out.println("waiting for connected");
            new Helpers.ConnectionWaiter(ably.connection).waitFor(ConnectionState.connected);
            System.out.println("got connected");
            ably.close();

            /* Verify that,
             *   - connectionManager is connected
             *   - connectionManager's last host was a fallback host
             */
            assertThat(connectionManager.getConnectionState().state, is(ConnectionState.connected));
            assertThat(connectionManager.getHost(), is(not(equalTo(opts.realtimeHost))));
        }
    }

    /**
     * Connect, and then perform a close() from the calling ConnectionManager context;
     * verify that the closed state is reached, and the connectionmanager thread has exited
     */
    @Test
    public void close_from_connectionmanager() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        final AblyRealtime ably = new AblyRealtime(opts);
        final Thread[] threadContainer = new Thread[1];
        ably.connection.on(ConnectionEvent.connected, new ConnectionStateListener() {
            @Override
            public void onConnectionStateChanged(ConnectionStateChange state) {
                ably.close();
                threadContainer[0] = Thread.currentThread();
            }
        });

        /* wait for cm thread to exit */
        try {
            Thread.sleep(2000L);
        } catch(InterruptedException e) {}

        assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
        Thread.State cmThreadState = threadContainer[0].getState();
        assertEquals("Verify cm thread has exited", cmThreadState, Thread.State.TERMINATED);
    }

    /**
     * Connect, and then perform a close();
     * verify that the closed state is reached, and immediately
     * reconnect; verify that it reconnects successfully
     */
    @Test
    public void connectionmanager_restart_race() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        final AblyRealtime ably = new AblyRealtime(opts);
        ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

        ably.connection.once(ConnectionEvent.connected, new ConnectionStateListener() {
            @Override
            public void onConnectionStateChanged(ConnectionStateChange state) {
                ably.close();
            }
        });

        connectionWaiter.waitFor(ConnectionState.closed);
        assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
        connectionWaiter.reset();

        /* reconnect */
        ably.connect();

        /* verify the connection is reestablished */
        connectionWaiter.waitFor(ConnectionState.connected);
        assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);

        /* close the connection */
        ably.close();
        connectionWaiter.waitFor(ConnectionState.closed);
        assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);
    }

    /**
     * Connect, and then perform a close() from the calling ConnectionManager context;
     * verify that the closed state is reached, and the connectionmanager thread has exited
     */
    @Test
    public void open_from_dedicated_thread() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        opts.autoConnect = false;
        final AblyRealtime ably = new AblyRealtime(opts);
        final Thread[] threadContainer = new Thread[1];
        ably.connection.on(ConnectionEvent.connected, new ConnectionStateListener() {
            @Override
            public void onConnectionStateChanged(ConnectionStateChange state) {
                threadContainer[0] = Thread.currentThread();
            }
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(new Runnable() {
            public void run() {
                try {
                    ably.connection.connect();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        });

        ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);

        connectionWaiter.waitFor(ConnectionState.connected);
        assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);
        assertTrue("Not expecting token auth", ably.auth.getAuthMethod() == AuthMethod.basic);

        ably.close();
        connectionWaiter.waitFor(ConnectionState.closed);
        assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);

        /* wait for cm thread to exit */
        try {
            Thread.sleep(2000L);
        } catch(InterruptedException e) {}

        Thread.State cmThreadState = threadContainer[0].getState();
        assertEquals("Verify cm thread has exited", cmThreadState, Thread.State.TERMINATED);
    }

    /**
     * Connect, and then perform a close() from the calling ConnectionManager context;
     * verify that the closed state is reached, and the connectionmanager thread has exited
     */
    @Test
    public void close_from_dedicated_thread() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        opts.autoConnect = false;
        final AblyRealtime ably = new AblyRealtime(opts);
        final Thread[] threadContainer = new Thread[1];
        ably.connection.on(ConnectionEvent.connected, new ConnectionStateListener() {
            @Override
            public void onConnectionStateChanged(ConnectionStateChange state) {
                threadContainer[0] = Thread.currentThread();
            }
        });

        ably.connection.connect();
        final ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
        connectionWaiter.waitFor(ConnectionState.connected);
        assertEquals("Verify connected state is reached", ConnectionState.connected, ably.connection.state);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(new Runnable() {
            public void run() {
                try {
                    ably.close();
                    connectionWaiter.waitFor(ConnectionState.closed);
                    assertEquals("Verify closed state is reached", ConnectionState.closed, ably.connection.state);

                    /* wait for cm thread to exit */
                    try {
                        Thread.sleep(2000L);
                    } catch(InterruptedException e) {}

                    Thread.State cmThreadState = threadContainer[0].getState();
                    assertEquals("Verify cm thread has exited", cmThreadState, Thread.State.TERMINATED);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        });
    }

    /**
     * Connect and then verify that the connection manager has the default value for connectionStateTtl;
     */
    @Test
    public void connection_details_has_ttl() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        try (AblyRealtime ably = new AblyRealtime(opts)) {
            final boolean[] callbackWasRun = new boolean[1];
            ably.connection.on(ConnectionEvent.connected, new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange state) {
                    synchronized(callbackWasRun) {
                        callbackWasRun[0] = true;
                        try {
                            Field field = ably.connection.connectionManager.getClass().getDeclaredField("connectionStateTtl");
                            field.setAccessible(true);
                            assertEquals("Verify connectionStateTtl has the default value", field.get(ably.connection.connectionManager), 120000L);
                        } catch (NoSuchFieldException|IllegalAccessException e) {
                            fail("Unexpected exception in checking connectionStateTtl");
                        }
                        callbackWasRun.notify();
                    }
                }
            });

            synchronized (callbackWasRun) {
                try { callbackWasRun.wait(); } catch(InterruptedException ie) {}
                assertTrue("Connected callback was not run", callbackWasRun[0]);
            }
        }
    }

    /**
     * RTN15g1, RTN15g2. Connect, disconnect, reconnect after (ttl + idle interval) period has passed,
     * check that the connection is a new one;
     */
    @Test
    public void connection_has_new_id_when_reconnecting_after_statettl_plus_idleinterval_has_passed() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        opts.realtimeRequestTimeout = 2000L;
        try(AblyRealtime ably = new AblyRealtime(opts)) {
            final long newTtl = 1000L;
            final long newIdleInterval = 1000L;
            /* We want this greater than newTtl + newIdleInterval */
            final long waitInDisconnectedState = 3000L;

            ably.connection.on(ConnectionEvent.connected, new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange state) {
                    try {
                        Field connectionStateField = ably.connection.connectionManager.getClass().getDeclaredField("connectionStateTtl");
                        connectionStateField.setAccessible(true);
                        connectionStateField.setLong(ably.connection.connectionManager, newTtl);
                        Field maxIdleField = ably.connection.connectionManager.getClass().getDeclaredField("maxIdleInterval");
                        maxIdleField.setAccessible(true);
                        maxIdleField.setLong(ably.connection.connectionManager, newIdleInterval);
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        fail("Unexpected exception in checking connectionStateTtl");
                    }
                }
            });

            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            connectionWaiter.waitFor(ConnectionState.connected);
            final String firstConnectionId = ably.connection.id;

            /* suppress automatic retries by the connection manager and disconnect */
            try {
                Method method = ably.connection.connectionManager.getClass().getDeclaredMethod("disconnectAndSuppressRetries");
                method.setAccessible(true);
                method.invoke(ably.connection.connectionManager);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                fail("Unexpected exception in suppressing retries");
            }
            connectionWaiter.waitFor(ConnectionState.disconnected);
            assertEquals("Disconnected state was not reached", ConnectionState.disconnected, ably.connection.state);

            /* Wait for the connection to go stale, then reconnect */
            try {
                Thread.sleep(waitInDisconnectedState);
            } catch (InterruptedException e) {
            }
            ably.connection.connect();
            connectionWaiter.waitFor(ConnectionState.connected);
            assertEquals("Connected state was not reached", ConnectionState.connected, ably.connection.state);

            /* Verify the connection is new */
            assertNotNull(ably.connection.id);
            assertNotEquals("Connection has the same id", firstConnectionId, ably.connection.id);
        }
    }

    /**
     * RTN15g1, RTN15g2. Connect, disconnect, reconnect before (ttl + idle interval) period has passed,
     * check that the connection is the same;
     */
    @Test
    public void connection_has_same_id_when_reconnecting_before_statettl_plus_idleinterval_has_passed() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        try(AblyRealtime ably = new AblyRealtime(opts)) {
            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            connectionWaiter.waitFor(ConnectionState.connected);
            String firstConnectionId = ably.connection.id;
            ably.connection.connectionManager.requestState(ConnectionState.disconnected);

            /* Wait for a connected state after the disconnection triggered above */
            connectionWaiter.waitFor(ConnectionState.connected);

            String secondConnectionId = ably.connection.id;
            assertNotNull(secondConnectionId);
            assertEquals("connection has the same id", firstConnectionId, secondConnectionId);
        }
    }

    /**
     * RTN15g3. Connect, attach some channels, disconnect, reconnect after (ttl + idle interval) period has passed,
     * check that the client reconnects with a different connection and that the channels attached during the first
     * connection are correctly reattached;
     */
    @Test
    public void channels_are_reattached_after_reconnecting_when_statettl_plus_idleinterval_has_passed() throws AblyException {
        ClientOptions opts = createOptions(testVars.keys[0].keyStr);
        try(AblyRealtime ably = new AblyRealtime(opts)) {
            final long newTtl = 1000L;
            final long newIdleInterval = 1000L;
            /* We want this greater than newTtl + newIdleInterval */
            final long waitInDisconnectedState = 3000L;
            final List<String> attachedChannelHistory = new ArrayList<String>();
            final List<String> expectedAttachedChannelHistory = Arrays.asList("attaching", "attached", "attaching", "attached");
            final List<String> suspendedChannelHistory = new ArrayList<String>();
            final List<String> expectedSuspendedChannelHistory = Arrays.asList("attaching", "attached");
            ably.connection.on(ConnectionEvent.connected, new ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(ConnectionStateChange state) {
                    try {
                        Field connectionStateField = ably.connection.connectionManager.getClass().getDeclaredField("connectionStateTtl");
                        connectionStateField.setAccessible(true);
                        connectionStateField.setLong(ably.connection.connectionManager, newTtl);
                        Field maxIdleField = ably.connection.connectionManager.getClass().getDeclaredField("maxIdleInterval");
                        maxIdleField.setAccessible(true);
                        maxIdleField.setLong(ably.connection.connectionManager, newIdleInterval);
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        fail("Unexpected exception in checking connectionStateTtl");
                    }
                }
            });

            ConnectionWaiter connectionWaiter = new ConnectionWaiter(ably.connection);
            connectionWaiter.waitFor(ConnectionState.connected);
            final String firstConnectionId = ably.connection.id;

            /* Prepare channels */
            final Channel attachedChannel = ably.channels.get("test-reattach-after-ttl" + testParams.name);
            ChannelWaiter attachedChannelWaiter = new Helpers.ChannelWaiter(attachedChannel);
            attachedChannel.on(new ChannelStateListener() {
                @Override
                public void onChannelStateChanged(ChannelStateChange stateChange) {
                    attachedChannelHistory.add(stateChange.current.name());
                }
            });
            final Channel suspendedChannel = ably.channels.get("test-reattach-suspended-after-ttl" + testParams.name);
            suspendedChannel.state = ChannelState.suspended;
            ChannelWaiter suspendedChannelWaiter = new Helpers.ChannelWaiter(suspendedChannel);
            suspendedChannel.on(new ChannelStateListener() {
                @Override
                public void onChannelStateChanged(ChannelStateChange stateChange) {
                    suspendedChannelHistory.add(stateChange.current.name());
                }
            });

            /* attach first channel and wait for it to be attached */
            attachedChannel.attach();
            attachedChannelWaiter.waitFor(ChannelState.attached);

            /* suppress automatic retries by the connection manager and disconnect */
            try {
                Method method = ably.connection.connectionManager.getClass().getDeclaredMethod("disconnectAndSuppressRetries");
                method.setAccessible(true);
                method.invoke(ably.connection.connectionManager);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                fail("Unexpected exception in suppressing retries");
            }
            connectionWaiter.waitFor(ConnectionState.disconnected);
            assertEquals("Disconnected state was not reached", ConnectionState.disconnected, ably.connection.state);

            /* Wait for the connection to go stale, then reconnect */
            try {
                Thread.sleep(waitInDisconnectedState);
            } catch (InterruptedException e) {
            }
            ably.connection.connect();
            connectionWaiter.waitFor(ConnectionState.connected);
            assertEquals("Connected state was not reached", ConnectionState.connected, ably.connection.state);

            /* Verify the connection is new */
            assertNotNull(ably.connection.id);
            assertNotEquals("Connection has the same id", firstConnectionId, ably.connection.id);

            /* Verify that the attached channel is reattached with resumed false */
            attachedChannel.once(ChannelEvent.attached, new ChannelStateListener() {
                @Override
                public void onChannelStateChanged(ChannelStateChange stateChange) {
                    assertEquals("Resumed is true and should be false", stateChange.resumed, false);
                }
            });

            /* Wait for both channels to reattach and verify state histories match the expected ones */
            attachedChannelWaiter.waitFor(ChannelState.attached);
            suspendedChannelWaiter.waitFor(ChannelState.attached);
            assertEquals("Attached channel histories do not match", attachedChannelHistory, expectedAttachedChannelHistory);
            assertEquals("Suspended channel histories do not match", suspendedChannelHistory, expectedSuspendedChannelHistory);
        }
    }
}
