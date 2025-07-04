package io.ably.lib.objects

import io.ably.lib.types.Callback
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Default implementation of LiveObjects interface.
 * Provides the core functionality for managing live objects on a channel.
 *
 * @spec RTO1 - Provides access to the root LiveMap object
 * @spec RTO2 - Validates channel modes for operations
 * @spec RTO3 - Maintains an objects pool for all live objects on the channel
 * @spec RTO4 - Handles channel attachment and sync initiation
 * @spec RTO5 - Processes OBJECT_SYNC messages during sync sequences
 * @spec RTO6 - Creates zero-value objects when needed
 */
internal class DefaultLiveObjects(private val channelName: String, private val adapter: LiveObjectsAdapter): LiveObjects {
  private val tag = "DefaultLiveObjects"

  // State management similar to JavaScript implementation
  /**
   * @spec RTO2 - Objects state enum matching JavaScript ObjectsState
   */
  private enum class ObjectsState {
    INITIALIZED,
    SYNCING,
    SYNCED
  }

  private var state = ObjectsState.INITIALIZED
  /**
   * @spec RTO3 - Objects pool storing all live objects by object ID
   */
  private val objectsPool = ConcurrentHashMap<String, LiveObject>()
  /**
   * @spec RTO5 - Sync objects data pool for collecting sync messages
   */
  private val syncObjectsDataPool = ConcurrentHashMap<String, ObjectState>()
  private var currentSyncId: String? = null
  private var currentSyncCursor: String? = null
  /**
   * @spec RTO5 - Buffered object operations during sync
   */
  private val bufferedObjectOperations = mutableListOf<ObjectMessage>()

  /**
   * @spec RTO1 - Returns the root LiveMap object with proper validation and sync waiting
   */
  override fun getRoot(): LiveMap {
    TODO("Not yet implemented")
  }

  override fun createMap(liveMap: LiveMap): LiveMap {
    TODO("Not yet implemented")
  }

  override fun createMap(liveCounter: LiveCounter): LiveMap {
    TODO("Not yet implemented")
  }

  override fun createMap(map: MutableMap<String, Any>): LiveMap {
    TODO("Not yet implemented")
  }

  override fun getRootAsync(callback: Callback<LiveMap>) {
    TODO("Not yet implemented")
  }

  override fun createMapAsync(liveMap: LiveMap, callback: Callback<LiveMap>) {
    TODO("Not yet implemented")
  }

  override fun createMapAsync(liveCounter: LiveCounter, callback: Callback<LiveMap>) {
    TODO("Not yet implemented")
  }

  override fun createMapAsync(map: MutableMap<String, Any>, callback: Callback<LiveMap>) {
    TODO("Not yet implemented")
  }

  override fun createCounterAsync(initialValue: Long, callback: Callback<LiveCounter>) {
    TODO("Not yet implemented")
  }

  override fun createCounter(initialValue: Long): LiveCounter {
    TODO("Not yet implemented")
  }

  /**
   * Handles a ProtocolMessage containing proto action as `object` or `object_sync`.
   * This method implements the same logic as the JavaScript handleObjectMessages and handleObjectSyncMessages.
   *
   * @spec RTL1 - Processes incoming object messages and object sync messages
   * @spec RTL15b - Sets channel serial for OBJECT messages
   * @spec OM2 - Populates missing fields from parent protocol message
   */
  fun handle(protocolMessage: ProtocolMessage) {
    // RTL15b - Set channel serial for OBJECT messages
    if (protocolMessage.action == ProtocolMessage.Action.`object`) {
      setChannelSerial(protocolMessage.channelSerial)
    }

    if (protocolMessage.state == null || protocolMessage.state.isEmpty()) {
      Log.w(tag, "Received ProtocolMessage with null or empty object state, ignoring")
      return
    }

    // OM2 - Populate missing fields from parent
    val objects = protocolMessage.state.filterIsInstance<ObjectMessage>()
      .mapIndexed { index, objMsg ->
        objMsg.copy(
          connectionId = objMsg.connectionId ?: protocolMessage.connectionId, // OM2c
          timestamp = objMsg.timestamp ?: protocolMessage.timestamp, // OM2e
          id = objMsg.id ?: (protocolMessage.id + ':' + index) // OM2a
      )
    }

    when (protocolMessage.action) {
        ProtocolMessage.Action.`object` -> handleObjectMessages(objects)
        ProtocolMessage.Action.object_sync -> handleObjectSyncMessages(objects, protocolMessage.channelSerial)
        else -> Log.w(tag, "Ignoring protocol message with unhandled action: ${protocolMessage.action}")
    }
  }

