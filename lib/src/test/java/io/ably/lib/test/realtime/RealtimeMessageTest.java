package io.ably.lib.test.realtime;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.ably.lib.types.MessageExtras;
import io.ably.lib.util.Serialisation;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import io.ably.lib.http.Http;
import io.ably.lib.http.HttpCore;
import io.ably.lib.http.HttpHelpers;
import io.ably.lib.http.HttpScheduler;
import io.ably.lib.http.HttpUtils;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.test.common.Helpers.ChannelWaiter;
import io.ably.lib.test.common.Helpers.CompletionSet;
import io.ably.lib.test.common.Helpers.CompletionWaiter;
import io.ably.lib.test.common.Helpers.ConnectionWaiter;
import io.ably.lib.test.common.Helpers.MessageWaiter;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.test.common.Setup;
import io.ably.lib.transport.ConnectionManager;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.Callback;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.ErrorInfo;
import io.ably.lib.types.Message;
import io.ably.lib.types.ProtocolMessage;
import io.ably.lib.util.Log;

public class RealtimeMessageTest extends ParameterizedTest {

    private static final String testMessagesEncodingFile = "ably-common/test-resources/messages-encoding.json";
    private static Gson gson = new Gson();

    @Rule
    public Timeout testTimeout = Timeout.seconds(300);

    /**
     * Connect to the service and attach, subscribe to an event, and publish on that channel
     */
    @Test
    public void single_send() {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);

            /* create a channel */
            final Channel channel = ably.channels.get("subscribe_send_binary");

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* subscribe */
            MessageWaiter messageWaiter = new MessageWaiter(channel);

            /* publish to the channel */
            CompletionWaiter msgComplete = new CompletionWaiter();
            channel.publish("test_event", "Test message (subscribe_send_binary)", msgComplete);

