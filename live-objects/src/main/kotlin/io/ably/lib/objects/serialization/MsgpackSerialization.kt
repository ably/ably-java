package io.ably.lib.objects.serialization

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ably.lib.objects.Binary
import io.ably.lib.objects.MapSemantics
import io.ably.lib.objects.ObjectCounter
import io.ably.lib.objects.ObjectCounterOp
import io.ably.lib.objects.ObjectData
import io.ably.lib.objects.ObjectMap
import io.ably.lib.objects.ObjectMapEntry
import io.ably.lib.objects.ObjectMapOp
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
    siteCode = siteCode
  )
}

/**
 * Write ObjectOperation to MessagePacker
 */
private fun ObjectOperation.writeMsgpack(packer: MessagePacker) {
  var fieldCount = 1 // action is always required
  require(objectId.isNotEmpty()) { "objectId must be non-empty per LiveObjects protocol" }
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

  // Always include objectId as per LiveObjects protocol
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
  var mapOp: ObjectMapOp? = null
  var counterOp: ObjectCounterOp? = null
  var map: ObjectMap? = null
  var counter: ObjectCounter? = null
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
        action = ObjectOperationAction.entries.find { it.code == actionCode }
          ?: throw IllegalArgumentException("Unknown ObjectOperationAction code: $actionCode")
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
    throw IllegalArgumentException("Missing required 'action' field in ObjectOperation")
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
  var map: ObjectMap? = null
  var counter: ObjectCounter? = null

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
private fun ObjectMapOp.writeMsgpack(packer: MessagePacker) {
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
private fun readObjectMapOp(unpacker: MessageUnpacker): ObjectMapOp {
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

  return ObjectMapOp(key = key, data = data)
}

/**
 * Write ObjectCounterOp to MessagePacker
 */
private fun ObjectCounterOp.writeMsgpack(packer: MessagePacker) {
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
private fun readObjectCounterOp(unpacker: MessageUnpacker): ObjectCounterOp {
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

  return ObjectCounterOp(amount = amount)
}

/**
 * Write ObjectMap to MessagePacker
 */
private fun ObjectMap.writeMsgpack(packer: MessagePacker) {
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
private fun readObjectMap(unpacker: MessageUnpacker): ObjectMap {
  val fieldCount = unpacker.unpackMapHeader()

  var semantics: MapSemantics? = null
  var entries: Map<String, ObjectMapEntry>? = null

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
        semantics = MapSemantics.entries.find { it.code == semanticsCode }
          ?: throw IllegalArgumentException("Unknown MapSemantics code: $semanticsCode")
      }
      "entries" -> {
        val mapSize = unpacker.unpackMapHeader()
        val tempMap = mutableMapOf<String, ObjectMapEntry>()
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

  return ObjectMap(semantics = semantics, entries = entries)
}

/**
 * Write ObjectCounter to MessagePacker
 */
private fun ObjectCounter.writeMsgpack(packer: MessagePacker) {
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
private fun readObjectCounter(unpacker: MessageUnpacker): ObjectCounter {
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

  return ObjectCounter(count = count)
}

/**
 * Write ObjectMapEntry to MessagePacker
 */
private fun ObjectMapEntry.writeMsgpack(packer: MessagePacker) {
  var fieldCount = 0

  if (tombstone != null) fieldCount++
  if (timeserial != null) fieldCount++
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

  if (data != null) {
    packer.packString("data")
    data.writeMsgpack(packer)
  }
}

/**
 * Read ObjectMapEntry from MessageUnpacker
 */
private fun readObjectMapEntry(unpacker: MessageUnpacker): ObjectMapEntry {
  val fieldCount = unpacker.unpackMapHeader()

  var tombstone: Boolean? = null
  var timeserial: String? = null
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
      "data" -> data = readObjectData(unpacker)
      else -> unpacker.skipValue()
    }
  }

  return ObjectMapEntry(tombstone = tombstone, timeserial = timeserial, data = data)
}

/**
 * Write ObjectData to MessagePacker
 */
private fun ObjectData.writeMsgpack(packer: MessagePacker) {
  var fieldCount = 0

  if (objectId != null) fieldCount++
  value?.let {
    fieldCount++
    if (it.value is JsonElement) {
      fieldCount += 1 // For extra "encoding" field
    }
  }

  packer.packMapHeader(fieldCount)

  if (objectId != null) {
    packer.packString("objectId")
    packer.packString(objectId)
  }

  if (value != null) {
    when (val v = value.value) {
      is Boolean -> {
        packer.packString("boolean")
        packer.packBoolean(v)
      }
      is String -> {
        packer.packString("string")
        packer.packString(v)
      }
      is Number -> {
        packer.packString("number")
        packer.packDouble(v.toDouble())
      }
      is Binary -> {
        packer.packString("bytes")
        packer.packBinaryHeader(v.data.size)
        packer.writePayload(v.data)
      }
      is JsonObject, is JsonArray -> {
        packer.packString("string")
        packer.packString(v.toString())
        packer.packString("encoding")
        packer.packString("json")
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
  var encoding: String? = null
  var stringValue: String? = null

  for (i in 0 until fieldCount) {
    val fieldName = unpacker.unpackString().intern()
    val fieldFormat = unpacker.nextFormat

    if (fieldFormat == MessageFormat.NIL) {
      unpacker.unpackNil()
      continue
    }

    when (fieldName) {
      "objectId" -> objectId = unpacker.unpackString()
      "boolean" -> value = ObjectValue(unpacker.unpackBoolean())
      "string" -> stringValue = unpacker.unpackString()
      "number" -> value = ObjectValue(unpacker.unpackDouble())
      "bytes" -> {
        val size = unpacker.unpackBinaryHeader()
        val bytes = ByteArray(size)
        unpacker.readPayload(bytes)
        value = ObjectValue(Binary(bytes))
      }
      "encoding" -> encoding = unpacker.unpackString()
      else -> unpacker.skipValue()
    }
  }

  // Handle string with encoding if needed
  if (stringValue != null && encoding == "json") {
    val parsed = JsonParser.parseString(stringValue)
    value = ObjectValue(
      when {
        parsed.isJsonObject -> parsed.asJsonObject
        parsed.isJsonArray -> parsed.asJsonArray
        else -> throw IllegalArgumentException("Invalid JSON string for encoding=json")
      }
    )
  } else if (stringValue != null) {
    value = ObjectValue(stringValue)
  }

  return ObjectData(objectId = objectId, value = value)
}
