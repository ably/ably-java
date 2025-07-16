package io.ably.lib.objects.unit.type.livecounter

import io.ably.lib.objects.*
import io.ably.lib.objects.ObjectCounterOp
import io.ably.lib.objects.ObjectMessage
import io.ably.lib.objects.ObjectOperation
import io.ably.lib.objects.ObjectOperationAction
import io.ably.lib.objects.ObjectState
import io.ably.lib.objects.type.livecounter.DefaultLiveCounter
import io.ably.lib.objects.type.ObjectType
import io.ably.lib.objects.unit.LiveCounterManager
import io.ably.lib.objects.unit.getDefaultLiveCounterWithMockedDeps
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultLiveCounterManagerTest {

  @Test
  fun `(RTLC6, RTLC6b, RTLC6c) DefaultLiveCounter should override counter data with state from sync`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps()
    val liveCounterManager = liveCounter.LiveCounterManager

    // Set initial data
    liveCounter.data = 10L

    val objectState = ObjectState(
      objectId = "testCounterId",
      counter = ObjectCounter(count = 25L),
      siteTimeserials = mapOf("site3" to "serial3", "site4" to "serial4"),
      tombstone = false,
    )

    val update = liveCounterManager.applyState(objectState)

    assertFalse(liveCounter.createOperationIsMerged) // RTLC6b
    assertEquals(25L, liveCounter.data) // RTLC6c
    assertEquals(15L, update["amount"]) // Difference between old and new data
  }


  @Test
  fun `(RTLC6, RTLC6d) DefaultLiveCounter should merge initial data from create operation`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps()
    val liveCounterManager = liveCounter.LiveCounterManager

    // Set initial data
    liveCounter.data = 5L

    val createOp = ObjectOperation(
      action = ObjectOperationAction.CounterCreate,
      objectId = "testCounterId",
      counter = ObjectCounter(count = 10)
    )

    val objectState = ObjectState(
      objectId = "testCounterId",
      counter = ObjectCounter(count = 15),
      createOp = createOp,
      siteTimeserials = mapOf("site1" to "serial1"),
      tombstone = false,
    )

    // RTLC6d - Merge initial data from create operation
    val update = liveCounterManager.applyState(objectState)

    assertEquals(25L, liveCounter.data) // 15 from state + 10 from create op
    assertEquals(20L, update["amount"]) // Total change
  }


}
