package io.ably.lib.objects

import com.google.gson.JsonElement
import com.google.gson.JsonObject

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import io.ably.lib.objects.serialization.ObjectDataJsonSerializer
import io.ably.lib.objects.serialization.gson
import java.util.Base64

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
  ObjectDelete(5),
  Unknown(-1); // code for unknown value during deserialization
}

/**
 * An enum class representing the conflict-resolution semantics used by a Map object.
 * Spec: OMP2
 */
internal enum class ObjectsMapSemantics(val code: Int) {
  LWW(0),
  Unknown(-1); // code for unknown value during deserialization
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

  /** String value. Spec: OD2c */
  val string: String? = null,

  /** Numeric value. Spec: OD2c */
  val number: Double? = null,

  /** Boolean value. Spec: OD2c */
  val boolean: Boolean? = null,

  /** Binary value encoded as a base64 string. Spec: OD2c */
  val bytes: String? = null,

  /** JSON object or array value. Spec: OD2c */
  val json: JsonElement? = null,
)

/**
 * Payload for MAP_CREATE operation.
 * Spec: MCR*
 */
internal data class MapCreate(
  val semantics: ObjectsMapSemantics, // MCR2a
  val entries: Map<String, ObjectsMapEntry> // MCR2b
)

/**
 * Payload for MAP_SET operation.
 * Spec: MST*
 */
internal data class MapSet(
  val key: String, // MST2a
  val value: ObjectData // MST2b - REQUIRED
)

/**
 * Payload for MAP_REMOVE operation.
 * Spec: MRM*
 */
internal data class MapRemove(
  val key: String // MRM2a
)

/**
 * Payload for COUNTER_CREATE operation.
 * Spec: CCR*
 */
internal data class CounterCreate(
  val count: Double // CCR2a - REQUIRED
)

/**
 * Payload for COUNTER_INC operation.
 * Spec: CIN*
 */
internal data class CounterInc(
  val number: Double // CIN2a - REQUIRED
)

/**
 * Payload for OBJECT_DELETE operation.
 * Spec: ODE*
 * No fields - action is sufficient
 */
internal object ObjectDelete

/**
 * Payload for MAP_CREATE_WITH_OBJECT_ID operation.
 * Spec: MCRO*
 */
internal data class MapCreateWithObjectId(
  val initialValue: String, // MCRO2a
  val nonce: String // MCRO2b
)

/**
 * Payload for COUNTER_CREATE_WITH_OBJECT_ID operation.
 * Spec: CCRO*
 */
internal data class CounterCreateWithObjectId(
  val initialValue: String, // CCRO2a
  val nonce: String // CCRO2b
)

/**
 * A MapEntry represents the value at a given key in a Map object.
 * Spec: ME1
 */
