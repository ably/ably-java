package io.ably.lib.test.rest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.types.*;

import io.ably.lib.rest.AblyRest;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

public class RestChannelBulkPublishTest extends ParameterizedTest  {

    /**
     * Publish a single message on multiple channels
     *
     * The payload constructed has the form
     * [
     *   {
     *     channel: [ <channel 0>, <channel 1>, ... ],
     *     message: [{ data: <message text> }]
     *   }
     * ]
     *
     * It publishes the given message on all of the given channels.
     */
    @Test
    public void bulk_publish_multiple_channels_simple() {
        try {
            /* setup library instance */
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            AblyRest ably = new AblyRest(opts);

            /* first, publish some messages */
            int channelCount = 5;
            ArrayList<String> channelIds = new ArrayList<String>();
            for(int i = 0; i < channelCount; i++) {
                channelIds.add("persisted:" + randomString());
            }

            Message message = new Message(null, "bulk_publish_multiple_channels_simple");
            String messageId = message.id = randomString();
            Message.Batch payload = new Message.Batch(channelIds, Collections.singleton(message));

            PublishResponse[] result = ably.publishBatch(new Message.Batch[] { payload }, null);
            for(PublishResponse response : result) {
                assertEquals("Verify expected response id", response.messageId, messageId);
                assertTrue("Verify expected channel name", channelIds.contains(response.channelId));
                assertNull("Verify no publish error", response.error);
            }

            /* get the history for this channel */
            for(String channel : channelIds) {
                PaginatedResult<Message> messages = ably.channels.get(channel).history(null);
                assertNotNull("Expected non-null messages", messages);
                assertEquals("Expected 1 message", messages.items().length, 1);
                /* verify message contents */
                assertEquals("Expect message data to be expected String", messages.items()[0].data, message.data);
            }
        } catch (AblyException e) {
            e.printStackTrace();
            fail("bulkpublish_multiple_channels_simple: Unexpected exception");
            return;
        }
    }

    /**
     * As above but with the param method
     */
    @Test
    public void bulk_publish_multiple_channels_param() {
        try {
            /* setup library instance */
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            AblyRest ably = new AblyRest(opts);

            /* first, publish some messages */
            int channelCount = 5;
            ArrayList<String> channelIds = new ArrayList<String>();
            for (int i = 0; i < channelCount; i++) {
                channelIds.add("persisted:" + randomString());
            }

            Message message = new Message(null, "bulk_publish_multiple_channels_param");
            String messageId = message.id = randomString();
            Message.Batch payload = new Message.Batch(channelIds, Collections.singleton(message));

            Param[] params = new Param[]{new Param("quickAck", "true")};

            PublishResponse[] result = ably.publishBatch(new Message.Batch[]{payload}, null, params);
            for (PublishResponse response : result) {
                assertEquals("Verify expected response id", response.messageId, messageId);
                assertTrue("Verify expected channel name", channelIds.contains(response.channelId));
                assertNull("Verify no publish error", response.error);
            }

            /* get the history for this channel */
            for (String channel : channelIds) {
                PaginatedResult<Message> messages = ably.channels.get(channel).history(null);
                assertNotNull("Expected non-null messages", messages);
                assertEquals("Expected 1 message", messages.items().length, 1);
                /* verify message contents */
                assertEquals("Expect message data to be expected String", messages.items()[0].data, message.data);
            }
        } catch (AblyException e) {
            e.printStackTrace();
            fail("bulk_publish_multiple_channels_param: Unexpected exception");
            return;
        }
    }

