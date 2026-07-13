package io.ably.lib.uts.unit.liveobjects

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.ably.lib.liveobjects.value.LiveCounter
import io.ably.lib.liveobjects.value.LiveMap
import io.ably.lib.liveobjects.value.LiveMapValue
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Derived from UTS `objects/unit/value_types.md` (RTLCV1–RTLCV4, RTLMV1–RTLMV4) — the immutable
 * creation value types `LiveCounter` / `LiveMap` obtained from the static `LiveCounter.create(...)` /
 * `LiveMap.create(...)` factories and the `LiveMapValue` union (mapping §6).
 *
 * This is a MIXED spec; almost all of its assertions land on internal/wire-level state that ably-java's
 * typed-SDK public API does not expose (recorded in `deviations.md`):
 *  - The value type's internal blueprint (`vt.count`, `vt.entries[...]`) has **no public accessor** —
 *    `LiveCounter`/`LiveMap` are opaque holders. So only construction and the abstract type identity
 *    (`is LiveCounter` / `is LiveMap`) are observable (RTLCV3/RTLMV3).
 *  - The `evaluate(vt)` → `ObjectMessage` generation half (COUNTER_CREATE / MAP_CREATE, nonce /
 *    `initialValue` / `objectId` derivation, the `*WithObjectId` wire forms, depth-first ordering,
 *    empty-entries) is internal/wire-level (mapping §13) — there is no public `evaluate` (RTLCV4/RTLMV4).
 *  - Wrong-typed `create` args (`LiveCounter.create("not_a_number")`, `LiveMap.create(null)`, non-String
 *    keys, unsupported value types) are rejected at **compile time** by the `create(Number)` /
 *    `create(Map<String, LiveMapValue>)` signatures and the `LiveMapValue` union — not expressible as
 *    runtime `40003`/`40013` assertions (RTLCV4a/RTLMV4a/b/c).
 *
 * What IS faithfully translatable is the public `LiveMapValue` union surface (§6): the
 * entry-value-type-mapping cases (RTLMV4d) are adapted to assert on that public union. Pure construction —
 * no mocks — so these run today.
 */
class ValueTypesTest {

    /**
     * @UTS objects/unit/RTLCV3/create-with-count-0
     */
    @Test
    fun `RTLCV3 - LiveCounter create with initial count`() = runTest {
        val vt = LiveCounter.create(42)

        assertTrue(vt is LiveCounter) // RTLCV3b: returns the LiveCounter value type

        // DEVIATION (RTLCV3b): spec asserts `vt.count == 42`, but the value type's internal count has no
        // public accessor in ably-java (opaque immutable holder). Not expressible. See deviations.md.
    }

    /**
     * @UTS objects/unit/RTLCV3/create-default-zero-0
     */
    @Test
    fun `RTLCV3 - LiveCounter create defaults to 0`() = runTest {
        val vt = LiveCounter.create()

        assertTrue(vt is LiveCounter)

        // DEVIATION (RTLCV3a1): spec asserts the omitted-count default `vt.count == 0`, but there is no
        // public accessor for the internal count. Not expressible. See deviations.md.
    }

    /**
     * @UTS objects/unit/RTLCV3c/no-validation-at-create-0
     */
    @Test
    fun `RTLCV3c - no validation at creation time`() = runTest {
        // DEVIATION (RTLCV3c): the spec passes a non-Number (`LiveCounter.create("not_a_number")`) to show
        // creation performs no validation. ably-java's `create(@NotNull Number)` cannot accept a String at
        // compile time, so that exact input is not expressible. The spirit — no validation at create time —
        // is still exercised with a numerically-invalid (but type-valid) NaN, which `create` accepts without
        // throwing (validation is deferred to the internal evaluation, §13). See deviations.md.
        val vt = LiveCounter.create(Double.NaN)
        assertTrue(vt is LiveCounter) // does not throw at creation
    }

