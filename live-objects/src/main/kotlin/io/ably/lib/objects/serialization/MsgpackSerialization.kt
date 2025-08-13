package io.ably.lib.objects.serialization

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ably.lib.objects.*
import io.ably.lib.objects.Binary
import io.ably.lib.objects.ErrorCode
import io.ably.lib.objects.ObjectsMapSemantics
import io.ably.lib.objects.ObjectsCounter
import io.ably.lib.objects.ObjectsCounterOp
import io.ably.lib.objects.ObjectData
import io.ably.lib.objects.ObjectsMap
import io.ably.lib.objects.ObjectsMapEntry
import io.ably.lib.objects.ObjectsMapOp
import io.ably.lib.objects.ObjectMessage
import io.ably.lib.objects.ObjectOperation
import io.ably.lib.objects.ObjectOperationAction
import io.ably.lib.objects.ObjectState
import io.ably.lib.objects.ObjectValue
import io.ably.lib.util.Serialisation
import org.msgpack.core.MessageFormat
import org.msgpack.core.MessagePacker
import org.msgpack.core.MessageUnpacker

/**
 * Write ObjectMessage to MessagePacker
 */
internal fun ObjectMessage.writeMsgpack(packer: MessagePacker) {
  var fieldCount = 0

  if (id != null) fieldCount++
  if (timestamp != null) fieldCount++
  if (clientId != null) fieldCount++
  if (connectionId != null) fieldCount++
  if (extras != null) fieldCount++
  if (operation != null) fieldCount++
  if (objectState != null) fieldCount++
  if (serial != null) fieldCount++
  if (serialTimestamp != null) fieldCount++
  if (siteCode != null) fieldCount++

  packer.packMapHeader(fieldCount)

  if (id != null) {
    packer.packString("id")
    packer.packString(id)
  }

  if (timestamp != null) {
    packer.packString("timestamp")
    packer.packLong(timestamp)
  }

  if (clientId != null) {
    packer.packString("clientId")
    packer.packString(clientId)
  }

  if (connectionId != null) {
    packer.packString("connectionId")
    packer.packString(connectionId)
  }

  if (extras != null) {
    packer.packString("extras")
    packer.writePayload(Serialisation.gsonToMsgpack(extras))
  }

  if (operation != null) {
    packer.packString("operation")
    operation.writeMsgpack(packer)
  }

  if (objectState != null) {
    packer.packString("object")
    objectState.writeMsgpack(packer)
  }

  if (serial != null) {
    packer.packString("serial")
    packer.packString(serial)
  }

  if (serialTimestamp != null) {
    packer.packString("serialTimestamp")
    packer.packLong(serialTimestamp)
  }

  if (siteCode != null) {
    packer.packString("siteCode")
    packer.packString(siteCode)
  }
}

/**
 * Read an ObjectMessage from MessageUnpacker
 */
internal fun readObjectMessage(unpacker: MessageUnpacker): ObjectMessage {
  if (unpacker.nextFormat == MessageFormat.NIL) {
    unpacker.unpackNil()
    return ObjectMessage() // default/empty message
  }

  val fieldCount = unpacker.unpackMapHeader()

  var id: String? = null
  var timestamp: Long? = null
  var clientId: String? = null
  var connectionId: String? = null
  var extras: JsonObject? = null
  var operation: ObjectOperation? = null
  var objectState: ObjectState? = null
  var serial: String? = null
  var serialTimestamp: Long? = null
  var siteCode: String? = null

  for (i in 0 until fieldCount) {
    val fieldName = unpacker.unpackString().intern()
    val fieldFormat = unpacker.nextFormat

    if (fieldFormat == MessageFormat.NIL) {
      unpacker.unpackNil()
      continue
    }

    when (fieldName) {
      "id" -> id = unpacker.unpackString()
      "timestamp" -> timestamp = unpacker.unpackLong()
      "clientId" -> clientId = unpacker.unpackString()
      "connectionId" -> connectionId = unpacker.unpackString()
      "extras" -> extras = Serialisation.msgpackToGson(unpacker.unpackValue()) as? JsonObject
      "operation" -> operation = readObjectOperation(unpacker)
      "object" -> objectState = readObjectState(unpacker)
      "serial" -> serial = unpacker.unpackString()
      "serialTimestamp" -> serialTimestamp = unpacker.unpackLong()
      "siteCode" -> siteCode = unpacker.unpackString()
      else -> unpacker.skipValue()
    }
  }

  return ObjectMessage(
    id = id,
    timestamp = timestamp,
    clientId = clientId,
    connectionId = connectionId,
    extras = extras,
    operation = operation,
    objectState = objectState,
    serial = serial,
    serialTimestamp = serialTimestamp,
    siteCode = siteCode
  )
}

