package io.ably.lib.objects

import io.ably.lib.util.Log

/**
 * Base implementation of LiveObject interface.
 * Provides common functionality for all live objects.
 *
 * @spec RTLM1/RTLC1 - Base class for LiveMap/LiveCounter objects
 */
internal abstract class BaseLiveObject(
  protected val objectId: String,
  protected val adapter: LiveObjectsAdapter
) : LiveObject {

  protected open val tag = "BaseLiveObject"
  protected var isTombstoned = false
  protected var tombstonedAt: Long? = null

  /**
   * @spec RTLM6/RTLC6 - Map of serials keyed by site code for LiveMap/LiveCounter
   */
  protected val siteTimeserials = mutableMapOf<String, String>()

  /**
   * @spec RTLM6/RTLC6 - Flag to track if create operation has been merged for LiveMap/LiveCounter
   */
  protected var createOperationIsMerged = false

  override fun notifyUpdated(update: Any) {
    // TODO: Implement event emission for updates
    Log.v(tag, "Object $objectId updated: $update")
  }

  /**
   * Checks if an operation can be applied based on serial comparison.
   * Similar to JavaScript _canApplyOperation method.
   *
   * @spec RTLM9/RTLC9 - Serial comparison logic for LiveMap/LiveCounter operations
   */
  protected fun canApplyOperation(siteCode: String?, serial: String?): Boolean {
    if (isTombstoned) { // this object is tombstoned so the operation cannot be applied
      return false
    }
    if (serial.isNullOrEmpty()) {
      throw objectError("Invalid serial: $serial")
    }
    if (siteCode.isNullOrEmpty()) {
      throw objectError("Invalid site code: $siteCode")
    }
    val existingSiteSerial = siteTimeserials[siteCode]
    return existingSiteSerial == null || serial > existingSiteSerial
  }

  /**
   * Updates the time serial for a given site code.
   */
  protected fun updateTimeSerial(opSiteCode: String, opSerial: String) {
    siteTimeserials[opSiteCode] = opSerial
  }

  /**
   * Applies object delete operation.
   * Similar to JavaScript _applyObjectDelete method.
   *
   * @spec RTLM10/RTLC10 - Object deletion for LiveMap/LiveCounter
   */
  protected fun applyObjectDelete(): Any {
    return tombstone()
  }

  /**
   * Marks the object as tombstoned.
   * Similar to JavaScript tombstone method.
   *
   * @spec RTLM11/RTLC11 - Tombstone functionality for LiveMap/LiveCounter
   */
  protected fun tombstone(): Any {
    isTombstoned = true
    tombstonedAt = System.currentTimeMillis()
    val update = clearData()
    // TODO: Emit lifecycle events
    return update
  }

  /**
   * Clears the object's data.
   * Similar to JavaScript clearData method.
   */
  protected abstract fun clearData(): Map<String, Any>

  /**
   * Gets the timestamp when the object was tombstoned.
   */
  fun tombstonedAt(): Long? = tombstonedAt
}

/**
 * Interface for live objects that can be stored in the objects pool.
 * This is a placeholder interface that will be implemented by LiveMap and LiveCounter.
 *
 * @spec RTO3 - Base interface for all live objects in the pool
 */
internal interface LiveObject {
  /**
   * @spec RTLM6/RTLC6 - Overrides object data with state from sync for LiveMap/LiveCounter
   */
  fun overrideWithObjectState(objectState: ObjectState): Any

  /**
   * @spec RTLM7/RTLC7 - Applies operations to LiveMap/LiveCounter
   */
  fun applyOperation(operation: ObjectOperation, message: ObjectMessage)

  /**
   * Notifies subscribers of object updates
   */
  fun notifyUpdated(update: Any)
}
