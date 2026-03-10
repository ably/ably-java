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
      objectsManager.endSync() //
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
    objectsManager.endSync() // End sync without new sync
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
  fun `(RTO22) applyAckResult waits for SYNCED state and applies with LOCAL source after endSync`() = runTest {
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

    // Launch applyAckResult in background — will suspend while SYNCING
    val ackJob = launch {
      objectsManager.applyAckResult(listOf(msg))
    }

    // Allow the coroutine to start and reach deferred.await()
    yield()

    // During SYNCING — waiter is pending, message NOT yet applied
    assertNotNull(objectsManager.SyncCompletionWaiter, "sync completion should be pending during SYNCING")
    assertTrue(defaultRealtimeObjects.appliedOnAckSerials.isEmpty(),
      "appliedOnAckSerials should be empty while waiting")
    assertEquals(0.0, counter.data.get(), "data should not be applied while SYNCING")

    // End sync — completes waiters (schedules resume), then transitions to SYNCED
    objectsManager.endSync()
    ackJob.join()

    // After endSync — message applied with LOCAL source, serial tracked
    assertEquals(5.0, counter.data.get(), "counter should be incremented after endSync")
    assertTrue(defaultRealtimeObjects.appliedOnAckSerials.contains("ser-ack-01"),
      "serial should be tracked in appliedOnAckSerials after LOCAL apply")
    assertEquals(ObjectsState.Synced, defaultRealtimeObjects.state)
  }

  @Test
  fun `(RTO5c6) endSync applies buffered CHANNEL messages then unblocks pending ACK waiters`() = runTest {
    val defaultRealtimeObjects = makeRealtimeObjects()
    defaultRealtimeObjects.state = ObjectsState.Synced

    val counter = DefaultLiveCounter.zeroValue("counter:test@1", defaultRealtimeObjects)
    counter.data.set(10.0)
    defaultRealtimeObjects.objectsPool.set("counter:test@1", counter)

    val objectsManager = defaultRealtimeObjects.ObjectsManager

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

    // Suspend the ACK waiter (SYNCING)
    val ackJob = launch {
      objectsManager.applyAckResult(listOf(incMsg))
    }
    yield()
    assertNotNull(objectsManager.SyncCompletionWaiter)

    // Buffer the echo OBJECT message (also buffered since SYNCING)
    objectsManager.handleObjectMessages(listOf(incMsg))
    assertEquals(1, objectsManager.BufferedObjectOperations.size)

    // End sync — applies CHANNEL buffered messages first, clears appliedOnAckSerials, then unblocks waiters
    objectsManager.endSync()
    ackJob.join()

    // After endSync:
    // 1. CHANNEL echo applied: counter = 10 + 5 = 15; siteTimeserials["site1"] = "ser-01"
    // 2. appliedOnAckSerials cleared (was empty since no LOCAL applied during sync)
    // 3. Waiter resumes → LOCAL apply → canApplyOperation rejects (serial not newer) → applied=false
    assertEquals(15.0, counter.data.get(), "counter should be incremented exactly once")
    assertEquals("ser-01", counter.siteTimeserials["site1"],
      "siteTimeserials should be updated by CHANNEL echo")
    assertTrue(defaultRealtimeObjects.appliedOnAckSerials.isEmpty(),
      "appliedOnAckSerials should be empty (LOCAL apply was rejected by canApplyOperation)")
    assertEquals(ObjectsState.Synced, defaultRealtimeObjects.state)
  }

  @Test
  fun `(RTO5c9) endSync applies buffered CHANNEL messages then clears appliedOnAckSerials`() {
    val defaultRealtimeObjects = makeRealtimeObjects()

    val counter = DefaultLiveCounter.zeroValue("counter:test@1", defaultRealtimeObjects)
    counter.data.set(10.0)
    defaultRealtimeObjects.objectsPool.set("counter:test@1", counter)

    val objectsManager = defaultRealtimeObjects.ObjectsManager

    // Start a sync
    objectsManager.startNewSync(null)
    assertEquals(ObjectsState.Syncing, defaultRealtimeObjects.state)

    // Buffer a CHANNEL message during sync
    val channelMsg = ObjectMessage(
      operation = ObjectOperation(
        action = ObjectOperationAction.CounterInc,
        objectId = "counter:test@1",
        counterOp = ObjectsCounterOp(amount = 3.0)
      ),
      serial = "ser-channel-01",
      siteCode = "site1"
    )
    objectsManager.handleObjectMessages(listOf(channelMsg))
    assertEquals(1, objectsManager.BufferedObjectOperations.size)

    // Simulate a serial that was somehow added during sync
    defaultRealtimeObjects.appliedOnAckSerials.add("ser-during-sync")

    // End sync — CHANNEL messages applied first, then appliedOnAckSerials cleared (RTO5c9)
    objectsManager.endSync()

    // CHANNEL message was applied (counter incremented)
    assertEquals(13.0, counter.data.get(),
      "buffered CHANNEL message should be applied by endSync")
    // appliedOnAckSerials cleared at sync end (RTO5c9)
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

  @Test
  fun `Echo arrives before ACK - operation applied exactly once via canApplyOperation`() = runTest {
    val defaultRealtimeObjects = makeRealtimeObjects()
    defaultRealtimeObjects.state = ObjectsState.Synced

    val counter = DefaultLiveCounter.zeroValue("counter:test@1", defaultRealtimeObjects)
    counter.data.set(10.0)
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

    // Step 1: echo arrives first as CHANNEL message — applied normally
    objectsManager.handleObjectMessages(listOf(msg))
    assertEquals(15.0, counter.data.get(), "echo should be applied as CHANNEL message")
    assertEquals("ser-01", counter.siteTimeserials["site1"],
      "siteTimeserials should be updated by CHANNEL echo")

    // Step 2: ACK fires — applyAckResult with same serial (state is SYNCED, no suspend)
    objectsManager.applyAckResult(listOf(msg))

    // canApplyOperation rejects (serial "ser-01" is not newer than siteTimeserials["site1"] = "ser-01")
    assertEquals(15.0, counter.data.get(), "counter should NOT be incremented again by late ACK apply")
    // applied=false → serial NOT added to appliedOnAckSerials
    assertFalse(defaultRealtimeObjects.appliedOnAckSerials.contains("ser-01"),
      "serial should NOT be in appliedOnAckSerials when LOCAL apply was rejected")
  }

  @Test
  fun `publishAndApply logs error and returns without apply when siteCode is null`() = runTest {
    val adapter = getMockObjectsAdapter()
    // Create a ConnectionManager mock with all fields needed for publish() to succeed
    val cm = mockk<io.ably.lib.transport.ConnectionManager>(relaxed = true)
    cm.maxMessageSize = 65536 // direct field assignment bypasses mock interception issues
    every { cm.isActive } returns true
    every { cm.send(any(), any(), any()) } answers {
      @Suppress("UNCHECKED_CAST")
      val callback = thirdArg<io.ably.lib.types.Callback<io.ably.lib.types.PublishResult>>()
      callback.onSuccess(io.ably.lib.types.PublishResult(null)) // null serials → RTO20c2 path
    }
    every { adapter.connectionManager } returns cm
    // siteCode is null (relaxed mock default) — triggers RTO20c1 graceful degradation path

    val defaultRealtimeObjects = DefaultRealtimeObjects("testChannel", adapter).also { testInstances.add(it) }
    defaultRealtimeObjects.state = ObjectsState.Synced

    val counter = DefaultLiveCounter.zeroValue("counter:test@1", defaultRealtimeObjects)
    defaultRealtimeObjects.objectsPool.set("counter:test@1", counter)

    val msg = ObjectMessage(
      operation = ObjectOperation(
        action = ObjectOperationAction.CounterInc,
        objectId = "counter:test@1",
        counterOp = ObjectsCounterOp(amount = 5.0)
      )
    )

    // Should not throw even when siteCode is null (RTO20c1 graceful degradation)
    defaultRealtimeObjects.publishAndApply(arrayOf(msg))

    assertEquals(0.0, counter.data.get(), "no local apply should happen when siteCode is null")
    assertTrue(defaultRealtimeObjects.appliedOnAckSerials.isEmpty(),
      "appliedOnAckSerials should be empty when siteCode is null")
  }

  @Test
  fun `(issue 7b) publishAndApply logs error and returns without apply when serials length mismatches`() = runTest {
    val adapter = getMockObjectsAdapter()
    // Create a ConnectionManager mock that returns a PublishResult with wrong-length serials
    val cm = mockk<io.ably.lib.transport.ConnectionManager>(relaxed = true)
    cm.maxMessageSize = 65536 // direct field assignment bypasses mock interception issues
    every { cm.isActive } returns true
    cm.siteCode = "site1" // direct field assignment (siteCode is a Java public field)
    every { cm.send(any(), any(), any()) } answers {
      @Suppress("UNCHECKED_CAST")
      val callback = thirdArg<io.ably.lib.types.Callback<io.ably.lib.types.PublishResult>>()
      callback.onSuccess(io.ably.lib.types.PublishResult(arrayOfNulls(0))) // wrong length (0 instead of 1)
    }
    every { adapter.connectionManager } returns cm

    val defaultRealtimeObjects = DefaultRealtimeObjects("testChannel", adapter).also { testInstances.add(it) }
    defaultRealtimeObjects.state = ObjectsState.Synced

    val counter = DefaultLiveCounter.zeroValue("counter:test@1", defaultRealtimeObjects)
    defaultRealtimeObjects.objectsPool.set("counter:test@1", counter)

    val msg = ObjectMessage(
      operation = ObjectOperation(
        action = ObjectOperationAction.CounterInc,
        objectId = "counter:test@1",
        counterOp = ObjectsCounterOp(amount = 5.0)
      )
    )

    // Should not throw even when serials length mismatches (RTO20c2 graceful degradation)
    defaultRealtimeObjects.publishAndApply(arrayOf(msg))

    assertEquals(0.0, counter.data.get(), "no local apply should happen when serials length mismatches")
    assertTrue(defaultRealtimeObjects.appliedOnAckSerials.isEmpty(),
      "appliedOnAckSerials should be empty when serials length mismatches")
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
