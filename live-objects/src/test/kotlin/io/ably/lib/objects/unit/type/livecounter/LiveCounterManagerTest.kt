package io.ably.lib.objects.unit.type.livecounter

import io.ably.lib.objects.*
import io.ably.lib.objects.unit.LiveCounterManager
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
      counter = ObjectCounter(count = 25.0),
      siteTimeserials = mapOf("site3" to "serial3", "site4" to "serial4"),
      tombstone = false,
    )

    val update = liveCounterManager.applyState(objectState)

    assertFalse(liveCounter.createOperationIsMerged) // RTLC6b
    assertEquals(25.0, liveCounter.data.get()) // RTLC6c
    assertEquals(15.0, update["amount"]) // Difference between old and new data
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
      counter = ObjectCounter(count = 10.0)
    )

    val objectState = ObjectState(
      objectId = "testCounterId",
      counter = ObjectCounter(count = 15.0),
      createOp = createOp,
      siteTimeserials = mapOf("site1" to "serial1"),
      tombstone = false,
    )

    // RTLC6d - Merge initial data from create operation
    val update = liveCounterManager.applyState(objectState)

    assertEquals(25.0, liveCounter.data.get()) // 15 from state + 10 from create op
    assertEquals(20.0, update["amount"]) // Total change
  }


  @Test
  fun `(RTLC7, RTLC7d3) LiveCounterManager should throw error for unsupported action`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps()
    val liveCounterManager = liveCounter.LiveCounterManager

    val operation = ObjectOperation(
      action = ObjectOperationAction.MapCreate, // Unsupported action for counter
      objectId = "testCounterId",
      map = ObjectMap(semantics = MapSemantics.LWW, entries = emptyMap())
    )

    // RTLC7d3 - Should throw error for unsupported action
    val exception = assertFailsWith<AblyException> {
      liveCounterManager.applyOperation(operation)
    }

    val errorInfo = exception.errorInfo
    assertNotNull(errorInfo)
    assertEquals(92000, errorInfo.code) // InvalidObject error code
    assertEquals(500, errorInfo.statusCode) // InternalServerError status code
  }

  @Test
  fun `(RTLC7, RTLC7d1, RTLC8) LiveCounterManager should apply counter create operation`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps()
    val liveCounterManager = liveCounter.LiveCounterManager

    val operation = ObjectOperation(
      action = ObjectOperationAction.CounterCreate,
      objectId = "testCounterId",
      counter = ObjectCounter(count = 20.0)
    )

    // RTLC7d1 - Apply counter create operation
     liveCounterManager.applyOperation(operation)

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
      counter = ObjectCounter(count = 20.0)
    )

    // RTLC8b - Should skip if already merged
    liveCounterManager.applyOperation(operation)

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
      counter = ObjectCounter(count = 20.0)
    )

    // RTLC8c - Should apply if not merged
    liveCounterManager.applyOperation(operation)
    assertTrue(liveCounter.createOperationIsMerged) // Should be marked as merged

    assertEquals(30.0, liveCounter.data.get()) // Should be set to counter count
    assertTrue(liveCounter.createOperationIsMerged) // RTLC10b - Should be marked as merged
  }

  @Test
  fun `(RTLC8, RTLC10, RTLC10a) LiveCounterManager should handle null count in create operation`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps()
    val liveCounterManager = liveCounter.LiveCounterManager

    // Set initial data
    liveCounter.data.set(10.0)

    val operation = ObjectOperation(
      action = ObjectOperationAction.CounterCreate,
      objectId = "testCounterId",
      counter = null // No count specified
    )

    // RTLC10a - Should default to 0
    // RTLC10b - Mark as merged
    liveCounterManager.applyOperation(operation)

    assertEquals(10.0, liveCounter.data.get()) // No change (null defaults to 0)
    assertTrue(liveCounter.createOperationIsMerged) // RTLC10b
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
      counterOp = ObjectCounterOp(amount = 5.0)
    )

    // RTLC7d2 - Apply counter increment operation
    liveCounterManager.applyOperation(operation)

    assertEquals(15.0, liveCounter.data.get()) // RTLC9b - 10 + 5
  }

  @Test
  fun `(RTLC7, RTLC7d2) LiveCounterManager should throw error for missing payload for counter increment operation`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps()
    val liveCounterManager = liveCounter.LiveCounterManager

    val operation = ObjectOperation(
      action = ObjectOperationAction.CounterInc,
      objectId = "testCounterId",
      counterOp = null // Missing payload
    )

    // RTLC7d2 - Should throw error for missing payload
    val exception = assertFailsWith<AblyException> {
      liveCounterManager.applyOperation(operation)
    }

    val errorInfo = exception.errorInfo
    assertNotNull(errorInfo)
    assertEquals(92000, errorInfo.code) // InvalidObject error code
    assertEquals(500, errorInfo.statusCode) // InternalServerError status code
  }


  @Test
  fun `(RTLC9, RTLC9b) LiveCounterManager should apply counter increment operation correctly`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps()
    val liveCounterManager = liveCounter.LiveCounterManager

    // Set initial data
    liveCounter.data.set(10.0)

    val counterOp = ObjectCounterOp(amount = 7.0)

    // RTLC9b - Apply counter increment
    liveCounterManager.applyOperation(ObjectOperation(
      action = ObjectOperationAction.CounterInc,
      objectId = "testCounterId",
      counterOp = counterOp
    ))

    assertEquals(17.0, liveCounter.data.get()) // 10 + 7
  }

  @Test
  fun `(RTLC9, RTLC9b) LiveCounterManager should handle null amount in counter increment`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps()
    val liveCounterManager = liveCounter.LiveCounterManager

    // Set initial data
    liveCounter.data.set(10.0)

    val counterOp = ObjectCounterOp(amount = null) // Null amount

    // RTLC9b - Apply counter increment with null amount
    liveCounterManager.applyOperation(ObjectOperation(
      action = ObjectOperationAction.CounterInc,
      objectId = "testCounterId",
      counterOp = counterOp
    ))

    assertEquals(10.0, liveCounter.data.get()) // Should not change (null defaults to 0)
  }
}
