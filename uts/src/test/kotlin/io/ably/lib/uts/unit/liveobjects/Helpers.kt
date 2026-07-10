package io.ably.lib.uts.unit.liveobjects

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ably.lib.liveobjects.message.ObjectMessage
import io.ably.lib.liveobjects.path.types.LiveMapPathObject
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.Channel
import io.ably.lib.types.ChannelMode
import io.ably.lib.types.ChannelOptions
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.types.PublishResult
import io.ably.lib.uts.infra.unit.ConnectionDetails
import io.ably.lib.uts.infra.unit.MockWebSocket
import io.ably.lib.uts.infra.unit.TestRealtimeClient
import kotlinx.coroutines.future.await

/**
 * LiveObjects unit-test helpers — the ably-java translation of the UTS
 * `objects/helpers/standard_test_pool.md` (standard test pool, protocol-message /
 * object-message builders, and the synced-channel setup) used by every objects
 * unit spec.
 *
 * Status:
 *  - The builders construct the **wire JSON** form (Gson [JsonObject]) of each object message, then convert
 *    it to the SDK's internal `WireObjectMessage` (reflectively, via `JsonSerializationKt.toObjectMessage`)
 *    before placing it in [ProtocolMessage.state] (`Object[]`). The conversion is required because the SDK's
 *    `@JsonAdapter(ObjectJsonSerializer)` outbound path casts each `state` element to `WireObjectMessage`;
 *    raw `JsonObject`s would throw `ClassCastException` when the mock serializes the frame. The file still
 *    compiles against `:java` only (the reflection targets the runtime-only `:liveobjects` dependency).
 *  - [buildPublicObjectMessage] reaches the implemented message/operation layer in `:liveobjects` by
 *    reflection (`testRuntimeOnly(project(":liveobjects"))` in build.gradle.kts).
 *  - [setupSyncedChannel] drives CONNECTED -> ATTACH/ATTACHED(HAS_OBJECTS) -> OBJECT_SYNC over the existing
 *    [MockWebSocket], then awaits `channel.object.get()` — which now resolves against the implemented SDK
 *    engine (OBJECT_SYNC processing + `RealtimeObject.get()`), so the generated tests run.
 */

// ---------------------------------------------------------------------------
// small Gson DSL
// ---------------------------------------------------------------------------

private fun json(build: JsonObject.() -> Unit): JsonObject = JsonObject().apply(build)
private fun JsonObject.str(key: String, value: String) = addProperty(key, value)
private fun JsonObject.num(key: String, value: Number) = addProperty(key, value)
private fun JsonObject.bool(key: String, value: Boolean) = addProperty(key, value)

// ---------------------------------------------------------------------------
// Canonical constants (standard_test_pool.md "Canonical Constants")
// ---------------------------------------------------------------------------

/** The harness `ConnectionDetails` siteCode delivered on CONNECT. */
const val SITE_CODE: String = "test-site"

/**
 * The fixed apply-on-ACK serial scheme: `ack_serial(msgSerial, i) == "t:${msgSerial + 1}:$i"`, so the
 * first publish's first op is `t:1:0`. The value must sort AFTER the standard pool's `t:0` entry
 * timeserials under string LWW comparison (RTLM9) — otherwise locally applied MAP_SETs on existing pool
 * entries would be rejected as stale. Replay tests that reuse the apply-on-ACK serial MUST reference this.
 */
fun ackSerial(msgSerial: Long?, i: Int): String = "t:${(msgSerial ?: 0) + 1}:$i"

/**
 * Baseline timeserial every standard-pool entry/object is seeded with; every synthetic serial is chosen
 * relative to this under lexicographic string LWW comparison (RTLM9e).
 */
const val POOL_SERIAL: String = "t:0"

/**
 * A REMOTE inbound MAP_SET / MAP_REMOVE serial on an EXISTING pool entry: it sorts after [POOL_SERIAL], so it
 * wins the per-entry LWW comparison (RTLM9e). A bare number like `"99"` sorts BEFORE `"t:0"` and would be
 * rejected as stale. 0-based → `remoteSerial(0) == "t:1"`, `remoteSerial(1) == "t:2"`. (Counter increments and
 * other object-level ops from a fresh siteCode compare per-site, not per-entry, so they apply regardless of
 * serial value and need NOT use this.)
 */
