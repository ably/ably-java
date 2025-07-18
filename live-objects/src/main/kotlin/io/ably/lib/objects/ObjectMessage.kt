package io.ably.lib.objects

import com.google.gson.JsonArray
import com.google.gson.JsonObject

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import io.ably.lib.objects.serialization.ObjectDataJsonSerializer
import io.ably.lib.objects.serialization.gson

/**
 * An enum class representing the different actions that can be performed on an object.
 * Spec: OOP2
 */
internal enum class ObjectOperationAction(val code: Int) {
  MapCreate(0),
  MapSet(1),
  MapRemove(2),
  CounterCreate(3),
  CounterInc(4),
  ObjectDelete(5);
}

/**
 * An enum class representing the conflict-resolution semantics used by a Map object.
 * Spec: OMP2
 */
internal enum class MapSemantics(val code: Int) {
  LWW(0);
}

/**
 * An ObjectData represents a value in an object on a channel.
 * Spec: OD1
 */
@JsonAdapter(ObjectDataJsonSerializer::class)
internal data class ObjectData(
  /**
   * A reference to another object, used to support composable object structures.
   * Spec: OD2a
   */
  val objectId: String? = null,

  /**
   * String, number, boolean or binary - a concrete value of the object
   * Spec: OD2c
   */
  val value: ObjectValue? = null,
)

/**
 * Represents a value that can be a String, Number, Boolean, Binary, JsonObject or JsonArray.
 * Performs a type check on initialization.
 * Spec: OD2c
 */
internal data class ObjectValue(
  /**
   * The concrete value of the object. Can be a String, Number, Boolean, Binary, JsonObject or JsonArray.
   * Spec: OD2c
   */
  val value: Any,
) {
  init {
    require(
      value is String ||
        value is Number ||
        value is Boolean ||
        value is Binary ||
        value is JsonObject ||
        value is JsonArray
    ) {
      "value must be String, Number, Boolean, Binary, JsonObject or JsonArray"
    }
  }
}

/**
 * A MapOp describes an operation to be applied to a Map object.
 * Spec: OMO1
 */
internal data class ObjectMapOp(
  /**
   * The key of the map entry to which the operation should be applied.
   * Spec: OMO2a
   */
  val key: String,

  /**
   * The data that the map entry should contain if the operation is a MAP_SET operation.
   * Spec: OMO2b
   */
  val data: ObjectData? = null
)

/**
 * A CounterOp describes an operation to be applied to a Counter object.
 * Spec: OCO1
 */
internal data class ObjectCounterOp(
  /**
   * The data value that should be added to the counter
   * Spec: OCO2a
   */
  val amount: Double? = null
)

/**
 * A MapEntry represents the value at a given key in a Map object.
 * Spec: ME1
 */
internal data class ObjectMapEntry(
  /**
   * Indicates whether the map entry has been removed.
   * Spec: OME2a
   */
  val tombstone: Boolean? = null,

  /**
   * The serial value of the last operation that was applied to the map entry.
   * It is optional in a MAP_CREATE operation and might be missing, in which case the client should use a nullish value for it
   * and treat it as the "earliest possible" serial for comparison purposes.
   * Spec: OME2b
   */
  val timeserial: String? = null,

  /**
   * The data that represents the value of the map entry.
   * Spec: OME2c
   */
  val data: ObjectData? = null
)

/**
 * An ObjectMap object represents a map of key-value pairs.
 * Spec: OMP1
 */
internal data class ObjectMap(
  /**
   * The conflict-resolution semantics used by the map object.
   * Spec: OMP3a
   */
  val semantics: MapSemantics? = null,

  /**
   * The map entries, indexed by key.
   * Spec: OMP3b
   */
  val entries: Map<String, ObjectMapEntry>? = null
)

/**
 * An ObjectCounter object represents an incrementable and decrementable value
 * Spec: OCN1
 */
internal data class ObjectCounter(
  /**
   * The value of the counter
   * Spec: OCN2a
   */
  val count: Double? = null
)

/**
 * An ObjectOperation describes an operation to be applied to an object on a channel.
 * Spec: OOP1
 */