/**
 * Write ObjectOperation to MessagePacker
 */
private fun ObjectOperation.writeMsgpack(packer: MessagePacker) {
  var fieldCount = 1 // action is always required
  require(objectId.isNotEmpty()) { "objectId must be non-empty per Objects protocol" }
  fieldCount++

  if (mapOp != null) fieldCount++
  if (counterOp != null) fieldCount++
  if (map != null) fieldCount++
  if (counter != null) fieldCount++
  if (nonce != null) fieldCount++
  if (initialValue != null) fieldCount++

  packer.packMapHeader(fieldCount)

  packer.packString("action")
  packer.packInt(action.code)

  // Always include objectId as per Objects protocol
  packer.packString("objectId")
  packer.packString(objectId)

  if (mapOp != null) {
    packer.packString("mapOp")
    mapOp.writeMsgpack(packer)
  }

  if (counterOp != null) {
    packer.packString("counterOp")
    counterOp.writeMsgpack(packer)
  }

  if (map != null) {
    packer.packString("map")
    map.writeMsgpack(packer)
  }

  if (counter != null) {
    packer.packString("counter")
    counter.writeMsgpack(packer)
  }

  if (nonce != null) {
    packer.packString("nonce")
    packer.packString(nonce)
  }

  if (initialValue != null) {
    packer.packString("initialValue")
    packer.packString(initialValue)
  }
}

/**
 * Read ObjectOperation from MessageUnpacker
 */
private fun readObjectOperation(unpacker: MessageUnpacker): ObjectOperation {
  val fieldCount = unpacker.unpackMapHeader()

  var action: ObjectOperationAction? = null
  var objectId: String = ""
  var mapOp: ObjectsMapOp? = null
  var counterOp: ObjectsCounterOp? = null
  var map: ObjectsMap? = null
  var counter: ObjectsCounter? = null
  var nonce: String? = null
  var initialValue: String? = null

  for (i in 0 until fieldCount) {
    val fieldName = unpacker.unpackString().intern()
    val fieldFormat = unpacker.nextFormat

    if (fieldFormat == MessageFormat.NIL) {
      unpacker.unpackNil()
      continue
    }

    when (fieldName) {
      "action" -> {
        val actionCode = unpacker.unpackInt()
        action = ObjectOperationAction.entries.firstOrNull { it.code == actionCode }
          ?: ObjectOperationAction.entries.firstOrNull { it.code == -1 }
          ?: throw objectError("Unknown ObjectOperationAction code: $actionCode and no Unknown fallback found")
      }
      "objectId" -> objectId = unpacker.unpackString()
      "mapOp" -> mapOp = readObjectMapOp(unpacker)
      "counterOp" -> counterOp = readObjectCounterOp(unpacker)
      "map" -> map = readObjectMap(unpacker)
      "counter" -> counter = readObjectCounter(unpacker)
      "nonce" -> nonce = unpacker.unpackString()
      "initialValue" -> initialValue = unpacker.unpackString()
      else -> unpacker.skipValue()
    }
  }

  if (action == null) {
    throw objectError("Missing required 'action' field in ObjectOperation")
  }

  return ObjectOperation(
    action = action,
    objectId = objectId,
    mapOp = mapOp,
    counterOp = counterOp,
    map = map,
    counter = counter,
    nonce = nonce,
    initialValue = initialValue,
  )
}

/**
 * Write ObjectState to MessagePacker
 */
