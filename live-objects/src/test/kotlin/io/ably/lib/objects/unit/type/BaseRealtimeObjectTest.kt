package io.ably.lib.objects.unit.type

import io.ably.lib.objects.*
import io.ably.lib.objects.type.BaseRealtimeObject
import io.ably.lib.objects.type.livecounter.DefaultLiveCounter
import io.ably.lib.objects.type.livemap.DefaultLiveMap
import io.ably.lib.objects.unit.getDefaultRealtimeObjectsWithMockedDeps
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class BaseRealtimeObjectTest {

  private val defaultRealtimeObjects = getDefaultRealtimeObjectsWithMockedDeps()

  @Test
  fun `(RTLO1, RTLO2) BaseRealtimeObject should be abstract base class for LiveMap and LiveCounter`() {
    // RTLO2 - Check that BaseRealtimeObject is abstract
    val isAbstract = java.lang.reflect.Modifier.isAbstract(BaseRealtimeObject::class.java.modifiers)
    assertTrue(isAbstract, "BaseRealtimeObject should be an abstract class")

    // RTLO1 - Check that BaseRealtimeObject is the parent class of DefaultLiveMap and DefaultLiveCounter
    assertTrue(BaseRealtimeObject::class.java.isAssignableFrom(DefaultLiveMap::class.java),
      "DefaultLiveMap should extend BaseRealtimeObject")
    assertTrue(BaseRealtimeObject::class.java.isAssignableFrom(DefaultLiveCounter::class.java),
      "DefaultLiveCounter should extend BaseRealtimeObject")
  }

  @Test
  fun `(RTLO3) BaseRealtimeObject should have required properties`() {
    val liveMap: BaseRealtimeObject = DefaultLiveMap.zeroValue("map:testObject@1", defaultRealtimeObjects)
    val liveCounter: BaseRealtimeObject = DefaultLiveCounter.zeroValue("counter:testObject@1", defaultRealtimeObjects)
    // RTLO3a - check that objectId is set correctly
    assertEquals("map:testObject@1", liveMap.objectId)
    assertEquals("counter:testObject@1", liveCounter.objectId)

    // RTLO3b, RTLO3b1 - check that siteTimeserials is initialized as an empty map
    assertEquals(emptyMap(), liveMap.siteTimeserials)
    assertEquals(emptyMap(), liveCounter.siteTimeserials)

    // RTLO3c - Create operation merged flag
    assertFalse(liveMap.createOperationIsMerged, "Create operation should not be merged by default")
    assertFalse(liveCounter.createOperationIsMerged, "Create operation should not be merged by default")
  }

  @Test
  fun `(RTLO4a1, RTLO4a2) canApplyOperation should accept ObjectMessage params and return boolean`() {
    // RTLO4a1a - Assert parameter types and return type based on method signature using reflection
    val method = BaseRealtimeObject::class.java.findMethod("canApplyOperation")

    // RTLO4a1a - Verify parameter types
    val parameters = method.parameters
    assertEquals(2, parameters.size, "canApplyOperation should have exactly 2 parameters")

    // First parameter should be String? (siteCode)
    assertEquals(String::class.java, parameters[0].type, "First parameter should be of type String?")
    assertTrue(parameters[0].isVarArgs.not(), "First parameter should not be varargs")

    // Second parameter should be String? (timeSerial)
    assertEquals(String::class.java, parameters[1].type, "Second parameter should be of type String?")
    assertTrue(parameters[1].isVarArgs.not(), "Second parameter should not be varargs")

    // RTLO4a2 - Verify return type
    assertEquals(Boolean::class.java, method.returnType, "canApplyOperation should return Boolean")
  }

  @Test
  fun `(RTLO4a3) canApplyOperation should throw error for null or empty incoming siteSerial`() {
    val liveMap: BaseRealtimeObject = DefaultLiveMap.zeroValue("map:testObject@1", defaultRealtimeObjects)

    // Test null serial
    assertFailsWith<Exception>("Should throw error for null serial") {
      liveMap.canApplyOperation("site1", null)
    }

    // Test empty serial
    assertFailsWith<Exception>("Should throw error for empty serial") {
      liveMap.canApplyOperation("site1", "")
    }

    // Test null siteCode
    assertFailsWith<Exception>("Should throw error for null site code") {
      liveMap.canApplyOperation(null, "serial1")
    }

    // Test empty siteCode
    assertFailsWith<Exception>("Should throw error for empty site code") {
      liveMap.canApplyOperation("", "serial1")
    }
  }

  @Test
  fun `(RTLO4a4, RTLO4a5) canApplyOperation should return true when existing siteSerial is null or empty`() {
    val liveMap: BaseRealtimeObject = DefaultLiveMap.zeroValue("map:testObject@1", defaultRealtimeObjects)
    assertTrue(liveMap.siteTimeserials.isEmpty(), "Initial siteTimeserials should be empty")

    // RTLO4a4 - Get siteSerial from siteTimeserials map
    // RTLO4a5 - Return true when siteSerial is null (no entry in map)
    assertTrue(liveMap.canApplyOperation("site1", "serial1"),
      "Should return true when no siteSerial exists for the site")

    // RTLO4a5 - Return true when siteSerial is empty string
    liveMap.siteTimeserials["site1"] = ""
    assertTrue(liveMap.canApplyOperation("site1", "serial1"),
      "Should return true when siteSerial is empty string")
  }

  @Test
  fun `(RTLO4a6) canApplyOperation should return true when message siteSerial is greater than existing siteSerial`() {
    val liveMap: BaseRealtimeObject = DefaultLiveMap.zeroValue("map:testObject@1", defaultRealtimeObjects)

    // Set existing siteSerial
    liveMap.siteTimeserials["site1"] = "serial1"

    // RTLO4a6 - Return true when message serial is greater (lexicographically)
    assertTrue(liveMap.canApplyOperation("site1", "serial2"),
      "Should return true when message serial 'serial2' > siteSerial 'serial1'")

    assertTrue(liveMap.canApplyOperation("site1", "serial10"),
      "Should return true when message serial 'serial10' > siteSerial 'serial1'")

    assertTrue(liveMap.canApplyOperation("site1", "serialA"),
      "Should return true when message serial 'serialA' > siteSerial 'serial1'")
  }

  @Test
  fun `(RTLO4a6) canApplyOperation should return false when message siteSerial is less than or equal to siteSerial`() {
    val liveMap: BaseRealtimeObject = DefaultLiveMap.zeroValue("map:testObject@1", defaultRealtimeObjects)

    // Set existing siteSerial
    liveMap.siteTimeserials["site1"] = "serial2"

    // RTLO4a6 - Return false when message serial is less than siteSerial
    assertFalse(liveMap.canApplyOperation("site1", "serial1"),
      "Should return false when message serial 'serial1' < siteSerial 'serial2'")

    // RTLO4a6 - Return false when message serial equals siteSerial
    assertFalse(liveMap.canApplyOperation("site1", "serial2"),
      "Should return false when message serial equals siteSerial")

    // RTLO4a6 - Return false when message serial is less (lexicographically)
    assertTrue(liveMap.canApplyOperation("site1", "serialA"),
      "Should return true when message serial 'serialA' > siteSerial 'serial2'")
  }

  @Test
  fun `(RTLO4a) canApplyOperation should work with different site codes`() {
    val liveMap: BaseRealtimeObject = DefaultLiveCounter.zeroValue("counter:testObject@1", defaultRealtimeObjects)

    // Set serials for different sites
    liveMap.siteTimeserials["site1"] = "serial1"
    liveMap.siteTimeserials["site2"] = "serial5"

    // Test site1
    assertTrue(liveMap.canApplyOperation("site1", "serial2"),
      "Should return true for site1 when serial2 > serial1")
    assertFalse(liveMap.canApplyOperation("site1", "serial1"),
      "Should return false for site1 when serial1 = serial1")

    // Test site2
    assertTrue(liveMap.canApplyOperation("site2", "serial6"),
      "Should return true for site2 when serial6 > serial5")
    assertFalse(liveMap.canApplyOperation("site2", "serial4"),
      "Should return false for site2 when serial4 < serial5")

    // Test new site (should return true)
    assertTrue(liveMap.canApplyOperation("site3", "serial1"),
      "Should return true for new site with any serial")
  }
}
