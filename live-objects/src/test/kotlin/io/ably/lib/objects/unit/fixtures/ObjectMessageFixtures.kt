package io.ably.lib.objects.unit.fixtures

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.ably.lib.objects.*
import io.ably.lib.objects.Binary
import io.ably.lib.objects.ObjectData
import io.ably.lib.objects.ObjectMessage
import io.ably.lib.objects.ObjectState
import io.ably.lib.objects.ObjectValue

internal val dummyObjectDataStringValue = ObjectData(objectId = "object-id", ObjectValue("dummy string"))

internal val dummyBinaryObjectValue = ObjectData(objectId = "object-id", ObjectValue(Binary(byteArrayOf(1, 2, 3))))

internal val dummyNumberObjectValue = ObjectData(objectId = "object-id", ObjectValue(42.0))

internal val dummyBooleanObjectValue = ObjectData(objectId = "object-id", ObjectValue(true))

val dummyJsonObject = JsonObject().apply { addProperty("foo", "bar") }
internal val dummyJsonObjectValue = ObjectData(objectId = "object-id", ObjectValue(dummyJsonObject))

val dummyJsonArray = JsonArray().apply { add(1); add(2); add(3) }
internal val dummyJsonArrayValue = ObjectData(objectId = "object-id", ObjectValue(dummyJsonArray))

internal val dummyObjectMapEntry = ObjectMapEntry(
  tombstone = false,
  timeserial = "dummy-timeserial",
  data = dummyObjectDataStringValue
)

internal val dummyObjectMap = ObjectMap(
  semantics = MapSemantics.LWW,
  entries = mapOf("dummy-key" to dummyObjectMapEntry)
)

internal val dummyObjectCounter = ObjectCounter(
  count = 123.0
)

internal val dummyObjectMapOp = ObjectMapOp(
  key = "dummy-key",
  data = dummyObjectDataStringValue
)

internal val dummyObjectCounterOp = ObjectCounterOp(
  amount = 10.0
)

internal val dummyObjectOperation = ObjectOperation(
  action = ObjectOperationAction.MapCreate,
  objectId = "dummy-object-id",
  mapOp = dummyObjectMapOp,
  counterOp = dummyObjectCounterOp,
  map = dummyObjectMap,
  counter = dummyObjectCounter,
  nonce = "dummy-nonce",
  initialValue = "{\"foo\":\"bar\"}"
)

internal val dummyObjectState = ObjectState(
  objectId = "dummy-object-id",
  siteTimeserials = mapOf("site1" to "serial1"),
  tombstone = false,
  createOp = dummyObjectOperation,
  map = dummyObjectMap,
  counter = dummyObjectCounter
)

internal val dummyObjectMessage = ObjectMessage(
  id = "dummy-id",
  timestamp = 1234567890L,
  clientId = "dummy-client-id",
  connectionId = "dummy-connection-id",
  extras = JsonObject().apply { addProperty("meta", "data") },
  operation = dummyObjectOperation,
  objectState = dummyObjectState,
  serial = "dummy-serial",
  siteCode = "dummy-site-code"
)

internal fun dummyObjectMessageWithStringData(): ObjectMessage {
  return dummyObjectMessage
}

internal fun dummyObjectMessageWithBinaryData(): ObjectMessage {
  val binaryObjectMapEntry = dummyObjectMapEntry.copy(data = dummyBinaryObjectValue)
  val binaryObjectMap = dummyObjectMap.copy(entries = mapOf("dummy-key" to binaryObjectMapEntry))
  val binaryObjectMapOp = dummyObjectMapOp.copy(data = dummyBinaryObjectValue)
  val binaryObjectOperation = dummyObjectOperation.copy(
    mapOp = binaryObjectMapOp,
    map = binaryObjectMap
  )
  val binaryObjectState = dummyObjectState.copy(
    map = binaryObjectMap,
    createOp = binaryObjectOperation
  )
  return dummyObjectMessage.copy(
    operation = binaryObjectOperation,
    objectState = binaryObjectState
  )
}

internal fun dummyObjectMessageWithNumberData(): ObjectMessage {
  val numberObjectMapEntry = dummyObjectMapEntry.copy(data = dummyNumberObjectValue)
  val numberObjectMap = dummyObjectMap.copy(entries = mapOf("dummy-key" to numberObjectMapEntry))
  val numberObjectMapOp = dummyObjectMapOp.copy(data = dummyNumberObjectValue)
  val numberObjectOperation = dummyObjectOperation.copy(
    mapOp = numberObjectMapOp,
    map = numberObjectMap
  )
  val numberObjectState = dummyObjectState.copy(
    map = numberObjectMap,
    createOp = numberObjectOperation
  )
  return dummyObjectMessage.copy(
    operation = numberObjectOperation,
    objectState = numberObjectState
  )
}

internal fun dummyObjectMessageWithBooleanData(): ObjectMessage {
  val booleanObjectMapEntry = dummyObjectMapEntry.copy(data = dummyBooleanObjectValue)
  val booleanObjectMap = dummyObjectMap.copy(entries = mapOf("dummy-key" to booleanObjectMapEntry))
  val booleanObjectMapOp = dummyObjectMapOp.copy(data = dummyBooleanObjectValue)
  val booleanObjectOperation = dummyObjectOperation.copy(
    mapOp = booleanObjectMapOp,
    map = booleanObjectMap
  )
  val booleanObjectState = dummyObjectState.copy(
    map = booleanObjectMap,
    createOp = booleanObjectOperation
  )
  return dummyObjectMessage.copy(
    operation = booleanObjectOperation,
    objectState = booleanObjectState
  )
}

internal fun dummyObjectMessageWithJsonObjectData(): ObjectMessage {
  val jsonObjectMapEntry = dummyObjectMapEntry.copy(data = dummyJsonObjectValue)
  val jsonObjectMap = dummyObjectMap.copy(entries = mapOf("dummy-key" to jsonObjectMapEntry))
  val jsonObjectMapOp = dummyObjectMapOp.copy(data = dummyJsonObjectValue)
  val jsonObjectOperation = dummyObjectOperation.copy(
    action = ObjectOperationAction.MapSet,
    mapOp = jsonObjectMapOp,
    map = jsonObjectMap
  )
  val jsonObjectState = dummyObjectState.copy(
    map = jsonObjectMap,
    createOp = jsonObjectOperation
  )
  return dummyObjectMessage.copy(
    operation = jsonObjectOperation,
    objectState = jsonObjectState
  )
}

internal fun dummyObjectMessageWithJsonArrayData(): ObjectMessage {
  val jsonArrayMapEntry = dummyObjectMapEntry.copy(data = dummyJsonArrayValue)
  val jsonArrayMap = dummyObjectMap.copy(entries = mapOf("dummy-key" to jsonArrayMapEntry))
  val jsonArrayMapOp = dummyObjectMapOp.copy(data = dummyJsonArrayValue)
  val jsonArrayOperation = dummyObjectOperation.copy(
    action = ObjectOperationAction.MapSet,
    mapOp = jsonArrayMapOp,
    map = jsonArrayMap
  )
  val jsonArrayState = dummyObjectState.copy(
    map = jsonArrayMap,
    createOp = jsonArrayOperation
  )
  return dummyObjectMessage.copy(
    operation = jsonArrayOperation,
    objectState = jsonArrayState
  )
}
