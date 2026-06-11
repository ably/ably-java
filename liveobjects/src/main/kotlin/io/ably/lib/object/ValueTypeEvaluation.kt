package io.ably.lib.`object`

import io.ably.lib.`object`.value.LiveMapValue
import java.util.Base64

/**
 * Evaluation procedures for the LiveMap/LiveCounter value types: converts a
 * creation-intent holder into the ordered `WireObjectMessage`s that create
 * the described objects on the channel.
 *
 * Spec: RTLMV4, RTLCV4
 */

/**
 * Evaluates this map value type into an ordered list of ObjectMessages.
 * Messages for nested objects precede the final MAP_CREATE message for this
 * map (RTLMV4d1, RTLMV4d2, RTLMV4k) — the last message in the list is always
 * the MAP_CREATE whose objectId identifies the map this value represents.
 *
 * Spec: RTLMV4
 */
internal suspend fun DefaultLiveMap.evaluate(bridge: ObjectsBridge): List<WireObjectMessage> {
  val nestedMessages = mutableListOf<WireObjectMessage>()
  val mapEntries = mutableMapOf<String, WireObjectsMapEntry>()

  // RTLMV4b - keys must be valid
  if (entries.keys.any { it.isEmpty() }) {
    throw invalidInputError("Map keys must not be empty") // 400 / 40003
  }

  // RTLMV4d - build entries, recursively evaluating nested value types
  for ((key, value) in entries) {
    val data = objectDataFrom(value, nestedMessages, bridge)
    mapEntries[key] = WireObjectsMapEntry(tombstone = false, data = data)
  }

  // RTLMV4e - create the MapCreate object
  val mapCreate = WireMapCreate(semantics = WireObjectsMapSemantics.LWW, entries = mapEntries)

  // RTLMV4f - initial value JSON string of the encoded MapCreate
  val initialValueJSONString = wireGson.toJson(mapCreate)

  // RTLMV4g, RTLMV4h, RTLMV4i - nonce, server time, objectId
  val nonce = generateObjectNonce()
  val serverTime = bridge.getServerTime()
  val objectId = ObjectsIdentifier.fromInitialValue(WireObjectType.Map, initialValueJSONString, nonce, serverTime)

  // RTLMV4j - the MAP_CREATE ObjectMessage; retain the MapCreate via derivedFrom (RTLMV4j5)
  val mapCreateMessage = WireObjectMessage(
    operation = WireObjectOperation(
      action = WireObjectOperationAction.MapCreate,
      objectId = objectId,
      mapCreateWithObjectId = WireMapCreateWithObjectId(
        nonce = nonce,
        initialValue = initialValueJSONString,
        derivedFrom = mapCreate,
      ),
    )
  )

  // RTLMV4k - nested create messages first, this map's MAP_CREATE last
  return nestedMessages + mapCreateMessage
}

/**
 * Evaluates this counter value type into a COUNTER_CREATE ObjectMessage.
 *
 * Spec: RTLCV4
 */
internal suspend fun DefaultLiveCounter.evaluate(bridge: ObjectsBridge): WireObjectMessage {
  // RTLCV4a - validate the count is a valid number
  val countValue = count.toDouble()
  if (countValue.isNaN() || countValue.isInfinite()) {
    throw invalidInputError("Counter value must be a valid number") // 400 / 40003
  }

  // RTLCV4b - create the CounterCreate object
  val counterCreate = WireCounterCreate(count = countValue)

  // RTLCV4c - initial value JSON string
  val initialValueJSONString = wireGson.toJson(counterCreate)

  // RTLCV4d, RTLCV4e, RTLCV4f - nonce, server time, objectId
  val nonce = generateObjectNonce()
  val serverTime = bridge.getServerTime()
  val objectId = ObjectsIdentifier.fromInitialValue(WireObjectType.Counter, initialValueJSONString, nonce, serverTime)

  // RTLCV4g - the COUNTER_CREATE ObjectMessage; retain the CounterCreate via derivedFrom (RTLCV4g5)
  return WireObjectMessage(
    operation = WireObjectOperation(
      action = WireObjectOperationAction.CounterCreate,
      objectId = objectId,
      counterCreateWithObjectId = WireCounterCreateWithObjectId(
        nonce = nonce,
        initialValue = initialValueJSONString,
        derivedFrom = counterCreate,
      ),
    )
  )
}

/**
 * Converts a public LiveMapValue union member into wire ObjectData,
 * recursively evaluating nested value types and collecting their create
 * messages into [nestedMessages].
 *
 * Spec: RTLMV4d1-RTLMV4d7
 */
internal suspend fun objectDataFrom(
  value: LiveMapValue,
  nestedMessages: MutableList<WireObjectMessage>,
  bridge: ObjectsBridge,
): WireObjectData = when {
  // RTLMV4d1 - nested counter value type
  value.isLiveCounter -> {
    val counter = value.asLiveCounter as? DefaultLiveCounter
      ?: throw invalidInputError("LiveCounter value type must be created via LiveCounter.create")
    val message = counter.evaluate(bridge)
    nestedMessages.add(message)
    WireObjectData(objectId = message.operation!!.objectId)
  }
  // RTLMV4d2 - nested map value type; objectId comes from the final MAP_CREATE message
  value.isLiveMap -> {
    val map = value.asLiveMap as? DefaultLiveMap
      ?: throw invalidInputError("LiveMap value type must be created via LiveMap.create")
    val messages = map.evaluate(bridge)
    nestedMessages.addAll(messages)
    WireObjectData(objectId = messages.last().operation!!.objectId)
  }
  // RTLMV4d3 - JSON values
  value.isJsonObject -> WireObjectData(json = value.asJsonObject)
  value.isJsonArray -> WireObjectData(json = value.asJsonArray)
  // RTLMV4d4 - string
  value.isString -> WireObjectData(string = value.asString)
  // RTLMV4d5 - number
  value.isNumber -> WireObjectData(number = value.asNumber.toDouble())
  // RTLMV4d6 - boolean
  value.isBoolean -> WireObjectData(boolean = value.asBoolean)
  // RTLMV4d7 - binary (wire form is base64)
  value.isBinary -> WireObjectData(bytes = Base64.getEncoder().encodeToString(value.asBinary))
  // RTLMV4c - unsupported data type
  else -> throw objectsException(
    "Unsupported data type for map value: ${value.javaClass.name}",
    ObjectErrorCode.MapValueDataTypeUnsupported, // 40013
  )
}
