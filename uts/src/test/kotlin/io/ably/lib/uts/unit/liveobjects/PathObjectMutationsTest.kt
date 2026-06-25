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
 * Derived from UTS `objects/unit/path_object_mutations.md` (RTPO15–RTPO18, RTPO3c2) — write operations
 * through the typed `PathObject` view.
 *
 * ably-java implements the typed-SDK variant (RTTS): the spec's single polymorphic `PathObject` partitions
 * `set`/`remove` onto `LiveMapPathObject` (via `asLiveMap()`) and `increment`/`decrement` onto
 * `LiveCounterPathObject` (via `asLiveCounter()`). The root from `setupSyncedChannel` is already a
 * `LiveMapPathObject`, so `root.set(...)`/`root.remove(...)` need no cast; deeper navigated nodes do.
 *
 * Wrong-type write cases (RTPO15d/16d/17d/18d) and unresolvable-path cases (RTPO3c2) are fully expressible:
 * the `as*` cast never throws (RTTS5d), so we cast to the view whose write method we need, then assert the
 * **operation** itself throws `AblyException` with the spec's error code (92007 wrong type, 92005
 * unresolvable path). No deviations.
 *
 * All tests use `setupSyncedChannel` (helpers.kt), which needs the SDK's OBJECT_SYNC processing +
 * `RealtimeObject.get()` — still TODO — so these compile now and run once that lands (translate-only).
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
    fun `RTPO15d - set on non-LiveMap throws 92007`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        // The cast never throws (RTTS5d); the MAP_SET operation on a counter fails with 92007.
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
    fun `RTPO16d - remove on non-LiveMap throws 92007`() = runTest {
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
    fun `RTPO17d - increment on non-LiveCounter throws 92007`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        // increment on the root map: cast never throws (RTTS5d); the COUNTER_INC operation fails with 92007.
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
    fun `RTPO18d - decrement on non-LiveCounter throws 92007`() = runTest {
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
    fun `RTPO3c2 - set on unresolvable path throws 92005`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val ex = assertFailsWith<AblyException> {
            root.get("nonexistent").asLiveMap().get("deep").asLiveMap()
                .set("key", LiveMapValue.of("value")).await()
        }
        assertEquals(92005, ex.errorInfo.code)
        assertEquals(400, ex.errorInfo.statusCode)
    }

    /**
     * @UTS objects/unit/RTPO3c2/increment-unresolvable-throws-0
     */
    @Test
    fun `RTPO3c2 - increment on unresolvable path throws 92005`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val ex = assertFailsWith<AblyException> {
            root.get("nonexistent").asLiveCounter().increment(5).await()
        }
        assertEquals(92005, ex.errorInfo.code)
        assertEquals(400, ex.errorInfo.statusCode)
    }
}
