package io.ably.lib.objects

import io.ably.lib.objects.type.BaseLiveObject
import io.ably.lib.objects.type.livecounter.DefaultLiveCounter
import io.ably.lib.objects.type.livemap.DefaultLiveMap
import io.ably.lib.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * @spec RTO5 - Processes OBJECT and OBJECT_SYNC messages during sync sequences
 * @spec RTO6 - Creates zero-value objects when needed
 */
internal class ObjectsManager(private val liveObjects: DefaultLiveObjects) {
  private val tag = "LiveObjectsManager"
  /**
   * @spec RTO5 - Sync objects data pool for collecting sync messages
   */
  private val syncObjectsDataPool = ConcurrentHashMap<String, ObjectState>()
  private var currentSyncId: String? = null
  /**
   * @spec RTO7 - Buffered object operations during sync
   */
  private val bufferedObjectOperations = mutableListOf<ObjectMessage>() // RTO7a

  /**
   * Handles object messages (non-sync messages).
   *
   * @spec RTO8 - Buffers messages if not synced, applies immediately if synced
   */
  internal fun handleObjectMessages(objectMessages: List<ObjectMessage>) {
    if (liveObjects.state != ObjectsState.SYNCED) {
      // RTO7 - The client receives object messages in realtime over the channel concurrently with the sync sequence.
      // Some of the incoming object messages may have already been applied to the objects described in
      // the sync sequence, but others may not; therefore we must buffer these messages so that we can apply
      // them to the objects once the sync is complete.
      Log.v(tag, "Buffering ${objectMessages.size} object messages, state: $liveObjects.state")
      bufferedObjectOperations.addAll(objectMessages) // RTO8a
      return
    }

    // Apply messages immediately if synced
    applyObjectMessages(objectMessages) // RTO8b
  }

  /**
   * Handles object sync messages.
   *
   * @spec RTO5 - Parses sync channel serial and manages sync sequences
   */
  internal fun handleObjectSyncMessages(objectMessages: List<ObjectMessage>, syncChannelSerial: String?) {
    val syncTracker = ObjectsSyncTracker(syncChannelSerial)
    if (syncTracker.hasSyncStarted(currentSyncId)) {
      // RTO5a2 - new sync sequence started
      startNewSync(syncTracker.syncId)
    }

    // RTO5a3 - continue current sync sequence
    applyObjectSyncMessages(objectMessages) // RTO5b

    // RTO5a4 - if this is the last (or only) message in a sequence of sync updates, end the sync
    if (syncTracker.hasSyncEnded()) {
      // defer the state change event until the next tick if this was a new sync sequence
      // to allow any event listeners to process the start of the new sequence event that was emitted earlier during this event loop.
      endSync(true)
    }
  }

  /**
   * Starts a new sync sequence.
   *
   * @spec RTO5 - Sync sequence initialization
   */
  private fun startNewSync(syncId: String?) {
    Log.v(tag, "Starting new sync sequence: syncId=$syncId")

    // need to discard all buffered object operation messages on new sync start
    bufferedObjectOperations.clear() // RTO5a2b
    syncObjectsDataPool.clear() // RTO5a2a
    currentSyncId = syncId
    liveObjects.stateChange(ObjectsState.SYNCING, false)
  }

  /**
   * Ends the current sync sequence.
   *
   * @spec RTO5c - Applies sync data and buffered operations
   */
  private fun endSync(deferStateEvent: Boolean) {
    Log.v(tag, "Ending sync sequence")
    applySync()
    // should apply buffered object operations after we applied the sync.
    // can use regular non-sync object.operation logic
    applyObjectMessages(bufferedObjectOperations) // RTO5c6

    bufferedObjectOperations.clear() // RTO5c5
    syncObjectsDataPool.clear() // RTO5c4
    currentSyncId = null // RTO5c3
    liveObjects.stateChange(ObjectsState.SYNCED, deferStateEvent)
  }

  /**
   * Applies sync data to objects pool.
   *
   * @spec RTO5c - Processes sync data and updates objects pool
   */
  private fun applySync() {
    if (syncObjectsDataPool.isEmpty()) {
      return
    }

    val receivedObjectIds = mutableSetOf<String>()
    val existingObjectUpdates = mutableListOf<Pair<BaseLiveObject, Any>>()

    // RTO5c1
    for ((objectId, objectState) in syncObjectsDataPool) {
      receivedObjectIds.add(objectId)
      val existingObject = liveObjects.objectsPool.get(objectId)

      // RTO5c1a
      if (existingObject != null) {
        // Update existing object
        val update = existingObject.applyObjectSync(objectState) // RTO5c1a1
        existingObjectUpdates.add(Pair(existingObject, update))
      } else { // RTO5c1b
        // RTO5c1b1, RTO5c1b1a, RTO5c1b1b - Create new object and add it to the pool
        val newObject = createObjectFromState(objectState)
        newObject.applyObjectSync(objectState)
        liveObjects.objectsPool.set(objectId, newObject)
      }
    }

    // RTO5c2 - need to remove LiveObject instances from the ObjectsPool for which objectIds were not received during the sync sequence
    liveObjects.objectsPool.deleteExtraObjectIds(receivedObjectIds)

    // call subscription callbacks for all updated existing objects
    existingObjectUpdates.forEach { (obj, update) ->
      obj.notifyUpdated(update)
    }
  }

  /**
   * Applies object messages to objects.
   *
   * @spec RTO9 - Creates zero-value objects if they don't exist
   */
  private fun applyObjectMessages(objectMessages: List<ObjectMessage>) {
    // RTO9a
    for (objectMessage in objectMessages) {
      if (objectMessage.operation == null) {
        // RTO9a1
        Log.w(tag, "Object message received without operation field, skipping message: ${objectMessage.id}")
        continue
      }

      val objectOperation: ObjectOperation = objectMessage.operation // RTO9a2
      // RTO9a2a - we can receive an op for an object id we don't have yet in the pool. instead of buffering such operations,
      // we can create a zero-value object for the provided object id and apply the operation to that zero-value object.
      // this also means that all objects are capable of applying the corresponding *_CREATE ops on themselves,
      // since they need to be able to eventually initialize themselves from that *_CREATE op.
      // so to simplify operations handling, we always try to create a zero-value object in the pool first,
      // and then we can always apply the operation on the existing object in the pool.
      val obj = liveObjects.objectsPool.createZeroValueObjectIfNotExists(objectOperation.objectId) // RTO9a2a1
      obj.applyObject(objectMessage) // RTO9a2a2, RTO9a2a3
    }
  }

  /**
   * Applies sync messages to sync data pool.
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
      if (objectState.counter != null || objectState.map != null) {
        syncObjectsDataPool[objectState.objectId] = objectState
      } else {
        // RTO5c1b1c - object state must contain either counter or map data
        Log.w(tag, "Object state received without counter or map data, skipping message: ${objectMessage.id}")
      }
    }
  }

  /**
   * Creates an object from object state.
   *
   * @spec RTO5c1b - Creates objects from object state based on type
   */
  private fun createObjectFromState(objectState: ObjectState): BaseLiveObject {
    return when {
      objectState.counter != null -> DefaultLiveCounter.zeroValue(objectState.objectId, liveObjects.adapter) // RTO5c1b1a
      objectState.map != null -> DefaultLiveMap.zeroValue(objectState.objectId, liveObjects.adapter, liveObjects.objectsPool) // RTO5c1b1b
      else -> throw clientError("Object state must contain either counter or map data") // RTO5c1b1c
    }
  }

  internal fun dispose() {
    syncObjectsDataPool.clear()
    bufferedObjectOperations.clear()
  }
}