            /* wait for the publish callback to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.success);

            /* wait for the subscription callback to be called */
            messageWaiter.waitFor(1);
            assertEquals("Verify message subscription was called", messageWaiter.receivedMessages.size(), 1);

        } catch(AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Connect to the service on two connections;
     * attach, subscribe to an event, publish on one
     * connection and confirm receipt on the other.
     */
    @Test
    public void single_send_noecho() {
        AblyRealtime txAbly = null;
        AblyRealtime rxAbly = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.echoMessages = false;
            txAbly = new AblyRealtime(opts);
            rxAbly = new AblyRealtime(opts);
            String channelName = "subscribe_send_binary_noecho";

            /* create a channel */
            final Channel txChannel = txAbly.channels.get(channelName);
            final Channel rxChannel = rxAbly.channels.get(channelName);

            /* attach both connections */
            txChannel.attach();
            (new ChannelWaiter(txChannel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", txChannel.state, ChannelState.attached);
            rxChannel.attach();
            (new ChannelWaiter(rxChannel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", rxChannel.state, ChannelState.attached);

            /* subscribe on both connections */
            MessageWaiter txMessageWaiter = new MessageWaiter(txChannel);
            MessageWaiter rxMessageWaiter = new MessageWaiter(rxChannel);

            /* publish to the channel */
            CompletionWaiter msgComplete = new CompletionWaiter();
            txChannel.publish("test_event", "Test message (subscribe_send_binary_noecho)", msgComplete);

            /* wait for the publish callback to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.success);

            /* wait for the subscription callback to be called */
            rxMessageWaiter.waitFor(1);
            assertEquals("Verify rx message subscription was called", rxMessageWaiter.receivedMessages.size(), 1);

            /* wait to verify that the subscription callback is not called on txConnection */
            txMessageWaiter.waitFor(1, 1000L);
            assertEquals("Verify tx message subscription was not called", txMessageWaiter.receivedMessages.size(), 0);

        } catch(AblyException e) {
            e.printStackTrace();
            fail("single_send_binary_noecho: Unexpected exception instantiating library");
        } finally {
            if(txAbly != null)
                txAbly.close();
            if(rxAbly != null)
                rxAbly.close();
        }
    }

    /**
     * Get a channel and subscribe without explicitly attaching.
     * Verify that the channel reaches the attached state.
     */
    @Test
    public void subscribe_implicit_attach() {
        AblyRealtime ably = null;
        String channelName = "subscribe_implicit_attach_" + testParams.name;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* subscribe */
            MessageWaiter messageWaiter = new MessageWaiter(channel);

            /* verify attached state is reached */
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* publish to the channel */
            CompletionWaiter msgComplete = new CompletionWaiter();
            channel.publish("test_event", "Test message (" + channelName + ")", msgComplete);

            /* wait for the publish callback to be called */
            msgComplete.waitFor();
            assertTrue("Verify success callback was called", msgComplete.success);

            /* wait for the subscription callback to be called */
            messageWaiter.waitFor(1);
            assertEquals("Verify message subscription was called", messageWaiter.receivedMessages.size(), 1);

        } catch(AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Connect to the service using the default (binary) protocol
     * and attach, subscribe to an event, and publish multiple
     * messages on that channel
     */
    private void _multiple_send(String channelName, int messageCount, int msgSize, boolean binary, long delay) {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* subscribe */
            MessageWaiter messageWaiter = new MessageWaiter(channel);

            /* publish to the channel */
            CompletionSet msgComplete = new CompletionSet();
            if(binary) {
                byte[][] messagesSent = new byte[messageCount][];
                for(int i = 0; i < messageCount; i++) {
                    byte[] messageData = messagesSent[i] = Helpers.RandomGenerator.generateRandomBuffer(msgSize);
                    channel.publish("test_event", messageData, msgComplete.add());
                    try {
                        Thread.sleep(delay);
                    } catch(InterruptedException e) {
                    }
                }

                /* wait for the publish callback to be called */
                ErrorInfo[] errors = msgComplete.waitFor();
                assertTrue("Verify success from all message callbacks", errors.length == 0);

                /* wait for the subscription callback to be called */
                messageWaiter.waitFor(messageCount);

                /* verify received message content */
                List<Message> receivedMessages = messageWaiter.receivedMessages;
                assertEquals("Verify message subscriptions all called", receivedMessages.size(), messageCount);
                for(int i = 0; i < messageCount; i++) {
                    assertArrayEquals("Verify expected message contents", messagesSent[i], (byte[]) receivedMessages.get(i).data);
                }
            } else {
                String[] messagesSent = new String[messageCount];
                for(int i = 0; i < messageCount; i++) {
                    String messageData = messagesSent[i] = Helpers.RandomGenerator.generateRandomString(msgSize);
                    channel.publish("test_event", messageData, msgComplete.add());
                    try {
                        Thread.sleep(delay);
                    } catch(InterruptedException e) {
                    }
                }

                /* wait for the publish callback to be called */
                ErrorInfo[] errors = msgComplete.waitFor();
                assertTrue("Verify success from all message callbacks", errors.length == 0);

                /* wait for the subscription callback to be called */
                messageWaiter.waitFor(messageCount);

                /* verify received message content */
                List<Message> receivedMessages = messageWaiter.receivedMessages;
                assertEquals("Verify message subscriptions all called", receivedMessages.size(), messageCount);
                for(int i = 0; i < messageCount; i++) {
                    assertEquals("Verify expected message contents", messagesSent[i], (String) receivedMessages.get(i).data);
                }
            }

        } catch(AblyException e) {
            e.printStackTrace();
            fail("channelName: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    /**
     * Test right and wrong channel states to publish messages
     * Tests RTL6c
     **/
    @Test
    public void publish_channel_state() {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);

            Channel pubChannel = ably.channels.get("publish_channel_state");
            ChannelWaiter channelWaiter = new ChannelWaiter(pubChannel);
            pubChannel.attach();

            /* Publish in attaching state */
            pubChannel.publish(new Message("name1", "data1"));

            channelWaiter.waitFor(ChannelState.attached);

            /* Go to suspended state */
            ably.connection.connectionManager.requestState(ConnectionState.suspended);
            channelWaiter.waitFor(ChannelState.suspended);

            boolean error = false;
            try {
                pubChannel.publish(new Message("name2", "data2"));
            } catch(AblyException e) {
                error = true;
            }
            assertTrue("Verify exception was thrown on publishing in suspended state", error);

            /* reconnect and try again */
            ably.connection.connectionManager.requestState(ConnectionState.connecting);
            channelWaiter.waitFor(ChannelState.attached);

            pubChannel.publish(new Message("name3", "data3"));

            /* fail connection */
            ably.connection.connectionManager.requestState(ConnectionState.failed);
            channelWaiter.waitFor(ChannelState.failed);
            error = false;
            try {
                pubChannel.publish(new Message("name4", "data4"));
            } catch(AblyException e) {
                error = true;
            }
            assertTrue("Verify exception was thrown on publishing in failed state", error);

        } catch(AblyException e) {
            e.printStackTrace();
            fail("Unexpected exception");
        } finally {
            if(ably != null)
                ably.close();
        }

    }

    /**
     * Connect to the service using the default (binary) protocol
     * and attach, subscribe to an event, and publish multiple
     * messages on that channel
     */
    private void _multiple_send_batch(String channelName, int messageCount, int batchCount, long batchDelay) {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);

            /* create a channel */
            final Channel channel = ably.channels.get(channelName);

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* subscribe */
            MessageWaiter messageWaiter = new MessageWaiter(channel);

            /* publish to the channel */
            CompletionSet msgComplete = new CompletionSet();
            for(int i = 0; i < (int) (messageCount / batchCount); i++) {
                for(int j = 0; j < batchCount; j++) {
                    channel.publish("test_event", "Test message (_multiple_send_batch) " + i * batchCount + j, msgComplete.add());
                }
                try {
                    Thread.sleep(batchDelay);
                } catch(InterruptedException e) {/*ignore*/}
            }

            /* wait for the publish callback to be called */
            ErrorInfo[] errors = msgComplete.waitFor();
            assertTrue("Verify success from all message callbacks", errors.length == 0);

            /* wait for the subscription callback to be called */
            messageWaiter.waitFor(messageCount);
            assertEquals("Verify message subscriptions all called", messageWaiter.receivedMessages.size(), messageCount);

        } catch(AblyException e) {
            e.printStackTrace();
            fail("channelName: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    @Test
    public void multiple_send_10_1000_16_string() {
        int messageCount = 10;
        long delay = 1000L;
        _multiple_send("multiple_send_10_1000_16_string_" + testParams.name, messageCount, 16, false, delay);
    }

    @Test
    public void multiple_send_10_1000_16_binary() {
        int messageCount = 10;
        long delay = 1000L;
        _multiple_send("multiple_send_10_1000_16_binary_" + testParams.name, messageCount, 16, true, delay);
    }

    @Test
    public void multiple_send_10_1000_512_string() {
        int messageCount = 10;
        long delay = 1000L;
        _multiple_send("multiple_send_10_1000_512_string_" + testParams.name, messageCount, 512, false, delay);
    }

    @Test
    public void multiple_send_10_1000_512_binary() {
        int messageCount = 10;
        long delay = 1000L;
        _multiple_send("multiple_send_10_1000_512_binary_" + testParams.name, messageCount, 512, true, delay);
    }

    @Test
    public void multiple_send_20_200() {
        int messageCount = 20;
        long delay = 200L;
        _multiple_send("multiple_send_20_200_" + testParams.name, messageCount, 256, true, delay);
    }

    @Test
    public void multiple_send_200_50() {
        int messageCount = 200;
        long delay = 50L;
        _multiple_send("multiple_send_binary_200_50_" + testParams.name, messageCount, 256, true, delay);
    }

    @Test
    public void multiple_send_1000_10() {
        int messageCount = 1000;
        long delay = 10L;
        _multiple_send("multiple_send_binary_1000_10_" + testParams.name, messageCount, 256, true, delay);
    }

    /**
     * Connect to the service
     * using credentials that are unable to publish,and attach.
     * Attempt to publish and verify that an error is received.
     */
    @Test
    public void single_error() {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[4].keyStr);
            ably = new AblyRealtime(opts);

            /* create a channel; channel3 can subscribe but not publish
             * with this key */
            final Channel channel = ably.channels.get("channel3");

            /* attach */
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            /* publish to the channel */
            CompletionWaiter msgComplete = new CompletionWaiter();
            channel.publish("test_event", "Test message (single_error_binary)", msgComplete);

            /* wait for the publish callback to be called */
            ErrorInfo fail = msgComplete.waitFor();
            assertEquals("Verify error callback was called", fail.statusCode, 401);

        } catch(AblyException e) {
            e.printStackTrace();
            fail("single_error_binary: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    @Test
    public void ensure_disconnect_with_error_does_not_move_to_failed() {
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);

            /* wait until connected */
            (new ConnectionWaiter(ably.connection)).waitFor(ConnectionState.connected);
            assertEquals("Verify connected state reached", ably.connection.state, ConnectionState.connected);

            ProtocolMessage protoMessage = new ProtocolMessage(ProtocolMessage.Action.disconnect);
            protoMessage.error = new ErrorInfo("test error", 123);

            ConnectionManager connectionManager = ably.connection.connectionManager;
            connectionManager.onMessage(null, protoMessage);

            // On disconnected we retry right away since we're connected, so we can only
            // check that the state is not failed.
            assertNotEquals("connection state should not be failed", ably.connection.state, ConnectionState.failed);
        } catch(AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    @Test
    public void messages_encoding_fixtures() {
        MessagesEncodingData fixtures;
        try {
            fixtures = (MessagesEncodingData) Setup.loadJson(testMessagesEncodingFile, MessagesEncodingData.class);
        } catch(IOException e) {
            fail();
            return;
        }

        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(opts);
            final Channel channel = ably.channels.get("test");

            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", channel.state, ChannelState.attached);

            for(MessagesEncodingDataItem fixtureMessage : fixtures.messages) {
                /* subscribe */
                MessageWaiter messageWaiter = new MessageWaiter(channel);

                HttpHelpers.postSync(ably.http, "/channels/" + channel.name + "/messages", null, null, new HttpUtils.JsonRequestBody(fixtureMessage), null, true);

                messageWaiter.waitFor(1);
                channel.unsubscribe(messageWaiter);

                Message receivedMessage = messageWaiter.receivedMessages.get(0);

                expectDataToMatch(fixtureMessage, receivedMessage);

                CompletionWaiter msgComplete = new CompletionWaiter();
                channel.publish(receivedMessage, msgComplete);
                msgComplete.waitFor();

                MessagesEncodingDataItem persistedMessage = ably.http.request(new Http.Execute<MessagesEncodingDataItem[]>() {
                    @Override
                    public void execute(HttpScheduler http, Callback<MessagesEncodingDataItem[]> callback) throws AblyException {
                        http.get("/channels/" + channel.name + "/messages?limit=1", null, null, new HttpCore.ResponseHandler<MessagesEncodingDataItem[]>() {
                            @Override
                            public MessagesEncodingDataItem[] handleResponse(HttpCore.Response response, ErrorInfo error) throws AblyException {
                                if(error != null) {
                                    throw AblyException.fromErrorInfo(error);
                                }
                                return gson.fromJson(new String(response.body), MessagesEncodingDataItem[].class);
                            }
                        }, true, callback);
                    }
                }).sync()[0];

                assertEquals("Verify persisted message encoding", fixtureMessage.encoding, persistedMessage.encoding);
                assertEquals("Verify persisted message data", fixtureMessage.data, persistedMessage.data);
            }
        } catch(AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }

    @Test
    public void messages_msgpack_and_json_encoding_is_compatible() {
        MessagesEncodingData fixtures;
        try {
            fixtures = (MessagesEncodingData) Setup.loadJson(testMessagesEncodingFile, MessagesEncodingData.class);
        } catch(IOException e) {
            fail();
            return;
        }

        // Publish each data type through raw JSON POST and retrieve through MsgPack and JSON.

        AblyRealtime realtimeSubscribeClientMsgPack = null;
        AblyRealtime realtimeSubscribeClientJson = null;
        try {
            ClientOptions jsonOpts = createOptions(testVars.keys[0].keyStr);

            ClientOptions msgpackOpts = createOptions(testVars.keys[0].keyStr);
            msgpackOpts.useBinaryProtocol = !testParams.useBinaryProtocol;

            AblyRest restPublishClient = new AblyRest(jsonOpts);
            realtimeSubscribeClientMsgPack = new AblyRealtime(msgpackOpts);
            realtimeSubscribeClientJson = new AblyRealtime(jsonOpts);

            final Channel realtimeSubscribeChannelMsgPack = realtimeSubscribeClientMsgPack.channels.get("test-subscribe");
            final Channel realtimeSubscribeChannelJson = realtimeSubscribeClientJson.channels.get("test-subscribe");

            for(Channel realtimeSubscribeChannel : new Channel[]{realtimeSubscribeChannelMsgPack, realtimeSubscribeChannelJson}) {
                realtimeSubscribeChannel.attach();
                (new ChannelWaiter(realtimeSubscribeChannel)).waitFor(ChannelState.attached);
                assertEquals("Verify attached state reached", realtimeSubscribeChannel.state, ChannelState.attached);

                for(MessagesEncodingDataItem fixtureMessage : fixtures.messages) {
                    MessageWaiter messageWaiter = new MessageWaiter(realtimeSubscribeChannel);

                    HttpHelpers.postSync(restPublishClient.http, "/channels/" + realtimeSubscribeChannel.name + "/messages", null, null, new HttpUtils.JsonRequestBody(fixtureMessage), null, true);

                    messageWaiter.waitFor(1);
                    realtimeSubscribeChannel.unsubscribe(messageWaiter);

                    Message receivedMessage = messageWaiter.receivedMessages.get(0);

                    expectDataToMatch(fixtureMessage, receivedMessage);
                }
            }

            for(AblyRealtime realtimeSubscribeClient : new AblyRealtime[]{realtimeSubscribeClientMsgPack, realtimeSubscribeClientJson}) {
                realtimeSubscribeClient.close();
                realtimeSubscribeClient = null;
            }

            // Publish each data type through MsgPack and JSON and retrieve through raw JSON GET.

            AblyRest restPublishClientMsgPack = new AblyRest(msgpackOpts);
            AblyRest restPublishClientJson = new AblyRest(jsonOpts);
            AblyRest restRetrieveClient = new AblyRest(jsonOpts);

            final io.ably.lib.rest.Channel restPublishChannelMsgPack = restPublishClientMsgPack.channels.get("test-publish");
            final io.ably.lib.rest.Channel restPublishChannelJson = restPublishClientJson.channels.get("test-publish");

            for(MessagesEncodingDataItem fixtureMessage : fixtures.messages) {
                Object data = fixtureMessage.expectedValue;
                if(fixtureMessage.expectedHexValue != null) {
                    data = hexStringToByteArray(fixtureMessage.expectedHexValue);
                } else if(data instanceof JsonPrimitive) {
                    data = ((JsonPrimitive) data).getAsString();
                }

                for(final io.ably.lib.rest.Channel restPublishChannel : new io.ably.lib.rest.Channel[]{restPublishChannelMsgPack, restPublishChannelJson}) {
                    restPublishChannel.publish("event", data);

                    MessagesEncodingDataItem persistedMessage = restRetrieveClient.http.request(new Http.Execute<MessagesEncodingDataItem[]>() {
                        @Override
                        public void execute(HttpScheduler http, Callback<MessagesEncodingDataItem[]> callback) throws AblyException {
                            http.get("/channels/" + restPublishChannel.name + "/messages?limit=1", null, null, new HttpCore.ResponseHandler<MessagesEncodingDataItem[]>() {
                                @Override
                                public MessagesEncodingDataItem[] handleResponse(HttpCore.Response response, ErrorInfo error) throws AblyException {
                                    if(error != null) {
                                        throw AblyException.fromErrorInfo(error);
                                    }
                                    return gson.fromJson(new String(response.body), MessagesEncodingDataItem[].class);
                                }
                            }, true, callback);
                        }
                    }).sync()[0];

                    assertEquals("Verify persisted message encoding", fixtureMessage.encoding, persistedMessage.encoding);
                    assertEquals("Verify persisted message data", fixtureMessage.data, persistedMessage.data);
                }
            }
        } catch(AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(realtimeSubscribeClientMsgPack != null)
                realtimeSubscribeClientMsgPack.close();
            if(realtimeSubscribeClientJson != null)
                realtimeSubscribeClientJson.close();
        }
    }

    /**
     * Test behaviour when message is encoded as encrypted but encryption is not set up
     */
    @Test
    public void message_inconsistent_encoding() {
        AblyRealtime realtimeSubscribeClient = null;
        final ArrayList<String> log = new ArrayList<>();

        try {
            ClientOptions apiOptions = createOptions(testVars.keys[0].keyStr);
            apiOptions.logHandler = new Log.LogHandler() {
                @Override
                public void println(int severity, String tag, String msg, Throwable tr) {
                    synchronized(log) {
                        log.add(String.format(Locale.US, "%s: %s", tag, msg));
                    }
                }
            };
            apiOptions.logLevel = Log.INFO;

            AblyRest restPublishClient = new AblyRest(apiOptions);
            realtimeSubscribeClient = new AblyRealtime(apiOptions);

            final Channel realtimeSubscribeChannelJson = realtimeSubscribeClient.channels.get("test-encoding");

            realtimeSubscribeChannelJson.attach();
            (new ChannelWaiter(realtimeSubscribeChannelJson)).waitFor(ChannelState.attached);
            assertEquals("Verify attached state reached", realtimeSubscribeChannelJson.state, ChannelState.attached);

            MessageWaiter messageWaiter = new MessageWaiter(realtimeSubscribeChannelJson);

            MessagesEncodingDataItem testData = new MessagesEncodingDataItem();
            testData.data = "MDEyMzQ1Njc4OQ==";                    /* Base64("0123456789") */
            testData.encoding = "utf-8/cipher+aes-128-cbc/base64";
            testData.expectedType = "binary";
            testData.expectedHexValue = "30313233343536373839";    /* hex for "0123456789" */

            HttpHelpers.postSync(restPublishClient.http, "/channels/" + realtimeSubscribeChannelJson.name + "/messages", null, null, new HttpUtils.JsonRequestBody(testData), null, true);

            messageWaiter.waitFor(1);
            realtimeSubscribeChannelJson.unsubscribe(messageWaiter);

            Message receivedMessage = messageWaiter.receivedMessages.get(0);

            expectDataToMatch(testData, receivedMessage);
            assertEquals("Verify resulting encoding", receivedMessage.encoding, "utf-8/cipher+aes-128-cbc");

            synchronized(log) {
                boolean foundErrorMessage = false;
                for(String logMessage : log) {
                    if(logMessage.contains("encryption is not set up"))
                        foundErrorMessage = true;
                }
                assertTrue("Verify logged error messages", foundErrorMessage);
            }
        } catch(AblyException e) {
            e.printStackTrace();
            fail("init0: Unexpected exception instantiating library");
        } finally {
            if(realtimeSubscribeClient != null)
                realtimeSubscribeClient.close();
        }
    }

    private void expectDataToMatch(MessagesEncodingDataItem fixtureMessage, Message receivedMessage) {
        if(fixtureMessage.expectedType.equals("string")) {
            assertEquals("Verify decoded message data", fixtureMessage.expectedValue.getAsString(), receivedMessage.data);
        } else if(fixtureMessage.expectedType.equals("jsonObject")) {
            assertEquals("Verify decoded message data", fixtureMessage.expectedValue.getAsJsonObject(), receivedMessage.data);
        } else if(fixtureMessage.expectedType.equals("jsonArray")) {
            assertEquals("Verify decoded message data", fixtureMessage.expectedValue.getAsJsonArray(), receivedMessage.data);
        } else if(fixtureMessage.expectedType.equals("binary")) {
            byte[] receivedData = (byte[]) receivedMessage.data;
            StringBuilder sb = new StringBuilder(receivedData.length * 2);
            for(byte b : receivedData) {
                sb.append(String.format("%02x", b & 0xff));
            }
            String receivedDataHex = sb.toString();
            assertEquals("Verify decoded message data", fixtureMessage.expectedHexValue, receivedDataHex);
        } else {
            throw new RuntimeException(String.format(Locale.ROOT, "unhandled: %s", fixtureMessage.expectedType));
        }
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for(int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    static class MessagesEncodingData {
        public MessagesEncodingDataItem[] messages;
    }

    static class MessagesEncodingDataItem {
        public String data;
        public String encoding;
        public String expectedType;
        public JsonElement expectedValue;
        public String expectedHexValue;
    }

    @Test
    public void reject_invalid_message_data() throws AblyException {
        HashMap<String, Integer> data = new HashMap<String, Integer>();
        Message message = new Message("event", data);
        Log.LogHandler originalLogHandler = Log.handler;
        int originalLogLevel = Log.level;
        Log.setLevel(Log.DEBUG);
        final ArrayList<LogLine> capturedLog = new ArrayList<>();
        Log.setHandler(new Log.LogHandler() {
            @Override
            public void println(int severity, String tag, String msg, Throwable tr) {
                capturedLog.add(new LogLine(severity, tag, msg, tr));
            }
        });

        try {
            message.encode(null);
        } catch(AblyException e) {
            assertEquals(null, message.encoding);
            assertEquals(data, message.data);
            assertEquals(1, capturedLog.size());
            LogLine capturedLine = capturedLog.get(0);
            assertTrue(capturedLine.tag.contains("ably"));
            assertTrue(capturedLine.msg.contains("Message data must be either `byte[]`, `String` or `JSONElement`; implicit coercion of other types to String is deprecated"));
        } catch(Throwable t) {
            fail("reject_invalid_message_data: Unexpected exception");
        } finally {
            Log.setHandler(originalLogHandler);
            Log.setLevel(originalLogLevel);
        }
    }

    public static class LogLine {
        public int severity;
        public String tag;
        public String msg;
        public Throwable tr;

        public LogLine(int severity, String tag, String msg, Throwable tr) {
            this.severity = severity;
            this.tag = tag;
            this.msg = msg;
            this.tr = tr;
        }
    }

    /**
     * To Test Message.fromEncoded(JsonObject, ChannelOptions) and Message.fromEncoded(String, ChannelOptions)
     * Refer Spec TM3
     * @throws AblyException
     */
    @Test
    public void message_from_encoded_json_object() throws AblyException {
        /*Test Base64 data decoding in Message.fromEncoded(JsonObject)*/
        Message sendMsg = new Message("test_from_encoded_method", "0123456789".getBytes());
        sendMsg.clientId = "client-id";
        sendMsg.connectionId = "connection-id";
        sendMsg.timestamp = System.currentTimeMillis();
        sendMsg.encode(null);

        Message receivedMsg = Message.fromEncoded(Serialisation.gson.toJsonTree(sendMsg).getAsJsonObject(), null);
        assertEquals(receivedMsg.name, sendMsg.name);
        assertArrayEquals((byte[]) receivedMsg.data, "0123456789".getBytes());

        /*Test JSON Data decoding in Message.fromEncoded(JsonObject)*/
        JsonObject person = new JsonObject();
        person.addProperty("name", "Amit");
        person.addProperty("country", "Interlaken Ost");

        Message userDetails = new Message("user_details", person);
        userDetails.encode(null);
        Message decodedMessage1 = Message.fromEncoded(Serialisation.gson.toJsonTree(userDetails).getAsJsonObject(), null);

        assertEquals(userDetails.name, decodedMessage1.name);
        assertEquals(person, decodedMessage1.data);

        /*Test Message.fromEncoded(String)*/
        Message decodedMessage2 = Message.fromEncoded(Serialisation.gson.toJson(userDetails), null);
        assertEquals(userDetails.name, decodedMessage2.name);
        assertEquals(person, decodedMessage2.data);

        /*Test invalid case.*/
        try {
            //We pass invalid Message object
            Message.fromEncoded("[]", null);
            fail();
        } catch(Exception e) {/*ignore as we are expecting it to fail.*/}
    }

    /**
     * To test Message.fromEncodedArray(JsonArray, ChannelOptions) and Message.fromEncodedArray(String, ChannelOptions)
     * Refer Spec. TM3
     * @throws AblyException
     */
    @Test
    public void messages_from_encoded_json_array() throws AblyException {
        JsonArray fixtures = null;
        MessagesData testMessages = null;
        try {
            testMessages = (MessagesData) Setup.loadJson(testMessagesEncodingFile, MessagesData.class);
            JsonObject jsonObject = (JsonObject) Setup.loadJson(testMessagesEncodingFile, JsonObject.class);
            //We use this as-is for decoding purposes.
            fixtures = jsonObject.getAsJsonArray("messages");
        } catch(IOException e) {
            fail();
            return;
        }

        Message[] decodedMessages = Message.fromEncodedArray(fixtures, null);
        for(int index = 0; index < decodedMessages.length; index++) {
            Message testInputMsg = testMessages.messages[index];
            testInputMsg.decode(null);
            if(testInputMsg.data instanceof byte[]) {
                assertArrayEquals((byte[]) testInputMsg.data, (byte[]) decodedMessages[index].data);
            } else {
                assertEquals(testInputMsg.data, decodedMessages[index].data);
            }
        }
        /*Test Message.fromEncodedArray(String)*/
        String fixturesArray = Serialisation.gson.toJson(fixtures);
        Message[] decodedMessages2 = Message.fromEncodedArray(fixturesArray, null);
        for(int index = 0; index < decodedMessages2.length; index++) {
            Message testInputMsg = testMessages.messages[index];
            if(testInputMsg.data instanceof byte[]) {
                assertArrayEquals((byte[]) testInputMsg.data, (byte[]) decodedMessages2[index].data);
            } else {
                assertEquals(testInputMsg.data, decodedMessages2[index].data);
            }
        }
    }

    static class MessagesData {
        public Message[] messages;
    }

    /**
     * Publish a message that contains extras of arbitrary creation. Validate that when we receive that message
     * echoed back from the service that those extras remain intact.
     *
     * @see <a href="https://docs.ably.com/client-lib-development-guide/features/#RSL6a2">RSL6a2</a>
     */
    @Test
    public void opaque_message_extras() throws AblyException {
        AblyRealtime ably = null;
        try {
            final JsonObject opaqueJson = new JsonObject();
            opaqueJson.addProperty("headers", "{\"key_text\":\"text value\",\"key_boolean\":true,\"key_int\":33}\n");

            final MessageExtras extras = new MessageExtras(opaqueJson);
            final Message message = new Message();
            message.name = "The Test Message";
            message.data = "Some Value";
            message.extras = extras;

            final ClientOptions clientOptions = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(clientOptions);

            // create a channel and attach to it
            final Channel channel = ably.channels.get(createChannelName("opaque_message_extras"));
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals(ChannelState.attached, channel.state);

            // subscribe
            final MessageWaiter messageWaiter = new MessageWaiter(channel);

            // publish and await success
            final CompletionWaiter completionWaiter = new CompletionWaiter();
            channel.publish(message, completionWaiter);
            completionWaiter.waitFor();
            assertTrue(completionWaiter.success);

            // wait for the subscriber to receive the message
            messageWaiter.waitFor(1);
            assertEquals(1, messageWaiter.receivedMessages.size());

            // validate the contents of the received message
            final Message received = messageWaiter.receivedMessages.get(0);
            assertEquals("The Test Message", received.name);
            assertEquals("Some Value", received.data);
            assertEquals(extras, received.extras);
        } finally {
            if(ably != null) {
                ably.close();
            }
        }
    }

    /**
     * Publish a message that contains extras of invalid format.
     */
    @Test
    public void opaque_message_extras_fail() throws AblyException {
        AblyRealtime ably = null;
        try {
            final JsonObject opaqueJson = new JsonObject();
            opaqueJson.addProperty("Some Property", "Lorem Ipsum");
            opaqueJson.addProperty("Some Truth", false);
            opaqueJson.addProperty("Some Number", 321);

            final MessageExtras extras = new MessageExtras(opaqueJson);
            final Message message = new Message();
            message.name = "The Test Message";
            message.data = "Some Value";
            message.extras = extras;

            final ClientOptions clientOptions = createOptions(testVars.keys[0].keyStr);
            ably = new AblyRealtime(clientOptions);

            // create a channel and attach to it
            final Channel channel = ably.channels.get(createChannelName("opaque_message_extras_fail"));
            channel.attach();
            (new ChannelWaiter(channel)).waitFor(ChannelState.attached);
            assertEquals(ChannelState.attached, channel.state);

            // publish and await success
            final CompletionWaiter completionWaiter = new CompletionWaiter();
            channel.publish(message, completionWaiter);
            completionWaiter.waitFor();
            assertNotNull("error is not received for invalid extras format", completionWaiter.error);
        } finally {
            if(ably != null) {
                ably.close();
            }
        }
    }
}
