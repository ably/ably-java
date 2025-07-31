package io.ably.lib.objects

import io.ably.lib.objects.serialization.gson
import io.ably.lib.objects.state.ObjectsStateChange
import io.ably.lib.objects.state.ObjectsStateEvent
import io.ably.lib.objects.type.ObjectType
import io.ably.lib.objects.type.counter.LiveCounter
import io.ably.lib.objects.type.livecounter.DefaultLiveCounter
import io.ably.lib.objects.type.livemap.DefaultLiveMap
import io.ably.lib.objects.type.map.LiveMap
import io.ably.lib.objects.type.map.LiveMapValue
import io.ably.lib.realtime.ChannelState
import io.ably.lib.types.AblyException
import io.ably.lib.types.Callback
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.CancellationException

/**
 * Default implementation of LiveObjects interface.
 * Provides the core functionality for managing live objects on a channel.
 */
internal class DefaultLiveObjects(internal val channelName: String, internal val adapter: LiveObjectsAdapter): LiveObjects {
  private val tag = "DefaultLiveObjects"
  /**
   * @spec RTO3 - Objects pool storing all live objects by object ID
   */
  internal val objectsPool = ObjectsPool(this)

  internal var state = ObjectsState.Initialized

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
   * Processes messages inside [incomingObjectsHandler] job created using [sequentialScope].
   */
  private val objectsEventBus = MutableSharedFlow<ProtocolMessage>(extraBufferCapacity = UNLIMITED)
  private val incomingObjectsHandler: Job

  /**
   * Provides a channel-specific scope for safely executing asynchronous operations with callbacks.
   */
  internal val asyncScope = ObjectsAsyncScope(channelName)

  init {
    incomingObjectsHandler = initializeHandlerForIncomingObjectMessages()
  }

  override fun getRoot(): LiveMap = runBlocking { getRootAsync() }

  override fun createMap(): LiveMap = createMap(mutableMapOf())

  override fun createMap(entries: MutableMap<String, LiveMapValue>): LiveMap = runBlocking { createMapAsync(entries) }

  override fun createCounter(): LiveCounter = createCounter(0)

  override fun createCounter(initialValue: Number): LiveCounter = runBlocking { createCounterAsync(initialValue) }

  override fun getRootAsync(callback: Callback<LiveMap>) {
    asyncScope.launchWithCallback(callback) { getRootAsync() }
  }

  override fun createMapAsync(callback: Callback<LiveMap>) = createMapAsync(mutableMapOf(), callback)

  override fun createMapAsync(entries: MutableMap<String, LiveMapValue>, callback: Callback<LiveMap>) {
    asyncScope.launchWithCallback(callback) { createMapAsync(entries) }
  }

  override fun createCounterAsync(callback: Callback<LiveCounter>) = createCounterAsync(0, callback)

  override fun createCounterAsync(initialValue: Number, callback: Callback<LiveCounter>) {
    asyncScope.launchWithCallback(callback) { createCounterAsync(initialValue) }
  }

  override fun on(event: ObjectsStateEvent, listener: ObjectsStateChange.Listener): ObjectsSubscription =
    objectsManager.on(event, listener)

  override fun off(listener: ObjectsStateChange.Listener) = objectsManager.off(listener)

  override fun offAll() = objectsManager.offAll()

  private suspend fun getRootAsync(): LiveMap = withContext(sequentialScope.coroutineContext) {
    adapter.throwIfInvalidAccessApiConfiguration(channelName)
    objectsManager.ensureSynced(state)
    objectsPool.get(ROOT_OBJECT_ID) as LiveMap
  }

  private suspend fun createMapAsync(entries: MutableMap<String, LiveMapValue>): LiveMap {
    adapter.throwIfInvalidWriteApiConfiguration(channelName)

    if (entries.keys.any { it.isEmpty() }) {
      throw objectError("Map keys should not be empty")
    }

    // Create initial value operation
    val initialMapValue = DefaultLiveMap.initialValue(entries)

    // Create initial value JSON string
    val initialValueJSONString = gson.toJson(initialMapValue)

    // Create object ID from initial value
    val (objectId, nonce) = getObjectIdStringWithNonce(ObjectType.Map, initialValueJSONString)

    // Create ObjectMessage with the operation
    val msg = ObjectMessage(
      operation = ObjectOperation(
        action = ObjectOperationAction.MapCreate,
        objectId = objectId,
        map = initialMapValue.map,
        nonce = nonce,
        initialValue = initialValueJSONString,
      )
    )

    // Publish the message
    publish(arrayOf(msg))

    // Check if object already exists in pool, otherwise create a zero-value object using the sequential scope
    return objectsPool.get(objectId) as? LiveMap ?: withContext(sequentialScope.coroutineContext) {
      objectsPool.createZeroValueObjectIfNotExists(objectId) as LiveMap
    }
  }