fun remoteSerial(i: Int): String = "t:${i + 1}"

/**
 * A serial that is NOT an [ackSerial] (so it escapes the RTO9a3 apply-on-ACK echo dedup) yet sorts BELOW the
 * first ackSerial (`ackSerial(0, 0) == "t:1:0"`), while still after [POOL_SERIAL]. Used by RTO20f to prove a
 * LOCAL apply-on-ACK left siteTimeserials untouched (RTLC7c): had it wrongly recorded
 * `siteTimeserials[SITE_CODE] = "t:1:0"`, this lower serial would be rejected by the per-site newness check.
 * 0-based → `belowAckSerial(9) == "t:0:9"`.
 */
fun belowAckSerial(i: Int): String = "t:0:$i"

// ---------------------------------------------------------------------------
// ObjectData (leaf value) wire builders — the `data` of a map entry / mapSet
// ---------------------------------------------------------------------------

fun dataString(value: String): JsonObject = json { str("string", value) }
fun dataNumber(value: Number): JsonObject = json { num("number", value) }
fun dataBoolean(value: Boolean): JsonObject = json { bool("boolean", value) }
fun dataObjectId(objectId: String): JsonObject = json { str("objectId", objectId) }
fun dataBytes(base64: String): JsonObject = json { str("bytes", base64) }
// Wire format OD4c5: the `json` leaf is a *stringified* JSON value, not a raw element. The SDK's
// WireObjectDataJsonSerializer reads it via `get("json").asString`, so it must be encoded as a string.
fun dataJson(element: JsonElement): JsonObject = json { str("json", element.toString()) }

// ---------------------------------------------------------------------------
// map / counter state + createOp fragments
// ---------------------------------------------------------------------------

fun mapEntry(data: JsonObject, timeserial: String = POOL_SERIAL, tombstone: Boolean? = null): JsonObject = json {
    add("data", data)
    str("timeserial", timeserial)
    tombstone?.let { bool("tombstone", it) }
}

/**
 * Wire enum codes — the objects JSON protocol carries `action` / `semantics` as integer codes
 * (`WireObjectOperationAction` / `WireObjectsMapSemantics`), not strings. The SDK's Gson decodes them by
 * code, so the builders must emit the code for messages to deserialize.
 */
private object Action {
    const val MAP_CREATE = 0
    const val MAP_SET = 1
    const val MAP_REMOVE = 2
    const val COUNTER_CREATE = 3
    const val COUNTER_INC = 4
    const val OBJECT_DELETE = 5
    const val MAP_CLEAR = 6
}
private const val SEMANTICS_LWW = 0

fun mapState(entries: Map<String, JsonObject>, semantics: Int = SEMANTICS_LWW): JsonObject = json {
    num("semantics", semantics)
    add("entries", json { entries.forEach { (k, v) -> add(k, v) } })
}

fun counterState(count: Number): JsonObject = json { num("count", count) }

fun mapCreateOp(semantics: Int = SEMANTICS_LWW, entries: Map<String, JsonObject> = emptyMap()): JsonObject =
    json { num("action", Action.MAP_CREATE); add("mapCreate", mapState(entries, semantics)) }

fun counterCreateOp(count: Number): JsonObject =
    json { num("action", Action.COUNTER_CREATE); add("counterCreate", json { num("count", count) }) }

// ---------------------------------------------------------------------------
// ObjectMessage builders — STATE (for OBJECT_SYNC) and OPERATIONS (for OBJECT)
// ---------------------------------------------------------------------------

/** `build_object_state` — an ObjectMessage wrapping an ObjectState in its `object` field. */
fun buildObjectState(
    objectId: String,
    siteTimeserials: Map<String, String>,
    map: JsonObject? = null,
    counter: JsonObject? = null,
    tombstone: Boolean? = null,
    createOp: JsonObject? = null,
): JsonObject = json {
    add(
        "object",
        json {
            str("objectId", objectId)
            add("siteTimeserials", json { siteTimeserials.forEach { (k, v) -> str(k, v) } })
            map?.let { add("map", it) }
            counter?.let { add("counter", it) }
            bool("tombstone", tombstone ?: false) // WireObjectState.tombstone is non-nullable
            // The createOp is a WireObjectOperation whose objectId must equal the object's id
            // (LiveMapManager.validate / validateObjectId). Inject it so the create validates.
            createOp?.let { op ->
                if (!op.has("objectId")) op.addProperty("objectId", objectId)
                add("createOp", op)
            }
        },
    )
}

