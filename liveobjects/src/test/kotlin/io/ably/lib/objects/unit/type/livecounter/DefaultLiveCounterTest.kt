package io.ably.lib.objects.unit.type.livecounter

import io.ably.lib.objects.CounterCreate
import io.ably.lib.objects.CounterInc
import io.ably.lib.objects.ObjectMessage
import io.ably.lib.objects.ObjectOperation
import io.ably.lib.objects.ObjectOperationAction
import io.ably.lib.objects.ObjectsOperationSource
import io.ably.lib.objects.ObjectState
import io.ably.lib.objects.unit.getDefaultLiveCounterWithMockedDeps
import io.ably.lib.types.AblyException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

    val objectMessage = ObjectMessage(
      id = "testId",
      objectState = objectState,
      serial = "serial1",
      siteCode = "site1"
    )

    liveCounter.applyObjectSync(objectMessage)
    assertEquals(mapOf("site3" to "serial3", "site4" to "serial4"), liveCounter.siteTimeserials) // RTLC6a
  }

  @Test
  fun `(RTLC7, RTLC7a) DefaultLiveCounter should check objectId before applying operation`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps("counter:testCounter@1")

    val operation = ObjectOperation(
      action = ObjectOperationAction.CounterCreate,
      objectId = "counter:testCounter@2", // Different objectId
      counterCreate = CounterCreate(count = 20.0)
    )

    val message = ObjectMessage(
      id = "testId",
      operation = operation,
      serial = "serial1",
      siteCode = "site1"
    )

    // RTLC7a - Should throw error when objectId doesn't match
    val exception = assertFailsWith<AblyException> {
      liveCounter.applyObject(message, ObjectsOperationSource.CHANNEL)
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
      counterCreate = CounterCreate(count = 20.0)
    )

    val message = ObjectMessage(
      id = "testId",
      operation = operation,
      serial = "serial1", // Older serial
      siteCode = "site1"
    )

    // RTLC7b - Should skip operation when serial is not newer
    liveCounter.applyObject(message, ObjectsOperationSource.CHANNEL)

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
      counterCreate = CounterCreate(count = 20.0)
    )

    val message = ObjectMessage(
      id = "testId",
      operation = operation,
      serial = "serial2", // Newer serial
      siteCode = "site1"
    )

    // RTLC7c - Should update site serial when operation is valid
    liveCounter.applyObject(message, ObjectsOperationSource.CHANNEL)

    // Verify that the site serial was updated
    assertEquals("serial2", liveCounter.siteTimeserials["site1"])
  }

  @Test
  fun `(RTLC7c LOCAL) applyObject with LOCAL source updates data but does NOT update siteTimeserials`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps("counter:testCounter@1")
    assertTrue(liveCounter.siteTimeserials.isEmpty(), "siteTimeserials should start empty")

    val message = ObjectMessage(
      id = "testId",
      operation = ObjectOperation(
        action = ObjectOperationAction.CounterInc,
        objectId = "counter:testCounter@1",
        counterInc = io.ably.lib.objects.CounterInc(number = 5.0)
      ),
      serial = "serial1",
      siteCode = "site1"
    )

    // RTLC7c - LOCAL source: data IS updated, siteTimeserials is NOT updated
    val result = liveCounter.applyObject(message, ObjectsOperationSource.LOCAL)

    assertTrue(result, "applyObject should return true for successful COUNTER_INC")
    assertEquals(5.0, liveCounter.data.get(), "data should be updated for LOCAL source")
    assertFalse(liveCounter.siteTimeserials.containsKey("site1"),
      "siteTimeserials should NOT be updated for LOCAL source")
  }

  @Test
  fun `(RTLC7b return) applyObject returns false when incoming serial is not newer than existing`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps("counter:testCounter@1")
    liveCounter.siteTimeserials["site1"] = "serial5" // Newer than incoming "serial1"

    val message = ObjectMessage(
      id = "testId",
      operation = ObjectOperation(
        action = ObjectOperationAction.CounterInc,
        objectId = "counter:testCounter@1",
        counterInc = io.ably.lib.objects.CounterInc(number = 5.0)
      ),
      serial = "serial1", // Older than "serial5"
      siteCode = "site1"
    )

    // RTLC7b - Should return false when canApplyOperation fails
    val result = liveCounter.applyObject(message, ObjectsOperationSource.CHANNEL)

    assertFalse(result, "applyObject should return false when serial is not newer")
    assertEquals(0.0, liveCounter.data.get(), "data should not be changed")
    assertEquals("serial5", liveCounter.siteTimeserials["site1"], "siteTimeserials should not change")
  }

  @Test
  fun `(RTLC7e return) applyObject returns false when object is tombstoned`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps("counter:testCounter@1")
    liveCounter.tombstone(null) // Tombstone the object

    val message = ObjectMessage(
      id = "testId",
      operation = ObjectOperation(
        action = ObjectOperationAction.CounterInc,
        objectId = "counter:testCounter@1",
        counterInc = io.ably.lib.objects.CounterInc(number = 5.0)
      ),
      serial = "serial1",
      siteCode = "site1"
    )

    // RTLC7e - Should return false when object is tombstoned
    val result = liveCounter.applyObject(message, ObjectsOperationSource.CHANNEL)

    assertFalse(result, "applyObject should return false when object is tombstoned")
  }

  @Test
  fun `(RTLC7d2b) applyObject returns true for successful COUNTER_INC`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps("counter:testCounter@1")

    val message = ObjectMessage(
      id = "testId",
      operation = ObjectOperation(
        action = ObjectOperationAction.CounterInc,
        objectId = "counter:testCounter@1",
        counterInc = io.ably.lib.objects.CounterInc(number = 5.0)
      ),
      serial = "serial1",
      siteCode = "site1"
    )

    // RTLC7d2b - Should return true for successful COUNTER_INC
    val result = liveCounter.applyObject(message, ObjectsOperationSource.CHANNEL)

    assertTrue(result, "applyObject should return true for successful COUNTER_INC")
    assertEquals(5.0, liveCounter.data.get())
  }

  @Test
  fun `(RTLC7d1b) applyObject returns true for successful COUNTER_CREATE`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps("counter:testCounter@1")

    val message = ObjectMessage(
      id = "testId",
      operation = ObjectOperation(
        action = ObjectOperationAction.CounterCreate,
        objectId = "counter:testCounter@1",
        counterCreate = CounterCreate(count = 20.0)
      ),
      serial = "serial1",
      siteCode = "site1"
    )

    // RTLC7d1b - Should return true for successful COUNTER_CREATE
    val result = liveCounter.applyObject(message, ObjectsOperationSource.CHANNEL)

    assertTrue(result, "applyObject should return true for successful COUNTER_CREATE")
  }

  @Test
  fun `(RTLC7d4b) applyObject returns true for OBJECT_DELETE (tombstone)`() {
    val liveCounter = getDefaultLiveCounterWithMockedDeps("counter:testCounter@1")

    val message = ObjectMessage(
      id = "testId",
      operation = ObjectOperation(
        action = ObjectOperationAction.ObjectDelete,
        objectId = "counter:testCounter@1",
      ),
      serial = "serial1",
      siteCode = "site1"
    )

    // RTLC7d4b - Should return true for OBJECT_DELETE (tombstone applied)
    val result = liveCounter.applyObject(message, ObjectsOperationSource.CHANNEL)

    assertTrue(result, "applyObject should return true for OBJECT_DELETE")
    assertTrue(liveCounter.isTombstoned, "object should be tombstoned")
  }
}
