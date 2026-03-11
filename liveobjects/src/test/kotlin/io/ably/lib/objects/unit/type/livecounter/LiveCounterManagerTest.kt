package io.ably.lib.objects.unit.type.livecounter

import io.ably.lib.objects.*
import io.ably.lib.objects.CounterCreate
import io.ably.lib.objects.CounterInc
import io.ably.lib.objects.MapCreate
import io.ably.lib.objects.unit.LiveCounterManager
import io.ably.lib.objects.unit.TombstonedAt
import io.ably.lib.objects.unit.getDefaultLiveCounterWithMockedDeps
import io.ably.lib.types.AblyException
import org.junit.Test
import kotlin.test.*

class DefaultLiveCounterManagerTest {

  @Test
  fun `(RTLC6, RTLC6b, RTLC6c) DefaultLiveCounter should override counter data with state from sync`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps()
    val liveCounterManager = liveCounter.LiveCounterManager

    // Set initial data
    liveCounter.data.set(10.0)

    val objectState = ObjectState(
      objectId = "testCounterId",
      counter = ObjectsCounter(count = 25.0),
      siteTimeserials = mapOf("site3" to "serial3", "site4" to "serial4"),
      tombstone = false,
    )

    val update = liveCounterManager.applyState(objectState, null)

    assertFalse(liveCounter.createOperationIsMerged) // RTLC6b
    assertEquals(25.0, liveCounter.data.get()) // RTLC6c
    assertEquals(15.0, update.update.amount) // Difference between old and new data
  }


  @Test
  fun `(RTLC6, RTLC6d) DefaultLiveCounter should merge create operation in state from sync`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps()
    val liveCounterManager = liveCounter.LiveCounterManager

    // Set initial data
    liveCounter.data.set(5.0)

    val createOp = ObjectOperation(
      action = ObjectOperationAction.CounterCreate,
      objectId = "testCounterId",
      counterCreate = CounterCreate(count = 10.0)
    )

    val objectState = ObjectState(
      objectId = "testCounterId",
      counter = ObjectsCounter(count = 15.0),
      createOp = createOp,
      siteTimeserials = mapOf("site1" to "serial1"),
      tombstone = false,
    )

    // RTLC6d - Merge initial data from create operation
    val update = liveCounterManager.applyState(objectState, null)

    assertEquals(25.0, liveCounter.data.get()) // 15 from state + 10 from create op
    assertEquals(20.0, update.update.amount) // Total change
  }


  @Test
  fun `(RTLC7d1b) LiveCounterManager applyOperation returns true for COUNTER_CREATE`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps()
    val liveCounterManager = liveCounter.LiveCounterManager

    val operation = ObjectOperation(
      action = ObjectOperationAction.CounterCreate,
      objectId = "testCounterId",
      counterCreate = CounterCreate(count = 10.0)
    )

    // RTLC7d1b - Should return true for successful COUNTER_CREATE
    val result = liveCounterManager.applyOperation(operation, null)
    assertTrue(result, "applyOperation should return true for COUNTER_CREATE")
  }

  @Test
  fun `(RTLC7d2b) LiveCounterManager applyOperation returns true for COUNTER_INC`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps()
    val liveCounterManager = liveCounter.LiveCounterManager

    val operation = ObjectOperation(
      action = ObjectOperationAction.CounterInc,
      objectId = "testCounterId",
      counterInc = CounterInc(number = 5.0)
    )

    // RTLC7d2b - Should return true for successful COUNTER_INC
    val result = liveCounterManager.applyOperation(operation, null)
    assertTrue(result, "applyOperation should return true for COUNTER_INC")
  }

  @Test
  fun `(RTLC7d4b) LiveCounterManager applyOperation returns true for OBJECT_DELETE`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps()
    val liveCounterManager = liveCounter.LiveCounterManager

    val operation = ObjectOperation(
      action = ObjectOperationAction.ObjectDelete,
      objectId = "testCounterId",
    )

    // RTLC7d4b - Should return true for OBJECT_DELETE (tombstone)
    val result = liveCounterManager.applyOperation(operation, null)
    assertTrue(result, "applyOperation should return true for OBJECT_DELETE")
    assertTrue(liveCounter.isTombstoned, "counter should be tombstoned after ObjectDelete")
  }

  @Test
  fun `(RTLC7, RTLC7d3) LiveCounterManager should return false for unsupported action`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps()
    val liveCounterManager = liveCounter.LiveCounterManager

    val operation = ObjectOperation(
      action = ObjectOperationAction.MapCreate, // Unsupported action for counter
      objectId = "testCounterId",
      mapCreate = MapCreate(semantics = ObjectsMapSemantics.LWW, entries = emptyMap())
    )

    // RTLC7d3 - Should return false for unsupported action (no longer throws)
    val result = liveCounterManager.applyOperation(operation, null)
    assertFalse(result, "Should return false for unsupported action")
  }

  @Test
  fun `(RTLC7, RTLC7d1, RTLC8) LiveCounterManager should apply counter create operation`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps()
    val liveCounterManager = liveCounter.LiveCounterManager

    val operation = ObjectOperation(
      action = ObjectOperationAction.CounterCreate,
      objectId = "testCounterId",
      counterCreate = CounterCreate(count = 20.0)
    )

    // RTLC7d1 - Apply counter create operation
     liveCounterManager.applyOperation(operation, null)

    assertEquals(20.0, liveCounter.data.get()) // Should be set to counter count
    assertTrue(liveCounter.createOperationIsMerged) // Should be marked as merged
  }

  @Test
  fun `(RTLC8, RTLC8b) LiveCounterManager should skip counter create operation if already merged`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps()
    val liveCounterManager = liveCounter.LiveCounterManager

    liveCounter.data.set(4.0) // Start with 4

    // Set create operation as already merged
    liveCounter.createOperationIsMerged = true

    val operation = ObjectOperation(
      action = ObjectOperationAction.CounterCreate,
      objectId = "testCounterId",
      counterCreate = CounterCreate(count = 20.0)
    )

    // RTLC8b - Should skip if already merged
    liveCounterManager.applyOperation(operation, null)

    assertEquals(4.0, liveCounter.data.get()) // Should not change (still 0)
    assertTrue(liveCounter.createOperationIsMerged) // Should remain merged
  }

  @Test
  fun `(RTLC8, RTLC8c) LiveCounterManager should apply counter create operation if not merged`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps()
    val liveCounterManager = liveCounter.LiveCounterManager
    // Set initial data
    liveCounter.data.set(10.0) // Start with 10

    // Set create operation as not merged
    liveCounter.createOperationIsMerged = false

    val operation = ObjectOperation(
      action = ObjectOperationAction.CounterCreate,
      objectId = "testCounterId",
      counterCreate = CounterCreate(count = 20.0)
    )

    // RTLC8c - Should apply if not merged
    liveCounterManager.applyOperation(operation, null)
    assertTrue(liveCounter.createOperationIsMerged) // Should be marked as merged

    assertEquals(30.0, liveCounter.data.get()) // Should be set to counter count
    assertTrue(liveCounter.createOperationIsMerged) // RTLC16b - Should be marked as merged
  }

  @Test
  fun `(RTLC8, RTLC16) LiveCounterManager should handle null count in create operation`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps()
    val liveCounterManager = liveCounter.LiveCounterManager

    // Set initial data
    liveCounter.data.set(10.0)

    val operation = ObjectOperation(
      action = ObjectOperationAction.CounterCreate,
      objectId = "testCounterId",
      counterCreate = null // No count specified
    )

    // RTLC16a - Should default to 0
    liveCounterManager.applyOperation(operation, null)

    assertEquals(10.0, liveCounter.data.get()) // No change (null defaults to 0)
    assertTrue(liveCounter.createOperationIsMerged) // RTLC16b
  }

  @Test
  fun `(RTLC7, RTLC7d2, RTLC9) LiveCounterManager should apply counter increment operation`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps()
    val liveCounterManager = liveCounter.LiveCounterManager

    // Set initial data
    liveCounter.data.set(10.0)

    val operation = ObjectOperation(
      action = ObjectOperationAction.CounterInc,
      objectId = "testCounterId",
      counterInc = CounterInc(number = 5.0)
    )

    // RTLC7d2 - Apply counter increment operation
    liveCounterManager.applyOperation(operation, null)

    assertEquals(15.0, liveCounter.data.get()) // RTLC9f - 10 + 5
  }

  @Test
  fun `(RTLC7, RTLC7d2) LiveCounterManager should throw error for missing payload for counter increment operation`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps()
    val liveCounterManager = liveCounter.LiveCounterManager

    val operation = ObjectOperation(
      action = ObjectOperationAction.CounterInc,
      objectId = "testCounterId",
      counterInc = null // Missing payload
    )

    // RTLC7d2 - Should throw error for missing payload
    val exception = assertFailsWith<AblyException> {
      liveCounterManager.applyOperation(operation, null)
    }

    val errorInfo = exception.errorInfo
    assertNotNull(errorInfo)
    assertEquals(92000, errorInfo.code) // InvalidObject error code
    assertEquals(500, errorInfo.statusCode) // InternalServerError status code
  }


  @Test
  fun `(RTLC9, RTLC9f) LiveCounterManager should apply counter increment operation correctly`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps()
    val liveCounterManager = liveCounter.LiveCounterManager

    // Set initial data
    liveCounter.data.set(10.0)

    val counterInc = CounterInc(number = 7.0)

    // RTLC9f - Apply counter increment
    liveCounterManager.applyOperation(ObjectOperation(
      action = ObjectOperationAction.CounterInc,
      objectId = "testCounterId",
      counterInc = counterInc
    ), null)

    assertEquals(17.0, liveCounter.data.get()) // 10 + 7
  }

  @Test
  fun `(RTLC7, RTLC7d2) LiveCounterManager should throw error when counterInc payload missing`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps()
    val liveCounterManager = liveCounter.LiveCounterManager

    // Set initial data
    liveCounter.data.set(10.0)

    // RTLC7d2 - Apply counter increment with no payload - throws error
    val exception = assertFailsWith<io.ably.lib.types.AblyException> {
      liveCounterManager.applyOperation(ObjectOperation(
        action = ObjectOperationAction.CounterInc,
        objectId = "testCounterId",
        counterInc = null
      ), null)
    }
    assertNotNull(exception.errorInfo)
    assertEquals(92000, exception.errorInfo.code)
  }

  @Test
  fun `(RTLC6, OM2j) DefaultLiveCounter should handle tombstone with serialTimestamp in state`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps()
    val liveCounterManager = liveCounter.LiveCounterManager

    // Set initial data
    liveCounter.data.set(10.0)

    val expectedTimestamp = 1234567890L
    val objectState = ObjectState(
      objectId = "testCounterId",
      counter = null, // Null counter for tombstone
      siteTimeserials = mapOf("site1" to "serial1"),
      tombstone = true, // Object is tombstoned
    )

    val update = liveCounterManager.applyState(objectState, expectedTimestamp)

    assertTrue(liveCounter.isTombstoned) // Should be tombstoned
    assertEquals(expectedTimestamp, liveCounter.TombstonedAt) // Should use provided timestamp
    assertEquals(0.0, liveCounter.data.get()) // Should be reset after tombstone

    // Assert on update field - should show the change
    assertEquals(-10.0, update.update.amount) // Difference from 10.0 to 0.0
  }

  @Test
  fun `(RTLC6, OM2j) DefaultLiveCounter should handle tombstone without serialTimestamp in state`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps()
    val liveCounterManager = liveCounter.LiveCounterManager

    // Set initial data
    liveCounter.data.set(10.0)

    val objectState = ObjectState(
      objectId = "testCounterId",
      counter = null, // Null counter for tombstone
      siteTimeserials = mapOf("site1" to "serial1"),
      tombstone = true, // Object is tombstoned
    )

    val beforeOperation = System.currentTimeMillis()
    val update = liveCounterManager.applyState(objectState, null)
    val afterOperation = System.currentTimeMillis()

    assertTrue(liveCounter.isTombstoned) // Should be tombstoned
    assertNotNull(liveCounter.TombstonedAt) // Should have timestamp
    assertTrue(liveCounter.TombstonedAt!! >= beforeOperation) // Should be after operation start
    assertTrue(liveCounter.TombstonedAt!! <= afterOperation) // Should be before operation end
    assertEquals(0.0, liveCounter.data.get()) // Should be reset after tombstone

    // Assert on update field - should show the change
    assertEquals(-10.0, update.update.amount) // Difference from 10.0 to 0.0
  }
}