    /**
     * Publish a multiple messages on multiple channels
     *
     * The payload constructed has the form
     * [
     *   {
     *     channel: [ <channel 0> ],
     *     message: [
     *       { data: <message text> },
     *       { data: <message text> },
     *       { data: <message text> },
     *       ...
     *     ]
     *   },
     *   {
     *     channel: [ <channel 1> ],
     *     message: [
     *       { data: <message text> },
     *       { data: <message text> },
     *       { data: <message text> },
     *       ...
     *     ]
     *   },
     *   ...
     * ]
     *
     * It publishes the given messages on the associated channels.
     */
    @Test
    public void bulk_publish_multiple_channels_multiple_messages() {
        try {
            /* setup library instance */
            ClientOptions opts = createOptions(testVars.keys[0].keyStr);
            opts.idempotentRestPublishing = true;
            AblyRest ably = new AblyRest(opts);

            /* first, publish some messages */
            int channelCount = 5;
            int messageCount = 6;
            String baseMessageText = "bulk_publish_multiple_channels_multiple_messages";
            ArrayList<Message.Batch> payload = new ArrayList<Message.Batch>();

            ArrayList<String> rndMessageTexts = new ArrayList<String>();
            for(int i = 0; i < messageCount; i++) {
                rndMessageTexts.add(randomString());
            }

            ArrayList<String> channelIds = new ArrayList<String>();
            for(int i = 0; i < channelCount; i++) {
                String channel = "persisted:" + randomString();
                channelIds.add(channel);
                ArrayList<Message> messages = new ArrayList<Message>();
                for(int j = 0; j < messageCount; j++) {
                    messages.add(new Message(null, baseMessageText + '-' + channel + '-' + rndMessageTexts.get(j)));
                }
                payload.add(new Message.Batch(Collections.singleton(channel), messages));
            }

            PublishResponse[] result = ably.publishBatch(payload.toArray(new Message.Batch[payload.size()]), null);
            for(PublishResponse response : result) {
                assertNotNull("Verify expected response id", response.messageId);
                assertTrue("Verify expected channel name", channelIds.contains(response.channelId));
                assertNull("Verify no publish error", response.error);
            }

            /* get the history for this channel */
            for(String channel : channelIds) {
                PaginatedResult<Message> messages = ably.channels.get(channel).history(new Param[] {new Param("direction", "forwards")});
                assertNotNull("Expected non-null messages", messages);
                assertEquals("Expected correct number of messages", messages.items().length, messageCount);
                /* verify message contents */
                for(int i = 0; i < messageCount; i++) {
                    assertEquals("Expect message data to be expected String", messages.items()[i].data, baseMessageText + '-' + channel + '-' + rndMessageTexts.get(i));
                }
            }
        } catch (AblyException e) {
            e.printStackTrace();
            fail("bulk_publish_multiple_channels_multiple_messages: Unexpected exception");
            return;
        }
    }

    /**
     * Publish a single message on multiple channels, using credentials
     * that are only able to publish to a subset of the channels
     *
     * The payload constructed has the form
     * [
     *   {
     *     channel: [ <channel 0>, <channel 1>, ... ],
     *     message: [{ data: <message text> }]
     *   }
     * ]
     *
     * It attempts to publish the given message on all of the given channels.
     */
    @Ignore // awaiting channel member in error responses
    @Test
    public void bulk_publish_multiple_channels_partial_error() {
        try {
            /* setup library instance */
            ClientOptions opts = createOptions(testVars.keys[6].keyStr);
            AblyRest ably = new AblyRest(opts);

            /* first, publish some messages */
            String baseChannelName = "persisted:" + testParams.name + ":channel";
            int channelCount = 5;
            ArrayList<String> channelIds = new ArrayList<String>();
            for(int i = 0; i < channelCount; i++) {
                channelIds.add(baseChannelName + i);
            }

            Message message = new Message(null, "bulk_publish_multiple_channels_partial_error");
            String messageId = message.id = randomString();
            Message.Batch payload = new Message.Batch(channelIds, Collections.singleton(message));

            PublishResponse[] result = ably.publishBatch(new Message.Batch[] { payload }, null);
            for(PublishResponse response : result) {
                if((baseChannelName + "1").compareTo(response.channelId) >= 0) {
                    assertEquals("Verify expected response id", response.messageId, messageId);
                    assertTrue("Verify expected channel name", channelIds.contains(response.channelId));
                    assertNull("Verify no publish error", response.error);
                } else {
                    assertNotNull("Verify expected publish error", response.error);
                    assertEquals("Verify expected publish error code", response.error.code, 40160);
                }
            }

            /* get the history for this channel */
            for(String channel : channelIds) {
                if((baseChannelName + "1").compareTo(channel) >= 0) {
                    PaginatedResult<Message> messages = ably.channels.get(channel).history(null);
                    assertNotNull("Expected non-null messages", messages);
                    assertEquals("Expected 1 message", messages.items().length, 1);
                    /* verify message contents */
                    assertEquals("Expect message data to be expected String", messages.items()[0].data, message.data);
                }
            }
        } catch (AblyException e) {
            e.printStackTrace();
            fail("bulkpublish_multiple_channels_simple: Unexpected exception");
            return;
        }
    }

    private String randomString() { return String.valueOf(new Random().nextDouble()).substring(2); }
}
