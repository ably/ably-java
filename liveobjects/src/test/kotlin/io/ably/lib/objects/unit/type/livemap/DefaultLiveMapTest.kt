package io.ably.lib.objects.unit.type.livemap

import io.ably.lib.objects.ObjectsMapSemantics
import io.ably.lib.objects.ObjectsMap
import io.ably.lib.objects.MapCreate
import io.ably.lib.objects.MapSet
import io.ably.lib.objects.MapRemove
import io.ably.lib.objects.ObjectsOperationSource
import io.ably.lib.objects.ObjectState
import io.ably.lib.objects.ObjectMessage
import io.ably.lib.objects.ObjectOperation
import io.ably.lib.objects.ObjectOperationAction
import io.ably.lib.objects.unit.*
import io.ably.lib.types.AblyException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultLiveMapTest {
  @Test
  fun `(RTLM6, RTLM6a) DefaultLiveMap should override serials with state serials from sync`() {
    val liveMap = getDefaultLiveMapWithMockedDeps("map:testMap@1")

    // Set initial data
    liveMap.siteTimeserials["site1"] = "serial1"
    liveMap.siteTimeserials["site2"] = "serial2"

    val objectState = ObjectState(
      objectId = "map:testMap@1",
      siteTimeserials = mapOf("site3" to "serial3", "site4" to "serial4"),
      tombstone = false,
      map = ObjectsMap(
        semantics = ObjectsMapSemantics.LWW,
      )
    )

    val objectMessage = ObjectMessage(
      id = "testId",
      objectState = objectState,
      serial = "serial1",
      siteCode = "site1"
    )

    liveMap.applyObjectSync(objectMessage)
    assertEquals(mapOf("site3" to "serial3", "site4" to "serial4"), liveMap.siteTimeserials) // RTLM6a
  }

  @Test
  fun `(RTLM15, RTLM15a) DefaultLiveMap should check objectId before applying operation`() {
    val liveMap = getDefaultLiveMapWithMockedDeps("map:testMap@1")

    val operation = ObjectOperation(
      action = ObjectOperationAction.MapCreate,
      objectId = "map:testMap@2", // Different objectId
      mapCreate = MapCreate(
        semantics = ObjectsMapSemantics.LWW,
        entries = emptyMap()
      )
    )

    val message = ObjectMessage(
      id = "testId",
      operation = operation,
      serial = "serial1",
      siteCode = "site1"
    )

    // RTLM15a - Should throw error when objectId doesn't match
    val exception = assertFailsWith<AblyException> {
      liveMap.applyObject(message, ObjectsOperationSource.CHANNEL)
    }
    val errorInfo = exception.errorInfo
    assertNotNull(errorInfo)

    // Assert on error codes
    assertEquals(92000, exception.errorInfo?.code) // InvalidObject error code
    assertEquals(500, exception.errorInfo?.statusCode) // InternalServerError status code
  }

  @Test
  fun `(RTLM15, RTLM15b) DefaultLiveMap should validate site serial before applying operation`() {
    val liveMap = getDefaultLiveMapWithMockedDeps("map:testMap@1")

    // Set existing site serial that is newer than the incoming message
    liveMap.siteTimeserials["site1"] = "serial2" // Newer than "serial1"

    val operation = ObjectOperation(
      action = ObjectOperationAction.MapCreate,
      objectId = "map:testMap@1", // Matching objectId
      mapCreate = MapCreate(
        semantics = ObjectsMapSemantics.LWW,
        entries = emptyMap()
      )
    )

    val message = ObjectMessage(
      id = "testId",
      operation = operation,
      serial = "serial1", // Older serial
      siteCode = "site1"
    )

    // RTLM15b - Should skip operation when serial is not newer
    liveMap.applyObject(message, ObjectsOperationSource.CHANNEL)

    // Verify that the site serial was not updated (operation was skipped)
    assertEquals("serial2", liveMap.siteTimeserials["site1"])
  }

  @Test
  fun `(RTLM15, RTLM15c) DefaultLiveMap should update site serial if valid`() {
    val liveMap = getDefaultLiveMapWithMockedDeps("map:testMap@1")

    // Set existing site serial that is older than the incoming message
    liveMap.siteTimeserials["site1"] = "serial1" // Older than "serial2"

    val operation = ObjectOperation(
      action = ObjectOperationAction.MapCreate,
      objectId = "map:testMap@1", // Matching objectId
      mapCreate = MapCreate(
        semantics = ObjectsMapSemantics.LWW,
        entries = emptyMap()
      )
    )

    val message = ObjectMessage(
      id = "testId",
      operation = operation,
      serial = "serial2", // Newer serial
      siteCode = "site1"
    )

    // RTLM15c - Should update site serial when operation is valid
    liveMap.applyObject(message, ObjectsOperationSource.CHANNEL)

    // Verify that the site serial was updated
    assertEquals("serial2", liveMap.siteTimeserials["site1"])
  }

  @Test
  fun `(RTLM15c LOCAL) applyObject with LOCAL source updates data but does NOT update siteTimeserials`() {
    val liveMap = getDefaultLiveMapWithMockedDeps("map:testMap@1")
    assertTrue(liveMap.siteTimeserials.isEmpty(), "siteTimeserials should start empty")

    val message = ObjectMessage(
      id = "testId",
      operation = ObjectOperation(
        action = ObjectOperationAction.MapSet,
        objectId = "map:testMap@1",
        mapSet = io.ably.lib.objects.MapSet(key = "key1", value = io.ably.lib.objects.ObjectData(value = io.ably.lib.objects.ObjectValue.String("value1")))
      ),
      serial = "serial1",
      siteCode = "site1"
    )

    // RTLM15c - LOCAL source: data IS updated (entry set), siteTimeserials is NOT updated
    val result = liveMap.applyObject(message, ObjectsOperationSource.LOCAL)

    assertTrue(result, "applyObject should return true for successful MAP_SET")
    assertEquals("value1", liveMap.data["key1"]?.data?.value?.value, "map entry should be updated for LOCAL source")
    assertFalse(liveMap.siteTimeserials.containsKey("site1"),
      "siteTimeserials should NOT be updated for LOCAL source")
  }

  @Test
  fun `(RTLM15b return) applyObject returns false when incoming serial is not newer than existing`() {
    val liveMap = getDefaultLiveMapWithMockedDeps("map:testMap@1")
    liveMap.siteTimeserials["site1"] = "serial5" // Newer than incoming "serial1"

    val message = ObjectMessage(
      id = "testId",
      operation = ObjectOperation(
        action = ObjectOperationAction.MapSet,
        objectId = "map:testMap@1",
        mapSet = io.ably.lib.objects.MapSet(key = "key1", value = io.ably.lib.objects.ObjectData(value = io.ably.lib.objects.ObjectValue.String("value1")))
      ),
      serial = "serial1", // Older than "serial5"
      siteCode = "site1"
    )

    // RTLM15b - Should return false when canApplyOperation fails
    val result = liveMap.applyObject(message, ObjectsOperationSource.CHANNEL)

    assertFalse(result, "applyObject should return false when serial is not newer")
    assertEquals("serial5", liveMap.siteTimeserials["site1"], "siteTimeserials should not change")
  }

  @Test
  fun `(RTLM15e return) applyObject returns false when object is tombstoned`() {
    val liveMap = getDefaultLiveMapWithMockedDeps("map:testMap@1")
    liveMap.tombstone(null) // Tombstone the object

    val message = ObjectMessage(
      id = "testId",
      operation = ObjectOperation(
        action = ObjectOperationAction.MapSet,
        objectId = "map:testMap@1",
        mapSet = io.ably.lib.objects.MapSet(key = "key1", value = io.ably.lib.objects.ObjectData(value = io.ably.lib.objects.ObjectValue.String("value1")))
      ),
      serial = "serial1",
      siteCode = "site1"
    )

    // RTLM15e - Should return false when object is tombstoned
    val result = liveMap.applyObject(message, ObjectsOperationSource.CHANNEL)

    assertFalse(result, "applyObject should return false when object is tombstoned")
  }

  @Test
  fun `(RTLM15d2b) applyObject returns true for successful MAP_SET`() {
    val liveMap = getDefaultLiveMapWithMockedDeps("map:testMap@1")

    val message = ObjectMessage(
      id = "testId",
      operation = ObjectOperation(
        action = ObjectOperationAction.MapSet,
        objectId = "map:testMap@1",
        mapSet = io.ably.lib.objects.MapSet(key = "key1", value = io.ably.lib.objects.ObjectData(value = io.ably.lib.objects.ObjectValue.String("value1")))
      ),
      serial = "serial1",
      siteCode = "site1"
    )

    // RTLM15d2b - Should return true for successful MAP_SET
    val result = liveMap.applyObject(message, ObjectsOperationSource.CHANNEL)

    assertTrue(result, "applyObject should return true for successful MAP_SET")
    assertEquals("value1", liveMap.data["key1"]?.data?.value?.value)
  }

  @Test
  fun `(RTLM15d3b) applyObject returns true for successful MAP_REMOVE`() {
    val liveMap = getDefaultLiveMapWithMockedDeps("map:testMap@1")

    val message = ObjectMessage(
      id = "testId",
      operation = ObjectOperation(
        action = ObjectOperationAction.MapRemove,
        objectId = "map:testMap@1",
        mapRemove = io.ably.lib.objects.MapRemove(key = "key1")
      ),
      serial = "serial1",
      siteCode = "site1"
    )

    // RTLM15d3b - Should return true for successful MAP_REMOVE
    val result = liveMap.applyObject(message, ObjectsOperationSource.CHANNEL)

    assertTrue(result, "applyObject should return true for successful MAP_REMOVE")
  }

  @Test
  fun `(RTLM15d5b) applyObject returns true for OBJECT_DELETE (tombstone)`() {
    val liveMap = getDefaultLiveMapWithMockedDeps("map:testMap@1")

    val message = ObjectMessage(
      id = "testId",
      operation = ObjectOperation(
        action = ObjectOperationAction.ObjectDelete,
        objectId = "map:testMap@1",
      ),
      serial = "serial1",
      siteCode = "site1"
    )

    // RTLM15d5b - Should return true for OBJECT_DELETE (tombstone applied)
    val result = liveMap.applyObject(message, ObjectsOperationSource.CHANNEL)

    assertTrue(result, "applyObject should return true for OBJECT_DELETE")
    assertTrue(liveMap.isTombstoned, "object should be tombstoned")
  }
}
