package io.ably.lib.objects.type

import io.ably.lib.objects.*
import io.ably.lib.objects.ObjectMessage
import io.ably.lib.objects.ObjectOperation
import io.ably.lib.objects.ObjectState
import io.ably.lib.objects.ObjectsPoolDefaults
import io.ably.lib.objects.objectError
import io.ably.lib.util.Log

/**
 * Base implementation of LiveObject interface.
 * Provides common functionality for all live objects.
 *
 * @spec RTLO1/RTLO2 - Base class for LiveMap/LiveCounter object
 */
internal abstract class BaseLiveObject(
  internal val objectId: String, // // RTLO3a
  protected val adapter: LiveObjectsAdapter
) {

  protected open val tag = "BaseLiveObject"

  internal val siteTimeserials = mutableMapOf<String, String>() // RTLO3b

  internal var createOperationIsMerged = false // RTLO3c

  internal var isTombstoned = false
  private var tombstonedAt: Long? = null

  fun notifyUpdated(update: Any) {
    // TODO: Implement event emission for updates
    Log.v(tag, "Object $objectId updated: $update")
  }

  /**
   * Checks if an operation can be applied based on serial comparison.
   *
   * @spec RTLO4a - Serial comparison logic for LiveMap/LiveCounter operations
   */
  internal fun canApplyOperation(siteCode: String?, serial: String?): Boolean {
    if (serial.isNullOrEmpty()) {
      throw objectError("Invalid serial: $serial") // RTLO4a3
    }
    if (siteCode.isNullOrEmpty()) {
      throw objectError("Invalid site code: $siteCode") // RTLO4a3
    }
    val existingSiteSerial = siteTimeserials[siteCode] // RTLO4a4
    return existingSiteSerial == null || serial > existingSiteSerial // RTLO4a5, RTLO4a6
  }

  /**
   * Marks the object as tombstoned.
   */
  internal fun tombstone(): Any {
    isTombstoned = true
    tombstonedAt = System.currentTimeMillis()
    val update = clearData()
    // TODO: Emit lifecycle events
    return update
  }

  /**
   * Checks if the object is eligible for garbage collection.
   */
  internal fun isEligibleForGc(): Boolean {
    val currentTime = System.currentTimeMillis()
    return isTombstoned && tombstonedAt?.let { currentTime - it >= ObjectsPoolDefaults.GC_GRACE_PERIOD_MS } == true
  }

  /**
   * This is invoked by ObjectMessage having updated data with parent `ProtocolMessageAction` as `object_sync`
   * @return an update describing the changes
   * @spec RTLM6/RTLC6 - Overrides ObjectMessage with object data state from sync to LiveMap/LiveCounter
   */
  abstract fun applyObjectState(objectState: ObjectState): Map<String, Any>

  /**
   * This is invoked by ObjectMessage having updated data with parent `ProtocolMessageAction` as `object`
   * @return an update describing the changes
   * @spec RTLM7/RTLC7 - Applies ObjectMessage with object data operations to LiveMap/LiveCounter
   */
  abstract fun applyOperation(operation: ObjectOperation, message: ObjectMessage)

  /**
   * Clears the object's data and returns an update describing the changes.
   * This is called during tombstoning and explicit clear operations.
   *
   * This method:
   * 1. Calculates a diff between the current state and an empty state
   * 2. Clears all entries from the underlying data structure
   * 3. Returns a map containing metadata about what was cleared
   *
   * The returned map is used to notifying other components about what entries were removed.
   *
   * @return A map representing the diff of changes made
   */
  abstract fun clearData(): Map<String, Any>

  /**
   * Called during garbage collection intervals to clean up expired entries.
   *
   * This method should identify and remove entries that:
   * - Have been marked as tombstoned
   * - Have a tombstone timestamp older than the configured grace period
   *
   * Implementations typically use single-pass removal techniques to
   * efficiently clean up expired data without creating temporary collections.
   */
  abstract fun onGCInterval()
}