internal data class ObjectOperation(
  /**
   * Defines the operation to be applied to the object.
   * Spec: OOP3a
   */
  val action: ObjectOperationAction,

  /**
   * The object ID of the object on a channel to which the operation should be applied.
   * Spec: OOP3b
   */
  val objectId: String,

  /**
   * The payload for the operation if it is an operation on a Map object type.
   * Spec: OOP3c
   */
  val mapOp: ObjectMapOp? = null,

  /**
   * The payload for the operation if it is an operation on a Counter object type.
   * Spec: OOP3d
   */
  val counterOp: ObjectCounterOp? = null,

  /**
   * The payload for the operation if the operation is MAP_CREATE.
   * Defines the initial value for the Map object.
   * Spec: OOP3e
   */
  val map: ObjectMap? = null,

  /**
   * The payload for the operation if the operation is COUNTER_CREATE.
   * Defines the initial value for the Counter object.
   * Spec: OOP3f
   */
  val counter: ObjectCounter? = null,

  /**
   * The nonce, must be present on create operations. This is the random part
   * that has been hashed with the type and initial value to create the object ID.
   * Spec: OOP3g
   */
  val nonce: String? = null,

  /**
   * The initial value json string for the object. This value should be used along with the nonce
   * and timestamp to create the object ID. Frontdoor will use this to verify the object ID.
   * After verification the json string will be decoded into the Map or Counter objects and
   * the initialValue and nonce will be removed.
   * Spec: OOP3h
   */
  val initialValue: String? = null,
)

/**
 * An ObjectState describes the instantaneous state of an object on a channel.
 * Spec: OST1
 */
internal data class ObjectState(
  /**
   * The identifier of the object.
   * Spec: OST2a
   */
  val objectId: String,

  /**
   * A map of serials keyed by a {@link ObjectMessage.siteCode},
   * representing the last operations applied to this object
   * Spec: OST2b
   */
  val siteTimeserials: Map<String, String>,

  /**
   * True if the object has been tombstoned.
   * Spec: OST2c
   */
  val tombstone: Boolean,

  /**
   * The operation that created the object.
   * Can be missing if create operation for the object is not known at this point.
   * Spec: OST2d
   */
  val createOp: ObjectOperation? = null,

  /**
   * The data that represents the result of applying all operations to a Map object
   * excluding the initial value from the create operation if it is a Map object type.
   * Spec: OST2e
   */
  val map: ObjectMap? = null,

  /**
   * The data that represents the result of applying all operations to a Counter object
   * excluding the initial value from the create operation if it is a Counter object type.
   * Spec: OST2f
   */
  val counter: ObjectCounter? = null
)

/**
 * An @ObjectMessage@ represents an individual object message to be sent or received via the Ably Realtime service.
 * Spec: OM1
 */
internal data class ObjectMessage(
  /**
   * unique ID for this object message. This attribute is always populated for object messages received over REST.
   * For object messages received over Realtime, if the object message does not contain an @id@,
   * it should be set to @protocolMsgId:index@, where @protocolMsgId@ is the id of the @ProtocolMessage@ encapsulating it,
   * and @index@ is the index of the object message inside the @state@ array of the @ProtocolMessage@
   * Spec: OM2a
   */
  val id: String? = null,

  /**
   * time in milliseconds since epoch. If an object message received from Ably does not contain a @timestamp@,
   * it should be set to the @timestamp@ of the encapsulating @ProtocolMessage@
   * Spec: OM2e
   */
  val timestamp: Long? = null,

  /**
   * Spec: OM2b
   */
  val clientId: String? = null,

  /**
   * If an object message received from Ably does not contain a @connectionId@,
   * it should be set to the @connectionId@ of the encapsulating @ProtocolMessage@
   * Spec: OM2c
   */
  val connectionId: String? = null,

  /**
   * JSON-encodable object, used to contain any arbitrary key value pairs which may also contain other primitive JSON types,
   * JSON-encodable objects or JSON-encodable arrays. The @extras@ field is provided to contain message metadata and/or
   * ancillary payloads in support of specific functionality. For 3.1 no specific functionality is specified for
   * @extras@ in object messages. Unless otherwise specified, the client library should not attempt to do any filtering
   * or validation of the @extras@ field itself, but should treat it opaquely, encoding it and passing it to realtime unaltered
   * Spec: OM2d
   */
  val extras: JsonObject? = null,

  /**
   * Describes an operation to be applied to an object.
   * Mutually exclusive with the `object` field. This field is only set on object messages if the `action` field of the
   * `ProtocolMessage` encapsulating it is `OBJECT`.
   * Spec: OM2f
   */
  val operation: ObjectOperation? = null,

  /**
   * Describes the instantaneous state of an object.
   * Mutually exclusive with the `operation` field. This field is only set on object messages if the `action` field of
   * the `ProtocolMessage` encapsulating it is `OBJECT_SYNC`.
   * Spec: OM2g
   */
  @SerializedName("object")
  val objectState: ObjectState? = null,

  /**
   * An opaque string that uniquely identifies this object message.
   * Spec: OM2h
   */
  val serial: String? = null,

  /**
   * An opaque string used as a key to update the map of serial values on an object.
   * Spec: OM2i
   */
  val siteCode: String? = null
)