/**
 * `build_object_message_with_state` — wraps an already-built ObjectState (the inner `object` payload) in
 * an ObjectMessage. [buildObjectState] builds the state and wraps it in one step; this is the wrap-only
 * form used where a bare ObjectState needs to become an ObjectMessage (e.g. `replaceData`).
 */
fun buildObjectMessageWithState(objectState: JsonObject): JsonObject = json { add("object", objectState) }

private fun objectMessage(
    serial: String?,
    siteCode: String?,
    serialTimestamp: Long? = null,
    operation: JsonObject,
): JsonObject = json {
    serial?.let { str("serial", it) }
    siteCode?.let { str("siteCode", it) }
    serialTimestamp?.let { num("serialTimestamp", it) }
    add("operation", operation)
}

fun buildCounterInc(objectId: String, number: Number, serial: String? = null, siteCode: String? = null): JsonObject =
    objectMessage(serial, siteCode, operation = json {
        num("action", Action.COUNTER_INC); str("objectId", objectId); add("counterInc", json { num("number", number) })
    })

fun buildMapSet(objectId: String, key: String, value: JsonObject, serial: String? = null, siteCode: String? = null): JsonObject =
    objectMessage(serial, siteCode, operation = json {
        num("action", Action.MAP_SET); str("objectId", objectId)
        add("mapSet", json { str("key", key); add("value", value) })
    })

fun buildMapRemove(objectId: String, key: String, serial: String? = null, siteCode: String? = null, serialTimestamp: Long? = null): JsonObject =
    objectMessage(serial, siteCode, serialTimestamp, operation = json {
        num("action", Action.MAP_REMOVE); str("objectId", objectId); add("mapRemove", json { str("key", key) })
    })

fun buildMapClear(objectId: String, serial: String? = null, siteCode: String? = null): JsonObject =
    objectMessage(serial, siteCode, operation = json {
        num("action", Action.MAP_CLEAR); str("objectId", objectId)
    })

fun buildObjectDelete(objectId: String, serial: String? = null, siteCode: String? = null, serialTimestamp: Long? = null): JsonObject =
    objectMessage(serial, siteCode, serialTimestamp, operation = json {
        num("action", Action.OBJECT_DELETE); str("objectId", objectId)
    })

fun buildCounterCreate(objectId: String, counterCreate: JsonObject, serial: String? = null, siteCode: String? = null): JsonObject =
    objectMessage(serial, siteCode, operation = json {
        num("action", Action.COUNTER_CREATE); str("objectId", objectId); add("counterCreate", counterCreate)
    })

fun buildMapCreate(objectId: String, mapCreate: JsonObject, serial: String? = null, siteCode: String? = null): JsonObject =
    objectMessage(serial, siteCode, operation = json {
        num("action", Action.MAP_CREATE); str("objectId", objectId); add("mapCreate", mapCreate)
    })

// ---------------------------------------------------------------------------
// ProtocolMessage builders
// ---------------------------------------------------------------------------

/**
 * The SDK carries object messages in [ProtocolMessage.state] as an `Object[]` of internal
 * `WireObjectMessage` instances (its `@JsonAdapter(ObjectJsonSerializer)` outbound path casts each
 * element to `WireObjectMessage`). Our builders produce the **wire JSON** ([JsonObject]) form, so we
 * must convert each to a `WireObjectMessage` before placing it in `state` — otherwise the mock's
 * outbound serialization (`Serialisation.gson.toJson`) throws `ClassCastException` and the frame is
 * never delivered. We reach the internal `JsonSerializationKt.toObjectMessage(JsonObject)` by
 * reflection (same runtime-only technique as [buildPublicObjectMessage]).
 */
private val toWireObjectMessageMethod by lazy {
    Class.forName("io.ably.lib.liveobjects.serialization.JsonSerializationKt")
        .getMethod("toObjectMessage", JsonObject::class.java)
}

private fun JsonObject.toWireObjectMessage(): Any =
    toWireObjectMessageMethod.invoke(null, this) ?: error("toObjectMessage returned null for $this")

private fun List<JsonObject>.asState(): Array<Any?> = Array(size) { this[it].toWireObjectMessage() }

