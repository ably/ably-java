package io.ably.lib.objects

import io.ably.lib.util.Log

/**
 * Base implementation of LiveObject interface.
 * Provides common functionality for all live objects.
 *
 * @spec RTLM1 - Base class for LiveMap objects
 * @spec RTLC1 - Base class for LiveCounter objects
 */
internal abstract class BaseLiveObject(
  protected val objectId: String,
  protected val adapter: LiveObjectsAdapter
) : LiveObject {

  protected open val tag = "BaseLiveObject"
  protected var isTombstoned = false
  protected var tombstonedAt: Long? = null
  /**
   * @spec RTLM6 - Map of serials keyed by site code for LiveMap
   * @spec RTLC6 - Map of serials keyed by site code for LiveCounter
   */
  protected val siteTimeserials = mutableMapOf<String, String>()
  /**
   * @spec RTLM6 - Flag to track if create operation has been merged for LiveMap
   * @spec RTLC6 - Flag to track if create operation has been merged for LiveCounter
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
   * @spec RTLM9 - Serial comparison logic for LiveMap operations
   * @spec RTLC9 - Serial comparison logic for LiveCounter operations
   */
  protected fun canApplyOperation(opSiteCode: String?, opSerial: String?): Boolean {
    if (isTombstoned) {
      // this object is tombstoned so the operation cannot be applied
      return false
    }

    if (opSerial.isNullOrEmpty()) {
      throw objectError("Invalid serial: $opSerial")
    }
    if (opSiteCode.isNullOrEmpty()) {
      throw objectError("Invalid site code: $opSiteCode")
    }

    val siteSerial = siteTimeserials[opSiteCode]
    return siteSerial == null || opSerial > siteSerial
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
   * @spec RTLM10 - Object deletion for LiveMap
   * @spec RTLC10 - Object deletion for LiveCounter
   */
  protected fun applyObjectDelete(): Any {
    return tombstone()
  }

  /**
   * Marks the object as tombstoned.
   * Similar to JavaScript tombstone method.
   *
   * @spec RTLM11 - Tombstone functionality for LiveMap
   * @spec RTLC11 - Tombstone functionality for LiveCounter
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
  protected abstract fun clearData(): Any

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
   * @spec RTLM6 - Overrides object data with state from sync
   * @spec RTLC6 - Overrides counter data with state from sync
   */
  fun overrideWithObjectState(objectState: ObjectState): Any

  /**
   * @spec RTLM7 - Applies operations to LiveMap
   * @spec RTLC7 - Applies operations to LiveCounter
   */
  fun applyOperation(operation: ObjectOperation, message: ObjectMessage)

  /**
   * Notifies subscribers of object updates
   */
  fun notifyUpdated(update: Any)
}