  /**
   * Handles object messages (non-sync messages).
   * Similar to JavaScript handleObjectMessages method.
   *
   * @spec RTO5 - Buffers messages if not synced, applies immediately if synced
   */
  private fun handleObjectMessages(objectMessages: List<ObjectMessage>) {
    if (state != ObjectsState.SYNCED) {
      // Buffer messages if not synced yet
      Log.v(tag, "Buffering ${objectMessages.size} object messages, state: $state")
      bufferedObjectOperations.addAll(objectMessages)
      return
    }

    // Apply messages immediately if synced
    applyObjectMessages(objectMessages)
  }

  /**
   * Handles object sync messages.
   * Similar to JavaScript handleObjectSyncMessages method.
   *
   * @spec RTO5 - Parses sync channel serial and manages sync sequences
   */
  private fun handleObjectSyncMessages(objectMessages: List<ObjectMessage>, syncChannelSerial: String?) {
    val (syncId, syncCursor) = parseSyncChannelSerial(syncChannelSerial) // RTO5a
    val newSyncSequence = currentSyncId != syncId
    if (newSyncSequence) {
      // RTO5a2 - new sync sequence started
      startNewSync(syncId, syncCursor) // RTO5a2a
    }

    // RTO5a3 - continue current sync sequence
    applyObjectSyncMessages(objectMessages) // RTO5b

    // RTO5a4 - if this is the last (or only) message in a sequence of sync updates, end the sync
    if (syncCursor == null) {
      // defer the state change event until the next tick if this was a new sync sequence
      // to allow any event listeners to process the start of the new sequence event that was emitted earlier during this event loop.
      endSync(newSyncSequence)
    }
  }

  /**
   * Parses sync channel serial to extract syncId and syncCursor.
   * Similar to JavaScript _parseSyncChannelSerial method.
   *
   * @spec RTO5 - Sync channel serial parsing logic
   */
  private fun parseSyncChannelSerial(syncChannelSerial: String?): Pair<String?, String?> {
    if (syncChannelSerial.isNullOrEmpty()) {
      return Pair(null, null)
    }

    // RTO5a1 - syncChannelSerial is a two-part identifier: <sequence id>:<cursor value>
    val match = Regex("^([\\w-]+):(.*)$").find(syncChannelSerial)
    return if (match != null) {
      val syncId = match.groupValues[1]
      val syncCursor = match.groupValues[2]
      Pair(syncId, syncCursor)
    } else {
      Pair(null, null)
    }
  }

  /**
   * Starts a new sync sequence.
   * Similar to JavaScript _startNewSync method.
   *
   * @spec RTO5 - Sync sequence initialization
   */
  private fun startNewSync(syncId: String?, syncCursor: String?) {
    Log.v(tag, "Starting new sync sequence: syncId=$syncId, syncCursor=$syncCursor")

    // need to discard all buffered object operation messages on new sync start
    bufferedObjectOperations.clear()
    syncObjectsDataPool.clear()
    currentSyncId = syncId
    currentSyncCursor = syncCursor
    stateChange(ObjectsState.SYNCING, false)
  }

  /**
   * Ends the current sync sequence.
   * Similar to JavaScript _endSync method.
   *
   * @spec RTO5c - Applies sync data and buffered operations
   */
  private fun endSync(deferStateEvent: Boolean) {
    Log.v(tag, "Ending sync sequence")

    applySync()
    // should apply buffered object operations after we applied the sync.
    // can use regular object messages application logic
    applyObjectMessages(bufferedObjectOperations)

    bufferedObjectOperations.clear()
    syncObjectsDataPool.clear() // RTO5c4
    currentSyncId = null // RTO5c3
    currentSyncCursor = null // RTO5c3
    stateChange(ObjectsState.SYNCED, deferStateEvent)
  }

  /**
   * Applies sync data to objects pool.
   * Similar to JavaScript _applySync method.
   *
   * @spec RTO5c - Processes sync data and updates objects pool
   */
  private fun applySync() {
    if (syncObjectsDataPool.isEmpty()) {
      return
    }

    val receivedObjectIds = mutableSetOf<String>()
    val existingObjectUpdates = mutableListOf<Pair<LiveObject, Any>>()

    // RTO5c1
    for ((objectId, objectState) in syncObjectsDataPool) {
      receivedObjectIds.add(objectId)
      val existingObject = objectsPool[objectId]

      // RTO5c1a
      if (existingObject != null) {
        // Update existing object
        val update = existingObject.overrideWithObjectState(objectState) // RTO5c1a1
        existingObjectUpdates.add(Pair(existingObject, update))
      } else {
        // RTO5c1b
        // Create new object
        val newObject = createObjectFromState(objectState) // RTO5c1b1
        objectsPool[objectId] = newObject
      }
    }

    // RTO5c2 - need to remove LiveObject instances from the ObjectsPool for which objectIds were not received during the sync sequence
    val objectIdsToRemove = objectsPool.keys.filter { !receivedObjectIds.contains(it) }
    objectIdsToRemove.forEach { objectsPool.remove(it) }

    // call subscription callbacks for all updated existing objects
    existingObjectUpdates.forEach { (obj, update) ->
      obj.notifyUpdated(update)
    }
  }

