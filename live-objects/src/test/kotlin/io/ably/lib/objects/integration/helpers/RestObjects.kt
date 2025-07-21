package io.ably.lib.objects.integration.helpers

import com.google.gson.JsonObject
import io.ably.lib.objects.ObjectData
import io.ably.lib.objects.ObjectValue
import io.ably.lib.rest.AblyRest
import io.ably.lib.http.HttpUtils
import io.ably.lib.types.ClientOptions

/**
 * Helper class to create pre-determined objects and modify them on channels using rest api.
 */
internal class RestObjects(options: ClientOptions) {

  private val ablyRest: AblyRest = AblyRest(options)

  /**
   * Creates a new map object on the channel with optional initial data.
   * @return The object ID of the created map
   */
  internal fun createMap(channelName: String, data: Map<String, ObjectData>? = null): String {
    val mapCreateOp = PayloadBuilder.mapCreateRestOp(data = data)
    return operationRequest(channelName, mapCreateOp).objectId ?:
    throw Exception("Failed to create map: no objectId returned")
  }

  /**
   * Sets a value (primitives, JsonObject, JsonArray, etc.) at the specified key in an existing map.
   */
  internal fun setMapValue(channelName: String, mapObjectId: String, key: String, value: ObjectValue) {
    val data = ObjectData(value = value)
    val mapCreateOp = PayloadBuilder.mapSetRestOp(mapObjectId, key, data)
    operationRequest(channelName, mapCreateOp)
  }

  /**
   * Sets an object reference at the specified key in an existing map.
   */
  internal fun setMapRef(channelName: String, mapObjectId: String, key: String, refMapObjectId: String) {
    val data = ObjectData(objectId = refMapObjectId)
    val mapCreateOp = PayloadBuilder.mapSetRestOp(mapObjectId, key, data)
    operationRequest(channelName, mapCreateOp)
  }

  /**
   * Removes a key-value pair from an existing map.
   */
  internal fun removeMapValue(channelName: String, mapObjectId: String, key: String) {
    val mapRemoveOp = PayloadBuilder.mapRemoveRestOp(mapObjectId, key)
    operationRequest(channelName, mapRemoveOp)
  }

  /**
   * Creates a new counter object with an optional initial value (defaults to 0).
   * @return The object ID of the created counter
   */
  internal fun createCounter(channelName: String, initialValue: Long? = null): String {
    val counterCreateOp = PayloadBuilder.counterCreateRestOp(number = initialValue)
    return operationRequest(channelName, counterCreateOp).objectId
      ?: throw Exception("Failed to create counter: no objectId returned")
  }

  /**
   * Increments an existing counter by the specified amount.
   */
  internal fun incrementCounter(channelName: String, counterObjectId: String, incrementBy: Long) {
    val counterIncrementOp = PayloadBuilder.counterIncRestOp(counterObjectId, incrementBy)
    operationRequest(channelName, counterIncrementOp)
  }

  /**
   * Decrements an existing counter by the specified amount.
   */
  internal fun decrementCounter(channelName: String, counterObjectId: String, decrementBy: Long) {
    val counterDecrementOp = PayloadBuilder.counterIncRestOp(counterObjectId, -decrementBy)
    operationRequest(channelName, counterDecrementOp)
  }

  /**
   * Core method that executes object operations by sending POST requests to Ably's Objects REST API.
   * All public methods delegate to this for actual API communication.
   */
  private fun operationRequest(channelName: String, opBody: JsonObject): OperationResult {
    try {
      val path = "/channels/$channelName/objects"
      val requestBody = HttpUtils.requestBodyFromGson(opBody, ablyRest.options.useBinaryProtocol)

      val response = ablyRest.request("POST", path, null, requestBody, null)

      if (!response.success) {
        throw Exception("REST operation failed: HTTP ${response.statusCode} - ${response.errorMessage}")
      }

      val responseItems = response.items()
      if (responseItems.isEmpty()) {
        return OperationResult(null, null, success = true)
      }

      // Process first response item
      responseItems[0].asJsonObject.let { firstItem ->
        val objectIds = firstItem.get("objectIds")?.let { element ->
          if (element.isJsonArray) element.asJsonArray.map { it.asString } else null
        }
        return OperationResult(objectIds?.firstOrNull(), objectIds, success = true)
      }
    } catch (e: Exception) {
      throw Exception("Failed to execute operation request: ${e.message}", e)
    }
  }

  /**
   * Result class for operation requests containing the response data and extracted object ID.
   */
  private data class OperationResult(
    val objectId: String?,
    val objectIds: List<String>? = null, // Seems only used for batch operations
    val success: Boolean = true
  )
}

