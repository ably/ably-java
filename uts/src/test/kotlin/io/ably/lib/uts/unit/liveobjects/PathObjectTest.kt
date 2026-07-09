package io.ably.lib.uts.unit.liveobjects

import io.ably.lib.liveobjects.path.PathObject
import io.ably.lib.uts.infra.pollUntil
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Derived from UTS `objects/unit/path_object.md` (RTPO1–RTPO14) — the typed `PathObject` read/navigation
 * surface: `path()`, `get()` / `at()`, `value()`, `instance()`, `entries()` / `keys()` / `values()`,
 * `size()`, `getType()`, and the compacted-snapshot accessor.
 *
 * ably-java implements the typed-SDK variant (RTTS), so the spec's single polymorphic `PathObject.value()`
 * splits across typed `as*()` accessors, each returning `null` (never throwing) on a type mismatch (RTTS5d /
 * RTTS6g). `root` (from `setupSyncedChannel`) is already a `LiveMapPathObject`, so `root.get(...)` needs no
 * cast; deeper navigated nodes are `asLiveMap()`-ed before map ops. Number gotchas: counter `value()` is
 * `Double` (100.0), primitive `asNumber().value()` is a boxed `Number` (normalise with `?.toDouble()`),
 * `size()` is `Long` (7L). Three deviations recorded in `deviations.md`:
 *  - `get(non-string)` / `at(non-string)` failing with 40003 (RTPO5b / RTPO6b) is not expressible — the
 *    signatures take `@NotNull String`, so a non-string argument is a compile error.
 *  - `compact()` is not implemented (RTTS3f); `compactJson()` is the supported snapshot (RTPO13 / RTPO13b5 /
 *    RTPO13c, and the `compact()` sub-assertion of RTPO3c1).
 *
 * All tests use `setupSyncedChannel` (Helpers.kt), which needs the SDK's OBJECT_SYNC processing +
 * `RealtimeObject.get()` — still TODO — so these compile now and run once that lands (translate-only).
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
        assertTrue(child !== root) // RTPO5c: new PathObject, not the same instance as root
    }

    /**
     * @UTS objects/unit/RTPO5b/get-non-string-throws-0
     */
    @Test
    fun `RTPO5b - get throws on non-string key`() = runTest {
        setupSyncedChannel("test")

        // DEVIATION (RTPO5b): spec passes a non-string key (`root.get(123)`) and expects ErrorInfo 40003.
        // ably-java's `LiveMapPathObject.get(@NotNull String)` only accepts a String, so a non-string
        // argument is a compile error, not a runtime failure — the case is not expressible. See deviations.md.
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

        val po = root.at("a\\.b.c") // segments ["a.b", "c"]

        assertEquals("a\\.b.c", po.path())
    }

    /**
     * @UTS objects/unit/RTPO6b/at-non-string-throws-0
     */
    @Test
    fun `RTPO6b - at throws for non-string input`() = runTest {
        setupSyncedChannel("test")

        // DEVIATION (RTPO6b): spec passes a non-string path (`root.at(123)`) and expects ErrorInfo 40003.
        // ably-java's `LiveMapPathObject.at(@NotNull String)` only accepts a String, so a non-string argument
        // is a compile error, not a runtime failure — the case is not expressible. See deviations.md.
    }

    /**
     * @UTS objects/unit/RTPO7/value-counter-0
     */
    @Test
    fun `RTPO7 - value returns counter numeric value`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        // Counter value() is Double (RTPO7c -> LiveCounter#value); assert 100.0.
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

        // RTPO7e: a LiveMap has no scalar value; the typed counter/primitive accessors return null.
        assertNull(root.get("profile").asLiveCounter().value())
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
     * @UTS objects/unit/RTPO7/value-bytes-0
     */
    @Test
    fun `RTPO7 - value returns bytes for binary entry`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        // STANDARD_POOL_OBJECTS stores avatar as base64 "AQID" == bytes [1, 2, 3].
        assertEquals(listOf<Byte>(1, 2, 3), root.get("avatar").asBinary().value()?.toList())
    }

    /**
     * @UTS objects/unit/RTPO8/instance-live-object-0
     */
    @Test
    fun `RTPO8 - instance returns Instance for LiveObject`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val counterInst = root.get("score").instance()
        assertNotNull(counterInst) // RTPO8c: IS Instance
        assertEquals("counter:score@1000", counterInst!!.asLiveCounter().id)

        val mapInst = root.get("profile").instance()
        assertNotNull(mapInst) // RTPO8c: IS Instance
        assertEquals("map:profile@1000", mapInst!!.asLiveMap().id)
    }

    /**
     * @UTS objects/unit/RTPO8c/instance-primitive-null-0
     */
    @Test
    fun `RTPO8c - instance returns null for primitive`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        assertNull(root.get("name").instance())
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

        assertEquals(7, vals.size)
        // Each element is a PathObject whose path is the key.
        val paths = mutableSetOf<String>()
        for (v in vals) {
            paths.add(v.path())
        }
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

        // DEVIATION (RTPO13): ably-java does not implement `compact()` (RTTS3f); `compactJson()` is the
        // supported recursively-compacted snapshot. Binary is base64-encoded rather than raw bytes, so the
        // avatar assertion checks the base64 string. Assertions navigate the JsonObject. See deviations.md.
        val result = root.compactJson()!!.asJsonObject

        assertEquals("Alice", result.get("name").asString)
        assertEquals(30, result.get("age").asInt)
        assertEquals(true, result.get("active").asBoolean)
        assertEquals(100, result.get("score").asInt)
        assertEquals("a", result.getAsJsonObject("data").getAsJsonArray("tags").get(0).asString)
        assertEquals("b", result.getAsJsonObject("data").getAsJsonArray("tags").get(1).asString)
        assertEquals("AQID", result.get("avatar").asString) // base64 of bytes [1, 2, 3]
        val profile = result.getAsJsonObject("profile")
        assertEquals("alice@example.com", profile.get("email").asString)
        assertEquals(5, profile.get("nested_counter").asInt)
        assertEquals("dark", profile.getAsJsonObject("prefs").get("theme").asString)
    }

    /**
     * @UTS objects/unit/RTPO13b5/compact-cycle-detection-0
     */
    @Test
    fun `RTPO13b5 - compact handles cycles via shared reference`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")

        // Introduce a cycle: map:prefs@1000.back_ref points back at map:profile@1000.
        mockWs.sendToClient(
            buildObjectMessage(
                "test",
                listOf(buildMapSet("map:prefs@1000", "back_ref", dataObjectId("map:profile@1000"), "99", "remote")),
            ),
        )
        pollUntil(5.seconds) { root.get("profile").asLiveMap().get("prefs").asLiveMap().get("back_ref").exists() }

        // DEVIATION (RTPO13b5): spec asserts `result["prefs"]["back_ref"] IS result` — native object identity
        // from the unimplemented `compact()` (RTTS3f). `compactJson()` represents the cycle as an
        // `{ "objectId": ... }` marker instead (see RTPO14), so the identity assertion is replaced by the
        // objectId-marker assertion. See deviations.md.
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

        // DEVIATION (RTPO13c): `compact()` is unimplemented (RTTS3f); `compactJson()` is used. A LiveCounter
        // compacts to its numeric JSON value. See deviations.md.
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
        pollUntil(5.seconds) { root.get("profile").asLiveMap().get("prefs").asLiveMap().get("back_ref").exists() }

        val result = root.get("profile").compactJson()!!.asJsonObject

        assertEquals(
            "map:profile@1000",
            result.getAsJsonObject("prefs").getAsJsonObject("back_ref").get("objectId").asString,
        )
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

    /**
     * @UTS objects/unit/RTPO3/path-resolution-walk-0
     */
    @Test
    fun `RTPO3 - path resolution walks through LiveMaps`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        // RTPO3b: empty path resolves to root (a LiveMap) -> no scalar value.
        assertNull(root.asLiveCounter().value())
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

        // score resolves to a counter, so navigating past it fails to resolve -> read returns null.
        assertNull(root.get("score").asLiveMap().get("something").asString().value())
    }

    /**
     * @UTS objects/unit/RTPO3c1/read-null-on-failure-0
     */
    @Test
    fun `RTPO3c1 - read operation returns null on resolution failure`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val nonexistent: PathObject = root.get("nonexistent")
        assertNull(nonexistent.asString().value())
        assertNull(nonexistent.instance())
        assertNull(nonexistent.asLiveMap().size())
        // DEVIATION (RTPO3c1): spec asserts `compact() == null` on resolution failure; `compact()` is
        // unimplemented (RTTS3f), so `compactJson()` is asserted null instead. See deviations.md.
        assertNull(nonexistent.compactJson())
    }
}
