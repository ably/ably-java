package io.ably.lib.objects.unit.objects

import io.ably.lib.objects.DefaultLiveObjects
import io.ably.lib.objects.ROOT_OBJECT_ID
import io.ably.lib.objects.type.livecounter.DefaultLiveCounter
import io.ably.lib.objects.type.livemap.DefaultLiveMap
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    // RTO3a - ObjectsPool is a Dict, a map of LiveObjects keyed by objectId string
    val testLiveMap = DefaultLiveMap("testObjectId", mockk(relaxed = true), objectsPool)
    objectsPool.set("testObjectId", testLiveMap)
    val testLiveCounter = DefaultLiveCounter("testCounterId", mockk(relaxed = true))
    objectsPool.set("testCounterId", testLiveCounter)
    // Assert that the objects are stored in the pool
    assertEquals(testLiveMap, objectsPool.get("testObjectId"))
    assertEquals(testLiveCounter, objectsPool.get("testCounterId"))
  }
}
