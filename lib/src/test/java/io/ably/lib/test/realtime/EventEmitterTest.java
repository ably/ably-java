package io.ably.lib.test.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.InputMismatchException;

import io.ably.lib.util.EventPairEmitter;
import org.junit.Test;

import io.ably.lib.util.EventEmitter;

public class EventEmitterTest {

	private static enum MyEvents {
		event_0,
		event_1
	}

	private enum MySecondaryEvents {
		event_sec_0,
		event_sec_1
	}

	private static class MyEventPayload {
		public MyEvents event;
		public MySecondaryEvents event2 = MySecondaryEvents.event_sec_0;
		public String message;
	}

	private static interface MyListener {
		public void onMyThingHappened(MyEventPayload theThing);
	}

	private static class CountingListener implements MyListener {
		@Override
		public void onMyThingHappened(MyEventPayload theThing) {
			Integer count = counts.get(theThing.event); if(count == null) count = 0;
			counts.put(theThing.event, count + 1);

			if (theThing.event2 != null) {
				Integer count2 = counts2.get(theThing.event2);
				if (count2 == null) count2 = 0;
				counts2.put(theThing.event2, count2 + 1);
			}
		}
		HashMap<MyEvents, Integer> counts = new HashMap<MyEvents, Integer>();
		HashMap<MySecondaryEvents, Integer> counts2 = new HashMap<>();

		void reset() {
			counts.clear(); counts2.clear();
		}
	}

	private static class MyEmitter extends EventEmitter<MyEvents, MyListener> {
		@Override
		protected void apply(MyListener listener, final Object... args) {
			listener.onMyThingHappened((MyEventPayload)args[0]);
		}

		void emitMyEvent(final MyEvents _event, final String _message) {
			emit(_event, new MyEventPayload() {{ event=_event; message=_message; }});
		}
	}

	private static class MyPairEmitter extends EventPairEmitter<MyEvents, MySecondaryEvents, MyListener> {
		@Override
		protected void apply(MyListener listener, final Object... args) {
			listener.onMyThingHappened((MyEventPayload)args[0]);
		}

		void emitMyEventPair(final MyEvents _event, final MySecondaryEvents _event2, final String _message) {
			emitPair(_event, _event2, new MyEventPayload() {{ event=_event; event2=_event2; message=_message; }});
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
		emitter.emitMyEvent(MyEvents.event_0, "on_simple");
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
		emitter.emitMyEvent(MyEvents.event_0, "on_multiple_0");
		emitter.emitMyEvent(MyEvents.event_0, "on_multiple_0");
		emitter.emitMyEvent(MyEvents.event_1, "on_multiple_1");
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
		emitter.emitMyEvent(MyEvents.event_0, "on_multiple_0");
		emitter.emitMyEvent(MyEvents.event_1, "on_multiple_1");
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
		emitter.emitMyEvent(MyEvents.event_0, "on_multiple_0");
		emitter.emitMyEvent(MyEvents.event_1, "on_multiple_1");
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
		emitter.emitMyEvent(MyEvents.event_0, "on_event_simple_0");
		emitter.emitMyEvent(MyEvents.event_0, "on_event_simple_0");
		emitter.emitMyEvent(MyEvents.event_1, "on_event_simple_1");
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
		emitter.emitMyEvent(MyEvents.event_0, "off_event_simple_0");
		emitter.emitMyEvent(MyEvents.event_1, "off_event_simple_1");
		emitter.off(MyEvents.event_0, listener);
		emitter.emitMyEvent(MyEvents.event_0, "off_event_simple_0");
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
		emitter.emitMyEvent(MyEvents.event_0, "once_event_simple_0");
		emitter.emitMyEvent(MyEvents.event_0, "once_event_simple_0");
		emitter.emitMyEvent(MyEvents.event_1, "once_event_simple_1");
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
		emitter.emitMyEvent(MyEvents.event_1, "once_event_simple_1");
		emitter.off(MyEvents.event_0, listener);
		emitter.emitMyEvent(MyEvents.event_0, "once_event_simple_0");
		assertNull(listener.counts.get(MyEvents.event_0));
		assertNull(listener.counts.get(MyEvents.event_1));
	}

	/**
	 * Test event pair emitter
	 */
	@Test
	public void event_pair_test() {
		MyPairEmitter emitter = new MyPairEmitter();
		CountingListener listener = new CountingListener();

		emitter.on(listener);
		emitter.emitMyEventPair(MyEvents.event_0, MySecondaryEvents.event_sec_0, "message");
		emitter.emitMyEventPair(MyEvents.event_1, MySecondaryEvents.event_sec_0, "message");

		assertEquals(listener.counts.get(MyEvents.event_0), Integer.valueOf(1));
		assertEquals(listener.counts.get(MyEvents.event_1), Integer.valueOf(1));
		assertEquals(listener.counts2.get(MySecondaryEvents.event_sec_0), Integer.valueOf(2));

		listener.reset();
		emitter.off();

		emitter.on(MyEvents.event_0, MySecondaryEvents.event_sec_1, listener);
		emitter.emitMyEventPair(MyEvents.event_0, MySecondaryEvents.event_sec_0, "message");
		emitter.emitMyEventPair(MyEvents.event_1, MySecondaryEvents.event_sec_0, "message");
		emitter.emitMyEventPair(MyEvents.event_1, MySecondaryEvents.event_sec_1, "message");
		emitter.emitMyEventPair(MyEvents.event_0, MySecondaryEvents.event_sec_1, "message");

		assertNull(listener.counts.get(MyEvents.event_1));
		assertNull(listener.counts2.get(MySecondaryEvents.event_sec_0));
		assertEquals(listener.counts.get(MyEvents.event_0), Integer.valueOf(1));
		assertEquals(listener.counts2.get(MySecondaryEvents.event_sec_1), Integer.valueOf(1));

		listener.reset();
		emitter.off();

		emitter.on(MyEvents.event_0, listener);
		emitter.emitMyEventPair(MyEvents.event_0, MySecondaryEvents.event_sec_0, "message");
		emitter.emitMyEventPair(MyEvents.event_1, MySecondaryEvents.event_sec_0, "message");
		emitter.emitMyEventPair(MyEvents.event_1, MySecondaryEvents.event_sec_1, "message");
		emitter.emitMyEventPair(MyEvents.event_0, MySecondaryEvents.event_sec_1, "message");

		assertEquals(listener.counts.get(MyEvents.event_0), Integer.valueOf(2));
		assertEquals(listener.counts2.get(MySecondaryEvents.event_sec_0), Integer.valueOf(1));
		assertEquals(listener.counts2.get(MySecondaryEvents.event_sec_1), Integer.valueOf(1));
	}
}
