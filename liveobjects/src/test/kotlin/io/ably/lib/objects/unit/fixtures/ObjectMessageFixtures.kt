package io.ably.lib.objects.unit.fixtures

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.ably.lib.objects.*
import io.ably.lib.objects.Binary
import io.ably.lib.objects.MapCreate
import io.ably.lib.objects.MapCreateWithObjectId
import io.ably.lib.objects.MapSet
import io.ably.lib.objects.ObjectData
import io.ably.lib.objects.ObjectMessage
import io.ably.lib.objects.ObjectState
import java.util.Base64

internal val dummyObjectDataStringValue = ObjectData(objectId = "object-id", string = "dummy string")

internal val dummyBinaryObjectValue = ObjectData(objectId = "object-id", bytes = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)))

internal val dummyNumberObjectValue = ObjectData(objectId = "object-id", number = 42.0)

internal val dummyBooleanObjectValue = ObjectData(objectId = "object-id", boolean = true)

val dummyJsonObject = JsonObject().apply { addProperty("foo", "bar") }
internal val dummyJsonObjectValue = ObjectData(objectId = "object-id", json = dummyJsonObject)

val dummyJsonArray = JsonArray().apply { add(1); add(2); add(3) }
internal val dummyJsonArrayValue = ObjectData(objectId = "object-id", json = dummyJsonArray)

internal val dummyObjectsMapEntry = ObjectsMapEntry(
  tombstone = false,
  timeserial = "dummy-timeserial",
  data = dummyObjectDataStringValue
)

internal val dummyObjectsMap = ObjectsMap(
  semantics = ObjectsMapSemantics.LWW,
  entries = mapOf("dummy-key" to dummyObjectsMapEntry)
)

internal val dummyObjectsCounter = ObjectsCounter(
  count = 123.0
)

internal val dummyMapCreate = MapCreate(
  semantics = ObjectsMapSemantics.LWW,
  entries = mapOf("dummy-key" to dummyObjectsMapEntry)
)

internal val dummyObjectOperation = ObjectOperation(
  action = ObjectOperationAction.MapCreate,
  objectId = "dummy-object-id",
  mapCreate = dummyMapCreate,
  mapCreateWithObjectId = MapCreateWithObjectId(nonce = "dummy-nonce", initialValue = "{\"foo\":\"bar\"}")
)

internal val dummyObjectState = ObjectState(
  objectId = "dummy-object-id",
  siteTimeserials = mapOf("site1" to "serial1"),
  tombstone = false,
  createOp = dummyObjectOperation,
  map = dummyObjectsMap,
  counter = dummyObjectsCounter
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
  val binaryObjectMapEntry = dummyObjectsMapEntry.copy(data = dummyBinaryObjectValue)
  val binaryObjectMap = dummyObjectsMap.copy(entries = mapOf("dummy-key" to binaryObjectMapEntry))
  val binaryMapCreate = dummyMapCreate.copy(entries = mapOf("dummy-key" to binaryObjectMapEntry))
  val binaryObjectOperation = dummyObjectOperation.copy(mapCreate = binaryMapCreate)
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
  val numberObjectMapEntry = dummyObjectsMapEntry.copy(data = dummyNumberObjectValue)
  val numberObjectMap = dummyObjectsMap.copy(entries = mapOf("dummy-key" to numberObjectMapEntry))
  val numberMapCreate = dummyMapCreate.copy(entries = mapOf("dummy-key" to numberObjectMapEntry))
  val numberObjectOperation = dummyObjectOperation.copy(mapCreate = numberMapCreate)
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
  val booleanObjectMapEntry = dummyObjectsMapEntry.copy(data = dummyBooleanObjectValue)
  val booleanObjectMap = dummyObjectsMap.copy(entries = mapOf("dummy-key" to booleanObjectMapEntry))
  val booleanMapCreate = dummyMapCreate.copy(entries = mapOf("dummy-key" to booleanObjectMapEntry))
  val booleanObjectOperation = dummyObjectOperation.copy(mapCreate = booleanMapCreate)
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
  val jsonObjectMapEntry = dummyObjectsMapEntry.copy(data = dummyJsonObjectValue)
  val jsonObjectMap = dummyObjectsMap.copy(entries = mapOf("dummy-key" to jsonObjectMapEntry))
  val jsonMapCreate = dummyMapCreate.copy(entries = mapOf("dummy-key" to jsonObjectMapEntry))
  val jsonObjectOperation = dummyObjectOperation.copy(
    action = ObjectOperationAction.MapSet,
    mapCreate = null,
    mapSet = MapSet(key = "dummy-key", value = dummyJsonObjectValue)
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
  val jsonArrayMapEntry = dummyObjectsMapEntry.copy(data = dummyJsonArrayValue)
  val jsonArrayMap = dummyObjectsMap.copy(entries = mapOf("dummy-key" to jsonArrayMapEntry))
  val jsonArrayOperation = dummyObjectOperation.copy(
    action = ObjectOperationAction.MapSet,
    mapCreate = null,
    mapSet = MapSet(key = "dummy-key", value = dummyJsonArrayValue)
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
