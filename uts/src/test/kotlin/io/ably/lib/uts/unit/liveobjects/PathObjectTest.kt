package io.ably.lib.uts.unit.liveobjects

import com.google.gson.JsonParser
import io.ably.lib.liveobjects.ValueType
import io.ably.lib.uts.infra.pollUntil
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Derived from UTS `objects/unit/path_object.md` (RTPO1–RTPO14) — the **public** read/navigation surface
 * of `PathObject`.
 *
 * ably-java implements the typed-SDK variant (RTTS): the base `PathObject` exposes only the type-agnostic
 * methods (`path`, `instance`, `compactJson`, `getType`); everything type-specific is reached via an `as*`
 * cast (mapping §4). The root from `setupSyncedChannel` is a `LiveMapPathObject`, so `get`/`at`/`entries`/
 * `keys`/`values`/`size` need no cast on the root; a *navigated* `PathObject` needs `asLiveMap()` first.
 * `PathObject` casts never throw (RTTS5d) — a wrong-typed read returns `null`/empty.
 *
 * Deviations (recorded in `deviations.md`):
 *  - RTPO5b/RTPO6b: `get`/`at` with a non-`String` argument (spec `40003`) is a compile error in the
 *    statically-typed API, so not expressible as a runtime assertion.
 *  - RTPO13/RTPO13c5/RTPO13c/RTPO3c1: `compact()` is not implemented (RTTS3f); `compactJson()` is used —
 *    binary is a base64 string, cycles are `{ "objectId": … }` markers (not shared identity), resolution
 *    failure yields `compactJson() == null`.
 *  - RTPO10/RTPO10d/RTPO11/RTPO11d: the `keys()/values() IS Array` line is a static-type tautology
 *    (`Iterable<…>` guaranteed by the signature); only count + membership are asserted.
 *
 * Number normalisation (mapping §4): counter `value()` is `Double` (assert `100.0`); primitive
 * `asNumber().value()` is a boxed `Number` (normalise via `?.toDouble()`); `size()` is `Long` (assert `7L`).
 */
class PathObjectTest {

