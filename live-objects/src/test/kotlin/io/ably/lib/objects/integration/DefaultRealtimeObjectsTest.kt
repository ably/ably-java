package io.ably.lib.objects.integration

import io.ably.lib.objects.*
import io.ably.lib.objects.integration.helpers.State
import io.ably.lib.objects.integration.helpers.fixtures.initializeRootMap
import io.ably.lib.objects.integration.helpers.simulateObjectDelete
import io.ably.lib.objects.integration.setup.IntegrationTest
import io.ably.lib.objects.state.ObjectsStateEvent
import io.ably.lib.objects.type.livecounter.DefaultLiveCounter
import io.ably.lib.objects.type.livemap.DefaultLiveMap
import io.ably.lib.objects.type.map.LiveMapUpdate
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.text.toByteArray

class DefaultRealtimeObjectsTest : IntegrationTest() {

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

    val channel = getRealtimeChannel(channelName)
    val objects = channel.objects
    assertNotNull(objects)

    assertEquals(ObjectsState.Initialized, objects.State, "Initial state should be INITIALIZED")

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
    val emptyCounter = rootMap.get("emptyCounter")?.asLiveCounter
    assertNotNull(emptyCounter)
    assertEquals(0.0, emptyCounter.value())

    // Test initialValueCounter - should have initial value of 10
    val initialValueCounter = rootMap.get("initialValueCounter")?.asLiveCounter
    assertNotNull(initialValueCounter)
    assertEquals(10.0, initialValueCounter.value())

    // Test referencedCounter - should have initial value of 20
    val referencedCounter = rootMap.get("referencedCounter")?.asLiveCounter
    assertNotNull(referencedCounter)
    assertEquals(20.0, referencedCounter.value())

    // Assert Map Objects
    // Test emptyMap - should be an empty map
    val emptyMap = rootMap.get("emptyMap")?.asLiveMap
    assertNotNull(emptyMap)
    assertEquals(0L, emptyMap.size())

    // Test referencedMap - should contain one key "counterKey" pointing to referencedCounter
    val referencedMap = rootMap.get("referencedMap")?.asLiveMap
    assertNotNull(referencedMap)
    assertEquals(1L, referencedMap.size())
    val referencedMapCounter = referencedMap.get("counterKey")?.asLiveCounter
    assertNotNull(referencedMapCounter)
    assertEquals(20.0, referencedMapCounter.value()) // Should point to the same counter with value 20

    // Test valuesMap - should contain all primitive data types and one map reference
    val valuesMap = rootMap.get("valuesMap")?.asLiveMap
    assertNotNull(valuesMap)
    assertEquals(13L, valuesMap.size()) // Should have 13 entries

    // Assert string values
    assertEquals("stringValue", valuesMap.get("string")?.asString)
    assertEquals("", valuesMap.get("emptyString")?.asString)

    // Assert binary values
    val bytesValue = valuesMap.get("bytes")?.asBinary
    assertNotNull(bytesValue)
    val expectedBinary = "eyJwcm9kdWN0SWQiOiAiMDAxIiwgInByb2R1Y3ROYW1lIjogImNhciJ9".toByteArray()
    assertTrue(expectedBinary.contentEquals(bytesValue)) // Should contain encoded JSON data

    val emptyBytesValue = valuesMap.get("emptyBytes")?.asBinary
    assertNotNull(emptyBytesValue)
    assertEquals(0, emptyBytesValue.size) // Should be empty byte array

    // Assert numeric values
    assertEquals(99999999.0, valuesMap.get("maxSafeNumber")?.asNumber)
    assertEquals(-99999999.0, valuesMap.get("negativeMaxSafeNumber")?.asNumber)
    assertEquals(1.0, valuesMap.get("number")?.asNumber)
    assertEquals(0.0, valuesMap.get("zero")?.asNumber)

    // Assert boolean values
    assertEquals(true, valuesMap.get("true")?.asBoolean)
    assertEquals(false, valuesMap.get("false")?.asBoolean)

    // Assert JSON object value - should contain {"foo": "bar"}
    val jsonObjectValue = valuesMap.get("object")?.asJsonObject
    assertNotNull(jsonObjectValue)
    assertEquals("bar", jsonObjectValue.get("foo").asString)