  private suspend fun createCounterAsync(initialValue: Number): LiveCounter {
    adapter.throwIfInvalidWriteApiConfiguration(channelName)

    // Validate input parameter
    if (initialValue.toDouble().isNaN() || initialValue.toDouble().isInfinite()) {
      throw objectError("Counter value should be a valid number")
    }

    val initialCounterValue = DefaultLiveCounter.initialValue(initialValue)
    // Create initial value operation
    val initialValueJSONString = gson.toJson(initialCounterValue)

    // Create object ID from initial value
    val (objectId, nonce) = getObjectIdStringWithNonce(ObjectType.Counter, initialValueJSONString)

    // Create ObjectMessage with the operation
    val msg = ObjectMessage(
      operation = ObjectOperation(
        action = ObjectOperationAction.CounterCreate,
        objectId = objectId,
        counter = initialCounterValue.counter,
        nonce = nonce,
        initialValue = initialValueJSONString
      )
    )

    // Publish the message
    publish(arrayOf(msg))

    // Check if object already exists in pool, otherwise create a zero-value object using the sequential scope
    return objectsPool.get(objectId) as? LiveCounter ?: withContext(sequentialScope.coroutineContext) {
      objectsPool.createZeroValueObjectIfNotExists(objectId) as LiveCounter
    }
  }

  private suspend fun getObjectIdStringWithNonce(objectType: ObjectType, initialValue: String): Pair<String, String> {
    val nonce = generateNonce()
    val msTimestamp = ServerTime.getCurrentTime(adapter) // RTO16 - Get server time for nonce generation
    return Pair(ObjectId.fromInitialValue(objectType, initialValue, nonce, msTimestamp).toString(), nonce)
  }

  /**
   * Spec: RTO15
   */
  internal suspend fun publish(objectMessages: Array<ObjectMessage>) {
    // RTO15b, RTL6c - Ensure that the channel is in a valid state for publishing
    adapter.throwIfUnpublishableState(channelName)
    adapter.ensureMessageSizeWithinLimit(objectMessages)
    // RTO15e - Must construct the ProtocolMessage as per RTO15e1, RTO15e2, RTO15e3
    val protocolMessage = ProtocolMessage(ProtocolMessage.Action.`object`, channelName)
    protocolMessage.state = objectMessages
    // RTO15f, RTO15g - Send the ProtocolMessage using the adapter and capture success/failure
    adapter.sendAsync(protocolMessage)
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

        try {
          when (protocolMessage.action) {
            ProtocolMessage.Action.`object` -> objectsManager.handleObjectMessages(objects)
            ProtocolMessage.Action.object_sync -> objectsManager.handleObjectSyncMessages(
              objects,
              protocolMessage.channelSerial
            )
            else -> Log.w(tag, "Ignoring protocol message with unhandled action: ${protocolMessage.action}")
          }
        } catch (exception: Exception) {
          // Skip current message if an error occurs, don't rethrow to avoid crashing the collector
          Log.e(tag, "Error handling objects message with protocolMsg id ${protocolMessage.id}", exception)
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
          val fromInitializedState = this@DefaultLiveObjects.state == ObjectsState.Initialized
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
        ChannelState.detached,
        ChannelState.failed -> {
          // do not emit data update events as the actual current state of Objects data is unknown when we're in these channel states
          objectsPool.clearObjectsData(false)
          objectsManager.clearSyncObjectsDataPool()
        }

        else -> {
          // No action needed for other states
        }
      }
    }
  }

  // Dispose of any resources associated with this LiveObjects instance
  fun dispose(cause: AblyException) {
    val disposeReason = CancellationException().apply { initCause(cause) }
    incomingObjectsHandler.cancel(disposeReason) // objectsEventBus automatically garbage collected when collector is cancelled
    objectsPool.dispose()
    objectsManager.dispose()
    // Don't cancel sequentialScope (needed in getRoot method), just cancel ongoing coroutines
    sequentialScope.coroutineContext.cancelChildren(disposeReason)
    asyncScope.cancel(disposeReason) // cancel all ongoing callbacks
  }
}