    /**
     * @UTS objects/unit/RTPO4/path-string-representation-0
     */
    @Test
    fun `RTPO4 - path returns dot-delimited string`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        assertEquals("", root.path())
        assertEquals("profile", root.get("profile").path())
        assertEquals("profile.email", root.get("profile").asLiveMap().get("email").path())
    }

    /**
     * @UTS objects/unit/RTPO4b/path-escapes-dots-0
     */
    @Test
    fun `RTPO4b - path escapes dots in segments`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val po = root.get("a.b").asLiveMap().get("c")
        assertEquals("a\\.b.c", po.path())
    }

    /**
     * @UTS objects/unit/RTPO5/get-appends-key-0
     */
    @Test
    fun `RTPO5 - get returns new PathObject with appended key`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val child = root.get("profile")
        val grandchild = child.asLiveMap().get("email")

        assertEquals("profile", child.path())
        assertEquals("profile.email", grandchild.path())
        assertTrue(child !== root) // RTPO5: child IS NOT root
    }

    /**
     * @UTS objects/unit/RTPO5b/get-non-string-throws-0
     */
    @Test
    fun `RTPO5b - get throws on non-string key`() = runTest {
        setupSyncedChannel("test")

        // DEVIATION (RTPO5b): spec calls `get(123)` expecting ErrorInfo 40003. ably-java's
        // `LiveMapPathObject.get(@NotNull String)` only accepts a String — a non-string argument is a
        // compile error, never a runtime failure, so this case is not expressible. See deviations.md.
    }

    /**
     * @UTS objects/unit/RTPO6/at-parses-path-0
     */
    @Test
    fun `RTPO6 - at parses dot-delimited path`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val po = root.at("profile.email")
        assertEquals("profile.email", po.path())
        assertEquals("alice@example.com", po.asString().value())
    }

    /**
     * @UTS objects/unit/RTPO6/at-escaped-dots-0
     */
    @Test
    fun `RTPO6 - at respects escaped dots`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val po = root.at("a\\.b.c")
        assertEquals("a\\.b.c", po.path())
    }

    /**
     * @UTS objects/unit/RTPO7/value-counter-0
     */
    @Test
    fun `RTPO7 - value returns counter numeric value`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        assertEquals(100.0, root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTPO7/value-primitive-0
     */
    @Test
    fun `RTPO7 - value returns primitive value`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        assertEquals("Alice", root.get("name").asString().value())
        assertEquals(30.0, root.get("age").asNumber().value()?.toDouble())
        assertEquals(true, root.get("active").asBoolean().value())
    }

    /**
     * @UTS objects/unit/RTPO7d/value-livemap-null-0
     */
    @Test
    fun `RTPO7d - value returns null for LiveMap`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        // Typed SDK (mapping §4): there is no polymorphic value(); a map resolves to no counter/primitive
        // value, so each typed read returns null.
        assertNull(root.get("profile").asLiveCounter().value())
        assertNull(root.get("profile").asNumber().value())
    }

    /**
     * @UTS objects/unit/RTPO7e/value-unresolvable-null-0
     */
    @Test
    fun `RTPO7e - value returns null on resolution failure`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        assertNull(root.get("nonexistent").asLiveMap().get("deep").asString().value())
    }

    /**
     * @UTS objects/unit/RTPO8/instance-live-object-0
     */
    @Test
    fun `RTPO8 - instance returns Instance for LiveObject`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val counterInst = root.get("score").instance()
        assertNotNull(counterInst)
        assertEquals("counter:score@1000", counterInst!!.asLiveCounter().id)

        val mapInst = root.get("profile").instance()
        assertNotNull(mapInst)
        assertEquals("map:profile@1000", mapInst!!.asLiveMap().id)
    }

    /**
     * @UTS objects/unit/RTPO8f/instance-primitive-wrapped-0
     */
    @Test
    fun `RTPO8f - instance returns Instance for primitive`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val nameInst = root.get("name").instance()
        assertNotNull(nameInst)
        // Typed SDK (RTINS3b/RTTS10c): primitive instances are anonymous - no id member exists,
        // so the spec's `name_inst.id() == null` assertion is enforced at compile time.
        assertEquals(ValueType.STRING, nameInst!!.getType())
        assertEquals("Alice", nameInst.asString().value())
    }

    /**
     * @UTS objects/unit/RTPO9/entries-yields-pairs-0
     */
    @Test
    fun `RTPO9 - entries returns key PathObject pairs`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val entries = mutableMapOf<String, String>()
        for ((key, pathObj) in root.entries()) {
            entries[key] = pathObj.path()
        }

        assertEquals("name", entries["name"])
        assertEquals("profile", entries["profile"])
        assertEquals(7, entries.size)
    }

    /**
     * @UTS objects/unit/RTPO9d/entries-non-map-empty-0
     */
    @Test
    fun `RTPO9d - entries returns empty for non-LiveMap`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val entries = root.get("score").asLiveMap().entries().toList()
        assertEquals(0, entries.size)
    }

    /**
     * @UTS objects/unit/RTPO10/keys-returns-array-0
     */
    @Test
    fun `RTPO10 - keys returns array of key strings`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val keys = root.keys().toList()

        // DEVIATION (RTPO10): the `keys IS Array` line is a static-type tautology (keys() returns
        // Iterable<String>); only count + membership are asserted. See deviations.md.
        assertEquals(7, keys.size)
        assertTrue("name" in keys)
        assertTrue("profile" in keys)
        assertTrue("score" in keys)
    }

    /**
     * @UTS objects/unit/RTPO10d/keys-non-map-empty-0
     */
    @Test
    fun `RTPO10d - keys returns empty for non-LiveMap`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        // DEVIATION (RTPO10d): `keys IS Array` tautology omitted. See deviations.md.
        val keys = root.get("score").asLiveMap().keys().toList()
        assertEquals(0, keys.size)
    }

    /**
     * @UTS objects/unit/RTPO11/values-returns-array-0
     */
    @Test
    fun `RTPO11 - values returns array of PathObjects`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val vals = root.values().toList()

        // DEVIATION (RTPO11): `vals IS Array` tautology omitted. See deviations.md.
        assertEquals(7, vals.size)
        val paths = vals.map { it.path() }.toSet()
        assertTrue("name" in paths)
        assertTrue("profile" in paths)
        assertTrue("score" in paths)
    }

    /**
     * @UTS objects/unit/RTPO11d/values-non-map-empty-0
     */
    @Test
    fun `RTPO11d - values returns empty for non-LiveMap`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        // DEVIATION (RTPO11d): `vals IS Array` tautology omitted. See deviations.md.
        val vals = root.get("score").asLiveMap().values().toList()
        assertEquals(0, vals.size)
    }

    /**
     * @UTS objects/unit/RTPO12/size-count-0
     */
    @Test
    fun `RTPO12 - size returns non-tombstoned count`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        assertEquals(7L, root.size())
        assertEquals(3L, root.get("profile").asLiveMap().size())
    }

    /**
     * @UTS objects/unit/RTPO12c/size-non-map-null-0
     */
    @Test
    fun `RTPO12c - size returns null for non-LiveMap`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        assertNull(root.get("score").asLiveMap().size())
        assertNull(root.get("name").asLiveMap().size())
    }

    /**
     * @UTS objects/unit/RTPO13/compact-recursive-0
     */
    @Test
    fun `RTPO13 - compact recursively compacts LiveMap tree`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        // DEVIATION (RTPO13): ably-java implements only `compactJson()` (RTTS3f). Assertions navigate the
        // JsonObject; binary is a base64 string (RTPO14b1). See deviations.md.
        val result = root.compactJson()!!.asJsonObject

        assertEquals("Alice", result.get("name").asString)
        assertEquals(30, result.get("age").asInt)
        assertEquals(true, result.get("active").asBoolean)
        assertEquals(100, result.get("score").asInt)
        assertEquals(JsonParser.parseString("""{"tags":["a","b"]}"""), result.get("data"))
        assertEquals("AQID", result.get("avatar").asString) // DEVIATION: binary as base64
        assertEquals("alice@example.com", result.getAsJsonObject("profile").get("email").asString)
        assertEquals(5, result.getAsJsonObject("profile").get("nested_counter").asInt)
        assertEquals("dark", result.getAsJsonObject("profile").getAsJsonObject("prefs").get("theme").asString)
    }

    /**
     * @UTS objects/unit/RTPO13c5/compact-cycle-detection-0
     */
    @Test
    fun `RTPO13c5 - compact handles cycles via shared reference`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")

        // New key "back_ref" on map:prefs (no existing entry → applies regardless of serial, RTLM9d).
        mockWs.sendToClient(
            buildObjectMessage(
                "test",
                listOf(buildMapSet("map:prefs@1000", "back_ref", dataObjectId("map:profile@1000"), "99", "remote")),
            ),
        )
        pollUntil(5.seconds) {
            root.get("profile").asLiveMap().get("prefs").asLiveMap().get("back_ref").getType() != null
        }

        // DEVIATION (RTPO13c5): spec asserts `result["prefs"]["back_ref"] IS result` (shared object
        // identity). `compactJson()` emits cycles as an `{ "objectId": … }` marker, not shared identity.
        // See deviations.md.
        val result = root.get("profile").compactJson()!!.asJsonObject
        assertEquals(
            "map:profile@1000",
            result.getAsJsonObject("prefs").getAsJsonObject("back_ref").get("objectId").asString,
        )
    }

    /**
     * @UTS objects/unit/RTPO13c/compact-counter-0
     */
    @Test
    fun `RTPO13c - compact returns number for LiveCounter`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        // DEVIATION (RTPO13c): `compact()` → `compactJson()`; a counter compacts to its numeric JSON value.
        assertEquals(100, root.get("score").compactJson()!!.asInt)
    }

    /**
     * @UTS objects/unit/RTPO14/compact-json-0
     */
    @Test
    fun `RTPO14 - compactJson encodes cycles as objectId`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")

        mockWs.sendToClient(
            buildObjectMessage(
                "test",
                listOf(buildMapSet("map:prefs@1000", "back_ref", dataObjectId("map:profile@1000"), "99", "remote")),
            ),
        )
        pollUntil(5.seconds) {
            root.get("profile").asLiveMap().get("prefs").asLiveMap().get("back_ref").getType() != null
        }

        val result = root.get("profile").compactJson()!!.asJsonObject
        assertEquals(
            JsonParser.parseString("""{"objectId":"map:profile@1000"}"""),
            result.getAsJsonObject("prefs").get("back_ref"),
        )
    }

    /**
     * @UTS objects/unit/RTPO3/path-resolution-walk-0
     */
    @Test
    fun `RTPO3 - path resolution walks through LiveMaps`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        assertNull(root.asLiveCounter().value()) // root is a map → no scalar value
        assertEquals(
            "dark",
            root.get("profile").asLiveMap().get("prefs").asLiveMap().get("theme").asString().value(),
        )
    }

    /**
     * @UTS objects/unit/RTPO3a1/intermediate-not-map-0
     */
    @Test
    fun `RTPO3a1 - resolution fails if intermediate is not LiveMap`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        assertNull(root.get("score").asLiveMap().get("something").asString().value())
    }

    /**
     * @UTS objects/unit/RTPO3c1/read-null-on-failure-0
     */
    @Test
    fun `RTPO3c1 - read operation returns null on resolution failure`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        assertNull(root.get("nonexistent").asString().value())
        assertNull(root.get("nonexistent").instance())
        assertNull(root.get("nonexistent").asLiveMap().size())
        // DEVIATION (RTPO3c1): `compact()` → `compactJson()`; resolution failure yields null.
        assertNull(root.get("nonexistent").compactJson())
    }

    /**
     * @UTS objects/unit/RTPO6b/at-non-string-throws-0
     */
    @Test
    fun `RTPO6b - at throws for non-string input`() = runTest {
        setupSyncedChannel("test")

        // DEVIATION (RTPO6b): spec calls `at(123)` expecting ErrorInfo 40003. ably-java's
        // `LiveMapPathObject.at(@NotNull String)` only accepts a String — a non-string argument is a
        // compile error, not a runtime failure, so this case is not expressible. See deviations.md.
    }

    /**
     * @UTS objects/unit/RTPO7/value-bytes-0
     */
    @Test
    fun `RTPO7 - value returns bytes for binary entry`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        assertContentEquals(byteArrayOf(1, 2, 3), root.get("avatar").asBinary().value())
    }

    /**
     * @UTS objects/unit/RTPO14/compact-json-bytes-0
     */
    @Test
    fun `RTPO14 - compactJson encodes bytes as base64 string`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val result = root.compactJson()!!.asJsonObject
        assertEquals("AQID", result.get("avatar").asString)
    }
}
