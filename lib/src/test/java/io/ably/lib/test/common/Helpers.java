package io.ably.lib.test.common;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.ably.lib.debug.DebugOptions.RawHttpListener;
import io.ably.lib.debug.DebugOptions.RawProtocolListener;
import io.ably.lib.http.HttpCore;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.Channel.MessageListener;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.realtime.ChannelStateListener;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.realtime.Connection;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.realtime.ConnectionStateListener;
import io.ably.lib.realtime.Presence.PresenceListener;
import io.ably.lib.transport.ConnectionManager;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.BaseMessage;
import io.ably.lib.types.Callback;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.ErrorResponse;
import io.ably.lib.types.Message;
import io.ably.lib.types.PresenceMessage;
import io.ably.lib.types.ProtocolMessage;
import io.ably.lib.types.ProtocolMessage.Action;
import io.ably.lib.util.Base64Coder;
import io.ably.lib.util.Log;
import io.ably.lib.util.Serialisation;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class Helpers {

    public static <T> void assertArrayUnorderedEquals(T[] expected, T[] got) {
        Set<T> expectedSet = new CopyOnWriteArraySet<T>(Arrays.asList(expected));
        Set<T> gotSet = new CopyOnWriteArraySet<T>(Arrays.asList(got));
        assertEquals(expectedSet, gotSet);
    }

    public static <T> T expectedError(AblyFunction<Void, T> f, String expectedError) {
        return expectedError(f, expectedError, 0);
    }

    public static <T> T expectedError(AblyFunction<Void, T> f, String expectedError, int expectedStatusCode) {
        return expectedError(f, expectedError, expectedStatusCode, 0);
    }

    public static <T> T expectedError(AblyFunction<Void, T> f, String expectedError, int expectedStatusCode, int expectedCode) {
        try {
            T result = f.apply(null);
            assertEquals(null, expectedError);
            return result;
        } catch (AblyException e) {
            try {
                assertNotNull(String.format("got error \"%s\", none expected", e.errorInfo.message), expectedError);
                assertEquals(String.format("expected to match \"%s\", got \"%s\"", expectedError, e.errorInfo.message), true, Pattern.compile(expectedError).matcher(e.errorInfo.message).find());
                if (expectedCode > 0) {
                    assertEquals(expectedCode, e.errorInfo.code);
                }
                if (expectedStatusCode > 0) {
                    assertEquals(expectedStatusCode, e.errorInfo.statusCode);
                }
            } catch(AssertionError ae) {
                e.printStackTrace();
                throw ae;
            }
        }
        return null;
    }

    public static void assertInstanceOf(Class<?> c, Object o) {
        assertTrue(String.format("expected object of class %s to be instance of %s", o.getClass().getName(), c.getName()), c.isInstance(o));
    }

    public static void assertSize(int expected, Collection<?> c) {
        int size = c.size();
        assertEquals(String.format("expected collection to have size %d, got %d: %s", expected, size, c), expected, size);
    }

    public static <T> void assertSize(int expected, T[] c) {
        int size = c.length;
        assertEquals(String.format("expected array to have size %d, got %d: %s", expected, size, c), expected, size);
    }

    public static HttpCore.Response httpResponseFromErrorInfo(final ErrorInfo errorInfo) {
        HttpCore.Response response = new HttpCore.Response();
        response.contentType = "application/json";
        response.statusCode = errorInfo.statusCode > 0 ? errorInfo.statusCode : 400;
        response.body = Serialisation.gson.toJson(new ErrorResponse() {{
            error = errorInfo;
        }}, ErrorResponse.class).getBytes();
        return response;
    }

    public static String tokenFromAuthHeader(String authHeader) {
        if (!authHeader.startsWith("Bearer ")) {
            return null;
        }

        String token64 = authHeader.substring("Bearer ".length());
        String token = Base64Coder.decodeString(token64);

        return token;
    }

    /**
     * Trivial container for an int as counter.
     * @author paddy
     *
     */
    private static class Counter {
        public int value;
        public int incr() { return ++value; }
    }

    /**
     * A class that may be passed as a listener for completion
     * of an async operation.
     * @author paddy
     *
     */
    public static class CompletionWaiter implements CompletionListener {
        public boolean success;
        public int successCount;
        public ErrorInfo error;

        /**
         * Public API
         */
        public CompletionWaiter() {
            reset();
        }

        public void reset() {
            success = false;
            successCount = 0;
            error = null;
        }

        public synchronized ErrorInfo waitFor(int count) {
            while(successCount<count && error == null)
                try { wait(); } catch(InterruptedException e) {}
            success = successCount >= count;
            return error;
        }

        public synchronized ErrorInfo waitFor() {
            return waitFor(1);
        }

        /**
         * CompletionListener methods
         */
        @Override
        public void onSuccess() {
            synchronized(this) {
                successCount++;
                notifyAll();
            }
        }
        @Override
        public void onError(ErrorInfo reason) {
            synchronized(this) {
                error = reason;
                notifyAll();
            }
        }
    }

    /**
     * A class that subscribes to a channel and tracks messages received.
     * @author paddy
     *
     */
    public static class MessageWaiter implements MessageListener {
        public List<Message> receivedMessages;

        /**
         * Public API
         */

        /**
         * Track all messages on a channel.
         * @param channel
         */
        public MessageWaiter(Channel channel) {
            reset();
            try {
                channel.subscribe(this);
            } catch(AblyException e) {}
        }

        /**
         * Track messages on a channel with a given event name
         * @param channel
         * @param event
         */
        public MessageWaiter(Channel channel, String event) {
            reset();
            try {
                channel.subscribe(event, this);
            } catch(AblyException e) {}
        }

        /**
         * Wait for a given number of messages
         * @param count
         */
        public synchronized void waitFor(int count) {
            while(receivedMessages.size() < count)
                try { wait(); } catch(InterruptedException e) {}
        }

        /**
         * Wait for a given interval for a number of messages
         * @param count
         */
        public synchronized void waitFor(int count, long time) {
            long targetTime = System.currentTimeMillis() + time;
            long remaining = time;
            while(receivedMessages.size() < count && remaining > 0) {
                try { wait(remaining); } catch(InterruptedException e) {}
                remaining = targetTime - System.currentTimeMillis();
            }
        }

        /**
         * Reset the counter. Waiters will continue to
         * wait, and will be unblocked when the revised count
         * meets their requirements.
         */
        public synchronized void reset() {
            receivedMessages = new ArrayList<Message>();
        }

        /**
         * MessageListener interface
         */
        @Override
        public void onMessage(Message message) {
            synchronized(this) {
                receivedMessages.add(message);
                notify();
            }
        }
    }

    /**
     * A class that tracks presence events on a channel
     * @author paddy
     *
     */
    public static class PresenceWaiter implements PresenceListener {
        public List<PresenceMessage> receivedMessages;

        /**
         * Public API
         * @param channel
         */
        public PresenceWaiter(Channel channel) {
            reset();
            try {
                channel.presence.subscribe(this);
            } catch(AblyException e) {}
        }

        public PresenceWaiter(PresenceMessage.Action event, Channel channel) throws AblyException {
            reset();
            channel.presence.subscribe(event, this);
        }

        public PresenceWaiter(EnumSet<PresenceMessage.Action> events, Channel channel) throws AblyException {
            reset();
            channel.presence.subscribe(events, this);
        }

        /**
         * Wait for a given count of any type of message
         * @param count
         */
        public synchronized void waitFor(int count) {
            while(receivedMessages.size() < count)
                try { wait(); } catch(InterruptedException e) {}
        }

        /**
         * Wait for a given clientId.
         * @param clientId
         */
        public synchronized void waitFor(String clientId) {
            while(contains(clientId) == null)
                try { wait(); } catch(InterruptedException e) {}
        }

        /**
         * Wait for a given count of messages for a given clientId
         * having a given action.
         * @param clientId
         * @param action
         */
        public synchronized void waitFor(String clientId, PresenceMessage.Action action) {
            while(contains(clientId, action) == null)
                try { wait(); } catch(InterruptedException e) {}
        }

        /**
         * Reset the counter. Waiters will continue to
         * wait, and will be unblocked when the revised count
         * meets their requirements.
         */
        public synchronized void reset() {
            receivedMessages = new ArrayList<PresenceMessage>();
        }

        /**
         * PresenceListener API
         */
        @Override
        public void onPresenceMessage(PresenceMessage message) {
            synchronized(this) {
                receivedMessages.add(message);
                notify();
            }
        }

        /**
         * Internal
         */
        PresenceMessage contains(String clientId) {
            for(PresenceMessage message : receivedMessages)
                if(clientId.equals(message.clientId))
                    return message;
            return null;
        }
        public PresenceMessage contains(String clientId, PresenceMessage.Action action) {
            for(PresenceMessage message : receivedMessages)
                if(clientId.equals(message.clientId) && action == message.action)
                    return message;
            return null;
        }
        public PresenceMessage contains(String clientId, String connectionId, PresenceMessage.Action action) {
            for(PresenceMessage message : receivedMessages)
                if(clientId.equals(message.clientId) && connectionId.equals(message.connectionId) && action == message.action)
                    return message;
            return null;
        }
    }

    /**
     * A class that listens for state change events on a connection.
     * @author paddy
     *
     */
    public static class ConnectionWaiter implements ConnectionStateListener {

        /**
         * Public API
         */
        public ConnectionWaiter(Connection connection) {
            reset();
            this.connection = connection;
            connection.on(this);
        }

        /**
         * Wait for a given state to be reached.
         * @param state
         * @return error info
         */
        public synchronized ErrorInfo waitFor(ConnectionState state) {
            String targetStateName = state.getConnectionEvent().name();
            Log.d(TAG, "waitFor(state=" + targetStateName + ")");
            while (currentState() != state) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }
            Log.d(TAG, "waitFor done: state=" + targetStateName + ")");
            return reason;
        }

        /**
         * Wait for a given state to be reached a given number of times.
         * @param state
         * @param count
         */
        public synchronized void waitFor(ConnectionState state, int count) {
            Log.d(TAG, "waitFor(state=" + state.getConnectionEvent().name() + ", count=" + count + ")");

            while(getStateCount(state) < count)
                try { wait(); } catch(InterruptedException e) {}
            Log.d(TAG, "waitFor done: state=" + latestChange.current.getConnectionEvent().name() + ", count=" + getStateCount(state) + ")");
        }

        /**
         * Wait for a given state to be reached a given number of times, with a
         * timeout
         * @param state
         * @param count
         * @param time timeout in ms
         * @return true if state was reached
         */
        public synchronized boolean waitFor(ConnectionState state, int count, long time) {
            Log.d(TAG, "waitFor(state=" + state.getConnectionEvent().name() + ", count=" + count + ", time=" + time + ")");
            long targetTime = System.currentTimeMillis() + time;
            long remaining = time;
            while(getStateCount(state) < count && remaining > 0) {
                Log.d(TAG, "waitFor(state=" + state.getConnectionEvent().name() + ", waiting for=" + remaining + ")");
                try { wait(remaining); } catch(InterruptedException e) {}
                remaining = targetTime - System.currentTimeMillis();
            }
            int stateCount = getStateCount(state);
            if(remaining <= 0) {
                Log.d(TAG, "waitFor timed out: current state=" + currentState().getConnectionEvent().name());
            } else {
                Log.d(TAG, "waitFor done: state=" + currentState().getConnectionEvent().name() +
                        ", count=" + Integer.toString(stateCount));
            }
            return stateCount >= count;
        }

        /**
         * Get the count of number of times visited for a given state.
         * @param state
         * @return
         */
        public synchronized int getCount(ConnectionState state) {
            Counter counter = stateCounts.get(state);
            if (counter == null)
                return 0;
            return counter.value;
        }

        /**
         * Reset counters. Waiters will continue to
         * wait, and will be unblocked when the revised count
         * meets their requirements.
         */
        public synchronized void reset() {
            Log.d(TAG, "reset()");
            stateCounts = new HashMap<ConnectionState, Counter>();
        }

        /**
         * ConnectionStateListener interface
         */
        @Override
        public void onConnectionStateChanged(ConnectionStateListener.ConnectionStateChange state) {
            synchronized(this) {
                latestChange = state;
                reason = state.reason;
                Counter counter = stateCounts.get(state.current); if(counter == null) stateCounts.put(state.current, (counter = new Counter()));
                counter.incr();
                Log.d(TAG, "onConnectionStateChanged(" + state.current + "): count now " + counter.value);
                if(state.current == ConnectionState.connected) {
                    Log.d(TAG, "onConnectionStateChanged(connected): count now " + counter.value);
                }
                notify();
            }
        }

        /**
         * Helper function
         */
        private synchronized int getStateCount(ConnectionState state) {
            Counter counter = stateCounts.get(state);
            return counter != null ? counter.value : 0;
        }

        private synchronized ConnectionState currentState() {
            return latestChange == null ? connection.state : latestChange.current;
        }

        /**
         * Internal
         */
        private Connection connection;
        private ErrorInfo reason;
        private ConnectionStateChange latestChange;
        private Map<ConnectionState, Counter> stateCounts;
        private static final String TAG = ConnectionWaiter.class.getName();
    }

    /**
     * A class that listens for state change events on a {@code ConnectionManager}.
     */
    public static class ConnectionManagerWaiter {
        private static final long INTERVAL_POLLING = 1000;

        /**
         * Public API
         */
        public ConnectionManagerWaiter(ConnectionManager connectionManager) {
            this.connectionManager = connectionManager;
        }

        /**
         * Wait for a given state to be reached.
         * @param state
         * @return error info
         */
        public synchronized ErrorInfo waitFor(ConnectionState state) {
            while(connectionManager.getConnectionState().state != state)
                try { wait(INTERVAL_POLLING); } catch(InterruptedException e) {}
            return connectionManager.getConnectionState().defaultErrorInfo;
        }

        /**
         * Internal
         */
        private ConnectionManager connectionManager;
    }

    /**
     * A class that listens for state change events on a channel.
     * @author paddy
     *
     */
    public static class ChannelWaiter implements ChannelStateListener {
        private static final String TAG = ChannelWaiter.class.getName();

        /**
         * Public API
         * @param channel
         */
        public ChannelWaiter(Channel channel) {
            this.channel = channel;
            channel.on(this);
        }

        /**
         * Wait for a given state to be reached.
         * @param state
         */
        public synchronized ErrorInfo waitFor(ChannelState state) {
            Log.d(TAG, "waitFor(" + state + ")");
            while(channel.state != state)
                try { wait(); } catch(InterruptedException e) {}
            Log.d(TAG, "waitFor done: " + channel.state + ", " + channel.reason + ")");
            return channel.reason;
        }

        /**
         * ChannelStateListener interface
         */
        @Override
        public void onChannelStateChanged(ChannelStateListener.ChannelStateChange stateChange) {
            synchronized(this) { notify(); }
        }

        /**
         * Internal
         */
        private Channel channel;
    }

    /**
     * A class that listens for raw protocol messages sent and received on a realtime connection.
     *
     */
    public static class RawProtocolMonitor implements RawProtocolListener {
        public Action sendAction;
        public Action recvAction;
        public String connectUrl;
        public List<ProtocolMessage> sentMessages;
        public List<ProtocolMessage> receivedMessages;

        /**
         * Public API
         */
        public static RawProtocolMonitor createReceiver(Action recvAction) {
            return new RawProtocolMonitor(null, recvAction);
        }

        public static RawProtocolMonitor createSender(Action sendAction) {
            return new RawProtocolMonitor(sendAction, null);
        }

        public static RawProtocolMonitor createMonitor(Action sendAction, Action recvAction) {
            return new RawProtocolMonitor(sendAction, recvAction);
        }

        /**
         * Wait for a given number of messages
         */
        public void waitForRecv() {
            waitForRecv(1);
        }
        public void waitForSend() {
            waitForSend(1);
        }

        /**
         * Wait for a given number of messages
         * @param count
         */
        public synchronized void waitForRecv(int count) {
            while(receivedMessages.size() < count) {
                try { wait(); } catch(InterruptedException e) {}
            }
        }
        public synchronized void waitForSend(int count) {
            while(sentMessages.size() < count) {
                try { wait(); } catch(InterruptedException e) {}
            }
        }

        /**
         * Reset the counter. Waiters will continue to
         * wait, and will be unblocked when the revised count
         * meets their requirements.
         */
        public synchronized void reset() {
            sentMessages = new ArrayList<ProtocolMessage>();
            receivedMessages = new ArrayList<ProtocolMessage>();
        }


        /**
         * RawProtocolListener interface
         */
        @Override
        public void onRawMessageRecv(ProtocolMessage message) {
            if(message.action == recvAction) {
                synchronized(this) {
                    receivedMessages.add(message);
                    notify();
                }
            }
        }

        @Override
        public void onRawMessageSend(ProtocolMessage message) {
            if(message.action == sendAction) {
                synchronized(this) {
                    sentMessages.add(message);
                    notify();
                }
            }
        }

        @Override
        public void onRawConnectRequested(String url) {}

        @Override
        public void onRawConnect(String url) {
            connectUrl = url;
        }

        private RawProtocolMonitor(Action sendAction, Action recvAction) {
            this.sendAction = sendAction;
            this.recvAction = recvAction;
            reset();
        }
    }

    /**
     * A class that allows a series of async operations to be
     * tracked, and allows a caller to wait for all to complete.
     * @author paddy
     *
     */
    public static class CompletionSet {
        public Set<Member> pending = new HashSet<Member>();
        public Set<ErrorInfo> errors = new HashSet<ErrorInfo>();

        /**
         * Member type. A Member instance exists for each of
         * the operations added to a CompletionSet.
         * @author paddy
         *
         */
        public class Member implements CompletionListener {
            @Override
            public void onSuccess() {
                synchronized(CompletionSet.this) {
                    pending.remove(this);
                    CompletionSet.this.notifyAll();
                }
            }
            @Override
            public void onError(ErrorInfo reason) {
                synchronized(CompletionSet.this) {
                    pending.remove(this);
                    errors.add(reason);
                    CompletionSet.this.notifyAll();
                }
            }
        }

        /**
         * Obtain a new member/listener to associate with an
         * operation to be added to this set.
         * @return
         */
        public Member add() {
            Member member = new Member();
            synchronized(CompletionSet.this) { pending.add(member); }
            return member;
        }

        /**
         * Wait for all members to complete.
         * @return an array of errors.
         */
        public ErrorInfo[] waitFor() {
            synchronized(CompletionSet.this) {
                while(pending.size() > 0)
                    try { CompletionSet.this.wait(); } catch(InterruptedException e) {}
            }
            return errors.toArray(new ErrorInfo[errors.size()]);
        }
    }

    public static void assertMessagesEqual(BaseMessage expected, BaseMessage actual) {
        assertEquals("Encodings differ.", expected.encoding, actual.encoding);
        assertEquals("Message classes differ.", expected.getClass(), actual.getClass());
        assertEquals("Message data classes differ.", expected.data.getClass(), actual.data.getClass());
        if(expected.data instanceof byte[]) {
            assertArrayEquals("Message binary data contents differ.", (byte[])expected.data, (byte[])actual.data);
        } else if (expected.data instanceof JsonObject || expected.data instanceof JsonArray) {
            Gson gson = new Gson();
            assertEquals("Message JSON contents differ.", gson.toJson((JsonElement)expected.data), gson.toJson((JsonElement)actual.data));
        } else {
            assertEquals("Message data contents differ.", expected.data, actual.data);
        }
    }

    public static class AsyncWaiter<T> implements Callback<T> {
        @Override
        public synchronized void onSuccess(T result) {
            this.result = result;
            gotResult = true;
            notify();
        }

        @Override
        public synchronized void onError(ErrorInfo error) {
            this.error = error;
            gotResult = true;
            notify();
        }

        public synchronized void waitFor() {
            try {
                while(!gotResult) {
                    wait();
                }
            } catch(InterruptedException e) {}
        }

        public T result;
        public ErrorInfo error;
        private boolean gotResult = false;
    }

    public static boolean equalStrings(String one, String two) {
        return one != null && one.equals(two);
    }

    public static boolean equalNullableStrings(String one, String two) {
        return (one == null) ? (two == null) : one.equals(two);
    }

    public static class RawHttpRequest {
        public String id;
        public URL url;
        public HttpURLConnection conn;
        public String method;
        public String authHeader;
        public Map<String, List<String>> requestHeaders;
        public HttpCore.RequestBody requestBody;
        public HttpCore.Response response;
        public Throwable error;
    }

    public static class RawHttpTracker extends LinkedHashMap<String, RawHttpRequest> implements RawHttpListener {
        private static final long serialVersionUID = 1L;
        private boolean locked = false;
        public HttpCore.Response mockResponse = null;
        private AsyncWaiter<RawHttpRequest> requestWaiter = null;

        @Override
        public HttpCore.Response onRawHttpRequest(String id, HttpURLConnection conn, String method, String authHeader, Map<String, List<String>> requestHeaders,
                                                  HttpCore.RequestBody requestBody) {

            /* duplicating if necessary, ensure lower-case versions of header names are present */
            Map<String, List<String>> normalisedHeaders = new HashMap<String, List<String>>();
            if(requestHeaders != null) {
                normalisedHeaders.putAll(requestHeaders);
                for(String header : requestHeaders.keySet()) {
                    normalisedHeaders.put(header.toLowerCase(), requestHeaders.get(header));
                }
            }
            RawHttpRequest req = new RawHttpRequest();
            req.id = id;
            req.url = conn.getURL();
            req.conn = conn;
            req.method = method;
            req.authHeader = authHeader;
            req.requestHeaders = normalisedHeaders;
            req.requestBody = requestBody;
            put(id, req);

            if (requestWaiter != null) {
                AsyncWaiter<RawHttpRequest> w = requestWaiter;
                requestWaiter = null;
                w.onSuccess(req);
            }

            while (true) {
                boolean l;
                synchronized (this) {
                    l = locked;
                }
                if (!l) {
                    break;
                }
                try {
                    synchronized (this) {
                        wait();
                    }
                } catch (InterruptedException e) {}
            }

            HttpCore.Response response = mockResponse;
            mockResponse = null;
            return response;
        }

        @Override
        public void onRawHttpResponse(String id, String method, HttpCore.Response response) {
            /* duplicating if necessary, ensure lower-case versions of header names are present */
            Map<String, List<String>> headers = response.headers;
            Map<String, List<String>> normalisedHeaders = new HashMap<String, List<String>>();
            if(headers != null) {
                normalisedHeaders.putAll(headers);
                for(String header : headers.keySet()) {
                    normalisedHeaders.put(header.toLowerCase(), headers.get(header));
                }
                response.headers = normalisedHeaders;
            }

            RawHttpRequest req = get(id);
            if(req != null) {
                req.response = response;
            }
        }

        @Override
        public void onRawHttpException(String id, String method, Throwable t) {
            RawHttpRequest req = get(id);
            if(req != null) {
                req.error = t;
            }
        }

        public RawHttpRequest getFirstRequest() {
            Collection<RawHttpRequest> reqs = values();
            return (RawHttpRequest) reqs.toArray()[0];
        }

        public RawHttpRequest getLastRequest() {
            Collection<RawHttpRequest> reqs = values();
            return (RawHttpRequest) reqs.toArray()[reqs.size() - 1];
        }

        public String getRequestParam(String id, String param) {
            String result = null;
            RawHttpRequest req = get(id);
            if(req != null) {
                String query = req.conn.getURL().getQuery();
                if(query != null && !query.isEmpty()) {
                    result = HttpUtils.decodeParams(query).get(param).value;
                }
            }
            return result;
        }

        public List<String> getRequestHeader(String id, String header) {
            List<String> result = null;
            RawHttpRequest req = get(id);
            if(req != null) {
                header = header.toLowerCase();
                if(header.equalsIgnoreCase("authorization")) {
                    result = Collections.singletonList(req.authHeader);
                } else {
                    result = req.requestHeaders.get(header);
                }
            }
            return result;
        }

        public List<String> getResponseHeader(String id, String header) {
            List<String> result = null;
            RawHttpRequest req = get(id);
            if(req != null) {
                header = header.toLowerCase();
                List<String>headers = req.response.headers.get(header);
                if(headers != null && headers.size() > 0) {
                    result = headers;
                }
            }
            return result;
        }

        public void lockRequests() {
            synchronized (this) {
                locked = true;
            }
        }

        public void unlockRequests() {
            synchronized (this) {
                locked = false;
                notifyAll();
            }
        }

        public AsyncWaiter<RawHttpRequest> getRequestWaiter() {
            requestWaiter = new AsyncWaiter<>();
            return requestWaiter;
        }
    }

    public static class RandomGenerator {

        private static Random random = new Random((new Date()).getTime());
        private static final char[] values = {'a','b','c','d','e','f','g','h','i','j',
                'k','l','m','n','o','p','q','r','s','t',
                'u','v','w','x','y','z','0','1','2','3',
                '4','5','6','7','8','9'};

        public static String generateRandomString(int length) {
            char[] chars = new char[length];
            for (int i = 0; i < length; i++) {
                int idx = random.nextInt(values.length);
                chars[i] = values[idx];
            }
            return new String(chars);
        }

        public static byte[] generateRandomBuffer(int length) {
            byte[] buf = new byte[length];
            for (int i = 0; i < length; i++) {
                int idx = random.nextInt(256);
                buf[i] = (byte)(idx - 128);
            }
            return buf;
        }
    }

    public abstract static class SyncAndAsync<Arg, T> {
        public abstract T getSync(Arg arg) throws AblyException;
        public abstract void getAsync(Arg arg, Callback<T> callback);
        public abstract void then(AblyFunction<Arg, T> get) throws AblyException;

        public void run() throws AblyException {
            then(new AblyFunction<Arg, T>() {
                @Override
                public T apply(Arg arg) throws AblyException {
                    return getSync(arg);
                }
            });

            then(new AblyFunction<Arg, T>() {
                @Override
                public T apply(Arg arg) throws AblyException {
                    AsyncWaiter<T> callback = new AsyncWaiter<T>();
                    getAsync(arg, callback);
                    callback.waitFor();
                    if (callback.error != null) {
                        throw AblyException.fromErrorInfo(callback.error);
                    }
                    return callback.result;
                }
            });
        }
    }

    public interface AblyFunction<Arg, Result> {
        Result apply(Arg arg) throws AblyException;
    }
}
