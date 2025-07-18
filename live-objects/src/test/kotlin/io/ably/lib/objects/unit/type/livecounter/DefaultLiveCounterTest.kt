package io.ably.lib.objects.unit.type.livecounter

import io.ably.lib.objects.ObjectCounter
import io.ably.lib.objects.ObjectMessage
import io.ably.lib.objects.ObjectOperation
import io.ably.lib.objects.ObjectOperationAction
import io.ably.lib.objects.ObjectState
import io.ably.lib.objects.unit.getDefaultLiveCounterWithMockedDeps
import io.ably.lib.types.AblyException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class DefaultLiveCounterTest {
  @Test
  fun `(RTLC6, RTLC6a) DefaultLiveCounter should override serials with state serials from sync`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps("counter:testCounter@1")

    // Set initial data
    liveCounter.siteTimeserials["site1"] = "serial1"
    liveCounter.siteTimeserials["site2"] = "serial2"

    val objectState = ObjectState(
      objectId = "counter:testCounter@1",
      siteTimeserials = mapOf("site3" to "serial3", "site4" to "serial4"),
      tombstone = false,
    )
    liveCounter.applyObjectSync(objectState)
    assertEquals(mapOf("site3" to "serial3", "site4" to "serial4"), liveCounter.siteTimeserials) // RTLC6a
  }

  @Test
  fun `(RTLC7, RTLC7a) DefaultLiveCounter should check objectId before applying operation`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps("counter:testCounter@1")

    val operation = ObjectOperation(
      action = ObjectOperationAction.CounterCreate,
      objectId = "counter:testCounter@2", // Different objectId
      counter = ObjectCounter(count = 20)
    )

    val message = ObjectMessage(
      id = "testId",
      operation = operation,
      serial = "serial1",
      siteCode = "site1"
    )

    // RTLC7a - Should throw error when objectId doesn't match
    val exception = assertFailsWith<AblyException> {
      liveCounter.applyObject(message)
    }
    val errorInfo = exception.errorInfo
    assertNotNull(errorInfo)

    // Assert on error codes
    assertEquals(92000, exception.errorInfo?.code) // InvalidObject error code
    assertEquals(500, exception.errorInfo?.statusCode) // InternalServerError status code
  }

  @Test
  fun `(RTLC7, RTLC7b) DefaultLiveCounter should validate site serial before applying operation`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps("counter:testCounter@1")

    // Set existing site serial that is newer than the incoming message
    liveCounter.siteTimeserials["site1"] = "serial2" // Newer than "serial1"

    val operation = ObjectOperation(
      action = ObjectOperationAction.CounterCreate,
      objectId = "counter:testCounter@1", // Matching objectId
      counter = ObjectCounter(count = 20)
    )

    val message = ObjectMessage(
      id = "testId",
      operation = operation,
      serial = "serial1", // Older serial
      siteCode = "site1"
    )

    // RTLC7b - Should skip operation when serial is not newer
    liveCounter.applyObject(message)

    // Verify that the site serial was not updated (operation was skipped)
    assertEquals("serial2", liveCounter.siteTimeserials["site1"])
  }

  @Test
  fun `(RTLC7, RTLC7c) DefaultLiveCounter should update site serial if valid`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps("counter:testCounter@1")

    // Set existing site serial that is older than the incoming message
    liveCounter.siteTimeserials["site1"] = "serial1" // Older than "serial2"

    val operation = ObjectOperation(
      action = ObjectOperationAction.CounterCreate,
      objectId = "counter:testCounter@1", // Matching objectId
      counter = ObjectCounter(count = 20)
    )

    val message = ObjectMessage(
      id = "testId",
      operation = operation,
      serial = "serial2", // Newer serial
      siteCode = "site1"
    )

    // RTLC7c - Should update site serial when operation is valid
    liveCounter.applyObject(message)

    // Verify that the site serial was updated
    assertEquals("serial2", liveCounter.siteTimeserials["site1"])
  }
}
