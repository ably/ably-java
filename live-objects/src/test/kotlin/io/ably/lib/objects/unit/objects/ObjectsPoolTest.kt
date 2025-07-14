package io.ably.lib.objects.unit.objects

import io.ably.lib.objects.DefaultLiveObjects
import io.ably.lib.objects.ObjectData
import io.ably.lib.objects.ROOT_OBJECT_ID
import io.ably.lib.objects.type.livecounter.DefaultLiveCounter
import io.ably.lib.objects.type.livemap.DefaultLiveMap
import io.ably.lib.objects.type.livemap.LiveMapEntry
import io.ably.lib.objects.unit.*
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObjectsPoolTest {

  @Test
  fun `(RTO3, RTO3a, RTO3b) An internal ObjectsPool should be used to maintain the list of objects present on a channel`() {
    val defaultLiveObjects = DefaultLiveObjects("dummyChannel", mockk(relaxed = true))
    val objectsPool = defaultLiveObjects.objectsPool
    assertNotNull(objectsPool)

    // RTO3b - It must always contain a LiveMap object with id root
    val rootLiveMap = objectsPool.get(ROOT_OBJECT_ID)
    assertNotNull(rootLiveMap)
    assertTrue(rootLiveMap is DefaultLiveMap)
    assertTrue(rootLiveMap.data.isEmpty())
    assertEquals(ROOT_OBJECT_ID, rootLiveMap.objectId)
    assertEquals(1, objectsPool.size(), "RTO3 - Should only contain the root object initially")

    // RTO3a - ObjectsPool is a Dict, a map of LiveObjects keyed by objectId string
    val testLiveMap = DefaultLiveMap("testObjectId", mockk(relaxed = true), objectsPool)
    objectsPool.set("testObjectId", testLiveMap)
    val testLiveCounter = DefaultLiveCounter("testCounterId", mockk(relaxed = true))
    objectsPool.set("testCounterId", testLiveCounter)
    // Assert that the objects are stored in the pool
    assertEquals(testLiveMap, objectsPool.get("testObjectId"))
    assertEquals(testLiveCounter, objectsPool.get("testCounterId"))
    assertEquals(3, objectsPool.size(), "RTO3 - Should have 3 objects in pool (root + testLiveMap + testLiveCounter)")
  }

  @Test
  fun `(RTO6) ObjectsPool should create zero-value objects if not exists`() {
    val defaultLiveObjects = DefaultLiveObjects("dummyChannel", mockk(relaxed = true))
    val objectsPool = spyk(defaultLiveObjects.objectsPool)
    assertEquals(1, objectsPool.size(), "RTO3 - Should only contain the root object initially")

    // Test creating zero-value map
    // RTO6b1, RTO6b2 - Type is parsed from the objectId format (map:hash@timestamp)
    val mapId = "map:xyz789@67890"
    val map = objectsPool.createZeroValueObjectIfNotExists(mapId)
    assertNotNull(map, "Should create a map object")
    assertTrue(map is DefaultLiveMap, "RTO6b2 - Should create a LiveMap for map type")
    assertEquals(mapId, map.objectId)
    assertTrue(map.data.isEmpty(), "RTO6b2 - Should create an empty map")
    assertEquals(2, objectsPool.size(), "RTO6 - root + map should be in pool after creation")

    // Test creating zero-value counter
    // RTO6b1, RTO6b3 - Type is parsed from the objectId format (counter:hash@timestamp)
    val counterId = "counter:abc123@12345"
    val counter = objectsPool.createZeroValueObjectIfNotExists(counterId)
    assertNotNull(counter, "Should create a counter object")
    assertTrue(counter is DefaultLiveCounter, "RTO6b3 - Should create a LiveCounter for counter type")
    assertEquals(counterId, counter.objectId)
    assertEquals(0L, counter.data, "RTO6b3 - Should create a zero-value counter")
    assertEquals(3, objectsPool.size(), "RTO6 - root + map + counter should be in pool after creation")

    // RTO6a - If object exists in pool, do not create a new one
    val existingMap = objectsPool.createZeroValueObjectIfNotExists(mapId)
    assertEquals(map, existingMap, "RTO6a - Should return existing object, not create a new one")
    val existingCounter = objectsPool.createZeroValueObjectIfNotExists(counterId)
    assertEquals(counter, existingCounter, "RTO6a - Should return existing object, not create a new one")
    assertEquals(3, objectsPool.size(), "RTO6 - Should still have 3 objects in pool after re-creation attempt")
  }

  @Test
  fun `(RTO4b1, RTO4b2) ObjectsPool should reset to initial pool retaining original root map`() {
    val defaultLiveObjects = DefaultLiveObjects("dummyChannel", mockk(relaxed = true))
    val objectsPool = defaultLiveObjects.objectsPool
    assertEquals(1, objectsPool.size())
    val rootMap = objectsPool.get(ROOT_OBJECT_ID) as DefaultLiveMap
    // add some data to the root map
    rootMap.data["initialKey1"] = LiveMapEntry(data = ObjectData("testValue1"))
    rootMap.data["initialKey2"] = LiveMapEntry(data = ObjectData("testValue2"))
    assertEquals(2, rootMap.data.size, "RTO3 - Root map should have initial data")

    // Add some objects
    objectsPool.set("testObjectId", DefaultLiveCounter("testObjectId", mockk(relaxed = true)))
    assertEquals(2, objectsPool.size()) // root + testObject
    objectsPool.set("anotherObjectId", DefaultLiveCounter("anotherObjectId", mockk(relaxed = true)))
    assertEquals(3, objectsPool.size()) // root + testObject + anotherObject
    objectsPool.set("testMapId", DefaultLiveMap("testMapId", mockk(relaxed = true), objectsPool))
    assertEquals(4, objectsPool.size()) // root + testObject + anotherObject + testMap

    // Reset to initial pool
    objectsPool.resetToInitialPool(true)

    // RTO4b1 - Should only contain root object
    assertEquals(1, objectsPool.size())
    assertEquals(rootMap, objectsPool.get(ROOT_OBJECT_ID))
    // RTO4b2 - RootMap should be empty after reset
    assertTrue(rootMap.data.isEmpty(), "RTO3 - Root map should be empty after reset")
  }

  @Test
  fun `(RTO5c2, RTO5c2a) ObjectsPool should delete extra object IDs`() {
    val defaultLiveObjects = DefaultLiveObjects("dummyChannel", mockk(relaxed = true))
    val objectsPool = defaultLiveObjects.objectsPool

    // Add some objects
    objectsPool.set("object1", DefaultLiveCounter("object1", mockk(relaxed = true)))
    objectsPool.set("object2", DefaultLiveCounter("object2", mockk(relaxed = true)))
    objectsPool.set("object3", DefaultLiveCounter("object3", mockk(relaxed = true)))
    assertEquals(4, objectsPool.size()) // root + 3 objects

    // Delete extra object IDs (keep only object1 and object2)
    val receivedObjectIds = mutableSetOf("object1", "object2")
    objectsPool.deleteExtraObjectIds(receivedObjectIds)

    // Should only contain root, object1, and object2
    assertEquals(3, objectsPool.size())
    // RTO5c2a - Should keep the root object
    assertNotNull(objectsPool.get(ROOT_OBJECT_ID))
    // RTO5c2 - Should delete object3 and keep object1 and object2
    assertNotNull(objectsPool.get("object1"))
    assertNotNull(objectsPool.get("object2"))
    assertNull(objectsPool.get("object3")) // Should be deleted
  }
}
