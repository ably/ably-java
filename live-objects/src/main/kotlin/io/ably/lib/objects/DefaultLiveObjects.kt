package io.ably.lib.objects

import io.ably.lib.objects.state.ObjectsStateEvent
import io.ably.lib.objects.state.ObjectsStateListener
import io.ably.lib.objects.state.ObjectsStateSubscription
import io.ably.lib.realtime.ChannelState
import io.ably.lib.types.Callback
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.MutableSharedFlow

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
   * Coroutine scope for handling callbacks asynchronously.
   */
  private val callbackScope = CoroutineScope(Dispatchers.Default + CoroutineName("LiveObjectsCallback-$channelName"))

  /**
   * Event bus for handling incoming object messages sequentially.
   */
  private val objectsEventBus = MutableSharedFlow<ProtocolMessage>(extraBufferCapacity = UNLIMITED)
  private val incomingObjectsHandler: Job

  init {
    incomingObjectsHandler = initializeHandlerForIncomingObjectMessages()
  }

  override fun getRoot(): LiveMap {
    return runBlocking { getRootAsync() }
  }

  override fun getRootAsync(callback: Callback<LiveMap>) {
    callbackScope.launchWithCallback(callback) { getRootAsync() }
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

  override fun on(event: ObjectsStateEvent, listener: ObjectsStateListener): ObjectsStateSubscription {
    objectsManager.publicObjectStateEmitter.on(event, listener)
    return ObjectsStateSubscription {
      objectsManager.publicObjectStateEmitter.off(event, listener)
    }
  }

  override fun off(listener: ObjectsStateListener) {
    objectsManager.publicObjectStateEmitter.off(listener)
  }

  override fun offAll() {
    objectsManager.publicObjectStateEmitter.off()
  }

  private suspend fun getRootAsync(): LiveMap {
    return sequentialScope.async {
      adapter.throwIfInvalidAccessApiConfiguration(channelName)
      objectsManager.ensureSynced()
      objectsPool.get(ROOT_OBJECT_ID) as LiveMap
    }.await()
  }

  /**
   * Handles a ProtocolMessage containing proto action as `object` or `object_sync`.
   * @spec RTL1 - Processes incoming object messages and object sync messages
   */
  internal fun handle(protocolMessage: ProtocolMessage) {
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

  internal fun handleStateChange(state: ChannelState, hasObjects: Boolean) {
    sequentialScope.launch {
      when (state) {
        ChannelState.attached -> {
          Log.v(tag, "Objects.onAttached() channel=$channelName, hasObjects=$hasObjects")

          // RTO4a
          val fromInitializedState = this@DefaultLiveObjects.state == ObjectsState.INITIALIZED
          if (hasObjects || fromInitializedState) {
            // should always start a new sync sequence if we're in the initialized state, no matter the HAS_OBJECTS flag value.
            // this guarantees we emit both "syncing" -> "synced" events in that order.
            objectsManager.startNewSync(null)
          }

          // RTO4b
          if (!hasObjects) {
            // if no HAS_OBJECTS flag received on attach, we can end sync sequence immediately and treat it as no objects on a channel.
            // reset the objects pool to its initial state, and emit update events so subscribers to root object get notified about changes.
            objectsPool.resetToInitialPool(true) // RTO4b1, RTO4b2
            objectsManager.clearSyncObjectsDataPool() // RTO4b3
            objectsManager.clearBufferedObjectOperations() // RTO4b5
            // defer the state change event until the next tick if we started a new sequence just now due to being in initialized state.
            // this allows any event listeners to process the start of the new sequence event that was emitted earlier during this event loop.
            objectsManager.endSync(fromInitializedState) // RTO4b4
          }
        }

        else -> {
          // No action needed for other states
        }
      }
    }
  }

  // Dispose of any resources associated with this LiveObjects instance
  fun dispose() {
    incomingObjectsHandler.cancel() // objectsEventBus automatically garbage collected when collector is cancelled
    objectsPool.dispose()
    objectsManager.dispose()
  }
}