    // Assert JSON array value - should contain ["foo", "bar", "baz"]
    val jsonArrayValue = valuesMap.get("array")?.asJsonArray
    assertNotNull(jsonArrayValue)
    assertEquals(3, jsonArrayValue.size())
    assertEquals("foo", jsonArrayValue[0].asString)
    assertEquals("bar", jsonArrayValue[1].asString)
    assertEquals("baz", jsonArrayValue[2].asString)

    // Assert map reference - should point to the same referencedMap
    val mapRefValue = valuesMap.get("mapRef")?.asLiveMap
    assertNotNull(mapRefValue)
    assertEquals(1L, mapRefValue.size())
    val mapRefCounter = mapRefValue.get("counterKey")?.asLiveCounter
    assertNotNull(mapRefCounter)
    assertEquals(20.0, mapRefCounter.value()) // Should point to the same counter with value 20
  }

  /**
   * Server runs periodic garbage collection (GC) to remove orphaned objects and will send
   * OBJECT_DELETE events for objects that are no longer referenced.
   * So, we simulate the deletion of an object by sending an object delete ProtocolMessage.
   * This does not actually delete the object from the server, only simulates the deletion locally.
   * Spec: RTLO4e
   */
  @Test
  fun testObjectDelete() = runTest {
    val channelName = generateChannelName()
    // Initialize the root map on the channel with initial data
    restObjects.initializeRootMap(channelName)

    val channel = getRealtimeChannel(channelName)
    val rootMap = channel.objects.root
    assertEquals(6L, rootMap.size()) // Should have 6 entries initially

    // Remove the "referencedCounter" from the root map
    val refCounter = rootMap.get("referencedCounter")?.asLiveCounter
    assertNotNull(refCounter)
    // Subscribe to counter updates to verify removal
    val counterUpdates = mutableListOf<Double>()
    refCounter.subscribe { event ->
      counterUpdates.add(event.update.amount)
    }

    // Simulate the deletion of the referencedCounter object
    channel.objects.simulateObjectDelete(refCounter as DefaultLiveCounter)

    assertWaiter { rootMap.size() == 5L } // Wait for the removal to complete
    assertNull(rootMap.get("referencedCounter")) // Should be null after removal
    assertEquals(1, counterUpdates.size) // Should have received one update for deletion
    assertEquals(-20.0, counterUpdates[0]) // The update should indicate counter was removed with value 20

    // Remove the "referencedMap" from the root map
    val referencedMap = rootMap.get("referencedMap")?.asLiveMap
    assertNotNull(referencedMap)
    // Subscribe to map updates to verify removal
    val mapUpdates = mutableListOf<Map<String, LiveMapUpdate.Change>>()
    referencedMap.subscribe { event ->
      mapUpdates.add(event.update)
    }

    // Simulate the deletion of the referencedMap object
    channel.objects.simulateObjectDelete(referencedMap as DefaultLiveMap)

    assertWaiter { rootMap.size() == 4L } // Wait for the removal to complete
    assertNull(rootMap.get("referencedMap")) // Should be null after removal
    assertEquals(1, mapUpdates.size) // Should have received one update for deletion

    val updatedMap = mapUpdates.first()
    assertEquals(1, updatedMap.size) // Should have one change
    assertEquals("counterKey", updatedMap.keys.first()) // The change should be for the "counterKey"
    assertEquals(LiveMapUpdate.Change.REMOVED, updatedMap.values.first()) // Should indicate removal

    // Remove the "valuesMap" from the root map
    val valuesMap = rootMap.get("valuesMap")?.asLiveMap
    assertNotNull(valuesMap)
    // Subscribe to map updates to verify removal
    val valuesMapUpdates = mutableListOf<Map<String, LiveMapUpdate.Change>>()
    valuesMap.subscribe { event ->
      valuesMapUpdates.add(event.update)
    }

    // Simulate the deletion of the valuesMap object
    channel.objects.simulateObjectDelete(valuesMap as DefaultLiveMap)

    assertWaiter { rootMap.size() == 3L } // Wait for the removal to complete
    assertNull(rootMap.get("valuesMap")) // Should be null after removal
    assertEquals(1, valuesMapUpdates.size) // Should have received one update for deletion

    val updatedValuesMap = valuesMapUpdates.first()
    assertEquals(13, updatedValuesMap.size) // Should have 13 changes (one for each entry in valuesMap)
    // Verify that all entries in valuesMap were marked as REMOVED
    updatedValuesMap.values.forEach { change ->
      assertEquals(LiveMapUpdate.Change.REMOVED, change)
    }
  }
}
