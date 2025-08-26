package io.ably.lib.objects.integration.helpers

import com.google.gson.JsonObject
import io.ably.lib.objects.ObjectData
import io.ably.lib.objects.ObjectOperationAction
import io.ably.lib.objects.generateNonce
import io.ably.lib.objects.serialization.gson

internal object PayloadBuilder {
  /**
   * Action strings for REST API operations.
   * Maps ObjectOperationAction enum values to their string representations.
   */
  private val ACTION_STRINGS = mapOf(
    ObjectOperationAction.MapCreate to "MAP_CREATE",
    ObjectOperationAction.MapSet to "MAP_SET",
    ObjectOperationAction.MapRemove to "MAP_REMOVE",
    ObjectOperationAction.CounterCreate to "COUNTER_CREATE",
    ObjectOperationAction.CounterInc to "COUNTER_INC",
  )

  /**
   * Creates a MAP_CREATE operation payload for REST API.
   *
   * @param objectId Optional specific object ID
   * @param data Optional initial data for the map
   * @param nonce Optional nonce for deterministic object ID generation
   */
  internal fun mapCreateRestOp(
    objectId: String? = null,
    data: Map<String, ObjectData>? = null,
    nonce: String? = null,
  ): JsonObject {
    val opBody = JsonObject().apply {
      addProperty("operation", ACTION_STRINGS[ObjectOperationAction.MapCreate])
    }

    if (data != null) {
      opBody.add("data", gson.toJsonTree(data))
    }

    if (objectId != null) {
      opBody.addProperty("objectId", objectId)
      opBody.addProperty("nonce", nonce ?: generateNonce())
    }

    return opBody
  }


  /**
   * Creates a MAP_SET operation payload for REST API.
   */
  internal fun mapSetRestOp(objectId: String, key: String, value: ObjectData): JsonObject {
    val opBody = JsonObject().apply {
      addProperty("operation", ACTION_STRINGS[ObjectOperationAction.MapSet])
      addProperty("objectId", objectId)
    }

    val dataObj = JsonObject().apply {
      addProperty("key", key)
      add("value", gson.toJsonTree(value))
    }
    opBody.add("data", dataObj)

    return opBody
  }

  /**
   * Creates a MAP_REMOVE operation payload for REST API.
   */
  internal fun mapRemoveRestOp(objectId: String, key: String): JsonObject {
    val opBody = JsonObject().apply {
      addProperty("operation", ACTION_STRINGS[ObjectOperationAction.MapRemove])
      addProperty("objectId", objectId)
    }

    val dataObj = JsonObject().apply {
      addProperty("key", key)
    }
    opBody.add("data", dataObj)

    return opBody
  }

  /**
   * Creates a COUNTER_CREATE operation payload for REST API.
   *
   * @param objectId Optional specific object ID
   * @param nonce Optional nonce for deterministic object ID generation
   * @param number Optional initial counter value
   */
  internal fun counterCreateRestOp(
    objectId: String? = null,
    number: Double? = null,
    nonce: String? = null,
  ): JsonObject {
    val opBody = JsonObject().apply {
      addProperty("operation", ACTION_STRINGS[ObjectOperationAction.CounterCreate])
    }

    if (number != null) {
      val dataObj = JsonObject().apply {
        addProperty("number", number)
      }
      opBody.add("data", dataObj)
    }

    if (objectId != null) {
      opBody.addProperty("objectId", objectId)
      opBody.addProperty("nonce", nonce ?: generateNonce())
    }

    return opBody
  }

  /**
   * Creates a COUNTER_INC operation payload for REST API.
   */
  internal fun counterIncRestOp(objectId: String, number: Double): JsonObject {
    val opBody = JsonObject().apply {
      addProperty("operation", ACTION_STRINGS[ObjectOperationAction.CounterInc])
      addProperty("objectId", objectId)
      add("data", JsonObject().apply {
        addProperty("number", number)
      })
    }
    return opBody
  }
}
