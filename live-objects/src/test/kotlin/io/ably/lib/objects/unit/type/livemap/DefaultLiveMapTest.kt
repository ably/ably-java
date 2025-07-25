package io.ably.lib.objects.unit.type.livemap

import io.ably.lib.objects.MapSemantics
import io.ably.lib.objects.ObjectMap
import io.ably.lib.objects.ObjectState
import io.ably.lib.objects.ObjectMessage
import io.ably.lib.objects.ObjectOperation
import io.ably.lib.objects.ObjectOperationAction
import io.ably.lib.objects.unit.*
import io.ably.lib.types.AblyException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

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
      map = ObjectMap(
        semantics = MapSemantics.LWW,
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
      map = ObjectMap(
        semantics = MapSemantics.LWW,
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
      liveMap.applyObject(message)
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
      map = ObjectMap(
        semantics = MapSemantics.LWW,
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
    liveMap.applyObject(message)

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
      map = ObjectMap(
        semantics = MapSemantics.LWW,
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
    liveMap.applyObject(message)

    // Verify that the site serial was updated
    assertEquals("serial2", liveMap.siteTimeserials["site1"])
  }
}
