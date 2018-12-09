package io.ably.lib.test.realtime;

import org.junit.Test;

import java.util.HashMap;

import io.ably.lib.util.EventEmitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EventEmitterTest {

    private static enum MyEvents {
        event_0,
        event_1
    }

    private static class MyEventPayload {
        public MyEvents event;
        public String message;
    }

    private static interface MyListener {
        public void onMyThingHappened(MyEventPayload theThing);
    }

    private static class CountingListener implements MyListener {
        @Override
        public void onMyThingHappened(MyEventPayload theThing) {
            Integer count = counts.get(theThing.event);
            if (count == null) count = 0;
            counts.put(theThing.event, count + 1);
        }

        HashMap<MyEvents, Integer> counts = new HashMap<MyEvents, Integer>();
    }

    private static class MyEmitter extends EventEmitter<MyEvents, MyListener> {
        @Override
        protected void apply(MyListener listener, final MyEvents ev, final Object... args) {
            listener.onMyThingHappened(new MyEventPayload() {{
                event = ev;
                message = (String) args[0];
            }});
        }
    }

    /**
     * Register a listener, and verify it is called
     * when the event is emitted
     */
    @Test
    public void on_simple() {
        MyEmitter emitter = new MyEmitter();
        emitter.on(new MyListener() {
            @Override
            public void onMyThingHappened(MyEventPayload theThing) {
                assertEquals(theThing.event, MyEvents.event_0);
                assertEquals(theThing.message, "on_simple");
            }
        });
        emitter.emit(MyEvents.event_0, "on_simple");
    }

    /**
     * Register a listener, and verify it is called
     * when the event is emitted more than once
     */
    @Test
    public void on_multiple() {
        MyEmitter emitter = new MyEmitter();
        CountingListener listener = new CountingListener();
        emitter.on(listener);
        emitter.emit(MyEvents.event_0, "on_multiple_0");
        emitter.emit(MyEvents.event_0, "on_multiple_0");
        emitter.emit(MyEvents.event_1, "on_multiple_1");
        assertEquals(listener.counts.get(MyEvents.event_0), Integer.valueOf(2));
        assertEquals(listener.counts.get(MyEvents.event_1), Integer.valueOf(1));
    }

    /**
     * Register and unregister listener, and verify it
     * is not called when the event is emitted
     */
    @Test
    public void off_simple() {
        MyEmitter emitter = new MyEmitter();
        CountingListener listener = new CountingListener();
        emitter.on(listener);
        emitter.off(listener);
        emitter.emit(MyEvents.event_0, "on_multiple_0");
        emitter.emit(MyEvents.event_1, "on_multiple_1");
        assertNull(listener.counts.get(MyEvents.event_0));
        assertNull(listener.counts.get(MyEvents.event_1));
    }

    /**
     * Register and unregister multiple listeners, and verify they
     * are not called when the event is emitted
     */
    @Test
    public void off_all() {
        MyEmitter emitter = new MyEmitter();
        CountingListener listener1 = new CountingListener();
        CountingListener listener2 = new CountingListener();
        emitter.on(listener1);
        emitter.on(listener2);
        emitter.off();
        emitter.emit(MyEvents.event_0, "on_multiple_0");
        emitter.emit(MyEvents.event_1, "on_multiple_1");
        assertNull(listener1.counts.get(MyEvents.event_0));
        assertNull(listener1.counts.get(MyEvents.event_1));
        assertNull(listener2.counts.get(MyEvents.event_0));
        assertNull(listener2.counts.get(MyEvents.event_1));
    }

    /**
     * Register a listener for a specific event, and verify it is called
     * only when that event is emitted
     */
    @Test
    public void on_event_simple() {
        MyEmitter emitter = new MyEmitter();
        CountingListener listener = new CountingListener();
        emitter.on(MyEvents.event_0, listener);
        emitter.emit(MyEvents.event_0, "on_event_simple_0");
        emitter.emit(MyEvents.event_0, "on_event_simple_0");
        emitter.emit(MyEvents.event_1, "on_event_simple_1");
        assertEquals(listener.counts.get(MyEvents.event_0), Integer.valueOf(2));
        assertNull(listener.counts.get(MyEvents.event_1));
    }

    /**
     * Register a listener for a specific event, and verify
     * it is no longer called after it has been removed
     */
    @Test
    public void off_event_simple() {
        MyEmitter emitter = new MyEmitter();
        CountingListener listener = new CountingListener();
        emitter.on(MyEvents.event_0, listener);
        emitter.emit(MyEvents.event_0, "off_event_simple_0");
        emitter.emit(MyEvents.event_1, "off_event_simple_1");
        emitter.off(MyEvents.event_0, listener);
        emitter.emit(MyEvents.event_0, "off_event_simple_0");
        assertEquals(listener.counts.get(MyEvents.event_0), Integer.valueOf(1));
        assertNull(listener.counts.get(MyEvents.event_1));
    }

    /**
     * Register a "once" listener for a specific event, and
     * verify it is called only once when that event is emitted
     */
    @Test
    public void once_event_simple() {
        MyEmitter emitter = new MyEmitter();
        CountingListener listener = new CountingListener();
        emitter.once(MyEvents.event_0, listener);
        emitter.emit(MyEvents.event_0, "once_event_simple_0");
        emitter.emit(MyEvents.event_0, "once_event_simple_0");
        emitter.emit(MyEvents.event_1, "once_event_simple_1");
        assertEquals(listener.counts.get(MyEvents.event_0), Integer.valueOf(1));
        assertNull(listener.counts.get(MyEvents.event_1));
    }

    /**
     * Register a "once" listener for a specific event, then
     * remove it, and verify it is not called when that event is emitted
     */
    @Test
    public void once_off_event_simple() {
        MyEmitter emitter = new MyEmitter();
        CountingListener listener = new CountingListener();
        emitter.once(MyEvents.event_0, listener);
        emitter.emit(MyEvents.event_1, "once_event_simple_1");
        emitter.off(MyEvents.event_0, listener);
        emitter.emit(MyEvents.event_0, "once_event_simple_0");
        assertNull(listener.counts.get(MyEvents.event_0));
        assertNull(listener.counts.get(MyEvents.event_1));
    }


    @Test
    public void once_off_event() {
        MyEmitter emitter = new MyEmitter();
        CountingListener listener = new CountingListener();
        emitter.once(MyEvents.event_0, listener);
        emitter.emit(MyEvents.event_0, "once_event_simple_1");
        emitter.off(MyEvents.event_0, listener);
        emitter.emit(MyEvents.event_0, "once_event_simple_0");
        assertEquals((int) listener.counts.get(MyEvents.event_0), 1);

    }

    @Test
    public void test_listener_in_listener() {
        final MyEmitter emitter = new MyEmitter();

        emitter.on(MyEvents.event_0, new MyListener() {
            @Override
            public void onMyThingHappened(MyEventPayload theThing) {
                emitter.on(MyEvents.event_0, new CountingListener() {
                    @Override
                    public void onMyThingHappened(MyEventPayload theThing) {
                        super.onMyThingHappened(theThing);
                        assertEquals(theThing.event, MyEvents.event_0);
                        //Only second event is fired.
                        assertEquals(theThing.message, "firstEventAndSecondEvent");
                    }
                });

                assertTrue(theThing.message.contains("firstEvent"));

            }
        });

        emitter.emit(MyEvents.event_0, "firstEvent");
        emitter.emit(MyEvents.event_0, "firstEventAndSecondEvent");
    }

}
