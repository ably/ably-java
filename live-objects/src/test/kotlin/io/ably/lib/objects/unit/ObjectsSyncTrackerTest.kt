package io.ably.lib.objects.unit

import io.ably.lib.objects.ObjectsSyncTracker
import org.junit.Test
import org.junit.Assert.*

class ObjectsSyncTrackerTest {

    @Test
  fun `(RTO5a, RTO5a1, RTO5a2) Should parse valid sync channel serial with syncId and cursor`() {
    val syncTracker = ObjectsSyncTracker("sync-123:cursor-456")

    assertEquals("sync-123", syncTracker.syncId)
    assertFalse(syncTracker.hasSyncStarted("sync-123"))
    assertTrue(syncTracker.hasSyncStarted(null))
    assertTrue(syncTracker.hasSyncStarted("sync-124"))

    assertEquals("cursor-456", syncTracker.syncCursor)
    assertFalse(syncTracker.hasSyncEnded())
  }

  @Test
  fun `(RTO5a5) Should handle null sync channel serial`() {
    val syncTracker = ObjectsSyncTracker(null)

    assertNull(syncTracker.syncId)
    assertTrue(syncTracker.hasSyncStarted(null))

    assertNull(syncTracker.syncCursor)
    assertTrue(syncTracker.hasSyncEnded())
  }

  @Test
  fun `(RTO5a5) Should handle empty sync channel serial`() {
    val syncTracker = ObjectsSyncTracker("")

    assertNull(syncTracker.syncId)
    assertTrue(syncTracker.hasSyncStarted(null))

    assertNull(syncTracker.syncCursor)
    assertTrue(syncTracker.hasSyncEnded())
  }

  @Test
  fun `should handle sync channel serial with special characters`() {
    val syncTracker = ObjectsSyncTracker("sync_123-456:cursor_789-012")

    assertEquals("sync_123-456", syncTracker.syncId)

    assertEquals("cursor_789-012", syncTracker.syncCursor)
    assertFalse(syncTracker.hasSyncEnded())
  }

  @Test
  fun `(RTO5a4) should detect sync sequence ended when sync cursor is empty`() {
    val syncTracker = ObjectsSyncTracker("sync-123:")

    assertEquals("sync-123", syncTracker.syncId)
    assertTrue(syncTracker.hasSyncStarted(null))
    assertTrue(syncTracker.hasSyncStarted(""))

    assertEquals("", syncTracker.syncCursor)
    assertTrue(syncTracker.hasSyncEnded())
  }
}