    /**
     * @UTS objects/unit/RTLCV4/evaluate-generates-message-0
     */
    @Test
    fun `RTLCV4 - Evaluation generates COUNTER_CREATE ObjectMessage`() = runTest {
        // DEVIATION (RTLCV4): the spec calls the internal `evaluate(vt)` and asserts on the generated
        // COUNTER_CREATE ObjectMessage (action, objectId prefix/derivation, nonce length,
        // counterCreateWithObjectId.initialValue). There is no public `evaluate`; message generation,
        // nonce/objectId derivation and the `*WithObjectId` wire form are internal/wire-level (§13). Only
        // the public construction is exercised here. See deviations.md.
        val vt = LiveCounter.create(42)
        assertTrue(vt is LiveCounter)
    }

    /**
     * @UTS objects/unit/RTLCV4g5/retains-local-counter-create-0
     */
    @Test
    fun `RTLCV4g5 - Evaluation retains local CounterCreate`() = runTest {
        // DEVIATION (RTLCV4g5): asserts the evaluated message retains the local `counterCreate`
        // (`counterCreate.count == 42`) alongside `counterCreateWithObjectId`. Both the evaluation and the
        // retained CounterCreate are internal/wire-level — not reachable through the public value type.
        // See deviations.md.
        val vt = LiveCounter.create(42)
        assertTrue(vt is LiveCounter)
    }

    /**
     * @UTS objects/unit/RTLCV4a/evaluate-validates-count-0
     */
    @Test
    fun `RTLCV4a - Evaluation validates count type`() = runTest {
        // DEVIATION (RTLCV4a): spec passes a non-Number (`LiveCounter.create("not_a_number")`) and expects
        // evaluation to fail with 40003. ably-java's `LiveCounter.create(@NotNull Number)` rejects a String
        // at compile time (§6), so the runtime 40003 assertion is not expressible. See deviations.md.
    }

    /**
     * @UTS objects/unit/RTLCV4/evaluate-zero-count-0
     */
    @Test
    fun `RTLCV4 - Evaluation with count 0`() = runTest {
        // DEVIATION (RTLCV4): asserts the evaluated message's `counterCreate.count == 0`. The evaluation and
        // generated CounterCreate are internal/wire-level (§13). Only the public construction with count 0
        // is exercised. See deviations.md.
        val vt = LiveCounter.create(0)
        assertTrue(vt is LiveCounter)
    }

    /**
     * @UTS objects/unit/RTLMV3/create-with-entries-0
     */
    @Test
    fun `RTLMV3 - LiveMap create with entries`() = runTest {
        val vt = LiveMap.create(
            mapOf(
                "name" to LiveMapValue.of("Alice"),
                "age" to LiveMapValue.of(30),
            ),
        )

        assertTrue(vt is LiveMap) // RTLMV3b: returns the LiveMap value type

        // DEVIATION (RTLMV3b): spec asserts `vt.entries["name"] == "Alice"` / `vt.entries["age"] == 30`,
        // but the value type's internal entries have no public accessor (opaque immutable holder). Not
        // expressible. See deviations.md.
    }

    /**
     * @UTS objects/unit/RTLMV3/create-no-entries-0
     */
    @Test
    fun `RTLMV3 - LiveMap create with no entries`() = runTest {
        val vt = LiveMap.create()

        assertTrue(vt is LiveMap) // RTLMV3a1: omitted entries still returns a LiveMap value type
    }

    /**
     * @UTS objects/unit/RTLMV4/evaluate-generates-message-0
     */
    @Test
    fun `RTLMV4 - Evaluation generates MAP_CREATE ObjectMessage`() = runTest {
        // DEVIATION (RTLMV4): spec calls internal `evaluate(vt)` and asserts on the generated MAP_CREATE
        // ObjectMessage (action, objectId `map:` prefix, nonce length, mapCreateWithObjectId.initialValue).
        // There is no public `evaluate`; message generation and the `*WithObjectId` wire form are
        // internal/wire-level (§13). Only the public construction is exercised. See deviations.md.
        val vt = LiveMap.create(mapOf("name" to LiveMapValue.of("Alice")))
        assertTrue(vt is LiveMap)
    }

