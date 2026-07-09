package io.ably.lib.liveobjects

import io.ably.lib.liveobjects.adapter.AblyClientAdapter
import io.ably.lib.liveobjects.message.WireCounterCreateWithObjectId
import io.ably.lib.liveobjects.message.WireMapCreateWithObjectId
import io.ably.lib.liveobjects.message.WireObjectMessage
import io.ably.lib.liveobjects.message.WireObjectOperation
import io.ably.lib.liveobjects.message.WireObjectOperationAction
import io.ably.lib.liveobjects.path.types.LiveMapPathObject
import io.ably.lib.liveobjects.serialization.gson
import io.ably.lib.liveobjects.state.ObjectStateChange
import io.ably.lib.liveobjects.state.ObjectStateEvent
import io.ably.lib.liveobjects.value.LiveCounter
import io.ably.lib.liveobjects.value.LiveMap
import io.ably.lib.liveobjects.value.LiveMapValue
import io.ably.lib.liveobjects.value.ObjectType
import io.ably.lib.liveobjects.value.livecounter.InternalLiveCounter
import io.ably.lib.liveobjects.value.livemap.InternalLiveMap
import io.ably.lib.realtime.ChannelState
import io.ably.lib.types.AblyException
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.types.PublishResult
import io.ably.lib.util.Clock
import io.ably.lib.util.Log
import io.ably.lib.util.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture

/**
 * Default implementation of [RealtimeObject], the entry point to the strongly-typed,
 * path-based LiveObjects API for a single channel.
 *
 * This is currently a skeleton: the path-based read and subscribe operations are not yet
 * implemented. The method bodies will be filled in as the path-based API is built out.
 *
 * Spec: RTO23
 */