private fun ObjectState.writeMsgpack(packer: MessagePacker) {
  var fieldCount = 3 // objectId, siteTimeserials, and tombstone are required

  if (createOp != null) fieldCount++
  if (map != null) fieldCount++
  if (counter != null) fieldCount++

  packer.packMapHeader(fieldCount)

  packer.packString("objectId")
  packer.packString(objectId)

  packer.packString("siteTimeserials")
  packer.packMapHeader(siteTimeserials.size)
  for ((key, value) in siteTimeserials) {
    packer.packString(key)
    packer.packString(value)
  }

  packer.packString("tombstone")
  packer.packBoolean(tombstone)

  if (createOp != null) {
    packer.packString("createOp")
    createOp.writeMsgpack(packer)
  }

  if (map != null) {
    packer.packString("map")
    map.writeMsgpack(packer)
  }

  if (counter != null) {
    packer.packString("counter")
    counter.writeMsgpack(packer)
  }
}

/**
 * Read ObjectState from MessageUnpacker
 */
private fun readObjectState(unpacker: MessageUnpacker): ObjectState {
  val fieldCount = unpacker.unpackMapHeader()

  var objectId = ""
  var siteTimeserials = mapOf<String, String>()
  var tombstone = false
  var createOp: ObjectOperation? = null
  var map: ObjectsMap? = null
  var counter: ObjectsCounter? = null

  for (i in 0 until fieldCount) {
    val fieldName = unpacker.unpackString().intern()
    val fieldFormat = unpacker.nextFormat

    if (fieldFormat == MessageFormat.NIL) {
      unpacker.unpackNil()
      continue
    }

    when (fieldName) {
      "objectId" -> objectId = unpacker.unpackString()
      "siteTimeserials" -> {
        val mapSize = unpacker.unpackMapHeader()
        val tempMap = mutableMapOf<String, String>()
        for (j in 0 until mapSize) {
          val key = unpacker.unpackString()
          val value = unpacker.unpackString()
          tempMap[key] = value
        }
        siteTimeserials = tempMap
      }
      "tombstone" -> tombstone = unpacker.unpackBoolean()
      "createOp" -> createOp = readObjectOperation(unpacker)
      "map" -> map = readObjectMap(unpacker)
      "counter" -> counter = readObjectCounter(unpacker)
      else -> unpacker.skipValue()
    }
  }

  return ObjectState(
    objectId = objectId,
    siteTimeserials = siteTimeserials,
    tombstone = tombstone,
    createOp = createOp,
    map = map,
    counter = counter
  )
}

/**
 * Write ObjectMapOp to MessagePacker
 */
private fun ObjectsMapOp.writeMsgpack(packer: MessagePacker) {
  var fieldCount = 1 // key is required

  if (data != null) fieldCount++

  packer.packMapHeader(fieldCount)

  packer.packString("key")
  packer.packString(key)

  if (data != null) {
    packer.packString("data")
    data.writeMsgpack(packer)
  }
}

/**
 * Read ObjectMapOp from MessageUnpacker
 */
private fun readObjectMapOp(unpacker: MessageUnpacker): ObjectsMapOp {
  val fieldCount = unpacker.unpackMapHeader()

  var key = ""
  var data: ObjectData? = null

  for (i in 0 until fieldCount) {
    val fieldName = unpacker.unpackString().intern()
    val fieldFormat = unpacker.nextFormat

    if (fieldFormat == MessageFormat.NIL) {
      unpacker.unpackNil()
      continue
    }

    when (fieldName) {
      "key" -> key = unpacker.unpackString()
      "data" -> data = readObjectData(unpacker)
      else -> unpacker.skipValue()
    }
  }

  return ObjectsMapOp(key = key, data = data)
}

/**
 * Write ObjectCounterOp to MessagePacker
 */
private fun ObjectsCounterOp.writeMsgpack(packer: MessagePacker) {
  var fieldCount = 0

  if (amount != null) fieldCount++

  packer.packMapHeader(fieldCount)

  if (amount != null) {
    packer.packString("amount")
    packer.packDouble(amount)
  }
}

/**
 * Read ObjectCounterOp from MessageUnpacker
 */