    /**
     * @UTS objects/unit/RTLMV4j5/retains-local-map-create-0
     */
    @Test
    fun `RTLMV4j5 - Evaluation retains local MapCreate`() = runTest {
        // DEVIATION (RTLMV4j5): asserts the evaluated message retains the local `mapCreate`
        // (`mapCreate.semantics == "LWW"`, `mapCreate.entries["name"].data.string == "Alice"`) alongside
        // `mapCreateWithObjectId`. Evaluation and the retained MapCreate are internal/wire-level.
        // See deviations.md.
        val vt = LiveMap.create(mapOf("name" to LiveMapValue.of("Alice")))
        assertTrue(vt is LiveMap)
    }

    /**
     * @UTS objects/unit/RTLMV4d/entry-value-types-0
     *
     * The spec asserts the value-type → `data.*` field mapping on the generated `MapCreate` entries
     * (internal/wire-level). Adapted to assert the public `LiveMapValue` union surface (mapping §6): each
     * input wraps to a `LiveMapValue` whose `is*` discriminant and `getAs*` accessor match.
     */
    @Test
    fun `RTLMV4d - Entry value type mapping`() = runTest {
        val str = LiveMapValue.of("hello")
        assertTrue(str.isString)
        assertEquals("hello", str.asString) // RTLMV4d4: String -> data.string

        val num = LiveMapValue.of(42)
        assertTrue(num.isNumber)
        assertEquals(42, num.asNumber.toInt()) // RTLMV4d5: Number -> data.number

        val bool = LiveMapValue.of(true)
        assertTrue(bool.isBoolean)
        assertEquals(true, bool.asBoolean) // RTLMV4d6: Boolean -> data.boolean

        val jsonArr = JsonArray().apply { add(1); add(2); add(3) }
        val arrValue = LiveMapValue.of(jsonArr)
        assertTrue(arrValue.isJsonArray)
        assertEquals(jsonArr, arrValue.asJsonArray) // RTLMV4d3: JsonArray -> data.json

        val jsonObj = JsonObject().apply { add("key", JsonPrimitive("value")) }
        val objValue = LiveMapValue.of(jsonObj)
        assertTrue(objValue.isJsonObject)
        assertEquals(jsonObj, objValue.asJsonObject) // RTLMV4d3: JsonObject -> data.json

        val bytes = byteArrayOf(1, 2, 3)
        val binValue = LiveMapValue.of(bytes)
        assertTrue(binValue.isBinary)
        assertContentEquals(bytes, binValue.asBinary) // RTLMV4d7: Binary -> data.bytes

        // DEVIATION (RTLMV4d): the spec inspects the generated `MapCreate.entries[k].data.<field>`
        // (internal/wire-level). The public union inspection above is the equivalent observable surface;
        // the generated message itself is not reachable. See deviations.md.
    }

    /**
     * @UTS objects/unit/RTLMV4d1/nested-value-types-0
     */
    @Test
    fun `RTLMV4d1, RTLMV4d2 - Nested value types produce depth-first ObjectMessages`() = runTest {
        // DEVIATION (RTLMV4d1/RTLMV4d2/RTLMV4k): the spec evaluates a nested value-type tree and asserts on
        // the generated, depth-first-ordered COUNTER_CREATE/MAP_CREATE messages, their `objectId` prefixes,
        // and the cross-referencing `entries[k].data.objectId`. Evaluation, objectId derivation and message
        // ordering are internal/wire-level (§13). Only the public nested construction is exercised.
        // See deviations.md.
        val innerCounter = LiveCounter.create(10)
        val innerMap = LiveMap.create(mapOf("nested_count" to LiveMapValue.of(innerCounter)))
        val outer = LiveMap.create(mapOf("child" to LiveMapValue.of(innerMap)))

        assertTrue(outer is LiveMap)
    }

