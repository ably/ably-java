package io.ably.lib.objects.integration.java;

import io.ably.lib.objects.LiveObjects;
import io.ably.lib.objects.integration.setup.IntegrationTest;
import io.ably.lib.objects.type.counter.LiveCounter;
import io.ably.lib.objects.type.map.LiveMap;
import io.ably.lib.realtime.ChannelBase;
import io.ably.lib.realtime.CompletionListener;
import io.ably.lib.types.AblyException;
import io.ably.lib.types.ErrorInfo;
import org.junit.Test;

import static java.lang.Thread.sleep;
import static org.junit.Assert.assertNotNull;

public class LiveMapTest extends IntegrationTest {

    @Test
    public void testLiveMapFunctionality() throws AblyException, InterruptedException {
        String channelName = this.generateChannelName$live_objects_test();
        ChannelBase channel = this.getRealtimeChannel$live_objects_test(channelName);
        assertNotNull("Channel name should not be null", channel);
        channel.attach(new CompletionListener() {
            @Override
            public void onSuccess() {
                LiveObjects objects = null;
                try {
                    objects = channel.getObjects();
                } catch (AblyException e) {
                    throw new RuntimeException(e);
                }
                // Won't be able to receive sync message because
                // attach callback is blocked by getRoot -> internally blocks eventloop that processes incoming messages
                LiveMap root = objects.getRoot(); // getRoot keeps waiting for object sync message indefinitely
                System.out.println("LiveMap root: " + root);

                // Won't be able to receive newly created map/counter message
                LiveMap newMap = objects.createMap(); // blocks eventloop, keeps waiting indefinitely for publish ack
                System.out.println("New LiveMap created: " + newMap);
                LiveCounter counter = objects.createCounter(); // blocks eventloop, keeps waiting indefinitely for publish ack
                System.out.println("New LiveCounter created: " + counter);
            }

            @Override
            public void onError(ErrorInfo reason) {

            }
        });
        sleep(10000);
    }
}
