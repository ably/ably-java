package io.ably.lib.objects

import io.ably.lib.objects.type.BaseLiveObject
import io.ably.lib.objects.type.ObjectType
import io.ably.lib.objects.type.livecounter.DefaultLiveCounter
import io.ably.lib.objects.type.livemap.DefaultLiveMap
import io.ably.lib.util.Log
import kotlinx.coroutines.*

/**
 * Constants for ObjectsPool configuration
 */
internal object ObjectsPoolDefaults {
  const val GC_INTERVAL_MS = 1000L * 60 * 5 // 5 minutes
  /**
   * Must be > 2 minutes to ensure we keep tombstones long enough to avoid the possibility of receiving an operation
   * with an earlier serial that would not have been applied if the tombstone still existed.
   *
   * Applies both for map entries tombstones and object tombstones.
   */
  const val GC_GRACE_PERIOD_MS = 1000L * 60 * 60 * 24 // 24 hours
}

/**
 * Root object ID constant
 */
internal const val ROOT_OBJECT_ID = "root"

/**
 * ObjectsPool manages a pool of live objects for a channel.
 *
 * @spec RTO3 - Maintains an objects pool for all live objects on the channel
 */
internal class ObjectsPool(
  private val adapter: LiveObjectsAdapter
) {
  private val tag = "ObjectsPool"

  /**
   * @spec RTO3a - Pool storing all live objects by object ID
   * Note: This is the same as objectsPool property in DefaultLiveObjects.kt
   */
  private val pool = mutableMapOf<String, BaseLiveObject>()

  /**
   * Coroutine scope for garbage collection
   */
  private val gcScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

  /**
   * Job for the garbage collection coroutine
   */
  private var gcJob: Job? = null

  init {
    // Initialize pool with root object
    createInitialPool()

    // Start garbage collection coroutine
    startGCJob()
  }

  /**
   * Gets a live object from the pool by object ID.
   */
  internal fun get(objectId: String): BaseLiveObject? {
    return pool[objectId]
  }

  /**
   * Deletes objects from the pool for which object ids are not found in the provided array of ids.
   */
  internal fun deleteExtraObjectIds(objectIds: MutableSet<String>) {
    pool.entries.removeIf { (key, _) -> key !in objectIds }
  }

  /**
   * Sets a live object in the pool.
   */
  internal fun set(objectId: String, liveObject: BaseLiveObject) {
    pool[objectId] = liveObject
  }

  /**
   * Removes all objects but root from the pool and clears the data for root.
   * Does not create a new root object, so the reference to the root object remains the same.
   */
  internal fun resetToInitialPool(emitUpdateEvents: Boolean) {
    // Clear the pool first and keep the root object
    val root = pool[ROOT_OBJECT_ID]
    if (root != null) {
      pool.clear()
      set(ROOT_OBJECT_ID, root)

      // Clear the data, this will only clear the root object
      clearObjectsData(emitUpdateEvents)
    } else {
      Log.w(tag, "Root object not found in pool during reset")
    }
  }

  /**
   * Clears the data stored for all objects in the pool.
   */
  private fun clearObjectsData(emitUpdateEvents: Boolean) {
    for (obj in pool.values) {
      val update = obj.clearData()
      if (emitUpdateEvents) {
        obj.notifyUpdated(update)
      }
    }
  }

  /**
   * Creates a zero-value object if it doesn't exist in the pool.
   *
   * @spec RTO6 - Creates zero-value objects when needed
   */
  internal fun createZeroValueObjectIfNotExists(objectId: String): BaseLiveObject {
    val existingObject = get(objectId)
    if (existingObject != null) {
      return existingObject // RTO6a
    }

    val parsedObjectId = ObjectId.fromString(objectId) // RTO6b
    val zeroValueObject = when (parsedObjectId.type) {
      ObjectType.Map -> DefaultLiveMap.zeroValue(objectId, adapter, this) // RTO6b2
      ObjectType.Counter -> DefaultLiveCounter.zeroValue(objectId, adapter) // RTO6b3
    }

    set(objectId, zeroValueObject)
    return zeroValueObject
  }

  /**
   * Creates the initial pool with root object.
   *
   * @spec RTO3b - Creates root LiveMap object
   */
  private fun createInitialPool() {
    val root = DefaultLiveMap.zeroValue(ROOT_OBJECT_ID, adapter, this)
    pool[ROOT_OBJECT_ID] = root
  }

  /**
   * Garbage collection interval handler.
   */
  private fun onGCInterval() {
    pool.entries.removeIf { (_, obj) ->
      if (obj.isEligibleForGc()) { true } // Remove from pool
      else {
        obj.onGCInterval()
        false  // Keep in pool
      }
    }
  }

  /**
   * Starts the garbage collection coroutine.
   */
  private fun startGCJob() {
    gcJob = gcScope.launch {
      while (isActive) {
        try {
          onGCInterval()
        } catch (e: Exception) {
          Log.e(tag, "Error during garbage collection", e)
        }
        delay(ObjectsPoolDefaults.GC_INTERVAL_MS)
      }
    }
  }

  /**
   * Disposes of the ObjectsPool, cleaning up resources.
   * Should be called when the pool is no longer needed.
   */
  fun dispose() {
    gcJob?.cancel()
    gcScope.cancel()
    pool.clear()
  }
}
