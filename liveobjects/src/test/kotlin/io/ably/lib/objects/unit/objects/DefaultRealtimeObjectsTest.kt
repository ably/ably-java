package io.ably.lib.objects.unit.objects

import io.ably.lib.objects.*
import io.ably.lib.objects.ObjectsCounterOp
import io.ably.lib.objects.ObjectData
import io.ably.lib.objects.ObjectMessage
import io.ably.lib.objects.ObjectOperation
import io.ably.lib.objects.ObjectOperationAction
import io.ably.lib.objects.ObjectState
import io.ably.lib.objects.ObjectsState
import io.ably.lib.objects.ROOT_OBJECT_ID
import io.ably.lib.objects.type.livecounter.DefaultLiveCounter
import io.ably.lib.objects.type.livemap.DefaultLiveMap
import io.ably.lib.objects.type.livemap.LiveMapEntry
import io.ably.lib.objects.unit.BufferedObjectOperations
import io.ably.lib.objects.unit.ObjectsManager
import io.ably.lib.objects.unit.SyncObjectsDataPool
import io.ably.lib.objects.unit.getMockObjectsAdapter
import io.ably.lib.objects.unit.getDefaultRealtimeObjectsWithMockedDeps
import io.ably.lib.objects.unit.getMockRealtimeChannel
import io.ably.lib.objects.unit.size
import io.ably.lib.realtime.ChannelState
import io.ably.lib.types.AblyException
import io.ably.lib.types.ErrorInfo
import io.ably.lib.types.ProtocolMessage
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DefaultRealtimeObjectsTest {

  private val testInstances = mutableListOf<DefaultRealtimeObjects>()

  @After
  fun tearDown() {
    val cleanupError = AblyException.fromErrorInfo(ErrorInfo("test cleanup", 500))
    testInstances.forEach { it.dispose(cleanupError) }
    testInstances.clear()
  }

  @Test
  fun `(RTO4, RTO4a) When channel ATTACHED with HAS_OBJECTS flag true should start sync sequence`() = runTest {
    val defaultRealtimeObjects = getDefaultRealtimeObjectsWithMockedDeps()

    // RTO4a - If the HAS_OBJECTS flag is 1, the server will shortly perform an OBJECT_SYNC sequence
    defaultRealtimeObjects.handleStateChange(ChannelState.attached, true)

    assertWaiter { defaultRealtimeObjects.state == ObjectsState.Syncing }

    // It is expected that the client will start a new sync sequence
    verify(exactly = 1) {
      defaultRealtimeObjects.ObjectsManager.startNewSync(null)
    }
    verify(exactly = 0) {
      defaultRealtimeObjects.ObjectsManager.endSync()
    }
  }

  @Test
  fun `(RTO4, RTO4b) When channel ATTACHED with HAS_OBJECTS flag false should complete sync immediately`() = runTest {
    val defaultRealtimeObjects = getDefaultRealtimeObjectsWithMockedDeps()

    // Set up some objects in objectPool that should be cleared
    val rootObject = defaultRealtimeObjects.objectsPool.get(ROOT_OBJECT_ID) as DefaultLiveMap
    rootObject.data["key1"] = LiveMapEntry(data = ObjectData("testValue1"))
    defaultRealtimeObjects.objectsPool.set("counter:testObject@1", DefaultLiveCounter.zeroValue("counter:testObject@1", defaultRealtimeObjects))
    assertEquals(2, defaultRealtimeObjects.objectsPool.size(), "RTO4b - Should have 2 objects before state change")

    // RTO4b - If the HAS_OBJECTS flag is 0, the sync sequence must be considered complete immediately
    defaultRealtimeObjects.handleStateChange(ChannelState.attached, false)

    // Verify expected outcomes
    assertWaiter { defaultRealtimeObjects.state == ObjectsState.Synced } // RTO4b4

    verify(exactly = 1) {
      defaultRealtimeObjects.objectsPool.resetToInitialPool(true)
    }
    verify(exactly = 1) {
      defaultRealtimeObjects.ObjectsManager.endSync()
    }

    assertEquals(0, defaultRealtimeObjects.ObjectsManager.SyncObjectsDataPool.size) // RTO4b3
    assertEquals(0, defaultRealtimeObjects.ObjectsManager.BufferedObjectOperations.size) // RTO4d
    assertEquals(1, defaultRealtimeObjects.objectsPool.size()) // RTO4b1 - Only root remains
    assertEquals(rootObject, defaultRealtimeObjects.objectsPool.get(ROOT_OBJECT_ID)) // points to previously created root object
    assertEquals(0, rootObject.data.size) // RTO4b2 - root object must be empty
  }

  @Test
  fun `(RTO4) When channel ATTACHED from INITIALIZED state should always start sync`() = runTest {
    val defaultRealtimeObjects = getDefaultRealtimeObjectsWithMockedDeps()

    // Ensure we're in INITIALIZED state
    defaultRealtimeObjects.state = ObjectsState.Initialized

    // RTO4a - Should start sync even with HAS_OBJECTS flag false when in INITIALIZED state
    defaultRealtimeObjects.handleStateChange(ChannelState.attached, false)

    verify(exactly = 1) {
      defaultRealtimeObjects.ObjectsManager.startNewSync(null)
    }
    verify(exactly = 1) {
      defaultRealtimeObjects.ObjectsManager.endSync()
    }
  }

  @Test
  fun `(RTO5, RTO7) Should delegate OBJECT and OBJECT_SYNC protocolMessage to ObjectManager`() = runTest {
    val defaultRealtimeObjects = getDefaultRealtimeObjectsWithMockedDeps(relaxed = true)

    // Create test ObjectMessage for OBJECT action
    val objectMessage = ObjectMessage(
      id = "testId",
      timestamp = 1234567890L,
      connectionId = "testConnectionId",
      operation = ObjectOperation(
        action = ObjectOperationAction.CounterInc,
        objectId = "counter:testObject@1",
        counterOp = ObjectsCounterOp(amount = 5.0)
      ),
      serial = "serial1",
      siteCode = "site1"
    )
    // Create ProtocolMessage with OBJECT action
    val objectProtocolMessage = ProtocolMessage(ProtocolMessage.Action.`object`).apply {
      id = "protocolId1"
      channel = "testChannel"
      channelSerial = "channelSerial1"
      timestamp = 1234567890L
      state = arrayOf(objectMessage)
    }
    // Test OBJECT action delegation
    defaultRealtimeObjects.handle(objectProtocolMessage)

    // Verify that handleObjectMessages was called with the correct parameters
    verify(exactly = 1) {
      defaultRealtimeObjects.ObjectsManager.handleObjectMessages(listOf(objectMessage))
    }

    // Create test ObjectMessage for OBJECT_SYNC action
    val objectSyncMessage = ObjectMessage(
      id = "testSyncId",
      timestamp = 1234567890L,
      connectionId = "testSyncConnectionId",
      objectState = ObjectState(
        objectId = "map:testObject@1",
        tombstone = false,
        siteTimeserials = mapOf("site1" to "syncSerial1"),
      ),
      serial = "syncSerial1",
      siteCode = "site1"
    )
    // Create ProtocolMessage with OBJECT_SYNC action
    val objectSyncProtocolMessage = ProtocolMessage(ProtocolMessage.Action.object_sync).apply {
      id = "protocolId2"
      channel = "testChannel"
      channelSerial = "syncChannelSerial1"
      timestamp = 1234567890L
      state = arrayOf(objectSyncMessage)
    }
    // Test OBJECT_SYNC action delegation
    defaultRealtimeObjects.handle(objectSyncProtocolMessage)
    // Verify that handleObjectSyncMessages was called with the correct parameters
    verify(exactly = 1) {
      defaultRealtimeObjects.ObjectsManager.handleObjectSyncMessages(listOf(objectSyncMessage), "syncChannelSerial1")
    }
  }

  @Test
  fun `(RTO20e1) handleStateChange(DETACHED) fails pending ACK waiters with error 92008`() = runTest {
    val defaultRealtimeObjects = getDefaultRealtimeObjectsWithMockedDeps()

    // Capture the error passed to failBufferedAcks via a CompletableDeferred
    val capturedError = CompletableDeferred<AblyException>()
    every { defaultRealtimeObjects.ObjectsManager.failBufferedAcks(any()) } answers {
      capturedError.complete(firstArg())
      callOriginal()
    }

    defaultRealtimeObjects.handleStateChange(ChannelState.detached, false)

    val error = capturedError.await()
    assertEquals(92008, error.errorInfo.code) // PublishAndApplyFailedDueToChannelState
  }

  @Test
  fun `(RTO20e1) handleStateChange(SUSPENDED) fails pending ACK waiters with error 92008`() = runTest {
    val defaultRealtimeObjects = getDefaultRealtimeObjectsWithMockedDeps()

    val capturedError = CompletableDeferred<AblyException>()
    every { defaultRealtimeObjects.ObjectsManager.failBufferedAcks(any()) } answers {
      capturedError.complete(firstArg())
      callOriginal()
    }

    defaultRealtimeObjects.handleStateChange(ChannelState.suspended, false)

    val error = capturedError.await()
    assertEquals(92008, error.errorInfo.code) // PublishAndApplyFailedDueToChannelState
  }

  @Test
  fun `(RTO20e1) handleStateChange(FAILED) fails pending ACK waiters and propagates channel reason`() = runTest {
    val defaultRealtimeObjects = getDefaultRealtimeObjectsWithMockedDeps()

    // Override the channel returned by the adapter to carry a non-null reason
    val channelReason = ErrorInfo("channel failed due to auth error", 40100, 401)
    val channelWithReason = getMockRealtimeChannel("testChannelName")
    channelWithReason.reason = channelReason
    every { defaultRealtimeObjects.adapter.getChannel(any()) } returns channelWithReason

    val capturedError = CompletableDeferred<AblyException>()
    every { defaultRealtimeObjects.ObjectsManager.failBufferedAcks(any()) } answers {
      capturedError.complete(firstArg())
      callOriginal()
    }

    defaultRealtimeObjects.handleStateChange(ChannelState.failed, false)

    val error = capturedError.await()
    assertEquals(92008, error.errorInfo.code)
    val causeException = error.cause as? AblyException
    assertNotNull(causeException, "Error cause must include the channel's reason")
    assertEquals(channelReason.code, causeException.errorInfo.code)
    assertEquals(channelReason.message, causeException.errorInfo.message)
  }

  @Test
  fun `(RTO4) handleStateChange(SUSPENDED) does NOT clear objects data`() = runTest {
    val defaultRealtimeObjects = getDefaultRealtimeObjectsWithMockedDeps()

    // Use the failBufferedAcks call as a signal that the state-change coroutine has run to completion
    val failCalled = CompletableDeferred<Unit>()
    every { defaultRealtimeObjects.ObjectsManager.failBufferedAcks(any()) } answers {
      callOriginal()
      failCalled.complete(Unit)
    }

    defaultRealtimeObjects.handleStateChange(ChannelState.suspended, false)

    // For SUSPENDED, the coroutine ends immediately after failBufferedAcks (no clear calls)
    failCalled.await()

    verify(exactly = 0) { defaultRealtimeObjects.objectsPool.clearObjectsData(any()) }
    verify(exactly = 0) { defaultRealtimeObjects.ObjectsManager.clearSyncObjectsDataPool() }
  }

  @Test
  fun `(RTO4) handleStateChange(DETACHED) clears objects data and sync pool`() = runTest {
    val defaultRealtimeObjects = getDefaultRealtimeObjectsWithMockedDeps()

    // Use clearSyncObjectsDataPool (the last operation in the coroutine) as the completion signal
    val syncPoolCleared = CompletableDeferred<Unit>()
    every { defaultRealtimeObjects.ObjectsManager.clearSyncObjectsDataPool() } answers {
      callOriginal()
      syncPoolCleared.complete(Unit)
    }

    defaultRealtimeObjects.handleStateChange(ChannelState.detached, false)

    syncPoolCleared.await()

    verify(exactly = 1) { defaultRealtimeObjects.objectsPool.clearObjectsData(false) }
    verify(exactly = 1) { defaultRealtimeObjects.ObjectsManager.clearSyncObjectsDataPool() }
  }

  @Test
  fun `(RTO4d) ATTACHED with hasObjects=true still clears bufferedObjectOperations`() = runTest {
    val defaultRealtimeObjects = getDefaultRealtimeObjectsWithMockedDeps()
    val manager = defaultRealtimeObjects.ObjectsManager

    // Pre-populate bufferedObjectOperations with a dummy operation
    @Suppress("UNCHECKED_CAST")
    (manager.BufferedObjectOperations as MutableList<ObjectMessage>).add(
      ObjectMessage(
        id = "pre-attach-op",
        operation = ObjectOperation(
          action = ObjectOperationAction.CounterInc,
          objectId = "counter:test@1",
          counterOp = ObjectsCounterOp(amount = 5.0)
        )
      )
    )
    assertEquals(1, manager.BufferedObjectOperations.size)

    // ATTACHED with hasObjects=true — RTO4d must clear the buffer before starting sync
    defaultRealtimeObjects.handleStateChange(ChannelState.attached, true)

    assertWaiter { defaultRealtimeObjects.state == ObjectsState.Syncing }
    assertEquals(0, manager.BufferedObjectOperations.size, "RTO4d - buffer must be cleared unconditionally on ATTACHED")
  }

  @Test
  fun `(RTO4d) Pre-ATTACHED buffered operations are discarded, not applied after sync`() = runTest {
    val defaultRealtimeObjects = DefaultRealtimeObjects("testChannel", getMockObjectsAdapter())
      .also { testInstances.add(it) }

    // Set up a counter in the pool
    val counter = DefaultLiveCounter.zeroValue("counter:test@1", defaultRealtimeObjects)
    defaultRealtimeObjects.objectsPool.set("counter:test@1", counter)

    val objectsManager = defaultRealtimeObjects.ObjectsManager

    // Pre-populate bufferedObjectOperations with a COUNTER_INC — simulates an op received before ATTACHED
    @Suppress("UNCHECKED_CAST")
    (objectsManager.BufferedObjectOperations as MutableList<ObjectMessage>).add(
      ObjectMessage(
        id = "pre-attach-inc",
        operation = ObjectOperation(
          action = ObjectOperationAction.CounterInc,
          objectId = "counter:test@1",
          counterOp = ObjectsCounterOp(amount = 5.0)
        )
      )
    )
    assertEquals(1, objectsManager.BufferedObjectOperations.size)

    // ATTACHED with hasObjects=true: RTO4d clears the buffer, then starts sync
    defaultRealtimeObjects.handleStateChange(ChannelState.attached, true)
    assertWaiter { defaultRealtimeObjects.state == ObjectsState.Syncing }
    assertEquals(0, objectsManager.BufferedObjectOperations.size, "buffer must be cleared by RTO4d")

    // Complete sync by calling handleObjectSyncMessages directly (sequentialScope is idle now)
    objectsManager.handleObjectSyncMessages(
      listOf(
        ObjectMessage(
          id = "sync-msg-1",
          objectState = ObjectState(
            objectId = "counter:test@1",
            tombstone = false,
            siteTimeserials = mapOf("site1" to "serial1"),
            counter = ObjectsCounter(count = 0.0)
          )
        )
      ),
      "sync-id:"  // empty cursor — ends sync (RTO5a4)
    )

    assertEquals(ObjectsState.Synced, defaultRealtimeObjects.state)

    // The pre-ATTACHED COUNTER_INC was discarded — counter should remain at 0
    assertEquals(0.0, counter.data.get(), "RTO4d - pre-ATTACHED buffered op must be discarded, not applied after sync")
  }

  @Test
  fun `(RTO5a2b removed) Buffered operations survive a server-initiated resync (new OBJECT_SYNC without ATTACHED)`() {
    val defaultRealtimeObjects = DefaultRealtimeObjects("testChannel", getMockObjectsAdapter())
      .also { testInstances.add(it) }

    // Set up a counter in the pool
    val counter = DefaultLiveCounter.zeroValue("counter:test@1", defaultRealtimeObjects)
    counter.data.set(5.0)
    defaultRealtimeObjects.objectsPool.set("counter:test@1", counter)

    val objectsManager = defaultRealtimeObjects.ObjectsManager

    // sync-1 is in progress
    objectsManager.startNewSync("sync-1")
    assertEquals(ObjectsState.Syncing, defaultRealtimeObjects.state)

    // Buffer a COUNTER_INC during sync-1
    objectsManager.handleObjectMessages(
      listOf(
        ObjectMessage(
          id = "channel-op-1",
          operation = ObjectOperation(
            action = ObjectOperationAction.CounterInc,
            objectId = "counter:test@1",
            counterOp = ObjectsCounterOp(amount = 3.0)
          ),
          serial = "serial-op-1",
          siteCode = "site1"
        )
      )
    )
    assertEquals(1, objectsManager.BufferedObjectOperations.size, "op buffered during sync-1")

    // Server sends a new OBJECT_SYNC with a different sync-id — triggers startNewSync("sync-2") internally
    // OLD behaviour (RTO5a2b): startNewSync would have cleared bufferedObjectOperations here
    // NEW behaviour (RTO5a2b removed): buffer is preserved
    objectsManager.handleObjectSyncMessages(
      listOf(
        ObjectMessage(
          id = "sync2-msg-1",
          objectState = ObjectState(
            objectId = "counter:test@1",
            tombstone = false,
            siteTimeserials = mapOf("site1" to "resync-serial"),
            counter = ObjectsCounter(count = 5.0)
          )
        )
      ),
      "sync-2:cursor-1"  // has cursor — not ending yet
    )

    assertEquals(1, objectsManager.BufferedObjectOperations.size,
      "startNewSync must NOT clear bufferedObjectOperations (RTO5a2b removed)")

    // Complete sync-2 (ending serial, no new messages)
    objectsManager.handleObjectSyncMessages(emptyList(), "sync-2:")

    assertEquals(ObjectsState.Synced, defaultRealtimeObjects.state)
    // sync-2 restored counter to 5.0; buffered COUNTER_INC (+3.0) applied after sync → 8.0
    assertEquals(8.0, counter.data.get(),
      "buffered COUNTER_INC from before server-initiated resync must be applied after sync completes")
  }

  @Test
  fun `(OM2) Populate objectMessage missing id, timestamp and connectionId from protocolMessage`() = runTest {
    val defaultRealtimeObjects = getDefaultRealtimeObjectsWithMockedDeps()

    // Capture the ObjectMessages that are passed to ObjectsManager methods
    var capturedObjectMessages: List<ObjectMessage>? = null
    var capturedObjectSyncMessages: List<ObjectMessage>? = null

    // Mock the ObjectsManager to capture the messages
    defaultRealtimeObjects.ObjectsManager.apply {
      every { handleObjectMessages(any<List<ObjectMessage>>()) } answers {
        capturedObjectMessages = firstArg()
      }
      every { handleObjectSyncMessages(any(), any()) } answers {
        capturedObjectSyncMessages = firstArg()
      }
    }

    // Create ObjectMessage with missing fields (id, timestamp, connectionId)
    val objectMessageWithMissingFields = ObjectMessage(
      id = null, // OM2a - missing id
      timestamp = null, // OM2e - missing timestamp
      connectionId = null, // OM2c - missing connectionId
    )

    // Create ProtocolMessage with OBJECT action and populated fields
    val objectProtocolMessage = ProtocolMessage(ProtocolMessage.Action.`object`).apply {
      id = "protocolId1"
      channel = "testChannel"
      channelSerial = "channelSerial1"
      connectionId = "protocolConnectionId"
      timestamp = 1234567890L
      state = arrayOf(objectMessageWithMissingFields)
    }

    // Test OBJECT action - should populate missing fields
    defaultRealtimeObjects.handle(objectProtocolMessage)

    // Verify that the captured ObjectMessage has populated fields
    assertWaiter { capturedObjectMessages != null }
    assertEquals(1, capturedObjectMessages!!.size)

    val populatedObjectMessage = capturedObjectMessages!![0]
    assertEquals("protocolId1:0", populatedObjectMessage.id) // OM2a - id should be protocolId:index
    assertEquals(1234567890L, populatedObjectMessage.timestamp) // OM2e - timestamp from protocol message
    assertEquals("protocolConnectionId", populatedObjectMessage.connectionId) // OM2c - connectionId from protocol message


    // Create ObjectMessage with missing fields for OBJECT_SYNC
    val objectSyncMessageWithMissingFields = ObjectMessage(
      id = null, // OM2a - missing id
      timestamp = null, // OM2e - missing timestamp
      connectionId = null, // OM2c - missing connectionId
    )

    // Create ProtocolMessage with OBJECT_SYNC action and populated fields
    val objectSyncProtocolMessage = ProtocolMessage(ProtocolMessage.Action.object_sync).apply {
      id = "protocolId2"
      channel = "testChannel"
      channelSerial = "syncChannelSerial1"
      connectionId = "protocolConnectionId"
      timestamp = 9876543210L
      state = arrayOf(objectSyncMessageWithMissingFields)
    }

    // Test OBJECT_SYNC action - should populate missing fields
    defaultRealtimeObjects.handle(objectSyncProtocolMessage)

    // Verify that the captured ObjectMessage has populated fields
    assertWaiter { capturedObjectSyncMessages != null }
    assertEquals(1, capturedObjectSyncMessages!!.size)

    val populatedObjectSyncMessage = capturedObjectSyncMessages!![0]
    assertEquals("protocolId2:0", populatedObjectSyncMessage.id) // OM2a - id should be protocolId:index
    assertEquals(9876543210L, populatedObjectSyncMessage.timestamp) // OM2e - timestamp from protocol message
    assertEquals("protocolConnectionId", populatedObjectSyncMessage.connectionId) // OM2c - connectionId from protocol message
  }
}