internal class DefaultRealtimeObject(
  internal val channelName: String,
  internal val adapter: AblyClientAdapter,
) : RealtimeObject {

  private val tag = "DefaultRealtimeObjects"
  /**
   * @spec RTO3 - Objects pool storing all objects by object ID
   */
  internal val objectsPool = ObjectsPool(this)

  internal var state = ObjectsState.Initialized

  /**
   * Set of serials for operations applied locally upon ACK, awaiting deduplication of the server echo.
   * @spec RTO7b, RTO7b1
   */
  internal val appliedOnAckSerials = mutableSetOf<String>()

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

  init {
    incomingObjectsHandler = initializeHandlerForIncomingObjectMessages()
  }

  override fun get(): CompletableFuture<LiveMapPathObject> {
    throwIfInvalidAccessApiConfiguration()
    TODO("Not yet implemented, this should call getRootAsync")
  }

  override fun on(event: ObjectStateEvent, listener: ObjectStateChange.Listener): Subscription {
    throwIfInvalidAccessApiConfiguration()
    return objectsManager.on(event, listener)
  }

  override fun off(listener: ObjectStateChange.Listener) {
    throwIfInvalidAccessApiConfiguration()
    objectsManager.off(listener)
  }

  override fun offAll() {
    throwIfInvalidAccessApiConfiguration()
    objectsManager.offAll()
  }

  private suspend fun getRootAsync(): LiveMap = withContext(sequentialScope.coroutineContext) {
    adapter.ensureAttached(channelName)
    objectsManager.ensureSynced(state)
    objectsPool.get(ROOT_OBJECT_ID) as LiveMap
  }

  private suspend fun createMapAsync(entries: MutableMap<String, LiveMapValue>): LiveMap {
    throwIfInvalidWriteApiConfiguration() // RTO26

    if (entries.keys.any { it.isEmpty() }) { // RTLMV4b
      throw invalidInputError("Map keys should not be empty")
    }

    // RTLMV4e - Create initial value operation
    val initialMapValue = InternalLiveMap.initialValue(entries)

    // RTLMV4f - Create initial value JSON string
    val initialValueJSONString = gson.toJson(initialMapValue)

    // RTO14 - Create object ID from initial value
    val (objectId, nonce) = getObjectIdStringWithNonce(ObjectType.Map, initialValueJSONString)

    // Create ObjectMessage with the operation
    val msg = WireObjectMessage(
      operation = WireObjectOperation(
        action = WireObjectOperationAction.MapCreate,
        objectId = objectId,
        mapCreateWithObjectId = WireMapCreateWithObjectId(
          nonce = nonce,
          initialValue = initialValueJSONString,
          derivedFrom = initialMapValue,
        ),
      )
    )

    // RTLMV3 - publish and apply locally on ACK
    publishAndApply(arrayOf(msg))

    // RTLMV3 - Return existing object if found after apply
    return objectsPool.get(objectId) as? LiveMap
      ?: throw serverError("createMap: MAP_CREATE was not applied as expected; objectId=$objectId") // RTLMV3
  }

  private suspend fun createCounterAsync(initialValue: Number): LiveCounter {
    throwIfInvalidWriteApiConfiguration() // RTO26

    // Validate input parameter
    if (initialValue.toDouble().isNaN() || initialValue.toDouble().isInfinite()) {
      throw invalidInputError("Counter value should be a valid number")
    }

    // RTLCV4b
    val initialCounterValue = InternalLiveCounter.initialValue(initialValue)
    // RTLCV4c - Create initial value operation
    val initialValueJSONString = gson.toJson(initialCounterValue)

    // RTO14 - Create object ID from initial value
    val (objectId, nonce) = getObjectIdStringWithNonce(ObjectType.Counter, initialValueJSONString)

    // Create ObjectMessage with the operation
    val msg = WireObjectMessage(
      operation = WireObjectOperation(
        action = WireObjectOperationAction.CounterCreate,
        objectId = objectId,
        counterCreateWithObjectId = WireCounterCreateWithObjectId(
          nonce = nonce,
          initialValue = initialValueJSONString,
          derivedFrom = initialCounterValue,
        ),
      )
    )

    // RTLCV3 - publish and apply locally on ACK
    publishAndApply(arrayOf(msg))

    // RTLCV3 - Return existing object if found after apply
    return objectsPool.get(objectId) as? LiveCounter
      ?: throw serverError("createCounter: COUNTER_CREATE was not applied as expected; objectId=$objectId") // RTLCV3
  }

  /**
   * Spec: RTO14
   */
  private suspend fun getObjectIdStringWithNonce(objectType: ObjectType, initialValue: String): Pair<String, String> {
    val nonce = generateNonce()
    val msTimestamp = ServerTime.getCurrentTime(adapter) // RTO16 - Get server time for nonce generation
    return Pair(ObjectId.fromInitialValue(objectType, initialValue, nonce, msTimestamp).toString(), nonce)
  }

  /**
   * Spec: RTO15
   */
  internal suspend fun publish(wireObjectMessages: Array<WireObjectMessage>): PublishResult {
    // RTO15b, RTL6c - Ensure that the channel is in a valid state for publishing
    adapter.throwIfUnpublishableState(channelName)
    adapter.ensureMessageSizeWithinLimit(wireObjectMessages)
    // RTO15e - Must construct the ProtocolMessage as per RTO15e1, RTO15e2, RTO15e3
    val protocolMessage = ProtocolMessage(ProtocolMessage.Action.`object`, channelName)
    protocolMessage.state = wireObjectMessages
    // RTO15f, RTO15g - Send the ProtocolMessage using the adapter and capture success/failure
    return adapter.sendAsync(protocolMessage) // RTO15h
  }

  /**
   * Publishes the given object messages and, upon receiving the ACK, immediately applies them
   * locally as synthetic inbound messages using the assigned serial and connection's siteCode.
   *
   * Spec: RTO20
   */
  internal suspend fun publishAndApply(wireObjectMessages: Array<WireObjectMessage>) {
    // RTO20b - publish, propagate failure
    val publishResult = publish(wireObjectMessages)

    // RTO20c - validate required info
    val siteCode = adapter.connectionManager.siteCode
    if (siteCode == null) {
      Log.e(tag, "RTO20c1: siteCode not available; operations will be applied when echoed")
      return
    }
    val serials = publishResult.serials
    if (serials == null || serials.size != wireObjectMessages.size) {
      Log.e(tag, "RTO20c2: PublishResult.serials unavailable or wrong length; operations will be applied when echoed")
      return
    }

    // RTO20d - create synthetic inbound ObjectMessages
    val syntheticMessages = mutableListOf<WireObjectMessage>()
    wireObjectMessages.forEachIndexed { i, msg ->
      val serial = serials[i]
      if (serial == null) {
        Log.d(tag, "RTO20d1: serial null at index $i (conflated), skipping")
        return@forEachIndexed
      }
      syntheticMessages.add(msg.copy(serial = serial, siteCode = siteCode)) // RTO20d2a, RTO20d2b, RTO20d3
    }
    if (syntheticMessages.isEmpty()) return

    // RTO20e, RTO20f - dispatch to sequential scope for ordering
    withContext(sequentialScope.coroutineContext) {
      objectsManager.applyAckResult(syntheticMessages) // suspends if SYNCING (RTO20e), applies on SYNCED (RTO20f)
    }
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
        val objects = protocolMessage.state.filterIsInstance<WireObjectMessage>()
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

          objectsManager.clearBufferedObjectOperations() // RTO4d - clear unconditionally on ATTACHED

          // RTO4a
          val fromInitializedState = this@DefaultRealtimeObject.state == ObjectsState.Initialized
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
            objectsManager.clearSyncObjectsPool() // RTO4b3
            // RTO4b5 removed — buffer already cleared by RTO4d above
            // defer the state change event until the next tick if we started a new sequence just now due to being in initialized state.
            // this allows any event listeners to process the start of the new sequence event that was emitted earlier during this event loop.
            objectsManager.endSync() // RTO4b4
          }
        }
        ChannelState.detached,
        ChannelState.suspended,
        ChannelState.failed -> {
          val errorReason = try {
            adapter.getChannel(channelName).reason
          } catch (e: Exception) {
            null
          }
          val error = ablyException(
            "publishAndApply could not be applied locally: channel entered $state whilst waiting for objects sync",
            ObjectErrorCode.PublishAndApplyFailedDueToChannelState,
            ObjectHttpStatusCode.BadRequest,
            cause = errorReason?.let { AblyException.fromErrorInfo(it) }
          )
          objectsManager.failBufferedAcks(error) // RTO20e1
          if (state != ChannelState.suspended) {
            // do not emit data update events as the actual current state of Objects data is unknown when we're in these channel states
            objectsPool.clearObjectsData(false)
            objectsManager.clearSyncObjectsPool()
          }
        }
        else -> {
          // No action needed for other states
        }
      }
    }
  }

  // Dispose of any resources associated with this RealtimeObjects instance
  fun dispose(cause: AblyException) {
    val disposeReason = CancellationException().apply { initCause(cause) }
    incomingObjectsHandler.cancel(disposeReason) // objectsEventBus automatically garbage collected when collector is cancelled
    objectsPool.dispose()
    objectsManager.dispose()
    // Don't cancel sequentialScope (needed in getRoot method), just cancel ongoing coroutines
    sequentialScope.coroutineContext.cancelChildren(disposeReason)
  }

  /** Validates the channel is configured for access (read/subscribe) operations. Spec: RTO25 */
  internal fun throwIfInvalidAccessApiConfiguration() = adapter.throwIfInvalidAccessApiConfiguration(channelName)

  /** Validates the channel is configured for write (mutation) operations. Spec: RTO26 */
  internal fun throwIfInvalidWriteApiConfiguration() = adapter.throwIfInvalidWriteApiConfiguration(channelName)

  /**
   * Provides the default Clock instance for the DefaultRealtimeObject.
   * This Clock is derived from the system clock, utilizing the client options
   * from the adapter configuration.
   */
  internal val clock: Clock get() = SystemClock.clockFrom(adapter.clientOptions)
}
