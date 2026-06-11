package io.ably.lib.`object`.unit

import io.ably.lib.`object`.PathFinder
import io.ably.lib.`object`.ValueType
import io.ably.lib.`object`.WireMapSet
import io.ably.lib.`object`.WireObjectData
import io.ably.lib.`object`.WireObjectMessage
import io.ably.lib.`object`.WireObjectOperation
import io.ably.lib.`object`.WireObjectOperationAction
import io.ably.lib.`object`.path.DefaultBasePathObject
import io.ably.lib.`object`.path.DefaultLiveMapPathObject
import io.ably.lib.`object`.path.DefaultPathObject
import io.ably.lib.`object`.path.PathObjectSubscriptionEvent
import io.ably.lib.`object`.value.LiveMapValue
import io.ably.lib.types.AblyException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Covers path parsing/escaping (RTPO4/RTPO6), path resolution (RTPO3), typed
 * reads/writes, the depth coverage rule (RTO24c1/RTO24c2), PathFinder
 * (RTLO4f-equivalent) and subscription event delivery (RTPO19/RTINS16).
 */
internal class PathApiTest {

  /** root { profile: { name: "sachin", score: counter(5) }, flag: true } */
  private fun graphBridge(): FakeBridge {
    val bridge = FakeBridge()
    val root = bridge.addMap("root")
    val profile = bridge.addMap("map:profile@1")
    bridge.addCounter("counter:score@1", 5.0)
    root.data["profile"] = WireObjectData(objectId = "map:profile@1")
    root.data["flag"] = WireObjectData(boolean = true)
    profile.data["name"] = WireObjectData(string = "sachin")
    profile.data["score"] = WireObjectData(objectId = "counter:score@1")
    return bridge
  }

  @Test
  fun `path escaping and parsing round-trip`() {
    val bridge = graphBridge()
    // RTPO4b - dots inside segments are escaped
    assertEquals("a\\.b.c", DefaultPathObject(bridge, listOf("a.b", "c")).path())
    // RTPO6 - parsing honours the escape
    assertEquals(listOf("a.b", "c"), DefaultBasePathObject.parsePath("a\\.b.c"))
    assertEquals(listOf("users", "emma"), DefaultBasePathObject.parsePath("users.emma"))
  }

  @Test
  fun `resolution and typed reads`() {
    val bridge = graphBridge()
    val root = DefaultLiveMapPathObject(bridge, emptyList())

    assertEquals("sachin", root.at("profile.name").asString().value()) // RTPO7
    assertEquals(5.0, root.at("profile.score").asLiveCounter().value()) // RTTS6b
    assertEquals(true, root.at("flag").asBoolean().value())
    assertNull(root.at("missing.path").asString().value()) // RTPO3c1

    assertEquals(ValueType.LIVE_MAP, root.get("profile").getType()) // RTTS4b
    assertEquals(ValueType.UNKNOWN, root.get("missing").getType()) // RTTS4b3
    assertTrue(root.get("profile").exists()) // RTTS4a
    assertFalse(root.get("missing").exists())

    assertEquals(setOf("profile", "flag"), root.keys().toSet()) // RTPO10
    assertEquals(2L, root.size()) // RTPO12
    assertNull(root.at("profile.name").asLiveMap().size()) // size on non-map -> null

    // RTPO8 - instance() wraps live objects, null for primitives
    assertNotNull(root.get("profile").instance())
    assertNull(root.at("profile.name").instance())

    // RTPO14 - compactJson
    val json = root.compactJson()!!.asJsonObject
    assertEquals("sachin", json.getAsJsonObject("profile").get("name").asString)
    assertEquals(5.0, json.getAsJsonObject("profile").get("score").asDouble)
  }

  @Test
  fun `writes resolve the target and publish through the bridge`() {
    val bridge = graphBridge()
    val profile = DefaultLiveMapPathObject(bridge, emptyList()).get("profile").asLiveMap()

    profile.set("city", LiveMapValue.of("pune")).get() // RTPO15
    val mapSet = bridge.published.single().operation!!
    assertEquals(WireObjectOperationAction.MapSet, mapSet.action)
    assertEquals("map:profile@1", mapSet.objectId)
    assertEquals("pune", mapSet.mapSet!!.value.string)

    // RTPO3c2 - write on an unresolvable path fails with 92005
    val unresolved = DefaultLiveMapPathObject(bridge, listOf("missing"))
    val resolutionFailure = assertFailsWithAblyCode(92005) { unresolved.set("k", LiveMapValue.of(1)).get() }
    assertEquals(400, resolutionFailure.errorInfo.statusCode)

    // RTTS5d2 - write on a mismatched type fails with 92007
    val mismatch = DefaultLiveMapPathObject(bridge, listOf("flag"))
    assertFailsWithAblyCode(92007) { mismatch.remove("k").get() }
  }