/**
 * Calculates the size of an ObjectMessage in bytes.
 * Spec: OM3
 */
internal fun ObjectMessage.size(): Int {
  val clientIdSize = clientId?.length ?: 0 // Spec: OM3f
  val operationSize = operation?.size() ?: 0 // Spec: OM3b, OOP4
  val objectStateSize = objectState?.size() ?: 0 // Spec: OM3c, OST3
  val extrasSize = extras?.let { gson.toJson(it).length } ?: 0 // Spec: OM3d

  return clientIdSize + operationSize + objectStateSize + extrasSize
}

/**
 * Calculates the size of an ObjectOperation in bytes.
 * Spec: OOP4
 */
private fun ObjectOperation.size(): Int {
  val mapOpSize = mapOp?.size() ?: 0 // Spec: OOP4b, OMO3
  val counterOpSize = counterOp?.size() ?: 0 // Spec: OOP4c, OCO3
  val mapSize = map?.size() ?: 0 // Spec: OOP4d, OMP4
  val counterSize = counter?.size() ?: 0 // Spec: OOP4e, OCN3

  return mapOpSize + counterOpSize + mapSize + counterSize
}

/**
 * Calculates the size of an ObjectState in bytes.
 * Spec: OST3
 */
private fun ObjectState.size(): Int {
  val mapSize = map?.size() ?: 0 // Spec: OST3b, OMP4
  val counterSize = counter?.size() ?: 0 // Spec: OST3c, OCN3
  val createOpSize = createOp?.size() ?: 0 // Spec: OST3d, OOP4

  return mapSize + counterSize + createOpSize
}

/**
 * Calculates the size of an ObjectMapOp in bytes.
 * Spec: OMO3
 */
private fun ObjectMapOp.size(): Int {
  val keySize = key.length // Spec: OMO3d - Size of the key
  val dataSize = data?.size() ?: 0 // Spec: OMO3b - Size of the data, calculated per "OD3"
  return keySize + dataSize
}

/**
 * Calculates the size of a CounterOp in bytes.
 * Spec: OCO3
 */
private fun ObjectCounterOp.size(): Int {
  // Size is 8 if amount is a number, 0 if amount is null or omitted
  return if (amount != null) 8 else 0 // Spec: OCO3a, OCO3b
}

/**
 * Calculates the size of an ObjectMap in bytes.
 * Spec: OMP4
 */
private fun ObjectMap.size(): Int {
  // Calculate the size of all map entries in the map property
  val entriesSize = entries?.entries?.sumOf {
    it.key.length + it.value.size() // // Spec: OMP4a1, OMP4a2
  } ?: 0

  return entriesSize
}

/**
 * Calculates the size of an ObjectCounter in bytes.
 * Spec: OCN3
 */
private fun ObjectCounter.size(): Int {
  // Size is 8 if count is a number, 0 if count is null or omitted
  return if (count != null) 8 else 0
}

/**
 * Calculates the size of a MapEntry in bytes.
 * Spec: OME3
 */
private fun ObjectMapEntry.size(): Int {
  // The size is equal to the size of the data property, calculated per "OD3"
  return data?.size() ?: 0
}

/**
 * Calculates the size of an ObjectData in bytes.
 * Spec: OD3
 */
private fun ObjectData.size(): Int {
  return value?.size() ?: 0 // Spec: OD3f
}

/**
 * Calculates the size of an ObjectValue in bytes.
 * Spec: OD3*
 */
private fun ObjectValue.size(): Int {
  return when (value) {
    is Boolean -> 1 // Spec: OD3b
    is Binary -> value.size() // Spec: OD3c
    is Number -> 8 // Spec: OD3d
    is String -> value.byteSize // Spec: OD3e
    is JsonObject, is JsonArray -> value.toString().byteSize // Spec: OD3e
    else -> 0  // Spec: OD3f
  }
}
