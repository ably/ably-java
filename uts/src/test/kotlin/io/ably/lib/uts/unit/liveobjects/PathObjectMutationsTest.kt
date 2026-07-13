package io.ably.lib.uts.unit.liveobjects

import io.ably.lib.liveobjects.value.LiveMapValue
import io.ably.lib.types.AblyException
import kotlinx.coroutines.future.await
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Derived from UTS `objects/unit/path_object_mutations.md` (RTPO15–RTPO18, RTPO3c2) — the public
 * `PathObject` write surface (set / remove / increment / decrement).
 *
 * ably-java implements the typed-SDK variant (RTTS): the base `PathObject` has no write methods; writes
 * live on `LiveMapPathObject` (`set`/`remove`, reached via `asLiveMap()`) and `LiveCounterPathObject`
 * (`increment`/`decrement`, via `asLiveCounter()`). `PathObject` `as*` casts never throw (RTTS5d), so a
 * write on the wrong-typed view is expressed by casting to the needed view and asserting the **operation**
 * throws `AblyException` 92007 (mapping §7). Writes on an unresolvable path throw 92005/400 (RTPO3c2).
 * Values are wrapped in the `LiveMapValue` union; counter `value()` is a `Double`. All mutators return
 * `CompletableFuture<Void>` and apply locally on ACK, so a read straight after `await()` reflects the write.
 */
class PathObjectMutationsTest {

    /**
     * @UTS objects/unit/RTPO15/set-delegates-to-map-0
     */
    @Test
    fun `RTPO15 - set delegates to LiveMap set`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        root.set("name", LiveMapValue.of("Bob")).await()

        assertEquals("Bob", root.get("name").asString().value())
    }

    /**
     * @UTS objects/unit/RTPO15/set-nested-path-0
     */
    @Test
    fun `RTPO15 - set on nested path`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        root.get("profile").asLiveMap().set("email", LiveMapValue.of("bob@example.com")).await()

        assertEquals(
            "bob@example.com",
            root.get("profile").asLiveMap().get("email").asString().value(),
        )
    }

    /**
     * @UTS objects/unit/RTPO15d/set-non-map-throws-0
     */
    @Test
    fun `RTPO15d - set on non-LiveMap throws`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        // score resolves to a counter; asLiveMap() never throws (RTTS5d), the set operation surfaces 92007.
        val ex = assertFailsWith<AblyException> {
            root.get("score").asLiveMap().set("key", LiveMapValue.of("value")).await()
        }
        assertEquals(92007, ex.errorInfo.code)
    }

    /**
     * @UTS objects/unit/RTPO16/remove-delegates-to-map-0
     */
    @Test
    fun `RTPO16 - remove delegates to LiveMap remove`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        root.remove("name").await()

        assertNull(root.get("name").asString().value())
    }

    /**
     * @UTS objects/unit/RTPO16d/remove-non-map-throws-0
     */
    @Test
    fun `RTPO16d - remove on non-LiveMap throws`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val ex = assertFailsWith<AblyException> {
            root.get("score").asLiveMap().remove("key").await()
        }
        assertEquals(92007, ex.errorInfo.code)
    }

    /**
     * @UTS objects/unit/RTPO17/increment-delegates-to-counter-0
     */
    @Test
    fun `RTPO17 - increment delegates to LiveCounter increment`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        root.get("score").asLiveCounter().increment(25).await()

        assertEquals(125.0, root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTPO17/increment-default-amount-0
     */
    @Test
    fun `RTPO17 - increment defaults to 1`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        root.get("score").asLiveCounter().increment().await()

        assertEquals(101.0, root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTPO17d/increment-non-counter-throws-0
     */
    @Test
    fun `RTPO17d - increment on non-LiveCounter throws`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        // root is a map; asLiveCounter() never throws (RTTS5d), the increment operation surfaces 92007.
        val ex = assertFailsWith<AblyException> {
            root.asLiveCounter().increment(5).await()
        }
        assertEquals(92007, ex.errorInfo.code)
    }

    /**
     * @UTS objects/unit/RTPO18/decrement-delegates-to-counter-0
     */
    @Test
    fun `RTPO18 - decrement delegates to LiveCounter decrement`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        root.get("score").asLiveCounter().decrement(10).await()

        assertEquals(90.0, root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTPO18/decrement-default-amount-0
     */
    @Test
    fun `RTPO18 - decrement defaults to 1`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        root.get("score").asLiveCounter().decrement().await()

        assertEquals(99.0, root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTPO18d/decrement-non-counter-throws-0
     */
    @Test
    fun `RTPO18d - decrement on non-LiveCounter throws`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val ex = assertFailsWith<AblyException> {
            root.asLiveCounter().decrement(5).await()
        }
        assertEquals(92007, ex.errorInfo.code)
    }

    /**
     * @UTS objects/unit/RTPO3c2/set-unresolvable-throws-0
     */
    @Test
    fun `RTPO3c2 - set on unresolvable path throws`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val ex = assertFailsWith<AblyException> {
            root.get("nonexistent").asLiveMap().get("deep").asLiveMap().set("key", LiveMapValue.of("value")).await()
        }
        assertEquals(92005, ex.errorInfo.code)
        assertEquals(400, ex.errorInfo.statusCode)
    }

    /**
     * @UTS objects/unit/RTPO3c2/increment-unresolvable-throws-0
     */
    @Test
    fun `RTPO3c2 - increment on unresolvable path throws`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val ex = assertFailsWith<AblyException> {
            root.get("nonexistent").asLiveCounter().increment(5).await()
        }
        assertEquals(92005, ex.errorInfo.code)
        assertEquals(400, ex.errorInfo.statusCode)
    }
}
