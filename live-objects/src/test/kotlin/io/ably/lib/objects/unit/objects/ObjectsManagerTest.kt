package io.ably.lib.objects.unit.objects

import io.ably.lib.objects.*
import io.ably.lib.objects.ObjectMessage
import io.ably.lib.objects.ObjectState
import io.ably.lib.objects.ObjectsState
import io.ably.lib.objects.type.livecounter.DefaultLiveCounter
import io.ably.lib.objects.type.livemap.DefaultLiveMap
import io.ably.lib.objects.unit.*
import io.ably.lib.objects.unit.getDefaultLiveObjectsWithMockedDeps
import io.mockk.*
import org.junit.Test
import kotlin.test.*

class ObjectsManagerTest {

  @Test
  fun `(RTO5) ObjectsManager should handle object sync messages`() {
    val defaultLiveObjects = getDefaultLiveObjectsWithMockedDeps()
    assertEquals(ObjectsState.INITIALIZED, defaultLiveObjects.state, "Initial state should be INITIALIZED")

    val objectsManager = defaultLiveObjects.ObjectsManager

    mockZeroValuedObjects()

    // Populate objectsPool with existing objects
    val objectsPool = defaultLiveObjects.ObjectsPool
    objectsPool.set("map:testObject@1", mockk<DefaultLiveMap>(relaxed = true))
    objectsPool.set("counter:testObject@4", mockk<DefaultLiveCounter>(relaxed = true))

    // Incoming object messages
    val objectMessage1 = ObjectMessage(
      id = "testId1",
      objectState = ObjectState(
        objectId = "map:testObject@1", // already exists in pool
        tombstone = false,
        siteTimeserials = mapOf("site1" to "syncSerial1"),
        map = ObjectMap(),
      )
    )
    val objectMessage2 = ObjectMessage(
      id = "testId2",
      objectState = ObjectState(
        objectId = "counter:testObject@2", // Does not exist in pool
        tombstone = false,
        siteTimeserials = mapOf("site1" to "syncSerial1"),
        counter = ObjectCounter(count = 20.0)
      )
    )
    val objectMessage3 = ObjectMessage(
      id = "testId3",
      objectState = ObjectState(
        objectId = "map:testObject@3", // Does not exist in pool
        tombstone = false,
        siteTimeserials = mapOf("site1" to "syncSerial1"),
        map = ObjectMap(),
      )
    )
    // Should start and end sync, apply object states, and create new objects for missing ones
    objectsManager.handleObjectSyncMessages(listOf(objectMessage1, objectMessage2, objectMessage3), "sync-123:")

    verify(exactly = 1) {
      objectsManager.startNewSync("sync-123")
    }
    verify(exactly = 1) {
      objectsManager.endSync(true) // deferStateEvent = true since new sync was started
    }
    val newlyCreatedObjects = mutableListOf<ObjectState>()
    verify(exactly = 2) {
      objectsManager["createObjectFromState"](capture(newlyCreatedObjects))
    }
    assertEquals("counter:testObject@2", newlyCreatedObjects[0].objectId)
    assertEquals("map:testObject@3", newlyCreatedObjects[1].objectId)

    assertEquals(ObjectsState.SYNCED, defaultLiveObjects.state, "State should be SYNCED after sync sequence")
    // After sync `counter:testObject@4` will be removed from pool
    assertNull(objectsPool.get("counter:testObject@4"))
    assertEquals(4, objectsPool.size(), "Objects pool should contain 4 objects after sync including root")
    assertNotNull(objectsPool.get(ROOT_OBJECT_ID), "Root object should still exist in pool")
    val testObject1 = objectsPool.get("map:testObject@1")
    assertNotNull(testObject1, "map:testObject@1 should exist in pool after sync")
    verify(exactly = 1) {
      testObject1.applyObjectSync(any<ObjectState>())
    }
    val testObject2 = objectsPool.get("counter:testObject@2")
    assertNotNull(testObject2, "counter:testObject@2 should exist in pool after sync")
    verify(exactly = 1) {
      testObject2.applyObjectSync(any<ObjectState>())
    }
    val testObject3 = objectsPool.get("map:testObject@3")
    assertNotNull(testObject3, "map:testObject@3 should exist in pool after sync")
    verify(exactly = 1) {
      testObject3.applyObjectSync(any<ObjectState>())
    }
  }

