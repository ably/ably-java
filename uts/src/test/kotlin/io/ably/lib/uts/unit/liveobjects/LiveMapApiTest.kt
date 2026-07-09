package io.ably.lib.uts.unit.liveobjects

import com.google.gson.JsonObject
import io.ably.lib.liveobjects.ValueType
import io.ably.lib.liveobjects.value.LiveCounter
import io.ably.lib.liveobjects.value.LiveMap
import io.ably.lib.liveobjects.value.LiveMapValue
import io.ably.lib.uts.infra.pollUntil
import kotlinx.coroutines.future.await
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Derived from UTS `objects/unit/live_map_api.md` (RTLM5, RTLM10–RTLM13, RTLM20–RTLM21, RTLM24, RTLMV4,
 * RTLCV4) — the public LiveMap read/write surface (`get` / `size` / `entries` / `keys` / `set` / `remove`).
 *
 * ably-java implements the typed-SDK variant (RTTS): the root from `setupSyncedChannel` is a
 * `LiveMapPathObject`, so `get`/`set`/`remove`/`size`/`entries`/`keys` need no cast; navigated `PathObject`s
 * are narrowed with `as*` casts before a typed read (`asString().value()`, `asNumber().value()`,
 * `asLiveCounter().value()`, …). Write values are wrapped in the `LiveMapValue` union and
 * `LiveMap.create` / `LiveCounter.create` value types.
 *
 * Several spec cases assert on the **wire form of the sent OBJECT ProtocolMessage**
 * (`captured_messages[...].state[...].operation.action / mapSet.value.*`, the COUNTER_CREATE/MAP_CREATE
 * ordering for value-type sets, the MAP_REMOVE wire shape) — that is the internal `WireObjectMessage`
 * graph (objects-mapping §13), not the public API. Those are translated to the equivalent **observable
 * public effect** (the local round-trip read after the auto-ACK echo applies), with the wire-shape
 * sub-assertions recorded as deviations. The table-driven invalid-value case (function / undefined / symbol)
 * is rejected at compile time by the `LiveMapValue` union, so it is not expressible (deviation, §6).
 *
 * All tests use `setupSyncedChannel` (Helpers.kt), which needs the SDK's OBJECT_SYNC processing +
 * `RealtimeObject.get()` — still TODO — so these compile now and run once that lands (translate-only).
 */
class LiveMapApiTest {