private fun readObjectCounterOp(unpacker: MessageUnpacker): ObjectsCounterOp {
  val fieldCount = unpacker.unpackMapHeader()

  var amount: Double? = null

  for (i in 0 until fieldCount) {
    val fieldName = unpacker.unpackString().intern()
    val fieldFormat = unpacker.nextFormat

    if (fieldFormat == MessageFormat.NIL) {
      unpacker.unpackNil()
      continue
    }

    when (fieldName) {
      "amount" -> amount = unpacker.unpackDouble()
      else -> unpacker.skipValue()
    }
  }

  return ObjectsCounterOp(amount = amount)
}

/**
 * Write ObjectMap to MessagePacker
 */
private fun ObjectsMap.writeMsgpack(packer: MessagePacker) {
  var fieldCount = 0

  if (semantics != null) fieldCount++
  if (entries != null) fieldCount++

  packer.packMapHeader(fieldCount)

  if (semantics != null) {
    packer.packString("semantics")
    packer.packInt(semantics.code)
  }

  if (entries != null) {
    packer.packString("entries")
    packer.packMapHeader(entries.size)
    for ((key, value) in entries) {
      packer.packString(key)
      value.writeMsgpack(packer)
    }
  }
}

/**
 * Read ObjectMap from MessageUnpacker
 */
private fun readObjectMap(unpacker: MessageUnpacker): ObjectsMap {
  val fieldCount = unpacker.unpackMapHeader()

  var semantics: ObjectsMapSemantics? = null
  var entries: Map<String, ObjectsMapEntry>? = null

  for (i in 0 until fieldCount) {
    val fieldName = unpacker.unpackString().intern()
    val fieldFormat = unpacker.nextFormat

    if (fieldFormat == MessageFormat.NIL) {
      unpacker.unpackNil()
      continue
    }

    when (fieldName) {
      "semantics" -> {
        val semanticsCode = unpacker.unpackInt()
        semantics = ObjectsMapSemantics.entries.firstOrNull { it.code == semanticsCode }
          ?: ObjectsMapSemantics.entries.firstOrNull { it.code == -1 }
          ?: throw objectError("Unknown MapSemantics code: $semanticsCode and no UNKNOWN fallback found")
      }
      "entries" -> {
        val mapSize = unpacker.unpackMapHeader()
        val tempMap = mutableMapOf<String, ObjectsMapEntry>()
        for (j in 0 until mapSize) {
          val key = unpacker.unpackString()
          val value = readObjectMapEntry(unpacker)
          tempMap[key] = value
        }
        entries = tempMap
      }
      else -> unpacker.skipValue()
    }
  }

  return ObjectsMap(semantics = semantics, entries = entries)
}

/**
 * Write ObjectCounter to MessagePacker
 */
private fun ObjectsCounter.writeMsgpack(packer: MessagePacker) {
  var fieldCount = 0

  if (count != null) fieldCount++

  packer.packMapHeader(fieldCount)

  if (count != null) {
    packer.packString("count")
    packer.packDouble(count)
  }
}

/**
 * Read ObjectCounter from MessageUnpacker
 */
private fun readObjectCounter(unpacker: MessageUnpacker): ObjectsCounter {
  val fieldCount = unpacker.unpackMapHeader()

  var count: Double? = null

  for (i in 0 until fieldCount) {
    val fieldName = unpacker.unpackString().intern()
    val fieldFormat = unpacker.nextFormat

    if (fieldFormat == MessageFormat.NIL) {
      unpacker.unpackNil()
      continue
    }

    when (fieldName) {
      "count" -> count = unpacker.unpackDouble()
      else -> unpacker.skipValue()
    }
  }

  return ObjectsCounter(count = count)
}

/**
 * Write ObjectMapEntry to MessagePacker
 */
private fun ObjectsMapEntry.writeMsgpack(packer: MessagePacker) {
  var fieldCount = 0

  if (tombstone != null) fieldCount++
  if (timeserial != null) fieldCount++
  if (serialTimestamp != null) fieldCount++
  if (data != null) fieldCount++

  packer.packMapHeader(fieldCount)

  if (tombstone != null) {
    packer.packString("tombstone")
    packer.packBoolean(tombstone)
  }

  if (timeserial != null) {
    packer.packString("timeserial")
    packer.packString(timeserial)
  }

  if (serialTimestamp != null) {
    packer.packString("serialTimestamp")
    packer.packLong(serialTimestamp)
  }

  if (data != null) {
    packer.packString("data")
    data.writeMsgpack(packer)
  }
}

