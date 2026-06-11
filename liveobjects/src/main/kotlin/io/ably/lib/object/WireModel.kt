package io.ably.lib.`object`

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Wire-level object model for the path-based public API implementation.
 *
 * Copied from the legacy internal model (`io.ably.lib.objects.ObjectMessage`)
 * so that this package has no dependency on `io.ably.lib.objects`. The `Wire`
 * prefix distinguishes these internal carriers from the public interfaces in
 * `io.ably.lib.object.message`.
 *
 * Spec: OM*, OOP*, OD*, MCR*, MST*, MRM*, CCR*, CIN*, ODE*, MCL*, OME*, MCRO*, CCRO*
 */

/** Spec: OOP2 */
internal enum class WireObjectOperationAction(val code: Int) {
  MapCreate(0),
  MapSet(1),
  MapRemove(2),
  CounterCreate(3),
  CounterInc(4),
  ObjectDelete(5),
  MapClear(6),
  Unknown(-1); // code for unknown value during deserialization
}

/** Spec: OMP2 */
internal enum class WireObjectsMapSemantics(val code: Int) {
  LWW(0),
  Unknown(-1); // code for unknown value during deserialization
}

/** Spec: OD1, OD2 - binary carried as base64 string on the wire */
internal data class WireObjectData(
  val objectId: String? = null, // OD2a
  val string: String? = null, // OD2f
  val number: Double? = null, // OD2e
  val boolean: Boolean? = null, // OD2c
  val bytes: String? = null, // OD2d - base64
  val json: JsonElement? = null, // decoded JSON leaf
)

/** Spec: MCR2 */
internal data class WireMapCreate(
  val semantics: WireObjectsMapSemantics, // MCR2a
  val entries: Map<String, WireObjectsMapEntry>, // MCR2b
)

/** Spec: MST2 */
internal data class WireMapSet(
  val key: String, // MST2a
  val value: WireObjectData, // MST2b
)

/** Spec: MRM2 */
internal data class WireMapRemove(
  val key: String, // MRM2a
)

/** Spec: CCR2 */
internal data class WireCounterCreate(
  val count: Double, // CCR2a
)

/** Spec: CIN2 */
internal data class WireCounterInc(
  val number: Double, // CIN2a
)

/** Spec: ODE2 - no attributes */
internal object WireObjectDelete

/** Spec: MCL2 - no attributes */
internal object WireMapClear

/** Spec: MCRO2 */
internal data class WireMapCreateWithObjectId(
  val initialValue: String, // MCRO2a
  val nonce: String, // MCRO2b
  @Transient val derivedFrom: WireMapCreate? = null, // RTLMV4j5 - local use only
)

/** Spec: CCRO2 */
internal data class WireCounterCreateWithObjectId(
  val initialValue: String, // CCRO2a
  val nonce: String, // CCRO2b
  @Transient val derivedFrom: WireCounterCreate? = null, // RTLCV4g5 - local use only
)

/** Spec: OME2 */
internal data class WireObjectsMapEntry(
  val tombstone: Boolean? = null, // OME2a
  val timeserial: String? = null, // OME2b
  val serialTimestamp: Long? = null, // OME2d
  val data: WireObjectData? = null, // OME2c
)

/** Spec: OOP3 */
internal data class WireObjectOperation(
  val action: WireObjectOperationAction, // OOP3a
  val objectId: String, // OOP3b
  val mapCreate: WireMapCreate? = null, // OOP3j
  val mapSet: WireMapSet? = null, // OOP3k
  val mapRemove: WireMapRemove? = null, // OOP3l
  val counterCreate: WireCounterCreate? = null, // OOP3m
  val counterInc: WireCounterInc? = null, // OOP3n
  val objectDelete: WireObjectDelete? = null, // OOP3o
  val mapCreateWithObjectId: WireMapCreateWithObjectId? = null, // OOP3p
  val counterCreateWithObjectId: WireCounterCreateWithObjectId? = null, // OOP3q
  val mapClear: WireMapClear? = null, // OOP3r
)

/** Spec: OM2 */
internal data class WireObjectMessage(
  val id: String? = null, // OM2a
  val timestamp: Long? = null, // OM2e
  val clientId: String? = null, // OM2b
  val connectionId: String? = null, // OM2c
  val extras: JsonObject? = null, // OM2d
  val operation: WireObjectOperation? = null, // OM2f
  val serial: String? = null, // OM2h
  val serialTimestamp: Long? = null, // OM2j
  val siteCode: String? = null, // OM2i
)