internal data class ObjectsMapEntry(
  /**
   * Indicates whether the map entry has been removed.
   * Spec: OME2a
   */
  val tombstone: Boolean? = null,

  /**
   * The serial value of the latest operation that was applied to the map entry.
   * It is optional in a MAP_CREATE operation and might be missing, in which case the client should use a null value for it
   * and treat it as the "earliest possible" serial for comparison purposes.
   * Spec: OME2b
   */
  val timeserial: String? = null,

  /**
   * A timestamp from the [timeserial] field. Only present if [tombstone] is `true`
   * Spec: OME2d
   */
  val serialTimestamp: Long? = null,

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
internal data class ObjectsMap(
  /**
   * The conflict-resolution semantics used by the map object.
   * Spec: OMP3a
   */
  val semantics: ObjectsMapSemantics? = null,

  /**
   * The map entries, indexed by key.
   * Spec: OMP3b
   */
  val entries: Map<String, ObjectsMapEntry>? = null
)

/**
 * An ObjectCounter object represents an incrementable and decrementable value
 * Spec: OCN1
 */
internal data class ObjectsCounter(
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
   * Payload for MAP_CREATE operation.
   * Spec: OOP3j
   */
  val mapCreate: MapCreate? = null,

  /**
   * Payload for MAP_SET operation.
   * Spec: OOP3k
   */
  val mapSet: MapSet? = null,

  /**
   * Payload for MAP_REMOVE operation.
   * Spec: OOP3l
   */
  val mapRemove: MapRemove? = null,

  /**
   * Payload for COUNTER_CREATE operation.
   * Spec: OOP3m
   */
  val counterCreate: CounterCreate? = null,

  /**
   * Payload for COUNTER_INC operation.
   * Spec: OOP3n
   */
  val counterInc: CounterInc? = null,

  /**
   * Payload for OBJECT_DELETE operation.
   * Spec: OOP3o
   */
  val objectDelete: ObjectDelete? = null,

  /**
   * Payload for MAP_CREATE_WITH_OBJECT_ID operation.
   * Spec: OOP3p
   */
  val mapCreateWithObjectId: MapCreateWithObjectId? = null,

  /**
   * Payload for COUNTER_CREATE_WITH_OBJECT_ID operation.
   * Spec: OOP3q
   */
  val counterCreateWithObjectId: CounterCreateWithObjectId? = null,
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
  val map: ObjectsMap? = null,

  /**
   * The data that represents the result of applying all operations to a Counter object
   * excluding the initial value from the create operation if it is a Counter object type.
   * Spec: OST2f
   */
  val counter: ObjectsCounter? = null
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
   * A timestamp from the [serial] field.
   * Spec: OM2j
   */
  val serialTimestamp: Long? = null,

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
  val clientIdSize = clientId?.byteSize ?: 0 // Spec: OM3f
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
  val mapCreateSize = mapCreate?.size() ?: 0
  val mapSetSize = mapSet?.size() ?: 0
  val mapRemoveSize = mapRemove?.size() ?: 0
  val counterCreateSize = counterCreate?.size() ?: 0
  val counterIncSize = counterInc?.size() ?: 0
  val mapCreateWithObjectIdSize = mapCreateWithObjectId?.size() ?: 0
  val counterCreateWithObjectIdSize = counterCreateWithObjectId?.size() ?: 0

  return mapCreateSize + mapSetSize + mapRemoveSize +
    counterCreateSize + counterIncSize +
    mapCreateWithObjectIdSize + counterCreateWithObjectIdSize
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
 * Calculates the size of a MapCreate payload in bytes.
 */
private fun MapCreate.size(): Int {
  return entries.entries.sumOf { it.key.byteSize + it.value.size() }
}

/**
 * Calculates the size of a MapSet payload in bytes.
 */
private fun MapSet.size(): Int {
  return key.byteSize + value.size()
}

/**
 * Calculates the size of a MapRemove payload in bytes.
 */
private fun MapRemove.size(): Int {
  return key.byteSize
}

/**
 * Calculates the size of a CounterCreate payload in bytes.
 */
private fun CounterCreate.size(): Int {
  return 8 // Double is 8 bytes
}

/**
 * Calculates the size of a CounterInc payload in bytes.
 */
private fun CounterInc.size(): Int {
  return 8 // Double is 8 bytes
}

/**
 * Calculates the size of a MapCreateWithObjectId payload in bytes.
 */
private fun MapCreateWithObjectId.size(): Int {
  return initialValue.byteSize + nonce.byteSize
}

/**
 * Calculates the size of a CounterCreateWithObjectId payload in bytes.
 */
private fun CounterCreateWithObjectId.size(): Int {
  return initialValue.byteSize + nonce.byteSize
}

/**
 * Calculates the size of an ObjectMap in bytes.
 * Spec: OMP4
 */
private fun ObjectsMap.size(): Int {
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
private fun ObjectsCounter.size(): Int {
  // Size is 8 if count is a number, 0 if count is null or omitted
  return if (count != null) 8 else 0
}

/**
 * Calculates the size of a MapEntry in bytes.
 * Spec: OME3
 */
private fun ObjectsMapEntry.size(): Int {
  // The size is equal to the size of the data property, calculated per "OD3"
  return data?.size() ?: 0
}

/**
 * Calculates the size of an ObjectData in bytes.
 * Spec: OD3
 */
private fun ObjectData.size(): Int {
  string?.let { return it.byteSize } // Spec: OD3e
  number?.let { return 8 } // Spec: OD3d
  boolean?.let { return 1 } // Spec: OD3b
  bytes?.let { return Base64.getDecoder().decode(it).size } // Spec: OD3c
  json?.let { return it.toString().byteSize } // Spec: OD3e
  return 0
}

internal fun ObjectData?.isInvalid(): Boolean {
  return this?.objectId.isNullOrEmpty() &&
    this?.string == null &&
    this?.number == null &&
    this?.boolean == null &&
    this?.bytes == null &&
    this?.json == null
}
