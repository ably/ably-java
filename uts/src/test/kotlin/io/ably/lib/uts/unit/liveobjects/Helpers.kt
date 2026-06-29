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
 *  - The builders construct the **wire JSON** form (Gson [JsonObject]) of object messages and drop them
 *    into [ProtocolMessage.state] (`Object[]`); the file compiles against `:java` only (no *compile-time*
 *    `:liveobjects` dependency).
 *  - [buildPublicObjectMessage] reaches the implemented message/operation layer in `:liveobjects` by
 *    reflection (`testRuntimeOnly(project(":liveobjects"))` in build.gradle.kts), so PAOM3/PAOOP3
 *    construction tests run today.
 *  - [setupSyncedChannel] drives CONNECTED -> ATTACH/ATTACHED(HAS_OBJECTS) -> OBJECT_SYNC over the existing
 *    [MockWebSocket], then awaits `channel.object.get()`. That last step needs the SDK's OBJECT_SYNC
 *    processing + `RealtimeObject.get()`, both still TODO — so the generated tests **compile** now and
 *    become **runnable** once the SDK lands (translate-only until then).
 */

// ---------------------------------------------------------------------------
// small Gson DSL
// ---------------------------------------------------------------------------

private fun json(build: JsonObject.() -> Unit): JsonObject = JsonObject().apply(build)
private fun JsonObject.str(key: String, value: String) = addProperty(key, value)
private fun JsonObject.num(key: String, value: Number) = addProperty(key, value)
private fun JsonObject.bool(key: String, value: Boolean) = addProperty(key, value)

// ---------------------------------------------------------------------------
// ObjectData (leaf value) wire builders — the `data` of a map entry / mapSet
// ---------------------------------------------------------------------------

fun dataString(value: String): JsonObject = json { str("string", value) }
fun dataNumber(value: Number): JsonObject = json { num("number", value) }
fun dataBoolean(value: Boolean): JsonObject = json { bool("boolean", value) }
fun dataObjectId(objectId: String): JsonObject = json { str("objectId", objectId) }
fun dataBytes(base64: String): JsonObject = json { str("bytes", base64) }
fun dataJson(element: JsonElement): JsonObject = json { add("json", element) }

// ---------------------------------------------------------------------------
// map / counter state + createOp fragments
// ---------------------------------------------------------------------------

fun mapEntry(data: JsonObject, timeserial: String = "t:0", tombstone: Boolean? = null): JsonObject = json {
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
            createOp?.let { add("createOp", it) }
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

private fun List<JsonObject>.asState(): Array<Any?> = Array(size) { this[it] }

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

private val SITE = mapOf("aaa" to "t:0")

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
    buildObjectState("counter:score@1000", SITE, counter = counterState(100), createOp = counterCreateOp(100)),
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
    buildObjectState("counter:nested@1000", SITE, counter = counterState(5), createOp = counterCreateOp(5)),
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
                        siteCode = "test-site"
                        objectsGCGracePeriod = 86_400_000L
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
                    val serials = (msg.state?.indices ?: IntRange.EMPTY).map { "ack-${msg.msgSerial}:$it" }
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
    // NOTE: throws until :liveobjects implements RealtimeObject.get() + OBJECT_SYNC processing.
    val root = channel.`object`.get().await()
    return SyncedChannel(client, channel, root, mockWs)
}
