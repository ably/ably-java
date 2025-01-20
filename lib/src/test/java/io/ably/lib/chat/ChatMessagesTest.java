package io.ably.lib.chat;

import com.google.gson.JsonObject;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.Message;
import io.ably.lib.types.MessageAction;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatMessagesTest extends ParameterizedTest {
    /**
     * Test that a message sent via rest API is sent to a messages channel.
     * It should be received by the client that is subscribed to the messages channel.
     */
    @Test
    public void test_room_message_is_published() {
        String roomId = "1234";
        String channelName = roomId + "::$chat::$chatMessages";
        AblyRealtime ably = null;
        try {
            ClientOptions opts = createOptions(testVars.keys[7].keyStr);
            opts.clientId = "sandbox-client";
            ably = new AblyRealtime(opts);
            ChatRoom room = new ChatRoom(roomId, ably);

            /* create a channel and attach */
            final Channel channel = ably.channels.get(channelName);
            channel.attach();
            (new Helpers.ChannelWaiter(channel)).waitFor(ChannelState.attached);

            /* subscribe to messages */
            List<Message> receivedMsg = new ArrayList<>();
            channel.subscribe(receivedMsg::add);

            // send message to room
            ChatRoom.SendMessageParams params = new ChatRoom.SendMessageParams();
            params.text = "hello there";
            params.metadata = new JsonObject();
            JsonObject foo = new JsonObject();
            foo.addProperty("bar", 1);
            params.metadata.add("foo", foo);
            Map<String, String> headers = new HashMap<>();
            headers.put("header1", "value1");
            headers.put("baz", "qux");
            params.headers = headers;

            JsonObject sendMessageResult = (JsonObject) room.sendMessage(params);
            // check sendMessageResult has 2 fields and are not null
            Assert.assertEquals(2, sendMessageResult.entrySet().size());
            String resultSerial = sendMessageResult.get("serial").getAsString();
            Assert.assertFalse(resultSerial.isEmpty());
            String resultCreatedAt = sendMessageResult.get("createdAt").getAsString();
            Assert.assertFalse(resultCreatedAt.isEmpty());

            Exception err = new Helpers.ConditionalWaiter().wait(() -> !receivedMsg.isEmpty(), 10_000);
            Assert.assertNull(err);

            Assert.assertEquals(1, receivedMsg.size());
            Message message = receivedMsg.get(0);

            Assert.assertFalse("Message ID should not be empty", message.id.isEmpty());
            Assert.assertEquals("chat.message", message.name);
            Assert.assertEquals("sandbox-client", message.clientId);

            JsonObject data = (JsonObject) message.data;
            // has two fields "text" and "metadata"
            Assert.assertEquals(2, data.entrySet().size());
            // Assert for received text
            Assert.assertEquals("hello there", data.get("text").getAsString());
            // Assert on received metadata
            JsonObject metadata = data.getAsJsonObject("metadata");
            Assert.assertTrue(metadata.has("foo"));
            Assert.assertTrue(metadata.get("foo").isJsonObject());
            Assert.assertEquals(1, metadata.getAsJsonObject("foo").get("bar").getAsInt());

            // Assert sent headers as a part of message.extras.headers
            JsonObject extrasJson = message.extras.asJsonObject();
            Assert.assertTrue(extrasJson.has("headers"));
            JsonObject headersJson = extrasJson.getAsJsonObject("headers");
            Assert.assertEquals(2, headersJson.entrySet().size());
            Assert.assertEquals("value1", headersJson.get("header1").getAsString());
            Assert.assertEquals("qux", headersJson.get("baz").getAsString());

            Assert.assertEquals(resultCreatedAt, String.valueOf(message.timestamp));

            Assert.assertEquals(resultCreatedAt, message.createdAt.toString());
            Assert.assertEquals(resultSerial, message.serial);
            Assert.assertEquals(resultSerial, message.version);

            Assert.assertEquals(MessageAction.MESSAGE_CREATE, message.action);
            Assert.assertEquals(resultCreatedAt, message.createdAt.toString());

        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("init0: Unexpected exception instantiating library");
        } finally {
            if(ably != null)
                ably.close();
        }
    }
}
