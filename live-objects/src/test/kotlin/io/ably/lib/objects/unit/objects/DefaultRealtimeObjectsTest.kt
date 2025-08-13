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
import io.ably.lib.objects.unit.getDefaultRealtimeObjectsWithMockedDeps
import io.ably.lib.objects.unit.size
import io.ably.lib.realtime.ChannelState
import io.ably.lib.types.ProtocolMessage
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import io.mockk.every

class DefaultRealtimeObjectsTest {

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
      defaultRealtimeObjects.ObjectsManager.endSync(any<Boolean>())
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
      defaultRealtimeObjects.ObjectsManager.endSync(any<Boolean>())
    }

    assertEquals(0, defaultRealtimeObjects.ObjectsManager.SyncObjectsDataPool.size) // RTO4b3
    assertEquals(0, defaultRealtimeObjects.ObjectsManager.BufferedObjectOperations.size) // RTO4b5
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
      defaultRealtimeObjects.ObjectsManager.endSync(true) // deferStateEvent = true
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