  @Test
  fun `(RTO8) ObjectsManager should apply object operation when state is synced`() {
    val defaultLiveObjects = getDefaultLiveObjectsWithMockedDeps()
    defaultLiveObjects.state = ObjectsState.SYNCED // Ensure we're in SYNCED state

    val objectsManager = defaultLiveObjects.ObjectsManager

    mockZeroValuedObjects()

    // Populate objectsPool with existing objects
    val objectsPool = defaultLiveObjects.ObjectsPool
    objectsPool.set("map:testObject@1", mockk<DefaultLiveMap>(relaxed = true))

    // Incoming object messages with operation field instead of objectState
    val objectMessage1 = ObjectMessage(
      id = "testId1",
      operation = ObjectOperation(
        action = ObjectOperationAction.MapSet, // Assuming this is the right action for maps
        objectId = "map:testObject@1", // already exists in pool
      ),
      serial = "serial1",
      siteCode = "site1"
    )

    val objectMessage2 = ObjectMessage(
      id = "testId2",
      operation = ObjectOperation(
        action = ObjectOperationAction.CounterCreate, // Set the counter value
        objectId = "counter:testObject@2", // Does not exist in pool
      ),
      serial = "serial2",
      siteCode = "site1"
    )

    val objectMessage3 = ObjectMessage(
      id = "testId3",
      operation = ObjectOperation(
        action = ObjectOperationAction.MapCreate,
        objectId = "map:testObject@3", // Does not exist in pool
      ),
      serial = "serial3",
      siteCode = "site1"
    )

    // RTO8b - Apply messages immediately if synced
    objectsManager.handleObjectMessages(listOf(objectMessage1, objectMessage2, objectMessage3))
    assertEquals(0, objectsManager.BufferedObjectOperations.size, "No buffer needed in SYNCED state")

    assertEquals(4, objectsPool.size(), "Objects pool should contain 4 objects including root")
    assertNotNull(objectsPool.get(ROOT_OBJECT_ID), "Root object should still exist in pool")

    val testObject1 = objectsPool.get("map:testObject@1")
    assertNotNull(testObject1, "map:testObject@1 should exist in pool after sync")
    verify(exactly = 1) {
      testObject1.applyObject(objectMessage1)
    }
    val testObject2 = objectsPool.get("counter:testObject@2")
    assertNotNull(testObject2, "counter:testObject@2 should exist in pool after sync")
    verify(exactly = 1) {
      testObject2.applyObject(objectMessage2)
    }
    val testObject3 = objectsPool.get("map:testObject@3")
    assertNotNull(testObject3, "map:testObject@3 should exist in pool after sync")
    verify(exactly = 1) {
      testObject3.applyObject(objectMessage3)
    }
  }

  @Test
  fun `(RTO7) ObjectsManager should buffer operations when not in sync, apply them after synced`() {
    val defaultLiveObjects = getDefaultLiveObjectsWithMockedDeps()
    assertEquals(ObjectsState.INITIALIZED, defaultLiveObjects.state, "Initial state should be INITIALIZED")

    val objectsManager = defaultLiveObjects.ObjectsManager
    assertEquals(0, objectsManager.BufferedObjectOperations.size, "RTO7a1 - Initial buffer should be empty")

    val objectsPool = defaultLiveObjects.ObjectsPool
    assertEquals(1, objectsPool.size(), "RTO7a2 - Initial pool should contain only root object")

    mockZeroValuedObjects()

    // Set state to SYNCING
    defaultLiveObjects.state = ObjectsState.SYNCING

    val objectMessage = ObjectMessage(
      id = "testId",
      operation = ObjectOperation(
        action = ObjectOperationAction.CounterCreate,
        objectId = "counter:testObject@1",
        counterOp = ObjectCounterOp(amount = 5.0)
      ),
      serial = "serial1",
      siteCode = "site1"
    )

    // RTO7a - Buffer operations during sync
    objectsManager.handleObjectMessages(listOf(objectMessage))

    verify(exactly = 0) {
      objectsManager["applyObjectMessages"](any<List<ObjectMessage>>())
    }
    assertEquals(1, objectsManager.BufferedObjectOperations.size)
    assertEquals(objectMessage, objectsManager.BufferedObjectOperations[0])
    assertEquals(1, objectsPool.size(), "Pool should still contain only root object during sync")

    // RTO7 - Apply buffered operations after sync
    objectsManager.endSync(false) // End sync without new sync
    verify(exactly = 1) {
      objectsManager["applyObjectMessages"](any<List<ObjectMessage>>())
    }
    assertEquals(0, objectsManager.BufferedObjectOperations.size)
    assertEquals(2, objectsPool.size(), "Pool should contain 2 objects after applying buffered operations")
    assertNotNull(objectsPool.get("counter:testObject@1"), "Counter object should be created after sync")
    assertTrue(objectsPool.get("counter:testObject@1") is DefaultLiveCounter, "Should create a DefaultLiveCounter object")
  }

  private fun mockZeroValuedObjects() {
    mockkObject(DefaultLiveMap.Companion)
    every {
      DefaultLiveMap.zeroValue(any<String>(), any<DefaultLiveObjects>())
    } answers {
      mockk<DefaultLiveMap>(relaxed = true)
    }
    mockkObject(DefaultLiveCounter.Companion)
    every {
      DefaultLiveCounter.zeroValue(any<String>(), any<DefaultLiveObjects>())
    } answers {
      mockk<DefaultLiveCounter>(relaxed = true)
    }
  }

  @AfterTest
  fun tearDown() {
    unmockkAll() // Clean up all mockk objects after each test
  }
}