    /**
     * @UTS objects/unit/RTLM5/get-string-value-0
     */
    @Test
    fun `RTLM5 - get returns resolved value from LiveMap`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        assertEquals("Alice", root.get("name").asString().value())
        assertEquals(30.0, root.get("age").asNumber().value()?.toDouble())
        assertEquals(true, root.get("active").asBoolean().value())
    }

    /**
     * @UTS objects/unit/RTLM5/get-nonexistent-key-0
     */
    @Test
    fun `RTLM5 - get returns null for non-existent key`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        // No entry at this path: getType() is null and a typed read returns null.
        assertNull(root.get("nonexistent").getType())
        assertNull(root.get("nonexistent").asString().value())
    }

    /**
     * @UTS objects/unit/RTLM5/get-objectid-reference-0
     */
    @Test
    fun `RTLM5 - get resolves objectId to LiveObject`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        // score -> counter:score@1000 (value 100); spec `value() == 100` on a counter.
        assertEquals(100.0, root.get("score").asLiveCounter().value())
        // profile -> map:profile@1000; navigate the nested map and read the email primitive.
        assertEquals("alice@example.com", root.get("profile").asLiveMap().get("email").asString().value())
    }

    /**
     * @UTS objects/unit/RTLM10/size-non-tombstoned-0
     */
    @Test
    fun `RTLM10 - size returns non-tombstoned entry count`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        assertEquals(7L, root.size())
    }

    /**
     * @UTS objects/unit/RTLM11/entries-yields-pairs-0
     */
    @Test
    fun `RTLM11 - entries yields key value pairs`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val entries = mutableListOf<String>()
        for ((key, _) in root.entries()) {
            entries.add(key)
        }

        assertTrue("name" in entries)
        assertTrue("age" in entries)
        assertTrue("active" in entries)
        assertTrue("score" in entries)
        assertTrue("profile" in entries)
        assertTrue("data" in entries)
        assertTrue("avatar" in entries)
        assertEquals(7, entries.size)
    }

    /**
     * @UTS objects/unit/RTLM12/keys-0
     */
    @Test
    fun `RTLM12 - keys yields only keys`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val keys = root.keys().toList()

        assertEquals(7, keys.size)
        assertTrue("name" in keys)
    }

    /**
     * @UTS objects/unit/RTLM20/set-sends-map-set-0
     */
    @Test
    fun `RTLM20 - set sends MAP_SET message`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        root.set("name", LiveMapValue.of("Bob")).await()

        // DEVIATION (RTLM20e2/e3/e6/e7c, RTLM20h2): the spec asserts on the sent OBJECT ProtocolMessage's
        // wire shape (`captured_messages[0].state[0].operation.action == "MAP_SET"`, `objectId == "root"`,
        // `mapSet.key`, `mapSet.value.string`). That is the internal WireObjectMessage graph (§13), not the
        // public API. We assert the equivalent observable effect: the MAP_SET applies locally after the ACK
        // echo, so the typed read returns the new value. See deviations.md.
        pollUntil(5.seconds) { root.get("name").asString().value() == "Bob" }
        assertEquals("Bob", root.get("name").asString().value())
    }

    /**
     * @UTS objects/unit/RTLM20/set-value-types-0
     */
    @Test
    fun `RTLM20 - set with different value types`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        root.set("num_key", LiveMapValue.of(42)).await()
        root.set("bool_key", LiveMapValue.of(false)).await()
        val nested = JsonObject().apply { addProperty("nested", true) }
        root.set("json_key", LiveMapValue.of(nested)).await()

        // DEVIATION (RTLM20e7b/d/e): the spec asserts on the sent wire `mapSet.value.number / .boolean /
        // .json`. Those are internal WireObjectMessage fields (§13). We assert the equivalent observable
        // effect: each value round-trips through the local graph as the matching typed value.
        pollUntil(5.seconds) { root.get("json_key").getType() == ValueType.JSON_OBJECT }
        assertEquals(42.0, root.get("num_key").asNumber().value()?.toDouble())
        assertEquals(false, root.get("bool_key").asBoolean().value())
        assertEquals(nested, root.get("json_key").asJsonObject().value())
    }

    /**
     * @UTS objects/unit/RTLM20/set-bytes-value-0
     */
    @Test
    fun `RTLM20 - set with bytes value type`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val bytes = byteArrayOf(1, 2, 3)
        root.set("binary_data", LiveMapValue.of(bytes)).await()

        // DEVIATION (RTLM20e7f): the spec asserts the sent wire `mapSet.value.bytes == "AQID"` (base64).
        // That is the internal WireObjectMessage encoding (§13). We assert the equivalent observable effect:
        // the binary value round-trips through the local graph byte-for-byte.
        pollUntil(5.seconds) { root.get("binary_data").getType() == ValueType.BINARY }
        assertEquals(bytes.toList(), root.get("binary_data").asBinary().value()?.toList())
    }

    /**
     * @UTS objects/unit/RTLM20e7g/set-counter-value-type-0
     */
    @Test
    fun `RTLM20e7g - set with LiveCounterValueType generates COUNTER_CREATE plus MAP_SET`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        root.set("new_counter", LiveMapValue.of(LiveCounter.create(50))).await()

        // DEVIATION (RTLM20e7g1/e7g2, RTLM20h1): the spec asserts the sent OBJECT carries two wire messages,
        // a COUNTER_CREATE (objectId starts with "counter:") followed by a MAP_SET whose value.objectId
        // references it. That ordered wire-message generation (RTLCV4) is internal (§13). We assert the
        // equivalent observable effect: a new LiveCounter is created and reachable at the key with its
        // initial value.
        pollUntil(5.seconds) { root.get("new_counter").getType() == ValueType.LIVE_COUNTER }
        assertEquals(ValueType.LIVE_COUNTER, root.get("new_counter").getType())
        assertEquals(50.0, root.get("new_counter").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTLM20e7g/set-map-value-type-0
     */
    @Test
    fun `RTLM20e7g - set with LiveMapValueType generates nested CREATE plus MAP_SET`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        root.set("nested_map", LiveMapValue.of(LiveMap.create(mapOf("key1" to LiveMapValue.of("value1"))))).await()

        // DEVIATION (RTLM20e7g1/e7g2, RTLM20h1): the spec asserts the sent OBJECT carries an ordered list of
        // wire messages (MAP_CREATE with objectId starting "map:" followed by MAP_SET referencing it). That
        // wire-message generation (RTLMV4) is internal (§13). We assert the equivalent observable effect: a
        // new LiveMap is created at the key with its initial entry.
        pollUntil(5.seconds) { root.get("nested_map").getType() == ValueType.LIVE_MAP }
        assertEquals(ValueType.LIVE_MAP, root.get("nested_map").getType())
        assertEquals("value1", root.get("nested_map").asLiveMap().get("key1").asString().value())
    }

    /**
     * @UTS objects/unit/RTLM20h1/set-nested-value-types-0
     */
    @Test
    fun `RTLM20h1 - set with nested LiveMapValueType containing LiveCounterValueType`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        root.set(
            "stats",
            LiveMapValue.of(
                LiveMap.create(
                    mapOf(
                        "count" to LiveMapValue.of(LiveCounter.create(0)),
                        "label" to LiveMapValue.of("test"),
                    ),
                ),
            ),
        ).await()

        // DEVIATION (RTLM20h1, RTLMV4d1/d2): the spec asserts the sent OBJECT carries COUNTER_CREATE,
        // MAP_CREATE, MAP_SET in depth-first order with cross-referencing objectIds. That ordered wire-message
        // generation is internal (§13). We assert the equivalent observable effect: the nested map and its
        // nested counter / primitive resolve locally.
        pollUntil(5.seconds) { root.get("stats").getType() == ValueType.LIVE_MAP }
        val stats = root.get("stats").asLiveMap()
        assertEquals(0.0, stats.get("count").asLiveCounter().value())
        assertEquals("test", stats.get("label").asString().value())
    }

    /**
     * @UTS objects/unit/RTLM21/remove-sends-map-remove-0
     */
    @Test
    fun `RTLM21 - remove sends MAP_REMOVE message`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        root.remove("name").await()

        // DEVIATION (RTLM21e2/e5): the spec asserts the sent wire `operation.action == "MAP_REMOVE"`,
        // `objectId == "root"`, `mapRemove.key == "name"`. That is the internal WireObjectMessage graph
        // (§13). We assert the equivalent observable effect: the MAP_REMOVE applies locally after the ACK
        // echo, so the key no longer resolves.
        pollUntil(5.seconds) { root.get("name").getType() == null }
        assertNull(root.get("name").asString().value())
        assertNull(root.get("name").getType())
    }

    /**
     * @UTS objects/unit/RTLM20/set-applies-locally-0
     */
    @Test
    fun `RTLM20 - set applies locally after ACK`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        root.set("name", LiveMapValue.of("Bob")).await()

        pollUntil(5.seconds) { root.get("name").asString().value() == "Bob" }
        assertEquals("Bob", root.get("name").asString().value())
    }

    /**
     * @UTS objects/unit/RTLM20/set-invalid-values-table-0
     */
    @Test
    fun `RTLM20 - invalid set value types`() = runTest {
        setupSyncedChannel("test")

        // DEVIATION (RTLM20e1, RTLMV4c): the spec feeds deliberately unsupported values (a function,
        // `undefined`, a symbol) and expects ErrorInfo 40013 at runtime. ably-java's `set` takes a
        // `LiveMapValue`, and `LiveMapValue.of(...)` is only overloaded for the supported types
        // (Boolean/Binary/Number/String/JsonArray/JsonObject/LiveCounter/LiveMap) — there is no way to
        // construct a LiveMapValue from a function / undefined / symbol, so these cases are rejected at
        // compile time and are not expressible as a runtime 40013 assertion (§6). See deviations.md.
    }
}
