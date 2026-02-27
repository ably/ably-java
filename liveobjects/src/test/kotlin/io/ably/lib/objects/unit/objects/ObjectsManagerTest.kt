package io.ably.lib.objects.unit.objects

import io.ably.lib.objects.*
import io.ably.lib.objects.DefaultRealtimeObjects
import io.ably.lib.objects.ObjectMessage
import io.ably.lib.objects.ObjectState
import io.ably.lib.objects.ObjectsOperationSource
import io.ably.lib.objects.ObjectsState
import io.ably.lib.objects.type.livecounter.DefaultLiveCounter
import io.ably.lib.objects.type.livemap.DefaultLiveMap
import io.ably.lib.objects.unit.*
import io.ably.lib.objects.unit.getDefaultRealtimeObjectsWithMockedDeps
import io.ably.lib.types.AblyException
import io.ably.lib.types.ErrorInfo
import io.mockk.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Test
import kotlin.test.*

class ObjectsManagerTest {

  // Track instances created in tests to ensure background coroutines are cancelled at teardown
  private val testInstances = mutableListOf<DefaultRealtimeObjects>()

  private fun makeRealtimeObjects(channelName: String = "testChannel"): DefaultRealtimeObjects {
    return DefaultRealtimeObjects(channelName, getMockObjectsAdapter()).also { testInstances.add(it) }
  }