  @Test
  fun `coverage rule follows RTO24c2 worked examples`() {
    val bridge = graphBridge()
    val register = bridge.pathSubscriptionRegister
    val sub = listOf("users")

    // depth=null: covers any depth
    assertTrue(register.coversPath(sub, null, listOf("users")))
    assertTrue(register.coversPath(sub, null, listOf("users", "emma", "visits")))
    // depth=1: covers ["users"] only
    assertTrue(register.coversPath(sub, 1, listOf("users")))
    assertFalse(register.coversPath(sub, 1, listOf("users", "emma")))
    // depth=2: covers ["users"], ["users","emma"] only
    assertTrue(register.coversPath(sub, 2, listOf("users", "emma")))
    assertFalse(register.coversPath(sub, 2, listOf("users", "emma", "visits")))
    // depth=3: covers up to ["users","emma","visits"]
    assertTrue(register.coversPath(sub, 3, listOf("users", "emma", "visits")))
    // non-matching prefix / shorter event path
    assertFalse(register.coversPath(sub, null, listOf("admins", "emma")))
    assertFalse(register.coversPath(listOf("users", "emma"), null, listOf("users")))
  }

  @Test
  fun `PathFinder returns every path to the target`() {
    val bridge = graphBridge()
    // add a second reference to the same counter
    (bridge.nodes["root"] as FakeMapNode).data["topScore"] = WireObjectData(objectId = "counter:score@1")

    val paths = PathFinder.findFullPaths(bridge, "counter:score@1")
    assertEquals(setOf(listOf("profile", "score"), listOf("topScore")), paths.toSet())
    assertEquals(listOf(emptyList()), PathFinder.findFullPaths(bridge, "root"))
    assertTrue(PathFinder.findFullPaths(bridge, "counter:unreachable@1").isEmpty())
  }

  @Test
  fun `path subscriptions deliver covered events with the public message`() {
    val bridge = graphBridge()
    val root = DefaultLiveMapPathObject(bridge, emptyList())
    val events = mutableListOf<PathObjectSubscriptionEvent>()
    val subscription = root.get("profile").subscribe { events.add(it) } // RTPO19

    val wireMessage = WireObjectMessage(
      serial = "01-ab@cde-0",
      operation = WireObjectOperation(
        action = WireObjectOperationAction.MapSet,
        objectId = "map:profile@1",
        mapSet = WireMapSet("name", WireObjectData(string = "sachin")),
      ),
    )
    bridge.notifyUpdated("map:profile@1", setOf("name"), wireMessage)

    val event = events.single()
    assertEquals("profile", event.getObject().path()) // RTPO19e1 - first covered candidate path
    val publicMessage = event.getMessage()!! // RTPO19e2
    assertEquals("test-channel", publicMessage.channel) // PAOM2e
    assertEquals("01-ab@cde-0", publicMessage.serial)
    assertEquals("name", publicMessage.operation.mapSet!!.key)

    // SUB2a - unsubscribe stops delivery
    subscription.unsubscribe()
    bridge.notifyUpdated("map:profile@1", setOf("name"), wireMessage)
    assertEquals(1, events.size)
  }

  @Test
  fun `instance subscriptions deliver events with the public message`() {
    val bridge = graphBridge()
    val instance = DefaultLiveMapPathObject(bridge, emptyList()).get("profile").instance()!!.asLiveMap()
    var received = 0
    instance.subscribe { event -> // RTINS16
      received++
      assertNotNull(event.getObject()) // RTINS16e1
      assertEquals(WireObjectOperationAction.MapSet.let { "MAP_SET" }, event.getMessage()!!.operation.action.name)
    }
    bridge.notifyUpdated(
      "map:profile@1",
      setOf("name"),
      WireObjectMessage(
        operation = WireObjectOperation(
          action = WireObjectOperationAction.MapSet,
          objectId = "map:profile@1",
          mapSet = WireMapSet("name", WireObjectData(string = "x")),
        )
      ),
    )
    assertEquals(1, received)
  }

  private fun assertFailsWithAblyCode(code: Int, block: () -> Unit): AblyException {
    try {
      block()
    } catch (e: Exception) {
      val ably = (e as? AblyException) ?: (e.cause as? AblyException)
      if (ably != null) {
        assertEquals(code, ably.errorInfo.code)
        return ably
      }
      throw AssertionError("Expected AblyException with code $code, got $e", e)
    }
    throw AssertionError("Expected AblyException with code $code, but nothing was thrown")
  }
}