  /**
   * Applies object messages to objects.
   * Similar to JavaScript _applyObjectMessages method.
   *
   * @spec RTO6 - Creates zero-value objects if they don't exist
   */
  private fun applyObjectMessages(objectMessages: List<ObjectMessage>) {
    for (objectMessage in objectMessages) {
      if (objectMessage.operation == null) {
        Log.w(tag, "Object message received without operation field, skipping message: ${objectMessage.id}")
        continue
      }

      val objectOperation: ObjectOperation = objectMessage.operation
      // RTO6a - get or create the zero value object in the pool
      val obj = objectsPool.getOrPut(objectOperation.objectId) {
        createZeroValueObject(objectOperation.objectId)
      }
      obj.applyOperation(objectOperation, objectMessage)
    }
  }

  /**
   * Applies sync messages to sync data pool.
   * Similar to JavaScript applyObjectSyncMessages method.
   *
   * @spec RTO5b - Collects object states during sync sequence
   */
  private fun applyObjectSyncMessages(objectMessages: List<ObjectMessage>) {
    for (objectMessage in objectMessages) {
      if (objectMessage.objectState == null) {
        Log.w(tag, "Object message received during OBJECT_SYNC without object field, skipping message: ${objectMessage.id}")
        continue
      }

      val objectState: ObjectState = objectMessage.objectState
      syncObjectsDataPool[objectState.objectId] = objectState
    }
  }

  /**
   * Creates a zero-value object.
   *
   * @spec RTO6 - Creates zero-value objects based on object type
   */
  private fun createZeroValueObject(objectId: String): LiveObject {
    val objId = ObjectId.fromString(objectId) // RTO6b
    val zeroValueObject = when (objId.type) {
      ObjectType.Map -> LiveMap.zeroValue(objectId, adapter) // RTO6b2
      ObjectType.Counter -> LiveCounter.zeroValue(objectId, adapter) // RTO6b3
    }
    return zeroValueObject
  }

  /**
   * Creates an object from object state.
   *
   * @spec RTO5c1b - Creates objects from object state based on type
   * TODO - Need to update the implementation
   */
  private fun createObjectFromState(objectState: ObjectState): LiveObject {
    return when {
      objectState.counter != null -> LiveCounter(objectState.objectId, adapter) // RTO5c1b1a
      objectState.map != null -> LiveMap(objectState.objectId, adapter) // RTO5c1b1b
      else -> throw serverError("Object state must contain either counter or map data") // RTO5c1b1c
    }
  }

  /**
   * Changes the state and emits events.
   * Similar to JavaScript _stateChange method.
   *
   * @spec RTO2 - Emits state change events for syncing and synced states
   */
  private fun stateChange(newState: ObjectsState, deferEvent: Boolean) {
    if (state == newState) {
      return
    }

    state = newState
    Log.v(tag, "Objects state changed to: $newState")

    // TODO: Emit state change events
  }

  /**
   * @spec RTO2 - Validates channel modes for operations
   */
  private fun throwIfMissingChannelMode(expectedMode: String) {
    // TODO: Implement channel mode validation
    // RTO2a - channel.modes is only populated on channel attachment, so use it only if it is set
    // RTO2b - otherwise as a best effort use user provided channel options
  }

  private fun setChannelSerial(channelSerial: String?) {
    if (channelSerial.isNullOrEmpty()) {
      Log.w(tag, "setChannelSerial called with null or empty value, ignoring")
      return
    }
    Log.v(tag, "Setting channel serial for channelName: $channelName, value: $channelSerial")
    adapter.setChannelSerial(channelName, channelSerial)
  }

  fun dispose() {
    // Dispose of any resources associated with this LiveObjects instance
    // For example, close any open connections or clean up references
    objectsPool.clear()
    syncObjectsDataPool.clear()
    bufferedObjectOperations.clear()
  }
}
