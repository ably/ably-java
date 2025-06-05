package io.ably.lib.test.realtime;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.realtime.Channel;
import io.ably.lib.realtime.ChannelState;
import io.ably.lib.realtime.ConnectionState;
import io.ably.lib.rest.AblyRest;
import io.ably.lib.rest.Auth;
import io.ably.lib.test.common.Helpers;
import io.ably.lib.test.common.ParameterizedTest;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.Annotation;
import io.ably.lib.types.AnnotationAction;
import io.ably.lib.types.ClientOptions;
import io.ably.lib.types.Message;
import io.ably.lib.types.MessageAction;
import io.ably.lib.types.MessageExtras;
import io.ably.lib.types.Param;
import io.ably.lib.types.PaginatedResult;
import io.ably.lib.types.PresenceMessage;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RealtimeAnnotationsTest extends ParameterizedTest {

    @Rule
    public Timeout testTimeout = Timeout.seconds(300);

    @Test
    public void publish_and_subscribe_annotations() throws AblyException, InterruptedException {
        ClientOptions opts = createOptions();
        AblyRealtime realtime = new AblyRealtime(opts);
        AblyRest rest = new AblyRest(opts);

        Channel channel = realtime.channels.get("mutable:publish_subscribe_annotation",
            new Channel.ChannelOptions("publish", "subscribe", "annotation_publish", "annotation_subscribe"));
        Channel restChannel = rest.channels.get("mutable:publish_subscribe_annotation");

        channel.attach();
        Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
        channel.once(ChannelState.attached, waiter);
        waiter.waitFor();

        final Message[] receivedMessage = new Message[1];
        channel.subscribe(message -> receivedMessage[0] = message);

        final Annotation[] receivedAnnotation = new Annotation[1];
        channel.annotations.subscribe(annotation -> receivedAnnotation[0] = annotation);

        channel.publish("message", "foobar");
        Thread.sleep(1000);

        assertNotNull("Message should be received", receivedMessage[0]);

        channel.annotations.publish(receivedMessage[0], "reaction:distinct.v1", "👍");
        Thread.sleep(1000);

        assertNotNull("Annotation should be received", receivedAnnotation[0]);
        assertEquals(AnnotationAction.ANNOTATION_CREATE, receivedAnnotation[0].action);
        assertEquals(receivedMessage[0].serial, receivedAnnotation[0].messageSerial);
        assertEquals("reaction:distinct.v1", receivedAnnotation[0].type);
        assertEquals("👍", receivedAnnotation[0].name);
        assertTrue(Long.parseLong(receivedAnnotation[0].serial) > Long.parseLong(receivedAnnotation[0].messageSerial));

        receivedAnnotation[0] = null;
        restChannel.annotations.publish(receivedMessage[0], "reaction:distinct.v1", "😕");
        Thread.sleep(1000);

        assertNotNull("Rest annotation should be received", receivedAnnotation[0]);
        assertEquals(AnnotationAction.ANNOTATION_CREATE, receivedAnnotation[0].action);
        assertEquals(receivedMessage[0].serial, receivedAnnotation[0].messageSerial);
        assertEquals("reaction:distinct.v1", receivedAnnotation[0].type);
        assertEquals("😕", receivedAnnotation[0].name);
        assertTrue(Long.parseLong(receivedAnnotation[0].serial) > Long.parseLong(receivedAnnotation[0].messageSerial));

        realtime.close();
        rest.close();
    }

    @Test
    public void get_all_annotations() throws AblyException, InterruptedException {
        ClientOptions opts = createOptions();
        AblyRealtime realtime = new AblyRealtime(opts);

        Channel channel = realtime.channels.get("mutable:get_all_annotations_for_a_message",
            new Channel.ChannelOptions("publish", "subscribe", "annotation_publish", "annotation_subscribe"));

        channel.attach();
        Helpers.CompletionWaiter waiter = new Helpers.CompletionWaiter();
        channel.once(ChannelState.attached, waiter);
        waiter.waitFor();

        final Message[] receivedMessage = new Message[1];
        channel.subscribe(message -> receivedMessage[0] = message);

        channel.publish("message", "foobar");
        Thread.sleep(1000);

        String[] emojis = new String[]{"👍", "😕", "👎", "👍👍", "😕😕", "👎👎"};
        for (String emoji : emojis) {
            channel.annotations.publish(receivedMessage[0].serial, "reaction:distinct.v1", emoji);
            Thread.sleep(100);
        }

        Thread.sleep(1000);
        PaginatedResult<Annotation> result = channel.annotations.get(receivedMessage[0].serial);
        assertEquals(6, result.items.length);

        assertEquals(AnnotationAction.ANNOTATION_CREATE, result.items[0].action);
        assertEquals(receivedMessage[0].serial, result.items[0].messageSerial);
        assertEquals("reaction:distinct.v1", result.items[0].type);
        assertEquals("👍", result.items[0].name);
        assertEquals("😕", result.items[1].name);
        assertEquals("👎", result.items[2].name);
        assertTrue(Long.parseLong(result.items[1].serial) > Long.parseLong(result.items[0].serial));
        assertTrue(Long.parseLong(result.items[2].serial) > Long.parseLong(result.items[1].serial));

        result = channel.annotations.get(receivedMessage[0].serial, new Param[]{new Param("limit", "2")});
        assertEquals(2, result.items.length);
        assertEquals("👍", result.items[0].name);
        assertEquals("😕", result.items[1].name);
        assertTrue(result.hasNext());

        result = result.next();
        assertNotNull(result);
        assertEquals(2, result.items.length);
        assertEquals("👎", result.items[0].name);
        assertEquals("👍👍", result.items[1].name);
        assertTrue(result.hasNext());

        result = result.next();
        assertNotNull(result);
        assertEquals(2, result.items.length);
        assertEquals("😕😕", result.items[0].name);
        assertEquals("👎👎", result.items[1].name);
        assertTrue(!result.hasNext());

        realtime.close();
    }


}
