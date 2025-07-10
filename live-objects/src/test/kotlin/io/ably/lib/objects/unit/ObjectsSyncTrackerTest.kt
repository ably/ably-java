package io.ably.lib.objects.unit

import io.ably.lib.objects.ObjectsSyncTracker
import org.junit.Test
import org.junit.Assert.*

class ObjectsSyncTrackerTest {

    @Test
  fun `should parse valid sync channel serial with syncId and cursor`() {
    val syncTracker = ObjectsSyncTracker("sync-123:cursor-456")

    assertEquals("sync-123", syncTracker.syncId)
    assertFalse(syncTracker.hasSyncStarted("sync-123"))

    assertTrue(syncTracker.hasSyncStarted(null))
    assertTrue(syncTracker.hasSyncStarted("sync-124"))

    assertFalse(syncTracker.hasSyncEnded())
  }

  @Test
  fun `should handle null sync channel serial`() {
    val syncTracker = ObjectsSyncTracker(null)

    assertNull(syncTracker.syncId)
    assertTrue(syncTracker.hasSyncEnded())
  }

  @Test
  fun `should handle empty sync channel serial`() {
    val syncTracker = ObjectsSyncTracker("")

    assertNull(syncTracker.syncId)
    assertTrue(syncTracker.hasSyncEnded())
  }

  @Test
  fun `should handle sync channel serial with special characters`() {
    val syncTracker = ObjectsSyncTracker("sync_123-456:cursor_789-012")

    assertEquals("sync_123-456", syncTracker.syncId)
    assertFalse(syncTracker.hasSyncEnded())
  }

  @Test
  fun `should detect sync sequence ended when syncChannelSerial is null`() {
    val syncTracker = ObjectsSyncTracker(null)

    assertTrue(syncTracker.hasSyncEnded())
  }

  @Test
  fun `should detect sync sequence ended when sync cursor is empty`() {
    val syncTracker = ObjectsSyncTracker("sync-123:")
    assertTrue(syncTracker.hasSyncStarted(null))
    assertTrue(syncTracker.hasSyncStarted(""))
    assertTrue(syncTracker.hasSyncEnded())
  }
}
