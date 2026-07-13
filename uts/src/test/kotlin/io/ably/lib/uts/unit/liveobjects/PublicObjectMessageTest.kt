package io.ably.lib.uts.unit.liveobjects

import com.google.gson.JsonObject
import io.ably.lib.liveobjects.message.ObjectMessage
import io.ably.lib.liveobjects.message.ObjectOperationAction
import io.ably.lib.liveobjects.message.ObjectsMapSemantics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Derived from UTS `objects/unit/public_object_message.md` (PAOM1–3, PAOOP1–3) — construction of the
 * public-facing `ObjectMessage` (PAOM3) and `ObjectOperation` (PAOOP3) from a source wire object message.
 *
 * Pure data-structure construction, no mocks. The spec's `PublicObjectMessage.fromObjectMessage(source,
 * channel)` / `PublicObjectOperation.fromObjectOperation(op)` (PAOM3 / PAOOP3) are `internal` in
 * `:liveobjects`; [buildPublicObjectMessage] (in `Helpers.kt`) reaches them by reflection, so the source is
 * built with the wire op builders (`buildMapSet`, `buildCounterInc`, …) and the public getters are asserted
 * on the result. Spec `op.action == "MAP_SET"` (string tag) becomes the `ObjectOperationAction` enum
 * constant; `op.mapCreate == null` becomes `assertNull`; getters read as Kotlin properties.
 *
 * PR #499 renamed the retained-create field `_derivedFrom` → `derivedFrom` (local-only per RTLCV4g5 /
 * RTLMV4j5, not a wire field). PAOOP1: the public `ObjectOperation` carries only the resolved
 * `mapCreate`/`counterCreate` (no `*WithObjectId` getter); PAOOP3b2/c2 resolution from the derived create is
 * verified via [publicMessageWithDerivedCreate].
 */
class PublicObjectMessageTest {

    /**
     * @UTS objects/unit/PAOM3/construction-all-fields-0
     */
    @Test
    fun `PAOM3 - construction copies all fields from source ObjectMessage`() {
        val extras = JsonObject().apply { addProperty("key", "value") }
        // MAP_SET operation + every optional top-level field. The op builders cover serial/siteCode/the
        // operation; the remaining top-level fields aren't builder params, so augment the wire JSON directly.
        val source = buildMapSet("map:abc@1000", "name", dataString("Alice"), serial = "01", siteCode = "site1").apply {
            addProperty("id", "msg-id-1")
            addProperty("clientId", "client-1")
            addProperty("connectionId", "conn-1")
            addProperty("timestamp", 1700000000000L)
            addProperty("serialTimestamp", 1700000001000L)
            add("extras", extras)
        }

        val publicMsg = buildPublicObjectMessage(source, "test-channel")

        assertEquals("msg-id-1", publicMsg.id)
        assertEquals("client-1", publicMsg.clientId)
        assertEquals("conn-1", publicMsg.connectionId)
        assertEquals(1700000000000L, publicMsg.timestamp)
        assertEquals("test-channel", publicMsg.channel)
        assertEquals("01", publicMsg.serial)
        assertEquals(1700000001000L, publicMsg.serialTimestamp)
        assertEquals("site1", publicMsg.siteCode)
        assertEquals(extras, publicMsg.extras)
        assertNotNull(publicMsg.operation)
        assertEquals(ObjectOperationAction.MAP_SET, publicMsg.operation.action)
        assertEquals("map:abc@1000", publicMsg.operation.objectId)
        assertEquals("name", publicMsg.operation.mapSet!!.key)
    }

    /**
     * @UTS objects/unit/PAOM3/construction-optional-fields-missing-0
     */
    @Test
    fun `PAOM3 - construction with optional fields missing`() {
        // Only the required operation present; all optional top-level fields absent.
        val source = buildCounterInc("counter:abc@1000", 5)

        val publicMsg = buildPublicObjectMessage(source, "my-channel")

        assertNull(publicMsg.id)
        assertNull(publicMsg.clientId)
        assertNull(publicMsg.connectionId)
        assertNull(publicMsg.timestamp)
        assertEquals("my-channel", publicMsg.channel)
        assertNull(publicMsg.serial)
        assertNull(publicMsg.serialTimestamp)
        assertNull(publicMsg.siteCode)
        assertNull(publicMsg.extras)
        assertNotNull(publicMsg.operation)
        assertEquals(ObjectOperationAction.COUNTER_INC, publicMsg.operation.action)
    }

    /**
     * @UTS objects/unit/PAOM3/channel-from-channel-name-0
     */
    @Test
    fun `PAOM3b - channel is set from channel name not from ObjectMessage`() {
        val source = buildObjectDelete("counter:abc@1000")

        val publicMsg = buildPublicObjectMessage(source, "different-channel-name")

        assertEquals("different-channel-name", publicMsg.channel)
    }

    /**
     * @UTS objects/unit/PAOOP3/map-set-copies-fields-0
     */
    @Test
    fun `PAOOP3a - MAP_SET operation copies mapSet, omits unrelated fields`() {
        val source = buildMapSet("map:abc@1000", "color", dataString("blue"))

        val op = buildPublicObjectMessage(source, "test-channel").operation

        assertEquals(ObjectOperationAction.MAP_SET, op.action)
        assertEquals("map:abc@1000", op.objectId)
        assertEquals("color", op.mapSet!!.key)
        assertEquals("blue", op.mapSet!!.value.string)
        assertNull(op.mapCreate)
        assertNull(op.mapRemove)
        assertNull(op.counterCreate)
        assertNull(op.counterInc)
        assertNull(op.objectDelete)
        assertNull(op.mapClear)
    }

    /**
     * @UTS objects/unit/PAOOP3/map-remove-copies-fields-0
     */
    @Test
    fun `PAOOP3a - MAP_REMOVE operation copies mapRemove, omits unrelated fields`() {
        val source = buildMapRemove("map:abc@1000", "old-key")

        val op = buildPublicObjectMessage(source, "test-channel").operation

        assertEquals(ObjectOperationAction.MAP_REMOVE, op.action)
        assertEquals("map:abc@1000", op.objectId)
        assertEquals("old-key", op.mapRemove!!.key)
        assertNull(op.mapCreate)
        assertNull(op.mapSet)
        assertNull(op.counterCreate)
        assertNull(op.counterInc)
        assertNull(op.objectDelete)
        assertNull(op.mapClear)
    }

    /**
     * @UTS objects/unit/PAOOP3/counter-inc-copies-fields-0
     */
    @Test
    fun `PAOOP3a - COUNTER_INC operation copies counterInc, omits unrelated fields`() {
        val source = buildCounterInc("counter:abc@1000", 42)

        val op = buildPublicObjectMessage(source, "test-channel").operation

        assertEquals(ObjectOperationAction.COUNTER_INC, op.action)
        assertEquals("counter:abc@1000", op.objectId)
        assertEquals(42.0, op.counterInc!!.number)
        assertNull(op.mapCreate)
        assertNull(op.mapSet)
        assertNull(op.mapRemove)
        assertNull(op.counterCreate)
        assertNull(op.objectDelete)
        assertNull(op.mapClear)
    }

    /**
     * @UTS objects/unit/PAOOP3/object-delete-copies-fields-0
     */
    @Test
    fun `PAOOP3a - OBJECT_DELETE operation copies objectDelete, omits unrelated fields`() {
        // The spec's source carries `objectDelete: {}` (an empty marker). The `buildObjectDelete` helper
        // sets only action+objectId, so add the marker body here — otherwise the wire op's `objectDelete`
        // stays null and `getObjectDelete()` (which is `operation.objectDelete?.let { DefaultObjectDelete }`)
        // returns null. This mirrors the spec's source exactly.
        val source = buildObjectDelete("counter:abc@1000").apply {
            getAsJsonObject("operation").add("objectDelete", JsonObject())
        }

        val op = buildPublicObjectMessage(source, "test-channel").operation

        assertEquals(ObjectOperationAction.OBJECT_DELETE, op.action)
        assertEquals("counter:abc@1000", op.objectId)
        assertNotNull(op.objectDelete)
        assertNull(op.mapCreate)
        assertNull(op.mapSet)
        assertNull(op.mapRemove)
        assertNull(op.counterCreate)
        assertNull(op.counterInc)
        assertNull(op.mapClear)
    }

    /**
     * @UTS objects/unit/PAOOP3/map-clear-copies-fields-0
     */
    @Test
    fun `PAOOP3a - MAP_CLEAR operation copies mapClear, omits unrelated fields`() {
        // As OBJECT_DELETE above: the spec's source carries `mapClear: {}`; add the empty marker so the
        // wire op's `mapClear` is non-null and `getMapClear()` returns the DefaultMapClear marker.
        val source = buildMapClear("map:abc@1000").apply {
            getAsJsonObject("operation").add("mapClear", JsonObject())
        }

        val op = buildPublicObjectMessage(source, "test-channel").operation

        assertEquals(ObjectOperationAction.MAP_CLEAR, op.action)
        assertEquals("map:abc@1000", op.objectId)
        assertNotNull(op.mapClear)
        assertNull(op.mapCreate)
        assertNull(op.mapSet)
        assertNull(op.mapRemove)
        assertNull(op.counterCreate)
        assertNull(op.counterInc)
        assertNull(op.objectDelete)
    }

    /**
     * @UTS objects/unit/PAOOP3/map-create-direct-0
     */
    @Test
    fun `PAOOP3b1 - MAP_CREATE with mapCreate directly present`() {
        val source = buildMapCreate(
            "map:new@2000",
            mapState(linkedMapOf("key1" to mapEntry(dataString("val1")))),
        )

        val op = buildPublicObjectMessage(source, "test-channel").operation

        assertEquals(ObjectOperationAction.MAP_CREATE, op.action)
        assertEquals("map:new@2000", op.objectId)
        assertNotNull(op.mapCreate)
        assertEquals(ObjectsMapSemantics.LWW, op.mapCreate!!.semantics)
        assertEquals("val1", op.mapCreate!!.entries["key1"]!!.data!!.string)
        assertNull(op.counterCreate)
    }

    /**
     * @UTS objects/unit/PAOOP3/map-create-from-with-object-id-0
     */
    @Test
    fun `PAOOP3b2 - MAP_CREATE resolved from mapCreateWithObjectId`() {
        // The source carries mapCreateWithObjectId (no direct mapCreate); the public op must resolve
        // mapCreate from the MapCreate the WithObjectId variant was derived from. In ably-java that derived
        // form (WireMapCreateWithObjectId.derivedFrom) is @Transient/outbound-only and never arrives over the
        // wire, so it can't be carried by the wire-JSON helper — reconstruct it reflectively (see
        // publicMessageWithDerivedCreate).
        val withObjectId = JsonObject().apply {
            add(
                "operation",
                JsonObject().apply {
                    addProperty("action", 0) // MAP_CREATE wire code
                    addProperty("objectId", "map:derived@3000")
                    add(
                        "mapCreateWithObjectId",
                        JsonObject().apply {
                            addProperty("initialValue", "stub-initial-value")
                            addProperty("nonce", "stub-nonce")
                        },
                    )
                },
            )
        }
        val derived = buildMapCreate(
            "map:derived@3000",
            mapState(linkedMapOf("x" to mapEntry(dataNumber(10)))),
        )

        val op = publicMessageWithDerivedCreate(withObjectId, derived, "test-channel").operation

        assertEquals(ObjectOperationAction.MAP_CREATE, op.action)
        assertEquals("map:derived@3000", op.objectId)
        assertNotNull(op.mapCreate)
        assertEquals(ObjectsMapSemantics.LWW, op.mapCreate!!.semantics)
        assertEquals(10.0, op.mapCreate!!.entries["x"]!!.data!!.number)
        assertNull(op.counterCreate)
    }

    /**
     * @UTS objects/unit/PAOOP3/counter-create-from-with-object-id-0
     */
    @Test
    fun `PAOOP3c2 - COUNTER_CREATE resolved from counterCreateWithObjectId`() {
        // As PAOOP3b2 but for the counter variant — counterCreate resolved from the derived CounterCreate.
        val withObjectId = JsonObject().apply {
            add(
                "operation",
                JsonObject().apply {
                    addProperty("action", 3) // COUNTER_CREATE wire code
                    addProperty("objectId", "counter:derived@3000")
                    add(
                        "counterCreateWithObjectId",
                        JsonObject().apply {
                            addProperty("initialValue", "stub-initial-value")
                            addProperty("nonce", "stub-nonce")
                        },
                    )
                },
            )
        }
        val derived = buildCounterCreate("counter:derived@3000", counterState(100))

        val op = publicMessageWithDerivedCreate(withObjectId, derived, "test-channel").operation

        assertEquals(ObjectOperationAction.COUNTER_CREATE, op.action)
        assertEquals("counter:derived@3000", op.objectId)
        assertNotNull(op.counterCreate)
        assertEquals(100.0, op.counterCreate!!.count)
        assertNull(op.mapCreate)
    }

    /**
     * @UTS objects/unit/PAOOP3/create-payloads-omitted-0
     */
    @Test
    fun `PAOOP3b3, PAOOP3c3 - create payloads omitted when neither variant is present`() {
        val source = buildMapSet("map:abc@1000", "k", dataString("v"))

        val op = buildPublicObjectMessage(source, "test-channel").operation

        assertNull(op.mapCreate)
        assertNull(op.counterCreate)
    }

    /**
     * @UTS objects/unit/PAOOP3/only-relevant-field-per-action-0
     */
    @Test
    fun `PAOOP3 - only the relevant operation field is present per action type`() {
        val source = buildCounterCreate("counter:new@2000", counterState(50))

        val op = buildPublicObjectMessage(source, "test-channel").operation

        assertEquals(ObjectOperationAction.COUNTER_CREATE, op.action)
        assertEquals("counter:new@2000", op.objectId)
        assertNotNull(op.counterCreate)
        assertEquals(50.0, op.counterCreate!!.count)
        assertNull(op.mapCreate)
        assertNull(op.mapSet)
        assertNull(op.mapRemove)
        assertNull(op.counterInc)
        assertNull(op.objectDelete)
        assertNull(op.mapClear)
    }
}

/**
 * Builds a public [ObjectMessage] whose operation carries a `*CreateWithObjectId` variant resolved to its
 * derived `MapCreate` / `CounterCreate` (PAOOP3b2 / PAOOP3c2).
 *
 * Why this exists: `WireMapCreateWithObjectId.derivedFrom` / `WireCounterCreateWithObjectId.derivedFrom` are
 * `@Transient` — populated only when the SDK constructs an outbound create operation locally, never
 * deserialized from the wire. `buildPublicObjectMessage`'s wire-JSON path therefore cannot carry it. This
 * helper reconstructs it: it deserializes [withObjectIdMessage] to its internal `WireObjectMessage`,
 * manufactures the derived `WireMapCreate` / `WireCounterCreate` by deserializing [derivedCreateMessage]
 * (a normal direct-create message), grafts it onto the WithObjectId variant's `derivedFrom` field, then
 * builds the public message via the same `DefaultObjectMessage(WireObjectMessage, String)` constructor that
 * `buildPublicObjectMessage` uses. All access is by reflection because the wire/Default types are `internal`
 * to `:liveobjects` (runtime-only on the uts classpath).
 */
private fun publicMessageWithDerivedCreate(
    withObjectIdMessage: JsonObject,
    derivedCreateMessage: JsonObject,
    channelName: String,
): ObjectMessage {
    val serializationKt = Class.forName("io.ably.lib.liveobjects.serialization.JsonSerializationKt")
    val toWire = serializationKt.getMethod("toObjectMessage", JsonObject::class.java)
    val mainWire = toWire.invoke(null, withObjectIdMessage)
    val derivedWire = toWire.invoke(null, derivedCreateMessage)

    val operationField = mainWire.javaClass.getDeclaredField("operation").apply { isAccessible = true }
    val mainOp = operationField.get(mainWire)
    val derivedOp = operationField.get(derivedWire)

    fun graft(withObjectIdFieldName: String, derivedCreateFieldName: String) {
        val withObjectId = mainOp.javaClass.getDeclaredField(withObjectIdFieldName)
            .apply { isAccessible = true }.get(mainOp) ?: return
        val derivedCreate = derivedOp.javaClass.getDeclaredField(derivedCreateFieldName)
            .apply { isAccessible = true }.get(derivedOp)
        withObjectId.javaClass.getDeclaredField("derivedFrom")
            .apply { isAccessible = true }.set(withObjectId, derivedCreate)
    }
    graft("mapCreateWithObjectId", "mapCreate")
    graft("counterCreateWithObjectId", "counterCreate")

    val wireClass = Class.forName("io.ably.lib.liveobjects.message.WireObjectMessage")
    return Class.forName("io.ably.lib.liveobjects.message.DefaultObjectMessage")
        .getConstructor(wireClass, String::class.java)
        .newInstance(mainWire, channelName) as ObjectMessage
}