/**
 * Read ObjectMapEntry from MessageUnpacker
 */
private fun readObjectMapEntry(unpacker: MessageUnpacker): ObjectsMapEntry {
  val fieldCount = unpacker.unpackMapHeader()

  var tombstone: Boolean? = null
  var timeserial: String? = null
  var serialTimestamp: Long? = null
  var data: ObjectData? = null

  for (i in 0 until fieldCount) {
    val fieldName = unpacker.unpackString().intern()
    val fieldFormat = unpacker.nextFormat

    if (fieldFormat == MessageFormat.NIL) {
      unpacker.unpackNil()
      continue
    }

    when (fieldName) {
      "tombstone" -> tombstone = unpacker.unpackBoolean()
      "timeserial" -> timeserial = unpacker.unpackString()
      "serialTimestamp" -> serialTimestamp = unpacker.unpackLong()
      "data" -> data = readObjectData(unpacker)
      else -> unpacker.skipValue()
    }
  }

  return ObjectsMapEntry(tombstone = tombstone, timeserial = timeserial, serialTimestamp = serialTimestamp, data = data)
}

/**
 * Write ObjectData to MessagePacker
 */
private fun ObjectData.writeMsgpack(packer: MessagePacker) {
  var fieldCount = 0

  if (objectId != null) fieldCount++
  value?.let {
    fieldCount++
  }

  packer.packMapHeader(fieldCount)

  if (objectId != null) {
    packer.packString("objectId")
    packer.packString(objectId)
  }

  value?.let { v ->
    when (v) {
      is ObjectValue.Boolean -> {
        packer.packString("boolean")
        packer.packBoolean(v.value)
      }
      is ObjectValue.String -> {
        packer.packString("string")
        packer.packString(v.value)
      }
      is ObjectValue.Number -> {
        packer.packString("number")
        packer.packDouble(v.value.toDouble())
      }
      is ObjectValue.Binary -> {
        packer.packString("bytes")
        packer.packBinaryHeader(v.value.data.size)
        packer.writePayload(v.value.data)
      }
      is ObjectValue.JsonObject -> {
        packer.packString("json")
        packer.packString(v.value.toString())
      }
      is ObjectValue.JsonArray -> {
        packer.packString("json")
        packer.packString(v.value.toString())
      }
    }
  }
}

/**
 * Read ObjectData from MessageUnpacker
 */
private fun readObjectData(unpacker: MessageUnpacker): ObjectData {
  val fieldCount = unpacker.unpackMapHeader()
  var objectId: String? = null
  var value: ObjectValue? = null

  for (i in 0 until fieldCount) {
    val fieldName = unpacker.unpackString().intern()
    val fieldFormat = unpacker.nextFormat

    if (fieldFormat == MessageFormat.NIL) {
      unpacker.unpackNil()
      continue
    }

    when (fieldName) {
      "objectId" -> objectId = unpacker.unpackString()
      "boolean" -> value = ObjectValue.Boolean(unpacker.unpackBoolean())
      "string" -> value = ObjectValue.String(unpacker.unpackString())
      "number" -> value = ObjectValue.Number(unpacker.unpackDouble())
      "bytes" -> {
        val size = unpacker.unpackBinaryHeader()
        val bytes = ByteArray(size)
        unpacker.readPayload(bytes)
        value = ObjectValue.Binary(Binary(bytes))
      }
      "json" -> {
        val jsonString = unpacker.unpackString()
        val parsed = JsonParser.parseString(jsonString)
        value = when {
          parsed.isJsonObject -> ObjectValue.JsonObject(parsed.asJsonObject)
          parsed.isJsonArray -> ObjectValue.JsonArray(parsed.asJsonArray)
          else ->
            throw ablyException("Invalid JSON string for json field", ErrorCode.MapValueDataTypeUnsupported, HttpStatusCode.InternalServerError)
        }
      }
      else -> unpacker.skipValue()
    }
  }

  return ObjectData(objectId = objectId, value = value)
}
