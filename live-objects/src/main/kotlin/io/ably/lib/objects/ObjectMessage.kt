package io.ably.lib.objects

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
 * Spec: MAP2
 */
internal enum class MapSemantics(val code: Int) {
  LWW(0);
}

/**
 * An ObjectData represents a value in an object on a channel.
 * Spec: OD1
 */
internal data class ObjectData(
  /**
   * A reference to another object, used to support composable object structures.
   * Spec: OD2a
   */
  val objectId: String? = null,

  /**
   * Can be set by the client to indicate that value in `string` or `bytes` field have an encoding.
   * Spec: OD2b
   */
  val encoding: String? = null,

  /**
   * String, number, boolean or binary - a concrete value of the object
   * Spec: OD2c
   */
  val value: Any? = null,
)

/**
 * A MapOp describes an operation to be applied to a Map object.
 * Spec: MOP1
 */
internal data class MapOp(
  /**
   * The key of the map entry to which the operation should be applied.
   * Spec: MOP2a
   */
  val key: String,

  /**
   * The data that the map entry should contain if the operation is a MAP_SET operation.
   * Spec: MOP2b
   */
  val data: ObjectData? = null
)

/**
 * A CounterOp describes an operation to be applied to a Counter object.
 * Spec: COP1
 */
internal data class CounterOp(
  /**
   * The data value that should be added to the counter
   * Spec: COP2a
   */
  val amount: Double
)

/**
 * A MapEntry represents the value at a given key in a Map object.
 * Spec: ME1
 */
internal data class MapEntry(
  /**
   * Indicates whether the map entry has been removed.
   * Spec: ME2a
   */
  val tombstone: Boolean? = null,

  /**
   * The serial value of the last operation that was applied to the map entry.
   * It is optional in a MAP_CREATE operation and might be missing, in which case the client should use a nullish value for it
   * and treat it as the "earliest possible" serial for comparison purposes.
   * Spec: ME2b
   */
  val timeserial: String? = null,

  /**
   * The data that represents the value of the map entry.
   * Spec: ME2c
   */
  val data: ObjectData? = null
)

/**
 * An ObjectMap object represents a map of key-value pairs.
 * Spec: MAP1
 */
internal data class ObjectMap(
  /**
   * The conflict-resolution semantics used by the map object.
   * Spec: MAP3a
   */
  val semantics: MapSemantics? = null,

  /**
   * The map entries, indexed by key.
   * Spec: MAP3b
   */
  val entries: Map<String, MapEntry>? = null
)

/**
 * An ObjectCounter object represents an incrementable and decrementable value
 * Spec: CNT1
 */
internal data class ObjectCounter(
  /**
   * The value of the counter
   * Spec: CNT2a
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
  val mapOp: MapOp? = null,

  /**
   * The payload for the operation if it is an operation on a Counter object type.
   * Spec: OOP3d
   */
  val counterOp: CounterOp? = null,

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
   * The initial value bytes for the object. These bytes should be used along with the nonce
   * and timestamp to create the object ID. Frontdoor will use this to verify the object ID.
   * After verification the bytes will be decoded into the Map or Counter objects and
   * the initialValue, nonce, and initialValueEncoding will be removed.
   * Spec: OOP3h
   */
  val initialValue: Binary? = null,

  /** The initial value encoding defines how the initialValue should be interpreted.
   * Spec: OOP3i
   */
  val initialValueEncoding: ProtocolMessageFormat? = null
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
  val extras: Any? = null,

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