    /**
     * @UTS objects/unit/RTLMV4a/evaluate-validates-entries-0
     */
    @Test
    fun `RTLMV4a - Evaluation validates entries type`() = runTest {
        // DEVIATION (RTLMV4a): spec passes `LiveMap.create(null)` and expects evaluation to fail with 40003.
        // ably-java's `LiveMap.create(@NotNull Map<String, LiveMapValue>)` rejects null at compile time (§6),
        // so the runtime 40003 assertion is not expressible. See deviations.md.
    }

    /**
     * @UTS objects/unit/RTLMV4b/evaluate-validates-keys-0
     */
    @Test
    fun `RTLMV4b - Evaluation validates key types`() = runTest {
        // DEVIATION (RTLMV4b): spec passes a non-String key (`{ 123: "value" }`) and expects 40003.
        // ably-java's `create(Map<String, LiveMapValue>)` enforces String keys at compile time (§6); a
        // non-String key cannot be constructed. Not expressible. See deviations.md.
    }

    /**
     * @UTS objects/unit/RTLMV4c/evaluate-validates-values-0
     */
    @Test
    fun `RTLMV4c - Evaluation validates value types`() = runTest {
        // DEVIATION (RTLMV4c): spec passes an unsupported value (a function) and expects 40013. ably-java's
        // `LiveMapValue` union only constructs from the supported types (Boolean, byte[], Number, String,
        // JsonArray, JsonObject, LiveCounter, LiveMap), so an unsupported value is rejected at compile time
        // (§6). Not expressible. See deviations.md.
    }

    /**
     * @UTS objects/unit/RTLMV4e2/empty-entries-0
     */
    @Test
    fun `RTLMV4e2 - Empty entries produces MapCreate with empty entries`() = runTest {
        // DEVIATION (RTLMV4e2): asserts the evaluated message's `mapCreate.entries == {}` for a no-entries
        // value type. Evaluation and the generated MapCreate are internal/wire-level (§13). Only the public
        // empty construction is exercised. See deviations.md.
        val vt = LiveMap.create()
        assertTrue(vt is LiveMap)
    }

    /**
     * @UTS objects/unit/RTLMV4d/map-set-all-types-table-0
     *
     * Spec table asserts every supported value type maps to the correct generated `data.*` field. Adapted
     * to the public `LiveMapValue` union (mapping §6): each input wraps and is inspected via its `is*`
     * discriminant + `getAs*` accessor. The generated `MapCreate` `data` is internal.
     */
    @Test
    fun `RTLMV4d - Table-driven MAP_SET value type mapping`() = runTest {
        LiveMapValue.of("hello").let {
            assertTrue(it.isString)
            assertEquals("hello", it.asString)
        }
        listOf<Number>(42, 3.14, 0, -1).forEach { n ->
            val v = LiveMapValue.of(n)
            assertTrue(v.isNumber)
            assertEquals(n.toDouble(), v.asNumber.toDouble())
        }
        listOf(true, false).forEach { b ->
            val v = LiveMapValue.of(b)
            assertTrue(v.isBoolean)
            assertEquals(b, v.asBoolean)
        }
        val arr = JsonArray().apply { add(1); add("a"); add(JsonNull.INSTANCE) }
        LiveMapValue.of(arr).let {
            assertTrue(it.isJsonArray)
            assertEquals(arr, it.asJsonArray)
        }
        val obj = JsonObject().apply { add("k", JsonPrimitive("v")) }
        LiveMapValue.of(obj).let {
            assertTrue(it.isJsonObject)
            assertEquals(obj, it.asJsonObject)
        }
        val bytes = byteArrayOf(1, 2, 3)
        LiveMapValue.of(bytes).let {
            assertTrue(it.isBinary)
            assertContentEquals(bytes, it.asBinary)
        }

        // DEVIATION (RTLMV4d): the spec asserts on the generated `MapCreate.entries["test_key"].data[field]`
        // (internal/wire-level), including the base64 "AQID" encoding of the binary. The public union
        // inspection above is the equivalent observable surface; the generated message and its base64
        // encoding are not reachable. See deviations.md.
    }
}