  @Test
  fun `(RTO5) ObjectsManager should handle object sync messages`() {
    val defaultRealtimeObjects = getDefaultRealtimeObjectsWithMockedDeps()
    assertEquals(ObjectsState.Initialized, defaultRealtimeObjects.state, "Initial state should be INITIALIZED")

    val objectsManager = defaultRealtimeObjects.ObjectsManager

    mockZeroValuedObjects()

    // Populate objectsPool with existing objects
    val objectsPool = defaultRealtimeObjects.ObjectsPool
    objectsPool.set("map:testObject@1", mockk<DefaultLiveMap>(relaxed = true))
    objectsPool.set("counter:testObject@4", mockk<DefaultLiveCounter>(relaxed = true))

    // Incoming object messages
    val objectMessage1 = ObjectMessage(
      id = "testId1",
      objectState = ObjectState(
        objectId = "map:testObject@1", // already exists in pool
        tombstone = false,
        siteTimeserials = mapOf("site1" to "syncSerial1"),
        map = ObjectsMap(),
      )
    )
    val objectMessage2 = ObjectMessage(
      id = "testId2",
      objectState = ObjectState(
        objectId = "counter:testObject@2", // Does not exist in pool
        tombstone = false,
        siteTimeserials = mapOf("site1" to "syncSerial1"),
        counter = ObjectsCounter(count = 20.0)
      )
    )
    val objectMessage3 = ObjectMessage(
      id = "testId3",
      objectState = ObjectState(
        objectId = "map:testObject@3", // Does not exist in pool
        tombstone = false,
        siteTimeserials = mapOf("site1" to "syncSerial1"),
        map = ObjectsMap(),
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

    assertEquals(ObjectsState.Synced, defaultRealtimeObjects.state, "State should be SYNCED after sync sequence")
    // After sync `counter:testObject@4` will be removed from pool
    assertNull(objectsPool.get("counter:testObject@4"))
    assertEquals(4, objectsPool.size(), "Objects pool should contain 4 objects after sync including root")
    assertNotNull(objectsPool.get(ROOT_OBJECT_ID), "Root object should still exist in pool")
    val testObject1 = objectsPool.get("map:testObject@1")
    assertNotNull(testObject1, "map:testObject@1 should exist in pool after sync")
    verify(exactly = 1) {
      testObject1.applyObjectSync(any<ObjectMessage>())
    }
    val testObject2 = objectsPool.get("counter:testObject@2")
    assertNotNull(testObject2, "counter:testObject@2 should exist in pool after sync")
    verify(exactly = 1) {
      testObject2.applyObjectSync(any<ObjectMessage>())
    }
    val testObject3 = objectsPool.get("map:testObject@3")
    assertNotNull(testObject3, "map:testObject@3 should exist in pool after sync")
    verify(exactly = 1) {
      testObject3.applyObjectSync(any<ObjectMessage>())
    }
  }

  @Test
  fun `(RTO8) ObjectsManager should apply object operation when state is synced`() {
    val defaultRealtimeObjects = getDefaultRealtimeObjectsWithMockedDeps()
    defaultRealtimeObjects.state = ObjectsState.Synced // Ensure we're in SYNCED state

    val objectsManager = defaultRealtimeObjects.ObjectsManager

    mockZeroValuedObjects()

    // Populate objectsPool with existing objects
    val objectsPool = defaultRealtimeObjects.ObjectsPool
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
      testObject1.applyObject(objectMessage1, any())
    }
    val testObject2 = objectsPool.get("counter:testObject@2")
    assertNotNull(testObject2, "counter:testObject@2 should exist in pool after sync")
    verify(exactly = 1) {
      testObject2.applyObject(objectMessage2, any())
    }
    val testObject3 = objectsPool.get("map:testObject@3")
    assertNotNull(testObject3, "map:testObject@3 should exist in pool after sync")
    verify(exactly = 1) {
      testObject3.applyObject(objectMessage3, any())
    }
  }

  @Test
  fun `(RTO7) ObjectsManager should buffer operations when not in sync, apply them after synced`() {
    val defaultRealtimeObjects = getDefaultRealtimeObjectsWithMockedDeps()
    assertEquals(ObjectsState.Initialized, defaultRealtimeObjects.state, "Initial state should be INITIALIZED")

    val objectsManager = defaultRealtimeObjects.ObjectsManager
    assertEquals(0, objectsManager.BufferedObjectOperations.size, "RTO7a1 - Initial buffer should be empty")

    val objectsPool = defaultRealtimeObjects.ObjectsPool
    assertEquals(1, objectsPool.size(), "RTO7a2 - Initial pool should contain only root object")

    mockZeroValuedObjects()

    // Set state to SYNCING
    defaultRealtimeObjects.state = ObjectsState.Syncing

    val objectMessage = ObjectMessage(
      id = "testId",
      operation = ObjectOperation(
        action = ObjectOperationAction.CounterCreate,
        objectId = "counter:testObject@1",
        counterOp = ObjectsCounterOp(amount = 5.0)
      ),
      serial = "serial1",
      siteCode = "site1"
    )

    // RTO7a - Buffer operations during sync
    objectsManager.handleObjectMessages(listOf(objectMessage))

    verify(exactly = 0) {
      objectsManager["applyObjectMessages"](any<List<ObjectMessage>>(), any<ObjectsOperationSource>())
    }
    assertEquals(1, objectsManager.BufferedObjectOperations.size)
    assertEquals(objectMessage, objectsManager.BufferedObjectOperations[0])
    assertEquals(1, objectsPool.size(), "Pool should still contain only root object during sync")

    // RTO7 - Apply buffered operations after sync
    objectsManager.endSync(false) // End sync without new sync
    verify(exactly = 1) {
      objectsManager["applyObjectMessages"](any<List<ObjectMessage>>(), any<ObjectsOperationSource>())
    }
    assertEquals(0, objectsManager.BufferedObjectOperations.size)
    assertEquals(2, objectsPool.size(), "Pool should contain 2 objects after applying buffered operations")
    assertNotNull(objectsPool.get("counter:testObject@1"), "Counter object should be created after sync")
    assertTrue(objectsPool.get("counter:testObject@1") is DefaultLiveCounter, "Should create a DefaultLiveCounter object")
  }

  @Test
  fun `(RTO23 COUNTER_INC) applyAckResult applies COUNTER_INC locally and tracks serial in appliedOnAckSerials`() = runTest {
    val defaultRealtimeObjects = makeRealtimeObjects()
    defaultRealtimeObjects.state = ObjectsState.Synced

    val counter = DefaultLiveCounter.zeroValue("counter:test@1", defaultRealtimeObjects)
    defaultRealtimeObjects.objectsPool.set("counter:test@1", counter)

    val msg = ObjectMessage(
      operation = ObjectOperation(
        action = ObjectOperationAction.CounterInc,
        objectId = "counter:test@1",
        counterOp = ObjectsCounterOp(amount = 5.0)
      ),
      serial = "ser-ack-01",
      siteCode = "site1"
    )

    val objectsManager = defaultRealtimeObjects.ObjectsManager
    objectsManager.applyAckResult(listOf(msg))

    // Verify operation applied locally (RTO23)
    assertEquals(5.0, counter.data.get(), "COUNTER_INC should be applied locally on ACK")
    // Serial added to appliedOnAckSerials (RTO9a2a4)
    assertTrue(defaultRealtimeObjects.appliedOnAckSerials.contains("ser-ack-01"),
      "serial should be in appliedOnAckSerials")
    // siteTimeserials NOT updated (LOCAL source, RTLC7c)
    assertFalse(counter.siteTimeserials.containsKey("site1"),
      "siteTimeserials should NOT be updated for LOCAL source")
  }

  @Test
  fun `(RTO23 MAP_SET) applyAckResult applies MAP_SET locally and tracks serial in appliedOnAckSerials`() = runTest {
    val defaultRealtimeObjects = makeRealtimeObjects()
    defaultRealtimeObjects.state = ObjectsState.Synced

    val liveMap = DefaultLiveMap.zeroValue("map:testMap@1", defaultRealtimeObjects)
    defaultRealtimeObjects.objectsPool.set("map:testMap@1", liveMap)

    val msg = ObjectMessage(
      operation = ObjectOperation(
        action = ObjectOperationAction.MapSet,
        objectId = "map:testMap@1",
        mapOp = ObjectsMapOp(key = "key1", data = ObjectData(value = ObjectValue.String("value1")))
      ),
      serial = "ser-map-01",
      siteCode = "site1"
    )

    val objectsManager = defaultRealtimeObjects.ObjectsManager
    objectsManager.applyAckResult(listOf(msg))

    // Verify entry was set (LOCAL source)
    assertEquals("value1", liveMap.data["key1"]?.data?.value?.value,
      "MAP_SET should be applied locally on ACK")
    // Entry timeserial should be updated (within LiveMapManager, regardless of source)
    assertEquals("ser-map-01", liveMap.data["key1"]?.timeserial,
      "entry timeserial should be set by MAP_SET")
    // Serial added to appliedOnAckSerials
    assertTrue(defaultRealtimeObjects.appliedOnAckSerials.contains("ser-map-01"),
      "serial should be in appliedOnAckSerials")
    // Object-level siteTimeserials NOT updated (LOCAL source, RTLM15c)
    assertFalse(liveMap.siteTimeserials.containsKey("site1"),
      "siteTimeserials should NOT be updated for LOCAL source")
  }

  @Test
  fun `(RTO9a3) echo CHANNEL message is deduplicated - serial removed, data NOT re-applied`() {
    val defaultRealtimeObjects = makeRealtimeObjects()
    defaultRealtimeObjects.state = ObjectsState.Synced

    val counter = DefaultLiveCounter.zeroValue("counter:test@1", defaultRealtimeObjects)
    counter.data.set(10.0)
    defaultRealtimeObjects.objectsPool.set("counter:test@1", counter)

    // Simulate: serial already applied locally on ACK
    defaultRealtimeObjects.appliedOnAckSerials.add("ser-echo-01")

    val echoMsg = ObjectMessage(
      operation = ObjectOperation(
        action = ObjectOperationAction.CounterInc,
        objectId = "counter:test@1",
        counterOp = ObjectsCounterOp(amount = 5.0)
      ),
      serial = "ser-echo-01",
      siteCode = "site1"
    )

    val objectsManager = defaultRealtimeObjects.ObjectsManager
    objectsManager.handleObjectMessages(listOf(echoMsg))

    // Data NOT double-applied (RTO9a3)
    assertEquals(10.0, counter.data.get(), "data should NOT be re-applied on echo dedup")
    // Serial removed from appliedOnAckSerials (RTO9a3)
    assertFalse(defaultRealtimeObjects.appliedOnAckSerials.contains("ser-echo-01"),
      "serial should be removed from appliedOnAckSerials after dedup")
    // siteTimeserials NOT updated - discarded without further action (RTO9a3)
    assertNull(counter.siteTimeserials["site1"],
      "siteTimeserials should NOT be updated by echo dedup (RTO9a3: discard without further action)")
  }

  @Test
  fun `(RTO9) non-echo CHANNEL message is applied normally when serial not in appliedOnAckSerials`() {
    val defaultRealtimeObjects = makeRealtimeObjects()
    defaultRealtimeObjects.state = ObjectsState.Synced

    val counter = DefaultLiveCounter.zeroValue("counter:test@1", defaultRealtimeObjects)
    counter.data.set(10.0)
    defaultRealtimeObjects.objectsPool.set("counter:test@1", counter)

    val msg = ObjectMessage(
      operation = ObjectOperation(
        action = ObjectOperationAction.CounterInc,
        objectId = "counter:test@1",
        counterOp = ObjectsCounterOp(amount = 3.0)
      ),
      serial = "ser-channel-01",
      siteCode = "site1"
    )

    // serial NOT in appliedOnAckSerials — this is a regular (non-echo) CHANNEL message
    assertFalse(defaultRealtimeObjects.appliedOnAckSerials.contains("ser-channel-01"))

    val objectsManager = defaultRealtimeObjects.ObjectsManager
    objectsManager.handleObjectMessages(listOf(msg))

    // Should be applied normally (CHANNEL source)
    assertEquals(13.0, counter.data.get(), "counter should be incremented by CHANNEL message")
    // siteTimeserials IS updated for CHANNEL source (RTLC7c)
    assertEquals("ser-channel-01", counter.siteTimeserials["site1"],
      "siteTimeserials should be updated for CHANNEL source")
  }

  @Test
  fun `(RTO22) applyAckResult buffers messages during SYNCING and appliedOnAckSerials stays empty`() = runTest {
    val defaultRealtimeObjects = makeRealtimeObjects()
    defaultRealtimeObjects.state = ObjectsState.Syncing

    val counter = DefaultLiveCounter.zeroValue("counter:test@1", defaultRealtimeObjects)
    defaultRealtimeObjects.objectsPool.set("counter:test@1", counter)

    val msg = ObjectMessage(
      operation = ObjectOperation(
        action = ObjectOperationAction.CounterInc,
        objectId = "counter:test@1",
        counterOp = ObjectsCounterOp(amount = 5.0)
      ),
      serial = "ser-ack-01",
      siteCode = "site1"
    )

    val objectsManager = defaultRealtimeObjects.ObjectsManager

    // Launch applyAckResult in background — will suspend while SYNCING (RTO22)
    val ackJob = launch {
      objectsManager.applyAckResult(listOf(msg))
    }

    // Allow the coroutine to start and reach deferred.await()
    yield()

    // RTO22 — buffered, not yet applied
    assertEquals(1, objectsManager.BufferedAcks.size, "message should be buffered in bufferedAcks during SYNCING")
    assertTrue(defaultRealtimeObjects.appliedOnAckSerials.isEmpty(),
      "appliedOnAckSerials should be empty while message is buffered")
    assertEquals(0.0, counter.data.get(), "data should not be applied while SYNCING")

    // Cancel the job to clean up
    ackJob.cancel()
  }

  @Test
  fun `(RTO5c6b) buffered ACKs are applied before buffered OBJECT messages after sync ends`() = runTest {
    val defaultRealtimeObjects = makeRealtimeObjects()
    defaultRealtimeObjects.state = ObjectsState.Synced

    val counter = DefaultLiveCounter.zeroValue("counter:test@1", defaultRealtimeObjects)
    counter.data.set(10.0)
    defaultRealtimeObjects.objectsPool.set("counter:test@1", counter)

    val objectsManager = defaultRealtimeObjects.ObjectsManager

    // Use the same message for both the ACK and the echo to simulate the real scenario
    val incMsg = ObjectMessage(
      operation = ObjectOperation(
        action = ObjectOperationAction.CounterInc,
        objectId = "counter:test@1",
        counterOp = ObjectsCounterOp(amount = 5.0)
      ),
      serial = "ser-01",
      siteCode = "site1"
    )

    // Start a new sync (state → SYNCING)
    objectsManager.startNewSync(null)
    assertEquals(ObjectsState.Syncing, defaultRealtimeObjects.state)

    // Buffer the ACK (suspends since SYNCING)
    val ackJob = launch {
      objectsManager.applyAckResult(listOf(incMsg))
    }
    yield()
    assertEquals(1, objectsManager.BufferedAcks.size)

    // Buffer the echo OBJECT message (also buffered since SYNCING)
    objectsManager.handleObjectMessages(listOf(incMsg))
    assertEquals(1, objectsManager.BufferedObjectOperations.size)

    // End sync — applies buffered ACKs first (LOCAL), then buffered OBJECTs (CHANNEL)
    objectsManager.endSync(false)
    ackJob.join()

    // After endSync:
    // 1. ACK applied (LOCAL): counter = 10 + 5 = 15; "ser-01" added to appliedOnAckSerials
    // 2. Echo CHANNEL message: "ser-01" found → RTO9a3 dedup (discard without further action, siteTimeserials NOT updated)
    assertEquals(15.0, counter.data.get(), "counter should be incremented once (not twice)")
    assertNull(counter.siteTimeserials["site1"],
      "siteTimeserials should NOT be updated by echo dedup (RTO9a3: discard without further action)")
    assertTrue(defaultRealtimeObjects.appliedOnAckSerials.isEmpty(),
      "appliedOnAckSerials should be empty after dedup")
    assertEquals(ObjectsState.Synced, defaultRealtimeObjects.state)
  }

  @Test
  fun `(RTO21b) startNewSync clears appliedOnAckSerials and cancels buffered ACKs`() = runTest {
    val defaultRealtimeObjects = makeRealtimeObjects()
    defaultRealtimeObjects.state = ObjectsState.Synced

    // Add a serial to appliedOnAckSerials
    defaultRealtimeObjects.appliedOnAckSerials.add("ser-old-01")

    val counter = DefaultLiveCounter.zeroValue("counter:test@1", defaultRealtimeObjects)
    defaultRealtimeObjects.objectsPool.set("counter:test@1", counter)

    val objectsManager = defaultRealtimeObjects.ObjectsManager

    // Start first sync → SYNCING
    objectsManager.startNewSync("seq-1")

    // Simulate a buffered ACK during SYNCING
    val msg = ObjectMessage(
      operation = ObjectOperation(
        action = ObjectOperationAction.CounterInc,
        objectId = "counter:test@1",
        counterOp = ObjectsCounterOp(amount = 5.0)
      ),
      serial = "ser-buffered",
      siteCode = "site1"
    )

    val ackJob = launch {
      objectsManager.applyAckResult(listOf(msg))
    }
    yield()
    assertEquals(1, objectsManager.BufferedAcks.size, "should have 1 buffered ACK")

    // Start a new sync — should clear both appliedOnAckSerials and bufferedAcks (RTO21b)
    objectsManager.startNewSync("seq-2")

    // RTO21b — both cleared
    assertTrue(defaultRealtimeObjects.appliedOnAckSerials.isEmpty(),
      "appliedOnAckSerials should be cleared on new sync")
    assertEquals(0, objectsManager.BufferedAcks.size,
      "bufferedAcks should be cleared on new sync")

    // The buffered ACK coroutine should be cancelled
    ackJob.join()
    assertTrue(ackJob.isCancelled, "buffered ACK job should be cancelled when new sync starts")
  }

  @Test
  fun `(RTO5c9) endSync clears appliedOnAckSerials`() {
    val defaultRealtimeObjects = makeRealtimeObjects()
    defaultRealtimeObjects.state = ObjectsState.Synced

    val objectsManager = defaultRealtimeObjects.ObjectsManager

    // Start a sync
    objectsManager.startNewSync(null)

    // Manually add a serial (simulating an ACK received during sync)
    defaultRealtimeObjects.appliedOnAckSerials.add("ser-during-sync")

    // End sync — should clear appliedOnAckSerials (RTO5c9)
    objectsManager.endSync(false)

    assertTrue(defaultRealtimeObjects.appliedOnAckSerials.isEmpty(),
      "appliedOnAckSerials should be cleared at sync end (RTO5c9)")
    assertEquals(ObjectsState.Synced, defaultRealtimeObjects.state)
  }

  @Test
  fun `(RTO20e1) failBufferedAcks fails pending deferreds with error code 92008`() = runTest {
    val defaultRealtimeObjects = makeRealtimeObjects()
    defaultRealtimeObjects.state = ObjectsState.Syncing

    val counter = DefaultLiveCounter.zeroValue("counter:test@1", defaultRealtimeObjects)
    defaultRealtimeObjects.objectsPool.set("counter:test@1", counter)

    val msg = ObjectMessage(
      operation = ObjectOperation(
        action = ObjectOperationAction.CounterInc,
        objectId = "counter:test@1",
        counterOp = ObjectsCounterOp(amount = 5.0)
      ),
      serial = "ser-01",
      siteCode = "site1"
    )

    val objectsManager = defaultRealtimeObjects.ObjectsManager
    val error = AblyException.fromErrorInfo(
      ErrorInfo("channel failed while waiting for sync", 400, 92008)
    )

    var caughtException: Exception? = null
    val ackJob = launch {
      try {
        objectsManager.applyAckResult(listOf(msg))
      } catch (e: Exception) {
        caughtException = e
      }
    }

    // Allow the coroutine to start and suspend on deferred.await()
    yield()

    // Fail the buffered ACK (RTO20e1)
    objectsManager.failBufferedAcks(error)

    ackJob.join()

    assertNotNull(caughtException, "buffered ACK should fail with an exception")
    val ablyEx = caughtException as? AblyException
    assertNotNull(ablyEx, "exception should be an AblyException")
    assertEquals(92008, ablyEx.errorInfo.code,
      "error code should be 92008 (PublishAndApplyFailedDueToChannelState)")
    assertEquals(400, ablyEx.errorInfo.statusCode, "status code should be 400")
  }

  private fun mockZeroValuedObjects() {
    mockkObject(DefaultLiveMap.Companion)
    every {
      DefaultLiveMap.zeroValue(any<String>(), any<DefaultRealtimeObjects>())
    } answers {
      mockk<DefaultLiveMap>(relaxed = true)
    }
    mockkObject(DefaultLiveCounter.Companion)
    every {
      DefaultLiveCounter.zeroValue(any<String>(), any<DefaultRealtimeObjects>())
    } answers {
      mockk<DefaultLiveCounter>(relaxed = true)
    }
  }

  @AfterTest
  fun tearDown() {
    val cleanupError = AblyException.fromErrorInfo(ErrorInfo("test cleanup", 500))
    testInstances.forEach { it.dispose(cleanupError) }
    testInstances.clear()
    unmockkAll() // Clean up all mockk objects after each test
  }
}
