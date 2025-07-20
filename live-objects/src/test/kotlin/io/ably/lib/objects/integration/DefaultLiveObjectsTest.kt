package io.ably.lib.objects.integration

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.ably.lib.objects.Binary
import io.ably.lib.objects.LiveCounter
import io.ably.lib.objects.LiveMap
import io.ably.lib.objects.integration.helpers.initializeRootMap
import io.ably.lib.objects.integration.setup.IntegrationTest
import io.ably.lib.objects.size
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.text.toByteArray

class DefaultLiveObjectsTest : IntegrationTest() {

  @Test
  fun testChannelObjects() = runTest {
    val channelName = generateChannelName()
    val channel = getRealtimeChannel(channelName)
    val objects = channel.objects
    assertNotNull(objects)
  }

  /**
   * This will test objects sync process when the root map is initialized before channel attach.
   * This includes checking the initial values of counters, maps, and other data types.
   */
  @Test
  fun testObjectsSync() = runTest {
    val channelName = generateChannelName()

    // Initialize the root map on the channel with initial data
    restObjects.initializeRootMap(channelName)

    val channel = getRealtimeChannel(channelName)
    val objects = channel.objects
    assertNotNull(objects)

    val rootMap = objects.root
    assertNotNull(rootMap)

    // Assert Counter Objects
    // Test emptyCounter - should have initial value of 0
    val emptyCounter = rootMap.get("emptyCounter") as LiveCounter
    assertNotNull(emptyCounter)
    assertEquals(0L, emptyCounter.value())

    // Test initialValueCounter - should have initial value of 10
    val initialValueCounter = rootMap.get("initialValueCounter") as LiveCounter
    assertNotNull(initialValueCounter)
    assertEquals(10L, initialValueCounter.value())

    // Test referencedCounter - should have initial value of 20
    val referencedCounter = rootMap.get("referencedCounter") as LiveCounter
    assertNotNull(referencedCounter)
    assertEquals(20L, referencedCounter.value())

    // Assert Map Objects
    // Test emptyMap - should be an empty map
    val emptyMap = rootMap.get("emptyMap") as LiveMap
    assertNotNull(emptyMap)
    assertEquals(0L, emptyMap.size())

    // Test referencedMap - should contain one key "counterKey" pointing to referencedCounter
    val referencedMap = rootMap.get("referencedMap") as LiveMap
    assertNotNull(referencedMap)
    assertEquals(1L, referencedMap.size())
    val referencedMapCounter = referencedMap.get("counterKey") as LiveCounter
    assertNotNull(referencedMapCounter)
    assertEquals(20L, referencedMapCounter.value()) // Should point to the same counter with value 20

    // Test valuesMap - should contain all primitive data types and one map reference
    val valuesMap = rootMap.get("valuesMap") as LiveMap
    assertNotNull(valuesMap)
    assertEquals(13L, valuesMap.size()) // Should have 13 entries

    // Assert string values
    assertEquals("stringValue", valuesMap.get("string"))
    assertEquals("", valuesMap.get("emptyString"))

    // Assert binary values
    val bytesValue = valuesMap.get("bytes") as Binary
    assertNotNull(bytesValue)
    val expectedBinary = Binary("eyJwcm9kdWN0SWQiOiAiMDAxIiwgInByb2R1Y3ROYW1lIjogImNhciJ9".toByteArray())
    assertEquals(expectedBinary, bytesValue) // Should contain encoded JSON data

    val emptyBytesValue = valuesMap.get("emptyBytes") as Binary
    assertNotNull(emptyBytesValue)
    assertEquals(0, emptyBytesValue.size()) // Should be empty byte array

    // Assert numeric values
    assertEquals(99999999.0, valuesMap.get("maxSafeNumber"))
    assertEquals(-99999999.0, valuesMap.get("negativeMaxSafeNumber"))
    assertEquals(1.0, valuesMap.get("number"))
    assertEquals(0.0, valuesMap.get("zero"))

    // Assert boolean values
    assertEquals(true, valuesMap.get("true"))
    assertEquals(false, valuesMap.get("false"))

    // Assert JSON object value - should contain {"foo": "bar"}
    val jsonObjectValue = valuesMap.get("object") as JsonObject
    assertNotNull(jsonObjectValue)
    assertEquals("bar", jsonObjectValue.get("foo").asString)

    // Assert JSON array value - should contain ["foo", "bar", "baz"]
    val jsonArrayValue = valuesMap.get("array") as JsonArray
    assertNotNull(jsonArrayValue)
    assertEquals(3, jsonArrayValue.size())
    assertEquals("foo", jsonArrayValue[0].asString)
    assertEquals("bar", jsonArrayValue[1].asString)
    assertEquals("baz", jsonArrayValue[2].asString)

    // Assert map reference - should point to the same referencedMap
    val mapRefValue = valuesMap.get("mapRef") as LiveMap
    assertNotNull(mapRefValue)
    assertEquals(1L, mapRefValue.size())
    val mapRefCounter = mapRefValue.get("counterKey") as LiveCounter
    assertNotNull(mapRefCounter)
    assertEquals(20L, mapRefCounter.value()) // Should point to the same counter with value 20
  }
}
