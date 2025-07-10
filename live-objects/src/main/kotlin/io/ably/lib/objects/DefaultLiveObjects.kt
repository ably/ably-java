package io.ably.lib.objects

import io.ably.lib.types.Callback
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * @spec RTO2 - enum representing objects state
 */
internal enum class ObjectsState {
  INITIALIZED,
  SYNCING,
  SYNCED
}

/**
 * Default implementation of LiveObjects interface.
 * Provides the core functionality for managing live objects on a channel.
 */
internal class DefaultLiveObjects(private val channelName: String, internal val adapter: LiveObjectsAdapter): LiveObjects {
  private val tag = "DefaultLiveObjects"
  /**
   * @spec RTO3 - Objects pool storing all live objects by object ID
   */
  internal val objectsPool = ObjectsPool(adapter)

  internal var state = ObjectsState.INITIALIZED

  /**
   * @spec RTO4 - Used for handling object messages and object sync messages
   */
  private val objectsManager = ObjectsManager(this)

  /**
   *  Coroutine scope for running sequential operations on a single thread, used to avoid concurrency issues.
   */
  private val sequentialScope =
    CoroutineScope(Dispatchers.Default.limitedParallelism(1) + CoroutineName(channelName) + SupervisorJob())

  /**
   * Event bus for handling incoming object messages sequentially.
   */
  private val objectsEventBus = MutableSharedFlow<ProtocolMessage>(extraBufferCapacity = UNLIMITED)
  private val incomingObjectsHandler: Job

  init {
    incomingObjectsHandler = initializeHandlerForIncomingObjectMessages()
  }

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
   *  @spec RTL1 - Processes incoming object messages and object sync messages
   */
  fun handle(protocolMessage: ProtocolMessage) {
    // RTL15b - Set channel serial for OBJECT messages
    adapter.setChannelSerial(channelName, protocolMessage)

    if (protocolMessage.state == null || protocolMessage.state.isEmpty()) {
      Log.w(tag, "Received ProtocolMessage with null or empty objects, ignoring")
      return
    }

    objectsEventBus.tryEmit(protocolMessage)
  }

  /**
   * Initializes the handler for incoming object messages and object sync messages.
   * Processes the messages sequentially to ensure thread safety and correct order of operations.
   *
   * @spec OM2 - Populates missing fields from parent protocol message
   */
  private fun initializeHandlerForIncomingObjectMessages(): Job {
     return sequentialScope.launch {
      objectsEventBus.collect { protocolMessage ->
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
          ProtocolMessage.Action.`object` -> objectsManager.handleObjectMessages(objects)
          ProtocolMessage.Action.object_sync -> objectsManager.handleObjectSyncMessages(
            objects,
            protocolMessage.channelSerial
          )

          else -> Log.w(tag, "Ignoring protocol message with unhandled action: ${protocolMessage.action}")
        }
      }
    }
  }

  /**
   * Changes the state and emits events.
   *
   * @spec RTO2 - Emits state change events for syncing and synced states
   */
  internal fun stateChange(newState: ObjectsState, deferEvent: Boolean) {
    if (state == newState) {
      return
    }

    state = newState
    Log.v(tag, "Objects state changed to: $newState")

    // TODO: Emit state change events
  }

  // Dispose of any resources associated with this LiveObjects instance
  fun dispose() {
    incomingObjectsHandler.cancel() // objectsEventBus automatically garbage collected when collector is cancelled
    objectsPool.dispose()
    objectsManager.dispose()
  }
}
