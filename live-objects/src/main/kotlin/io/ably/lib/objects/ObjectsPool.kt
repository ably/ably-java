package io.ably.lib.objects

import io.ably.lib.objects.type.BaseLiveObject
import io.ably.lib.objects.type.DefaultLiveCounter
import io.ably.lib.objects.type.DefaultLiveMap
import io.ably.lib.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

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
  private val pool = ConcurrentHashMap<String, BaseLiveObject>()

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
  fun get(objectId: String): BaseLiveObject? {
    return pool[objectId]
  }

  /**
   * Deletes objects from the pool for which object ids are not found in the provided array of ids.
   */
  fun deleteExtraObjectIds(objectIds: List<String>) {
    val poolObjectIds = pool.keys.toList()
    val extraObjectIds = poolObjectIds.filter { !objectIds.contains(it) }

    extraObjectIds.forEach { remove(it) }
  }

  /**
   * Sets a live object in the pool.
   */
  fun set(objectId: String, liveObject: BaseLiveObject) {
    pool[objectId] = liveObject
  }

  /**
   * Removes all objects but root from the pool and clears the data for root.
   * Does not create a new root object, so the reference to the root object remains the same.
   */
  fun resetToInitialPool(emitUpdateEvents: Boolean) {
    // Clear the pool first and keep the root object
    val root = pool[ROOT_OBJECT_ID]
    if (root != null) {
      pool.clear()
      pool[ROOT_OBJECT_ID] = root

      // Clear the data, this will only clear the root object
      clearObjectsData(emitUpdateEvents)
    } else {
      Log.w(tag, "Root object not found in pool during reset")
    }
  }

  /**
   * Clears the data stored for all objects in the pool.
   */
  fun clearObjectsData(emitUpdateEvents: Boolean) {
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
  fun createZeroValueObjectIfNotExists(objectId: String): BaseLiveObject {
    val existingObject = get(objectId)
    if (existingObject != null) {
      return existingObject // RTO6a
    }

    val parsedObjectId = ObjectId.fromString(objectId) // RTO6b
    val zeroValueObject = when (parsedObjectId.type) {
      ObjectType.Map -> DefaultLiveMap.zeroValue(objectId, this) // RTO6b2
      ObjectType.Counter -> DefaultLiveCounter.zeroValue(objectId, this) // RTO6b3
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
    val root = DefaultLiveMap.zeroValue(ROOT_OBJECT_ID, this)
    pool[ROOT_OBJECT_ID] = root
  }

  /**
   * Garbage collection interval handler.
   */
  private fun onGCInterval() {
    val toDelete = mutableListOf<String>()

    for ((objectId, obj) in pool.entries) {
      // Tombstoned objects should be removed from the pool if they have been tombstoned for longer than grace period.
      // By removing them from the local pool, Objects plugin no longer keeps a reference to those objects, allowing JVM's
      // Garbage Collection to eventually free the memory for those objects, provided the user no longer references them either.
      if (obj.isTombstoned &&
          obj.tombstonedAt != null &&
          System.currentTimeMillis() - obj.tombstonedAt!! >= ObjectsPoolDefaults.GC_GRACE_PERIOD_MS) {
        toDelete.add(objectId)
        continue
      }

      obj.onGCInterval()
    }

    toDelete.forEach { pool.remove(it) }
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
    clear()
  }

  /**
   * Gets all object IDs in the pool.
   * Useful for debugging and testing.
   */
  fun getObjectIds(): Set<String> = pool.keys.toSet()

  /**
   * Gets the size of the pool.
   * Useful for debugging and testing.
   */
  fun size(): Int = pool.size

  /**
   * Checks if the pool contains an object with the given ID.
   */
  fun contains(objectId: String): Boolean = pool.containsKey(objectId)

  /**
   * Removes an object from the pool.
   */
  fun remove(objectId: String): BaseLiveObject? = pool.remove(objectId)

  /**
   * Clears all objects from the pool.
   */
  fun clear() = pool.clear()
}