fun buildObjectSyncMessage(channel: String, channelSerial: String, objectMessages: List<JsonObject>): ProtocolMessage =
    ProtocolMessage(ProtocolMessage.Action.object_sync).apply {
        this.channel = channel
        this.channelSerial = channelSerial
        state = objectMessages.asState()
    }

fun buildObjectMessage(channel: String, objectMessages: List<JsonObject>): ProtocolMessage =
    ProtocolMessage(ProtocolMessage.Action.`object`).apply {
        this.channel = channel
        state = objectMessages.asState()
    }

fun buildAckMessage(msgSerial: Long?, serials: List<String>): ProtocolMessage =
    ProtocolMessage(ProtocolMessage.Action.ack).apply {
        this.msgSerial = msgSerial
        // `count` is the number of protocol messages acknowledged starting at msgSerial (one OBJECT
        // publish per ACK here). ConnectionManager.PendingMessageQueue.ack acks `subList(0, count)`, so
        // an unset count (0) would acknowledge nothing and the publish future would hang. The single
        // acked message's PublishResult (res[0]) carries the per-object serials.
        count = 1
        res = arrayOf(PublishResult(serials.toTypedArray()))
    }

/**
 * `build_public_object_message` — constructs a public [ObjectMessage] (PAOM3) from the wire form of an
 * object message (as produced by the operation builders above) and a channel name.
 *
 * ably-java's public `ObjectMessage` / `ObjectOperation` are getter-only interfaces with no public factory
 * — the construction (`WireObjectMessage` -> `DefaultObjectMessage`) lives `internal` in `:liveobjects`.
 * We reach it by **reflection**, in the same spirit as `infra/unit/Utils.kt` (which reflectively reaches an
 * inaccessible `:java` member) — but here the classes are on the *runtime-only* classpath
 * (`testRuntimeOnly(project(":liveobjects"))`), so we load them with `Class.forName` rather than flipping
 * `isAccessible`. The targeted members compile to plain `public` on the JVM (Kotlin `internal` is not
 * name-mangled here), so they are addressable by their declared names:
 *   - `JsonSerializationKt.toObjectMessage(JsonObject): WireObjectMessage` (Gson, decodes enum codes)
 *   - `DefaultObjectMessage(WireObjectMessage, String)`
 */
fun buildPublicObjectMessage(objectMessage: JsonObject, channelName: String): ObjectMessage {
    val serializationKt = Class.forName("io.ably.lib.liveobjects.serialization.JsonSerializationKt")
    val toWire = serializationKt.getMethod("toObjectMessage", JsonObject::class.java)
    val wire = toWire.invoke(null, objectMessage)

    val wireClass = Class.forName("io.ably.lib.liveobjects.message.WireObjectMessage")
    val defaultMessage = Class.forName("io.ably.lib.liveobjects.message.DefaultObjectMessage")
        .getConstructor(wireClass, String::class.java)
        .newInstance(wire, channelName)
    return defaultMessage as ObjectMessage
}

// `provision_objects_via_rest(...)` is intentionally not here — it's REST fixture provisioning for
// *integration* tests and belongs in the integration infra, not this unit helper file.

// ---------------------------------------------------------------------------
// STANDARD_POOL_OBJECTS — the fixed tree shared by all objects unit specs
// ---------------------------------------------------------------------------

private val SITE = mapOf("aaa" to POOL_SERIAL)

