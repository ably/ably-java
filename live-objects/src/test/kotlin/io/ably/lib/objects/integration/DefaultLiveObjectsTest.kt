package io.ably.lib.objects.integration

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.ably.lib.objects.*
import io.ably.lib.objects.Binary
import io.ably.lib.objects.integration.helpers.State
import io.ably.lib.objects.integration.helpers.fixtures.initializeRootMap
import io.ably.lib.objects.integration.setup.IntegrationTest
import io.ably.lib.objects.size
import io.ably.lib.objects.state.ObjectsStateEvent
import io.ably.lib.objects.type.counter.LiveCounter
import io.ably.lib.objects.type.map.LiveMap
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.text.toByteArray

class DefaultLiveObjectsTest : IntegrationTest() {

  @Test
  fun testChannelObjects() = runTest {
    val channelName = generateChannelName()
    val channel = getRealtimeChannel(channelName)
    val objects = channel.objects
    assertNotNull(objects)
  }

  @Test
  fun testObjectsSyncEvents() = runTest {
    val channelName = generateChannelName()
    // Initialize the root map on the channel with initial data
    restObjects.initializeRootMap(channelName)

    val channel = getRealtimeChannel(channelName, autoAttach = false)
    val objects = channel.objects
    assertNotNull(objects)

    assertEquals(ObjectsState.INITIALIZED, objects.State, "Initial state should be INITIALIZED")

    val syncStates = mutableListOf<ObjectsStateEvent>()
    objects.on(ObjectsStateEvent.SYNCING) {
      syncStates.add(it)
    }
    objects.on(ObjectsStateEvent.SYNCED) {
      syncStates.add(it)
    }

    channel.attach()

    assertWaiter { syncStates.size == 2 } // Wait for both SYNCING and SYNCED events

    assertEquals(ObjectsStateEvent.SYNCING, syncStates[0], "First event should be SYNCING")
    assertEquals(ObjectsStateEvent.SYNCED, syncStates[1], "Second event should be SYNCED")

    val rootMap = objects.root
    assertEquals(6, rootMap.size(), "Root map should have 6 entries after sync")
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
    val rootMap = channel.objects.root
    assertNotNull(rootMap)

    // Assert Counter Objects
    // Test emptyCounter - should have initial value of 0
    val emptyCounter = rootMap.get("emptyCounter") as LiveCounter
    assertNotNull(emptyCounter)
    assertEquals(0.0, emptyCounter.value())

    // Test initialValueCounter - should have initial value of 10
    val initialValueCounter = rootMap.get("initialValueCounter") as LiveCounter
    assertNotNull(initialValueCounter)
    assertEquals(10.0, initialValueCounter.value())

    // Test referencedCounter - should have initial value of 20
    val referencedCounter = rootMap.get("referencedCounter") as LiveCounter
    assertNotNull(referencedCounter)
    assertEquals(20.0, referencedCounter.value())

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
    assertEquals(20.0, referencedMapCounter.value()) // Should point to the same counter with value 20

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
    assertEquals(20.0, mapRefCounter.value()) // Should point to the same counter with value 20
  }

  /**
   * Spec: RTLO4e - Tests the removal of objects from the root map.
   * Server runs periodic garbage collection (GC) to remove orphaned objects and will send
   * OBJECT_DELETE events for objects that are no longer referenced.
   * `OBJECT_DELETE` event is not covered in the test and we only check if map entries are removed
   */
  @Test
  fun testObjectRemovalFromRoot() = runTest {
    val channelName = generateChannelName()
    // Initialize the root map on the channel with initial data
    restObjects.initializeRootMap(channelName)

    val channel = getRealtimeChannel(channelName)
    val rootMap = channel.objects.root
    assertEquals(6L, rootMap.size()) // Should have 6 entries initially

    // Remove the "referencedCounter" from the root map
    assertNotNull(rootMap.get("referencedCounter")) // Access to ensure it exists before removal

    restObjects.removeMapValue(channelName, "root", "referencedCounter")

    assertWaiter { rootMap.size() == 5L } // Wait for the removal to complete
    assertNull(rootMap.get("referencedCounter")) // Should be null after removal

    // Remove the "referencedMap" from the root map
    assertNotNull(rootMap.get("referencedMap")) // Access to ensure it exists before removal

    restObjects.removeMapValue(channelName, "root", "referencedMap")

    assertWaiter { rootMap.size() == 4L } // Wait for the removal to complete
    assertNull(rootMap.get("referencedMap")) // Should be null after removal
  }
}