val STANDARD_POOL_OBJECTS: List<JsonObject> = listOf(
    buildObjectState(
        "root", SITE,
        map = mapState(
            linkedMapOf(
                "name" to mapEntry(dataString("Alice")),
                "age" to mapEntry(dataNumber(30)),
                "active" to mapEntry(dataBoolean(true)),
                "score" to mapEntry(dataObjectId("counter:score@1000")),
                "profile" to mapEntry(dataObjectId("map:profile@1000")),
                "data" to mapEntry(dataJson(JsonParser.parseString("""{"tags":["a","b"]}"""))),
                "avatar" to mapEntry(dataBytes("AQID")),
            ),
        ),
        createOp = mapCreateOp(),
    ),
    // Matches standard_test_pool.md: this counter's object-state carries the *post-create residual*
    // count (0), with the initial value on the createOp. Counter sync is additive (RTLC6c+RTLC6d/RTLC16
    // → data = count + createOp.count; the spec's own RTLC6/replace-data-with-create-op asserts 100+50=150),
    // so 0 + 100 = 100 with createOperationIsMerged==true, as every consumer asserts.
    // (Was UTS spec issue SI-1: the spec previously declared count:100 AND createOp:100, materialising 200;
    // fixed upstream in standard_test_pool.md to count:0 — see uts/SPEC_ISSUES.md.)
    buildObjectState("counter:score@1000", SITE, counter = counterState(0), createOp = counterCreateOp(100)),
    buildObjectState(
        "map:profile@1000", SITE,
        map = mapState(
            linkedMapOf(
                "email" to mapEntry(dataString("alice@example.com")),
                "nested_counter" to mapEntry(dataObjectId("counter:nested@1000")),
                "prefs" to mapEntry(dataObjectId("map:prefs@1000")),
            ),
        ),
        createOp = mapCreateOp(),
    ),
    // Matches standard_test_pool.md: residual count 0, the initial 5 carried by the createOp
    // (0 + 5 = 5, merged=true). (Was SI-1: spec previously had count:5 + createOp:5 → 10; fixed
    // upstream to count:0. See uts/SPEC_ISSUES.md.)
    buildObjectState("counter:nested@1000", SITE, counter = counterState(0), createOp = counterCreateOp(5)),
    buildObjectState(
        "map:prefs@1000", SITE,
        map = mapState(linkedMapOf("theme" to mapEntry(dataString("dark")))),
        createOp = mapCreateOp(),
    ),
)

// ---------------------------------------------------------------------------
// synced-channel setup
// ---------------------------------------------------------------------------

/** Result of [setupSyncedChannel] — the spec's `{ client, channel, root, mock_ws }`. */
data class SyncedChannel(
    val client: AblyRealtime,
    val channel: Channel,
    val root: LiveMapPathObject,
    val mockWs: MockWebSocket,
)

/** `setup_synced_channel` — connected client + channel synced with [STANDARD_POOL_OBJECTS]; auto-ACKs OBJECT publishes. */
suspend fun setupSyncedChannel(channelName: String): SyncedChannel = setup(channelName, autoAck = true)

/** `setup_synced_channel_no_ack` — as above but does not ACK OBJECT publishes (for tests that control ACK timing). */
suspend fun setupSyncedChannelNoAck(channelName: String): SyncedChannel = setup(channelName, autoAck = false)

private suspend fun setup(channelName: String, autoAck: Boolean): SyncedChannel {
    lateinit var mockWs: MockWebSocket
    mockWs = MockWebSocket {
        onConnectionAttempt = { conn ->
            conn.respondWithSuccess(
                ProtocolMessage(ProtocolMessage.Action.connected).apply {
                    connectionId = "conn-1"
                    connectionDetails = ConnectionDetails {
                        connectionKey = "conn-key-1"
                        siteCode = SITE_CODE
                        objectsGCGracePeriod = 86_400_000L
                        // Without an explicit maxMessageSize the field defaults to 0, which makes the
                        // SDK's RTO15d size check reject every OBJECT publish ("size N exceeds 0 bytes").
                        maxMessageSize = 65_536
                    }
                },
            )
        }
        onMessageFromClient = { msg ->
            when (msg.action) {
                ProtocolMessage.Action.attach -> {
                    mockWs.sendToClient(
                        ProtocolMessage(ProtocolMessage.Action.attached).apply {
                            channel = msg.channel
                            channelSerial = "sync1:"
                            setFlag(ProtocolMessage.Flag.has_objects)
                        },
                    )
                    mockWs.sendToClient(buildObjectSyncMessage(msg.channel, "sync1:", STANDARD_POOL_OBJECTS))
                }
                ProtocolMessage.Action.`object` -> if (autoAck) {
                    val serials = (msg.state?.indices ?: IntRange.EMPTY).map { ackSerial(msg.msgSerial, it) }
                    mockWs.sendToClient(buildAckMessage(msg.msgSerial, serials))
                }
                else -> Unit
            }
        }
    }

    val client = TestRealtimeClient {
        key = "fake:key"
        install(mockWs)
    }
    val channel = client.channels.get(
        channelName,
        ChannelOptions().apply { modes = arrayOf(ChannelMode.object_subscribe, ChannelMode.object_publish) },
    )
    val root = channel.`object`.get().await()
    return SyncedChannel(client, channel, root, mockWs)
}
